package cn.noname.coder.agent.cases.agent;

import cn.noname.coder.agent.api.dto.RunChangeActionResponseDTO;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRecordRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRunRepository;
import cn.noname.coder.agent.domain.workspace.adapter.repository.IRunChangeRepository;
import cn.noname.coder.agent.domain.workspace.adapter.port.IWorkspacePort;
import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.agent.model.entity.AuditEvent;
import cn.noname.coder.agent.domain.workspace.model.entity.RunChangeSet;
import cn.noname.coder.agent.domain.workspace.model.entity.RunFileChange;
import cn.noname.coder.agent.domain.workspace.model.valobj.ChangedFile;
import cn.noname.coder.agent.domain.workspace.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.types.enums.AuditEventType;
import cn.noname.coder.agent.types.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RunChangeService implements IRunChangeCase {

    private static final int MAX_REVERSIBLE_BYTES = 2 * 1024 * 1024;

    private final IRunChangeRepository changeRepository;
    private final IAgentRunRepository runRepository;
    private final IWorkspacePort workspacePort;
    private final IAgentRecordRepository recordRepository;

    public void record(WorkspaceDescriptor workspace, AgentRun run, List<ChangedFile> changedFiles) {
        if (workspace == null || run == null || changedFiles == null || changedFiles.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Map<String, RunFileChange> merged = new LinkedHashMap<>();
        for (RunFileChange existing : changeRepository.listFileChanges(run.getRunId())) {
            merged.put(existing.getFilePath(), existing);
        }
        boolean reversible = true;
        for (ChangedFile changedFile : changedFiles) {
            RunFileChange file = toFileChange(workspace, run, changedFile, now);
            merged.put(file.getFilePath(), file);
        }
        List<RunFileChange> files = new ArrayList<>(merged.values());
        for (RunFileChange file : files) {
            reversible = reversible && Boolean.TRUE.equals(file.getReversible());
        }
        RunChangeSet changeSet = RunChangeSet.builder()
                .runId(run.getRunId())
                .workspaceKey(run.getWorkspaceKey())
                .conversationId(run.getConversationId())
                .status("APPLIED")
                .reversible(reversible)
                .failureReason(reversible ? null : "存在不可自动撤销的文件")
                .createdAt(now)
                .updatedAt(now)
                .build();
        changeRepository.saveChangeSet(changeSet, files);
        log.info("已记录运行变更集 runId={} changedFiles={} reversible={}", run.getRunId(), files.size(), reversible);
    }

    @Override
    public RunChangeActionResponseDTO revert(String runId) {
        return apply(runId, true);
    }

    @Override
    public RunChangeActionResponseDTO restore(String runId) {
        return apply(runId, false);
    }

    public RunChangeActionResponseDTO validateRevert(String runId) {
        return validate(runId, true);
    }

    public List<String> revertedRunIds(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return List.of();
        }
        return changeRepository.listByConversationId(conversationId)
                .stream()
                .filter(changeSet -> "REVERTED".equals(changeSet.getStatus()))
                .map(RunChangeSet::getRunId)
                .toList();
    }

    public List<String> recentWorkStatusSummaries(String conversationId, String currentRunId, int limit) {
        if (!StringUtils.hasText(conversationId)) {
            return List.of();
        }
        int max = Math.max(1, limit);
        List<RunChangeSet> changeSets = changeRepository.listByConversationId(conversationId);
        List<String> summaries = new ArrayList<>();
        for (int i = changeSets.size() - 1; i >= 0 && summaries.size() < max; i--) {
            RunChangeSet changeSet = changeSets.get(i);
            if (changeSet == null || !StringUtils.hasText(changeSet.getRunId())
                    || changeSet.getRunId().equals(currentRunId)) {
                continue;
            }
            List<RunFileChange> files = changeRepository.listFileChanges(changeSet.getRunId());
            summaries.add(formatWorkStatus(changeSet, files));
        }
        return summaries;
    }

    private String formatWorkStatus(RunChangeSet changeSet, List<RunFileChange> files) {
        Map<String, Long> counts = files == null ? Map.of() : files.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        file -> StringUtils.hasText(file.getChangeType()) ? file.getChangeType().toUpperCase() : "UNKNOWN",
                        LinkedHashMap::new,
                        java.util.stream.Collectors.counting()));
        String paths = files == null || files.isEmpty()
                ? "无文件记录"
                : files.stream()
                .map(RunFileChange::getFilePath)
                .filter(StringUtils::hasText)
                .limit(5)
                .collect(java.util.stream.Collectors.joining(", "));
        return "- runId=%s, status=%s, files=%d, ADD=%d, MODIFY=%d, DELETE=%d, OVERWRITE=%d, paths=%s"
                .formatted(
                        changeSet.getRunId(),
                        StringUtils.hasText(changeSet.getStatus()) ? changeSet.getStatus() : "UNKNOWN",
                        files == null ? 0 : files.size(),
                        counts.getOrDefault("ADD", 0L),
                        counts.getOrDefault("MODIFY", 0L),
                        counts.getOrDefault("DELETE", 0L),
                        counts.getOrDefault("OVERWRITE", 0L),
                        paths);
    }

    private RunChangeActionResponseDTO validate(String runId, boolean revert) {
        AgentRun run = runRepository.findByRunId(runId)
                .orElseThrow(() -> new AppException("RUN_NOT_FOUND", "运行不存在：" + runId));
        WorkspaceDescriptor workspace = workspacePort.resolve(run.getWorkspaceKey())
                .orElseThrow(() -> new AppException("WORKSPACE_NOT_FOUND", "workspaceKey 未配置：" + run.getWorkspaceKey()));
        RunChangeSet changeSet = changeRepository.findChangeSet(runId)
                .orElseThrow(() -> new AppException("CHANGE_SET_NOT_FOUND", "运行没有可撤销变更：" + runId));
        if (revert && !"APPLIED".equals(changeSet.getStatus())) {
            return new RunChangeActionResponseDTO(runId, changeSet.getStatus(), List.of(), List.of(),
                    "当前变更集不能撤销：" + changeSet.getStatus());
        }
        List<RunFileChange> files = changeRepository.listFileChanges(runId);
        List<String> irreversible = files.stream()
                .filter(file -> !Boolean.TRUE.equals(file.getReversible()))
                .map(file -> file.getFilePath() + "：" + file.getIrreversibleReason())
                .toList();
        List<String> conflicts = irreversible.isEmpty() ? conflicts(workspace, files, revert) : List.of();
        return new RunChangeActionResponseDTO(runId,
                conflicts.isEmpty() && irreversible.isEmpty() ? "READY" : "CONFLICTED",
                conflicts,
                irreversible,
                conflicts.isEmpty() && irreversible.isEmpty() ? "可以执行" : "存在冲突或不可逆文件");
    }

    private RunChangeActionResponseDTO apply(String runId, boolean revert) {
        AgentRun run = runRepository.findByRunId(runId)
                .orElseThrow(() -> new AppException("RUN_NOT_FOUND", "运行不存在：" + runId));
        WorkspaceDescriptor workspace = workspacePort.resolve(run.getWorkspaceKey())
                .orElseThrow(() -> new AppException("WORKSPACE_NOT_FOUND", "workspaceKey 未配置：" + run.getWorkspaceKey()));
        RunChangeSet changeSet = changeRepository.findChangeSet(runId)
                .orElseThrow(() -> new AppException("CHANGE_SET_NOT_FOUND", "运行没有可撤销变更：" + runId));
        if (revert && !"APPLIED".equals(changeSet.getStatus())) {
            throw new AppException("CHANGE_SET_NOT_APPLIED", "当前变更集不能撤销：" + changeSet.getStatus());
        }
        if (!revert && !"REVERTED".equals(changeSet.getStatus())) {
            throw new AppException("CHANGE_SET_NOT_REVERTED", "当前变更集不能还原：" + changeSet.getStatus());
        }
        List<RunFileChange> files = changeRepository.listFileChanges(runId);
        List<String> irreversible = files.stream()
                .filter(file -> !Boolean.TRUE.equals(file.getReversible()))
                .map(file -> file.getFilePath() + "：" + file.getIrreversibleReason())
                .toList();
        if (!irreversible.isEmpty()) {
            changeSet.setStatus("CONFLICTED");
            changeSet.setFailureReason("存在不可自动撤销的文件");
            changeSet.setUpdatedAt(LocalDateTime.now());
            changeRepository.updateChangeSet(changeSet);
            return new RunChangeActionResponseDTO(runId, changeSet.getStatus(), List.of(), irreversible, changeSet.getFailureReason());
        }
        List<String> conflicts = conflicts(workspace, files, revert);
        if (!conflicts.isEmpty()) {
            changeSet.setStatus("CONFLICTED");
            changeSet.setFailureReason("文件已被手动修改，拒绝覆盖");
            changeSet.setUpdatedAt(LocalDateTime.now());
            changeRepository.updateChangeSet(changeSet);
            return new RunChangeActionResponseDTO(runId, changeSet.getStatus(), conflicts, List.of(), changeSet.getFailureReason());
        }
        files.forEach(file -> applyFile(workspace, file, revert));
        changeSet.setStatus(revert ? "REVERTED" : "APPLIED");
        changeSet.setFailureReason(null);
        changeSet.setUpdatedAt(LocalDateTime.now());
        changeRepository.updateChangeSet(changeSet);
        recordAudit(runId, revert ? "撤销运行改动" : "还原运行改动", "status=" + changeSet.getStatus());
        log.info("{}运行变更完成 runId={} fileCount={}", revert ? "撤销" : "还原", runId, files.size());
        return new RunChangeActionResponseDTO(runId, changeSet.getStatus(), List.of(), List.of(),
                revert ? "已撤销当前任务改动" : "已还原当前任务改动");
    }

    private RunFileChange toFileChange(WorkspaceDescriptor workspace, AgentRun run, ChangedFile changedFile, LocalDateTime now) {
        String path = changedFile.relativePath().replace('\\', '/');
        String before = changedFile.beforeContent();
        String after = changedFile.afterContent();
        if ("ADD".equalsIgnoreCase(changedFile.changeType())) {
            before = null;
        }
        if ("DELETE".equalsIgnoreCase(changedFile.changeType())) {
            after = null;
        }
        String reason = irreversibleReason(path, before, after);
        boolean reversible = reason == null;
        Path beforeSnapshot = null;
        Path afterSnapshot = null;
        if (reversible) {
            beforeSnapshot = writeSnapshot(workspace, run.getRunId(), "before", path, before);
            afterSnapshot = writeSnapshot(workspace, run.getRunId(), "after", path, after);
        }
        return RunFileChange.builder()
                .runId(run.getRunId())
                .filePath(path)
                .changeType(changedFile.changeType())
                .beforeHash(StringUtils.hasText(changedFile.beforeHash()) ? changedFile.beforeHash() : hashNullable(before))
                .afterHash(StringUtils.hasText(changedFile.afterHash()) ? changedFile.afterHash() : hashNullable(after))
                .beforeSnapshotPath(beforeSnapshot == null ? null : workspace.rootPath().relativize(beforeSnapshot).toString().replace('\\', '/'))
                .afterSnapshotPath(afterSnapshot == null ? null : workspace.rootPath().relativize(afterSnapshot).toString().replace('\\', '/'))
                .reversible(reversible)
                .irreversibleReason(reason)
                .createdAt(now)
                .build();
    }

    private String irreversibleReason(String path, String before, String after) {
        if (path == null || path.isBlank()) {
            return "文件路径为空";
        }
        if (containsBinaryMarker(before) || containsBinaryMarker(after)) {
            return "该文件疑似二进制内容";
        }
        int beforeBytes = before == null ? 0 : before.getBytes(StandardCharsets.UTF_8).length;
        int afterBytes = after == null ? 0 : after.getBytes(StandardCharsets.UTF_8).length;
        if (beforeBytes > MAX_REVERSIBLE_BYTES || afterBytes > MAX_REVERSIBLE_BYTES) {
            return "文件超过自动撤销大小上限";
        }
        return null;
    }

    private boolean containsBinaryMarker(String content) {
        return content != null && content.indexOf('\0') >= 0;
    }

    @SneakyThrows
    private Path writeSnapshot(WorkspaceDescriptor workspace, String runId, String side, String filePath, String content) {
        if (content == null) {
            return null;
        }
        Path snapshot = workspace.rootPath()
                .resolve(".coder/runs")
                .resolve(runId)
                .resolve("change-snapshot")
                .resolve(side)
                .resolve(filePath.replace(":", "_"));
        Files.createDirectories(snapshot.getParent());
        Files.writeString(snapshot, content, StandardCharsets.UTF_8);
        return snapshot;
    }

    private List<String> conflicts(WorkspaceDescriptor workspace, List<RunFileChange> files, boolean revert) {
        List<String> conflicts = new ArrayList<>();
        for (RunFileChange file : files) {
            Path target = workspace.rootPath().resolve(file.getFilePath()).normalize();
            if (!target.startsWith(workspace.rootPath().normalize())) {
                conflicts.add(file.getFilePath() + "：路径越界");
                continue;
            }
            String expected = revert ? file.getAfterHash() : file.getBeforeHash();
            String actual = currentHash(target);
            if (!equalsHash(expected, actual)) {
                conflicts.add(file.getFilePath() + "：当前文件状态与预期不一致");
            }
        }
        return conflicts;
    }

    private boolean equalsHash(String expected, String actual) {
        if (!StringUtils.hasText(expected)) {
            return !StringUtils.hasText(actual);
        }
        return expected.equals(actual);
    }

    @SneakyThrows
    private void applyFile(WorkspaceDescriptor workspace, RunFileChange file, boolean revert) {
        Path target = workspace.rootPath().resolve(file.getFilePath()).normalize();
        if (!target.startsWith(workspace.rootPath().normalize())) {
            throw new AppException("PATH_ESCAPE", "路径越界：" + file.getFilePath());
        }
        String snapshot = revert ? file.getBeforeSnapshotPath() : file.getAfterSnapshotPath();
        String targetHash = revert ? file.getBeforeHash() : file.getAfterHash();
        if (!StringUtils.hasText(targetHash) && !StringUtils.hasText(snapshot)) {
            Files.deleteIfExists(target);
            return;
        }
        Path snapshotPath = workspace.rootPath().resolve(snapshot).normalize();
        if (!snapshotPath.startsWith(workspace.rootPath().normalize()) || !Files.exists(snapshotPath)) {
            throw new AppException("SNAPSHOT_NOT_FOUND", "快照不存在：" + snapshot);
        }
        Files.createDirectories(target.getParent());
        Files.writeString(target, Files.readString(snapshotPath, StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    private String currentHash(Path target) {
        try {
            if (!Files.exists(target)) {
                return null;
            }
            return sha256(Files.readString(target, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return "HASH_ERROR:" + e.getMessage();
        }
    }

    private String hashNullable(String content) {
        return content == null ? null : sha256(content);
    }

    private String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(content.hashCode());
        }
    }

    private void recordAudit(String runId, String message, String detail) {
        recordRepository.saveAuditEvent(AuditEvent.builder()
                .runId(runId)
                .eventType(AuditEventType.HIGH_RISK_TOOL_USED)
                .message(message)
                .detail(detail)
                .createdAt(LocalDateTime.now())
                .build());
    }
}
