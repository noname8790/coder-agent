package cn.noname.coder.agent.api.dto;

public record EvalBenchmarkRequestDTO(
        String name,
        String workspaceKey,
        String task,
        String permissionLevel,
        String modelKey,
        String expectedOutcome,
        String evaluatorType,
        Integer timeoutSeconds
) {
}
