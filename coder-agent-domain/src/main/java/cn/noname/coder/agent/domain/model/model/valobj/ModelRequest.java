package cn.noname.coder.agent.domain.model.model.valobj;

import cn.noname.coder.agent.domain.tool.model.valobj.ToolDefinition;

import java.util.List;

/**
 * 统一模型请求，隔离具体 OpenAI-compatible 协议细节。
 */
public record ModelRequest(String runId,
                           String model,
                           List<String> messages,
                           List<ToolDefinition> tools,
                           List<ModelProtocolMessage> protocolMessages) {

    public ModelRequest(String runId, String model, List<String> messages, List<ToolDefinition> tools) {
        this(runId, model, messages, tools, List.of());
    }
}
