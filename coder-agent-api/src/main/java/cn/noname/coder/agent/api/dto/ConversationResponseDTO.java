package cn.noname.coder.agent.api.dto;

import java.time.LocalDateTime;

/**
 * 会话响应。
 */
public record ConversationResponseDTO(
        String conversationId,
        String workspaceKey,
        String title,
        String defaultModel,
        String lastModelKey,
        String lastPermissionLevel,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public ConversationResponseDTO(String conversationId,
                                   String workspaceKey,
                                   String title,
                                   String defaultModel,
                                   String lastPermissionLevel,
                                   LocalDateTime createdAt,
                                   LocalDateTime updatedAt) {
        this(conversationId, workspaceKey, title, defaultModel, defaultModel, lastPermissionLevel, createdAt, updatedAt);
    }
}
