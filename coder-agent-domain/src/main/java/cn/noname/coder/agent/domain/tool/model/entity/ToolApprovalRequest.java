package cn.noname.coder.agent.domain.tool.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolApprovalRequest {

    private Long id;
    private String approvalId;
    private String runId;
    private String workspaceKey;
    private String toolName;
    private String argumentsJson;
    private String riskSummary;
    private String diffSummary;
    private String status;
    private LocalDateTime requestedAt;
    private LocalDateTime decidedAt;
    private String decisionReason;
}
