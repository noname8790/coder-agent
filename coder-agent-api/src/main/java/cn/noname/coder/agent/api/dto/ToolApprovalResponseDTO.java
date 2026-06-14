package cn.noname.coder.agent.api.dto;

import java.time.LocalDateTime;

public record ToolApprovalResponseDTO(
        String approvalId,
        String runId,
        String workspaceKey,
        String toolName,
        String argumentsJson,
        String riskSummary,
        String diffSummary,
        String status,
        LocalDateTime requestedAt,
        LocalDateTime decidedAt,
        String decisionReason
) {
}
