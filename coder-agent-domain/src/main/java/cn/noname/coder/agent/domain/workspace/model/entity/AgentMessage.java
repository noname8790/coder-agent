package cn.noname.coder.agent.domain.workspace.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话消息，保存用户任务、Agent 最终回答和运行摘要。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMessage {
    private Long id;
    private String messageId;
    private String conversationId;
    private String runId;
    private String role;
    private String content;
    private Long sequenceNo;
    private String visibilityStatus;
    private String rolledBackByCheckpointId;
    private LocalDateTime createdAt;
}
