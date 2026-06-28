package cn.noname.coder.agent.domain.workspace.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话内检查点，用于把当前 workspace 回滚到某条 Agent 消息之后的状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentCheckpoint {
    private Long id;
    private String checkpointId;
    private String conversationId;
    private String workspaceKey;
    private String messageId;
    private String runId;
    private Long messageSeq;
    private String rollbackStatus;
    private LocalDateTime createdAt;
    private LocalDateTime rollbackAt;
}
