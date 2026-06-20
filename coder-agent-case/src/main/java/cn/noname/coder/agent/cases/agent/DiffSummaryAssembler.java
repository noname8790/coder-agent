package cn.noname.coder.agent.cases.agent;

import cn.noname.coder.agent.api.dto.DiffFileDTO;
import cn.noname.coder.agent.api.dto.DiffSummaryDTO;
import cn.noname.coder.agent.domain.agent.adapter.port.IArtifactPort;
import cn.noname.coder.agent.domain.agent.adapter.repository.IRunChangeRepository;
import cn.noname.coder.agent.domain.agent.model.entity.RunChangeSet;
import cn.noname.coder.agent.domain.agent.model.entity.RunFileChange;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
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
        List<DiffFileDTO> files = artifactPort.readChangedFiles(workspace, runId).stream()
                .map(item -> toFile(item, fileChanges))
                .toList();
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
