package cn.noname.coder.agent.api.dto;

import java.util.List;

/**
 * 注册 workspace 请求。
 */
public record CreateWorkspaceRequestDTO(String workspaceKey, String rootPath, List<String> capabilities) {
}
