package cn.noname.coder.agent.cases.agent;

import cn.noname.coder.agent.api.dto.DiffFileDTO;
import cn.noname.coder.agent.api.dto.DiffSummaryDTO;
import cn.noname.coder.agent.domain.agent.adapter.port.IArtifactPort;
import cn.noname.coder.agent.domain.workspace.adapter.repository.IRunChangeRepository;
import cn.noname.coder.agent.domain.workspace.model.entity.RunChangeSet;
import cn.noname.coder.agent.domain.workspace.model.entity.RunFileChange;
import cn.noname.coder.agent.domain.workspace.model.valobj.WorkspaceDescriptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DiffSummaryAssembler {

    private final IRunChangeRepository changeRepository;

    public DiffSummaryDTO load(IArtifactPort artifactPort, WorkspaceDescriptor workspace, String runId) {
        if (artifactPort == null || workspace == null || !StringUtils.hasText(runId)) {
            return DiffSummaryDTO.empty();
        }
        Map<String, RunFileChange> fileChanges = changeRepository.listFileChanges(runId).stream()
                .collect(Collectors.toMap(RunFileChange::getFilePath, Function.identity(), (left, right) -> left));
        List<DiffFileDTO> files = new ArrayList<>(artifactPort.readChangedFiles(workspace, runId).stream()
                .map(item -> toFile(item, fileChanges))
                .toList());
        Set<String> artifactPaths = files.stream().map(DiffFileDTO::path).collect(Collectors.toCollection(HashSet::new));
        fileChanges.values().stream()
                .filter(change -> !artifactPaths.contains(change.getFilePath()))
                .map(change -> toFile(workspace, change))
                .forEach(files::add);
        int added = files.stream().mapToInt(DiffFileDTO::addedLines).sum();
        int deleted = files.stream().mapToInt(DiffFileDTO::deletedLines).sum();
        RunChangeSet changeSet = changeRepository.findChangeSet(runId).orElse(null);
        return new DiffSummaryDTO(files.size(), added, deleted, files,
                changeSet == null ? null : changeSet.getStatus(),
                changeSet != null && Boolean.TRUE.equals(changeSet.getReversible()));
    }

    private DiffFileDTO toFile(Map<String, Object> item, Map<String, RunFileChange> fileChanges) {
        String path = string(item.get("relativePath"));
        RunFileChange change = fileChanges.get(path);
        return new DiffFileDTO(
                path,
                string(item.get("changeType")),
                integer(item.get("addedLines")),
                integer(item.get("deletedLines")),
                string(item.get("patchSnippet")),
                change == null || Boolean.TRUE.equals(change.getReversible()),
                change == null ? null : change.getIrreversibleReason()
        );
    }

    private DiffFileDTO toFile(WorkspaceDescriptor workspace, RunFileChange change) {
        String before = readSnapshot(workspace, change.getBeforeSnapshotPath());
        String after = readSnapshot(workspace, change.getAfterSnapshotPath());
        DiffStats stats = diffStats(before, after);
        return new DiffFileDTO(
                change.getFilePath(),
                change.getChangeType(),
                stats.addedLines(),
                stats.deletedLines(),
                stats.patchSnippet(),
                Boolean.TRUE.equals(change.getReversible()),
                change.getIrreversibleReason()
        );
    }

    private String readSnapshot(WorkspaceDescriptor workspace, String snapshotPath) {
        if (!StringUtils.hasText(snapshotPath)) {
            return "";
        }
        try {
            Path path = workspace.rootPath().resolve(snapshotPath).normalize();
            if (!path.startsWith(workspace.rootPath().normalize()) || !Files.exists(path)) {
                return "";
            }
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
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
        if (!StringUtils.hasText(content)) {
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
        for (int i = afterEnd; i < contextAfterEnd && i < after.size(); i++) {
            appendPatchLine(patch, " ", i + 1, after.get(i));
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

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
