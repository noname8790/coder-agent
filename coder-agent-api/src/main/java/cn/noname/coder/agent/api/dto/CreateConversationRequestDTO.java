package cn.noname.coder.agent.api.dto;

/**
 * 创建会话请求。
 */
public record CreateConversationRequestDTO(
        String workspaceKey,
        String title,
        String defaultModel,
        String lastPermissionLevel
) {
}
