package cn.noname.coder.agent.infrastructure.adapter.port;

import cn.noname.coder.agent.domain.agent.adapter.port.IArtifactPort;
import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.agent.model.entity.RunArtifact;
import cn.noname.coder.agent.domain.agent.model.valobj.AgentPermissionLevel;
import cn.noname.coder.agent.domain.agent.model.valobj.ChangedFile;
import cn.noname.coder.agent.domain.agent.model.valobj.TestCommandReport;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.types.enums.ArtifactType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件工件端口实现，固定写入 workspaceRoot/.coder/runs/{runId}/。
 */
@Component
@Slf4j
public class ArtifactFilePort implements IArtifactPort {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST_TYPE = new TypeReference<>() {
    };

    @Override
    @SneakyThrows
    public List<RunArtifact> initializeRun(WorkspaceDescriptor workspace, AgentRun run) {
        Path dir = runDir(workspace, run.getRunId());
        Files.createDirectories(dir.resolve("context-snapshot"));
        Files.createDirectories(dir.resolve("tool-output"));

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("runId", run.getRunId());
        meta.put("workspaceKey", run.getWorkspaceKey());
        meta.put("task", run.getTask());
        meta.put("model", run.getModel());
        meta.put("createdAt", run.getCreatedAt());
        Path metaFile = dir.resolve("run-meta.json");
        writeJson(metaFile, meta);
        Path traceFile = dir.resolve("trace.jsonl");
        if (!Files.exists(traceFile)) {
            Files.createFile(traceFile);
        }
        log.info("已初始化运行工件 runId={} dir={}", run.getRunId(), dir);
        return List.of(
                artifact(run.getRunId(), ArtifactType.RUN_META, workspace, metaFile),
                artifact(run.getRunId(), ArtifactType.TRACE, workspace, traceFile)
        );
    }

    @Override
    @SneakyThrows
    public RunArtifact appendTrace(WorkspaceDescriptor workspace, String runId, Map<String, Object> event) {
        Path file = runDir(workspace, runId).resolve("trace.jsonl");
        Files.createDirectories(file.getParent());
        Files.writeString(file, objectMapper.writeValueAsString(event) + System.lineSeparator(), StandardCharsets.UTF_8,
                Files.exists(file) ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.CREATE);
        return artifact(runId, ArtifactType.TRACE, workspace, file);
    }

    @Override
    @SneakyThrows
    public RunArtifact writeContextSnapshot(WorkspaceDescriptor workspace, String runId, int callNo, Map<String, Object> snapshot) {
        Path file = runDir(workspace, runId).resolve("context-snapshot").resolve("model-call-" + callNo + ".json");
        writeJson(file, snapshot);
        return artifact(runId, ArtifactType.CONTEXT_SNAPSHOT, workspace, file);
    }

