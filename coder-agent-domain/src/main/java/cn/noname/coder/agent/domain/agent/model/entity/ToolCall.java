package cn.noname.coder.agent.domain.agent.model.entity;

import cn.noname.coder.agent.types.enums.CallStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 工具调用审计记录，包含参数摘要、结果摘要和安全拒绝状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {

    private Long id;
    private String runId;
    private Integer callNo;
    private String toolName;
    private String argumentsSummary;
    private String resultSummary;
    private Integer exitCode;
    private CallStatus status;
    private Long latencyMs;
    private String errorMessage;
    private LocalDateTime createdAt;
}
