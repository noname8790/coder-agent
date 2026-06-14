package cn.noname.coder.agent.api.dto;

import java.time.LocalDateTime;
import java.util.List;

public record EvalRunResponseDTO(
        String evalId,
        String name,
        String status,
        String modelKeys,
        Double passRate,
        String reportPath,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        List<EvalCaseResultDTO> cases
) {
}