    @Override
    @SneakyThrows
    public RunArtifact writeToolOutput(WorkspaceDescriptor workspace, String runId, int callNo, String output) {
        Path file = runDir(workspace, runId).resolve("tool-output").resolve("tool-call-" + callNo + ".txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, output == null ? "" : output, StandardCharsets.UTF_8);
        return artifact(runId, ArtifactType.TOOL_OUTPUT, workspace, file);
    }

    @Override
    @SneakyThrows
    public RunArtifact writeFinalResult(WorkspaceDescriptor workspace, AgentRun run, Map<String, Object> result) {
        Path file = runDir(workspace, run.getRunId()).resolve("final-result.json");
        writeJson(file, result);
        log.info("已写入最终结果工件 runId={} status={} path={}", run.getRunId(), run.getStatus(), file);
        return artifact(run.getRunId(), ArtifactType.FINAL_RESULT, workspace, file);
    }

    @Override
    @SneakyThrows
    public List<RunArtifact> writeReviewArtifacts(WorkspaceDescriptor workspace,
                                                  AgentRun run,
                                                  List<ChangedFile> changedFiles,
                                                  List<TestCommandReport> testReports) {
        if ((changedFiles == null || changedFiles.isEmpty()) && (testReports == null || testReports.isEmpty())) {
            return List.of();
        }
        log.info("开始写入审查工件 runId={} changedFileCount={} testReportCount={}",
                run.getRunId(),
                changedFiles == null ? 0 : changedFiles.size(),
                testReports == null ? 0 : testReports.size());
        List<RunArtifact> artifacts = new ArrayList<>();
        if (changedFiles != null && !changedFiles.isEmpty()) {
            Path diff = runDir(workspace, run.getRunId()).resolve("patch.diff");
            Files.createDirectories(diff.getParent());
            Files.writeString(diff, buildDiff(changedFiles), StandardCharsets.UTF_8);
            artifacts.add(artifact(run.getRunId(), ArtifactType.PATCH_DIFF, workspace, diff));

            Path changed = runDir(workspace, run.getRunId()).resolve("changed-files.json");
            writeJson(changed, changedFiles.stream().map(this::changedFileJson).toList());
            artifacts.add(artifact(run.getRunId(), ArtifactType.CHANGED_FILES, workspace, changed));

            artifacts.addAll(writeRollbackArtifacts(workspace, run, changedFiles));
        }
        if (testReports != null && !testReports.isEmpty()) {
            Path report = runDir(workspace, run.getRunId()).resolve("test-report.json");
            writeJson(report, testReports);
            artifacts.add(artifact(run.getRunId(), ArtifactType.TEST_REPORT, workspace, report));
        }
        Path review = runDir(workspace, run.getRunId()).resolve("review-summary.md");
        Files.createDirectories(review.getParent());
        Files.writeString(review, buildReviewSummary(run, changedFiles, testReports), StandardCharsets.UTF_8);
        artifacts.add(artifact(run.getRunId(), ArtifactType.REVIEW_SUMMARY, workspace, review));
        if (run.getPermissionLevel() != null
                && run.getPermissionLevel().atLeast(AgentPermissionLevel.DEFAULT)
                && changedFiles != null
                && !changedFiles.isEmpty()) {
            Path prDraft = runDir(workspace, run.getRunId()).resolve("pull-request.md");
            Files.writeString(prDraft, buildPrDraft(run, changedFiles, testReports), StandardCharsets.UTF_8);
            artifacts.add(artifact(run.getRunId(), ArtifactType.PR_DRAFT, workspace, prDraft));
            log.info("已生成 PR 草稿 runId={} path={}", run.getRunId(), prDraft);
        }
        log.info("已写入审查工件 runId={} artifactCount={}", run.getRunId(), artifacts.size());
        return artifacts;
    }

    @Override
    @SneakyThrows
    public List<Map<String, Object>> readTrace(WorkspaceDescriptor workspace, String runId) {
        Path file = runDir(workspace, runId).resolve("trace.jsonl");
        if (!Files.exists(file)) {
            return List.of();
        }
        List<Map<String, Object>> events = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                events.add(objectMapper.readValue(line, MAP_TYPE));
            }
        }
        return events;
    }

    @Override
    @SneakyThrows
    public List<Map<String, Object>> readChangedFiles(WorkspaceDescriptor workspace, String runId) {
        Path file = runDir(workspace, runId).resolve("changed-files.json");
        if (!Files.exists(file)) {
            return List.of();
        }
        return objectMapper.readValue(file.toFile(), MAP_LIST_TYPE);
    }

    private Path runDir(WorkspaceDescriptor workspace, String runId) {
        return workspace.rootPath().resolve(".coder").resolve("runs").resolve(runId).normalize();
    }

    private Map<String, Object> changedFileJson(ChangedFile changedFile) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("relativePath", changedFile.relativePath());
        item.put("changeType", changedFile.changeType());
        item.put("beforeHash", changedFile.beforeHash());
        item.put("afterHash", changedFile.afterHash());
        item.put("toolCallNo", changedFile.toolCallNo());
        DiffStats stats = diffStats(changedFile.beforeContent(), changedFile.afterContent());
        item.put("addedLines", stats.addedLines());
        item.put("deletedLines", stats.deletedLines());
        item.put("patchSnippet", stats.patchSnippet());
        return item;
    }

    private DiffStats diffStats(String beforeContent, String afterContent) {
        List<String> before = lines(beforeContent);
        List<String> after = lines(afterContent);
        int prefix = 0;
        while (prefix < before.size() && prefix < after.size() && before.get(prefix).equals(after.get(prefix))) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < before.size() - prefix
                && suffix < after.size() - prefix
                && before.get(before.size() - 1 - suffix).equals(after.get(after.size() - 1 - suffix))) {
            suffix++;
        }
        int beforeEnd = before.size() - suffix;
        int afterEnd = after.size() - suffix;
        int deleted = Math.max(0, beforeEnd - prefix);
        int added = Math.max(0, afterEnd - prefix);
        return new DiffStats(added, deleted, buildPatchSnippet(before, after, prefix, beforeEnd, afterEnd));
    }

    private List<String> lines(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        return content.lines().toList();
    }

    private String buildPatchSnippet(List<String> before, List<String> after, int prefix, int beforeEnd, int afterEnd) {
        if (prefix == beforeEnd && prefix == afterEnd) {
            return "";
        }
        StringBuilder patch = new StringBuilder();
        int contextStart = Math.max(0, prefix - 2);
        int contextBeforeEnd = Math.min(before.size(), beforeEnd + 2);
        int contextAfterEnd = Math.min(after.size(), afterEnd + 2);
        for (int i = contextStart; i < prefix; i++) {
            appendPatchLine(patch, " ", i + 1, before.get(i));
        }
        for (int i = prefix; i < beforeEnd; i++) {
            appendPatchLine(patch, "-", i + 1, before.get(i));
        }
        for (int i = prefix; i < afterEnd; i++) {
            appendPatchLine(patch, "+", i + 1, after.get(i));
        }
        int contextEnd = Math.max(contextBeforeEnd, contextAfterEnd);
        for (int i = Math.max(beforeEnd, afterEnd); i < contextEnd && i < before.size(); i++) {
            appendPatchLine(patch, " ", i + 1, before.get(i));
        }
        return patch.toString().stripTrailing();
    }

    private void appendPatchLine(StringBuilder patch, String sign, int lineNo, String content) {
        patch.append(sign)
                .append(" ")
                .append(lineNo)
                .append("     ")
                .append(content)
                .append(System.lineSeparator());
    }

    private record DiffStats(int addedLines, int deletedLines, String patchSnippet) {
    }

    private String buildDiff(List<ChangedFile> changedFiles) {
        StringBuilder diff = new StringBuilder();
        for (ChangedFile file : changedFiles) {
            diff.append("diff --git a/").append(file.relativePath()).append(" b/").append(file.relativePath()).append(System.lineSeparator());
            if ("ADD".equals(file.changeType())) {
                diff.append("new file mode 100644").append(System.lineSeparator());
                diff.append("--- /dev/null").append(System.lineSeparator());
                diff.append("+++ b/").append(file.relativePath()).append(System.lineSeparator());
            } else if ("DELETE".equals(file.changeType())) {
                diff.append("deleted file mode 100644").append(System.lineSeparator());
                diff.append("--- a/").append(file.relativePath()).append(System.lineSeparator());
                diff.append("+++ /dev/null").append(System.lineSeparator());
            } else {
                diff.append("--- a/").append(file.relativePath()).append(System.lineSeparator());
                diff.append("+++ b/").append(file.relativePath()).append(System.lineSeparator());
            }
            diff.append("@@").append(System.lineSeparator());
            appendPrefixed(diff, "-", file.beforeContent());
            appendPrefixed(diff, "+", file.afterContent());
        }
        return diff.toString();
    }

    @SneakyThrows
    private List<RunArtifact> writeRollbackArtifacts(WorkspaceDescriptor workspace, AgentRun run, List<ChangedFile> changedFiles) {
        List<RunArtifact> artifacts = new ArrayList<>();
        Path rollback = runDir(workspace, run.getRunId()).resolve("rollback.patch");
        Files.writeString(rollback, buildRollbackPatch(changedFiles), StandardCharsets.UTF_8);
        artifacts.add(artifact(run.getRunId(), ArtifactType.ROLLBACK_PATCH, workspace, rollback));

        Path backupDir = runDir(workspace, run.getRunId()).resolve("file-backup");
        Files.createDirectories(backupDir);
        for (ChangedFile file : changedFiles) {
            if (file.beforeContent() == null || file.beforeContent().isEmpty()) {
                continue;
            }
            Path backup = backupDir.resolve(file.relativePath().replace("/", "__"));
            Files.writeString(backup, file.beforeContent(), StandardCharsets.UTF_8);
            artifacts.add(artifact(run.getRunId(), ArtifactType.FILE_BACKUP, workspace, backup));
        }
        return artifacts;
    }

    private String buildRollbackPatch(List<ChangedFile> changedFiles) {
        StringBuilder patch = new StringBuilder();
        patch.append("# Rollback Patch").append(System.lineSeparator()).append(System.lineSeparator());
        patch.append("# 说明：该文件记录人工回滚材料；必要时请结合 file-backup/ 手动恢复。").append(System.lineSeparator()).append(System.lineSeparator());
        for (ChangedFile file : changedFiles) {
            patch.append("diff --git a/").append(file.relativePath()).append(" b/").append(file.relativePath()).append(System.lineSeparator());
            if ("ADD".equals(file.changeType())) {
                patch.append("# 回滚新增文件：删除 ").append(file.relativePath()).append(System.lineSeparator());
                patch.append("--- a/").append(file.relativePath()).append(System.lineSeparator());
                patch.append("+++ /dev/null").append(System.lineSeparator());
                appendPrefixed(patch, "-", file.afterContent());
            } else if ("DELETE".equals(file.changeType())) {
                patch.append("# 回滚删除文件：从备份恢复 ").append(file.relativePath()).append(System.lineSeparator());
                patch.append("--- /dev/null").append(System.lineSeparator());
                patch.append("+++ b/").append(file.relativePath()).append(System.lineSeparator());
                appendPrefixed(patch, "+", file.beforeContent());
            } else {
                patch.append("# 回滚修改/覆盖文件：恢复覆盖前内容 ").append(file.relativePath()).append(System.lineSeparator());
                patch.append("--- a/").append(file.relativePath()).append(System.lineSeparator());
                patch.append("+++ b/").append(file.relativePath()).append(System.lineSeparator());
                appendPrefixed(patch, "-", file.afterContent());
                appendPrefixed(patch, "+", file.beforeContent());
            }
            patch.append(System.lineSeparator());
        }
        return patch.toString();
    }

    private void appendPrefixed(StringBuilder diff, String prefix, String content) {
        if (content == null || content.isEmpty()) {
            return;
        }
        content.lines().forEach(line -> diff.append(prefix).append(line).append(System.lineSeparator()));
    }

    private String buildReviewSummary(AgentRun run, List<ChangedFile> changedFiles, List<TestCommandReport> testReports) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Agent Run Review Summary").append(System.lineSeparator()).append(System.lineSeparator());
        markdown.append("- runId: ").append(run.getRunId()).append(System.lineSeparator());
        markdown.append("- task: ").append(run.getTask()).append(System.lineSeparator());
        markdown.append("- permissionLevel: ").append(run.getPermissionLevel() == null ? "DEFAULT" : run.getPermissionLevel().name()).append(System.lineSeparator());
        markdown.append("- model: ").append(run.getModel()).append(System.lineSeparator()).append(System.lineSeparator());
        markdown.append("## Changed Files").append(System.lineSeparator());
        if (changedFiles == null || changedFiles.isEmpty()) {
            markdown.append("- None").append(System.lineSeparator());
        } else {
            changedFiles.forEach(file -> markdown.append("- ").append(file.changeType()).append(" ").append(file.relativePath()).append(System.lineSeparator()));
        }
        markdown.append(System.lineSeparator()).append("## Test Results").append(System.lineSeparator());
        if (testReports == null || testReports.isEmpty()) {
            markdown.append("- NOT_RUN").append(System.lineSeparator());
        } else {
            testReports.forEach(report -> markdown.append("- ").append(report.status()).append(" `").append(report.command()).append("` exitCode=").append(report.exitCode()).append(System.lineSeparator()));
        }
        markdown.append(System.lineSeparator()).append("## Review Focus").append(System.lineSeparator());
        markdown.append("- 人工确认 diff 是否符合任务意图").append(System.lineSeparator());
        markdown.append("- 人工确认测试失败时是否需要继续修复").append(System.lineSeparator());
        markdown.append("- 第三版不会自动 push 或创建远程 PR").append(System.lineSeparator());
        return markdown.toString();
    }

    private String buildPrDraft(AgentRun run, List<ChangedFile> changedFiles, List<TestCommandReport> testReports) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# PR 草稿：").append(run.getTask()).append(System.lineSeparator()).append(System.lineSeparator());
        markdown.append("## 摘要").append(System.lineSeparator());
        markdown.append("- runId: ").append(run.getRunId()).append(System.lineSeparator());
        markdown.append("- permissionLevel: ").append(run.getPermissionLevel()).append(System.lineSeparator());
        markdown.append("- model: ").append(run.getModel()).append(System.lineSeparator());
        if (run.getCommitHash() != null) {
            markdown.append("- commit: ").append(run.getCommitHash()).append(System.lineSeparator());
        }
        markdown.append(System.lineSeparator()).append("## 变更文件").append(System.lineSeparator());
        changedFiles.forEach(file -> markdown.append("- ").append(file.changeType()).append(" ").append(file.relativePath()).append(System.lineSeparator()));
        markdown.append(System.lineSeparator()).append("## 测试结果").append(System.lineSeparator());
        if (testReports == null || testReports.isEmpty()) {
            markdown.append("- NOT_RUN").append(System.lineSeparator());
        } else {
            testReports.forEach(report -> markdown.append("- ").append(report.status())
                    .append(" `").append(report.command()).append("` exitCode=").append(report.exitCode()).append(System.lineSeparator()));
        }
        markdown.append(System.lineSeparator()).append("## 风险点").append(System.lineSeparator());
        markdown.append("- 请人工确认覆盖/删除文件是否符合预期").append(System.lineSeparator());
        markdown.append("- 请人工确认测试覆盖是否充分").append(System.lineSeparator());
        markdown.append(System.lineSeparator()).append("## 回滚说明").append(System.lineSeparator());
        markdown.append("- 查看 `.coder/runs/").append(run.getRunId()).append("/rollback.patch`").append(System.lineSeparator());
        markdown.append("- 查看 `.coder/runs/").append(run.getRunId()).append("/file-backup/`").append(System.lineSeparator());
        markdown.append(System.lineSeparator()).append("## Reviewer Checklist").append(System.lineSeparator());
        markdown.append("- [ ] 变更范围符合任务目标").append(System.lineSeparator());
        markdown.append("- [ ] 无敏感信息写入").append(System.lineSeparator());
        markdown.append("- [ ] 测试/构建结果可接受").append(System.lineSeparator());
        markdown.append("- [ ] 回滚材料可用").append(System.lineSeparator());
        return markdown.toString();
    }

    @SneakyThrows
    private void writeJson(Path file, Object value) {
        Files.createDirectories(file.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), value);
    }

    @SneakyThrows
    private RunArtifact artifact(String runId, ArtifactType type, WorkspaceDescriptor workspace, Path file) {
        return RunArtifact.builder()
                .runId(runId)
                .artifactType(type)
                .relativePath(workspace.rootPath().relativize(file).toString().replace('\\', '/'))
                .fileSize(Files.exists(file) ? Files.size(file) : 0L)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
