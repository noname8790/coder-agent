package cn.noname.coder.agent.api.dto;

/**
 * 注册 workspace 请求。
 */
public record CreateWorkspaceRequestDTO(String workspaceKey, String rootPath) {
}
