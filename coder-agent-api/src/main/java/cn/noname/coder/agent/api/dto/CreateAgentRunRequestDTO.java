package cn.noname.coder.agent.api.dto;

/**
 * 创建 Agent 运行请求。只允许传 workspaceKey，不允许直接传本地绝对路径。
 */
public record CreateAgentRunRequestDTO(String workspaceKey,
                                       String task,
                                       String model,
                                       String conversationId,
                                       String permissionLevel) {

    public CreateAgentRunRequestDTO(String workspaceKey, String task, String model) {
        this(workspaceKey, task, model, null, null);
    }
}
