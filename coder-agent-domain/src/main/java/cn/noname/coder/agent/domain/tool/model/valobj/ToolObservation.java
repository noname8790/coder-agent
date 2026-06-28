package cn.noname.coder.agent.domain.tool.model.valobj;

import cn.noname.coder.agent.types.enums.CallStatus;

/**
 * 进入上下文和记忆证据链的标准化工具观察。
 */
public record ToolObservation(
        String toolName,
        String arguments,
        CallStatus status,
        Integer exitCode,
        String error,
        String summary
) {

    public static ToolObservation from(ToolInvocation invocation, ToolResult result, String summary) {
        return new ToolObservation(
                invocation.name(),
                invocation.argumentsJson(),
                result.status(),
                result.exitCode(),
                result.errorMessage(),
                summary == null || summary.isBlank() ? "tool returned no visible content" : summary);
    }

    public String toPromptBlock() {
        StringBuilder content = new StringBuilder();
        content.append("TOOL_OBSERVATION").append('\n')
                .append("tool=").append(toolName).append('\n')
                .append("arguments=").append(arguments == null ? "" : arguments).append('\n')
                .append("status=").append(status).append('\n')
                .append("exitCode=").append(exitCode == null ? "" : exitCode).append('\n');
        if (error != null && !error.isBlank()) {
            content.append("error=").append(error).append('\n');
        }
        content.append("summary=").append(summary).append('\n');
        if (status == CallStatus.SUCCESS) {
            content.append("instruction=tool call completed. Do not call the same tool with the same arguments again; continue based on status and summary.");
        } else {
            content.append("instruction=tool call failed. Do not mechanically repeat the same arguments; try a different path/tool or explain the blocker.");
        }
        return content.toString();
    }
}
