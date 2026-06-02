package cn.noname.coder.agent.api.dto;

import java.time.LocalDateTime;

/**
 * workspace 详情响应。
 */
public record WorkspaceResponseDTO(
        String workspaceKey,
        String rootPath,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt
) {
}
