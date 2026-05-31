package cn.noname.coder.agent.domain.agent.model.valobj;

/**
 * 模型产生的工具调用请求。
 */
public record ToolInvocation(String id, String name, String argumentsJson) {
}
