package cn.noname.coder.agent.api.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * workspace 详情响应。
 */
public record WorkspaceResponseDTO(
        String workspaceKey,
        String rootPath,
        List<String> capabilities,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt
) {
}
