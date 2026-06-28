package cn.noname.coder.agent.domain.workspace.model.entity;

import cn.noname.coder.agent.domain.tool.model.valobj.AgentPermissionLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 一个本地 workspace 下的 Agent 对话。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentConversation {
    private Long id;
    private String conversationId;
    private String workspaceKey;
    private String title;
    private String defaultModel;
    private String lastModelKey;
    private AgentPermissionLevel lastPermissionLevel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
