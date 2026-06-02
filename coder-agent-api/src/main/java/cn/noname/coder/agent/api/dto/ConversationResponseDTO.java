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
        String defaultPermissionLevel,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
