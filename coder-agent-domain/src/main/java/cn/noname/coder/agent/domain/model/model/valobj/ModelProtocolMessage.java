package cn.noname.coder.agent.domain.model.model.valobj;

import cn.noname.coder.agent.domain.tool.model.valobj.ToolInvocation;

import java.util.List;

public record ModelProtocolMessage(
        String role,
        String content,
        List<ToolInvocation> toolCalls,
        String toolCallId,
        String toolName
) {

    public static ModelProtocolMessage user(String content) {
        return new ModelProtocolMessage("user", content, List.of(), null, null);
    }

    public static ModelProtocolMessage assistantToolCalls(List<ToolInvocation> toolCalls, String content) {
        return new ModelProtocolMessage("assistant", content, toolCalls == null ? List.of() : List.copyOf(toolCalls), null, null);
    }

    public static ModelProtocolMessage toolResult(ToolInvocation invocation, String content) {
        return new ModelProtocolMessage("tool", content, List.of(),
                invocation == null ? null : invocation.id(),
                invocation == null ? null : invocation.name());
    }
}
