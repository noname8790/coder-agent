package cn.noname.coder.agent.api.dto;

import java.util.List;

/**
 * workspace 列表响应。
 */
public record WorkspaceListResponseDTO(List<WorkspaceResponseDTO> workspaces) {
}
