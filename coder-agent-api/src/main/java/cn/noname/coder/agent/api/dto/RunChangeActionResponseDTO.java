package cn.noname.coder.agent.api.dto;

import java.util.List;

public record RunChangeActionResponseDTO(
        String runId,
        String status,
        List<String> conflictFiles,
        List<String> irreversibleFiles,
        String message
) {
}
