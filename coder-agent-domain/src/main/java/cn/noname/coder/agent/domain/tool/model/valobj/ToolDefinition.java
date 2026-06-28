package cn.noname.coder.agent.domain.tool.model.valobj;

import java.util.Map;

/**
 * 暴露给模型的工具定义，parameters 使用 JSON Schema 风格 Map。
 */
public record ToolDefinition(String name, String description, Map<String, Object> parameters) {
}
