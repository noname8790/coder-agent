package cn.noname.coder.agent.domain.agent.model.entity;

import cn.noname.coder.agent.types.enums.CallStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 模型调用审计记录，只保存摘要和状态，避免把长上下文直接塞入数据库。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelCall {

    private Long id;
    private String runId;
    private Integer callNo;
    private String provider;
    private String model;
    private String requestSummary;
    private String responseSummary;
    private CallStatus status;
    private Long latencyMs;
    private String errorMessage;
    private LocalDateTime createdAt;
}
