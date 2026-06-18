package cn.noname.coder.agent.cases.agent;

import cn.noname.coder.agent.api.dto.DiffFileDTO;
import cn.noname.coder.agent.api.dto.DiffSummaryDTO;
import cn.noname.coder.agent.domain.agent.adapter.port.IArtifactPort;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Component
public class DiffSummaryAssembler {

    public DiffSummaryDTO load(IArtifactPort artifactPort, WorkspaceDescriptor workspace, String runId) {
        if (artifactPort == null || workspace == null || !StringUtils.hasText(runId)) {
            return DiffSummaryDTO.empty();
        }
        List<DiffFileDTO> files = artifactPort.readChangedFiles(workspace, runId).stream()
                .map(this::toFile)
                .toList();
        int added = files.stream().mapToInt(DiffFileDTO::addedLines).sum();
        int deleted = files.stream().mapToInt(DiffFileDTO::deletedLines).sum();
        return new DiffSummaryDTO(files.size(), added, deleted, files);
    }

    private DiffFileDTO toFile(Map<String, Object> item) {
        return new DiffFileDTO(
                string(item.get("relativePath")),
                string(item.get("changeType")),
                integer(item.get("addedLines")),
                integer(item.get("deletedLines")),
                string(item.get("patchSnippet"))
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
