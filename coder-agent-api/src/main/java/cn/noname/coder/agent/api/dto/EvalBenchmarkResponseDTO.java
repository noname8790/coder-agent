package cn.noname.coder.agent.api.dto;

import java.time.LocalDateTime;

public record EvalBenchmarkResponseDTO(
        String benchmarkId,
        String name,
        String workspaceKey,
        String task,
        String permissionLevel,
        String modelKey,
        String expectedOutcome,
        String evaluatorType,
        Integer timeoutSeconds,
        String status,
        LocalDateTime createdAt
) {
}
