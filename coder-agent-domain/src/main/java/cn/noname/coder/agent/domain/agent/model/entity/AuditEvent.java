package cn.noname.coder.agent.domain.agent.model.entity;

import cn.noname.coder.agent.types.enums.AuditEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 审计事件记录安全治理、状态流转和不可恢复异常。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {

    private Long id;
    private String runId;
    private AuditEventType eventType;
    private String message;
    private String detail;
    private LocalDateTime createdAt;
}
