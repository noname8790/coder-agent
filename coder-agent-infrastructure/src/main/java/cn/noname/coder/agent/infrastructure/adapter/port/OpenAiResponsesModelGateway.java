package cn.noname.coder.agent.infrastructure.adapter.port;

import cn.noname.coder.agent.domain.agent.adapter.port.IModelConfigPort;
import cn.noname.coder.agent.domain.agent.adapter.port.IModelGateway;
import cn.noname.coder.agent.domain.agent.model.valobj.ModelBackendConfig;
import cn.noname.coder.agent.domain.agent.model.valobj.ModelRequest;
import cn.noname.coder.agent.domain.agent.model.valobj.ModelResponse;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolDefinition;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolInvocation;
import cn.noname.coder.agent.infrastructure.gateway.OpenAiHttpGatewayService;
import cn.noname.coder.agent.types.exception.AppException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible 模型网关。
 */
@Component
@RequiredArgsConstructor
public class OpenAiResponsesModelGateway implements IModelGateway {

    private final IModelConfigPort modelConfigPort;
    private final OpenAiHttpGatewayService httpGatewayService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ModelResponse call(ModelRequest request) {
        ModelBackendConfig modelConfig = modelConfigPort.resolve(request.model())
                .orElseThrow(() -> new AppException("MODEL_NOT_CONFIGURED", "模型未配置：" + request.model()));
        if (!StringUtils.hasText(modelConfig.baseUrl()) || !StringUtils.hasText(modelConfig.apiKey())) {
            throw new AppException("MODEL_CONFIG_MISSING", "OpenAI-compatible base-url 或 api-key 未配置：" + modelConfig.modelKey());
        }
        try {
            String body = objectMapper.writeValueAsString(buildPayload(request, modelConfig));
            return parseResponse(httpGatewayService.postResponses(body, modelConfig), modelConfig.endpointType());
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException("MODEL_CALL_FAILED", "模型调用异常：" + e.getMessage());
        }
    }

    private Map<String, Object> buildPayload(ModelRequest request, ModelBackendConfig modelConfig) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelConfig.actualModel());
        payload.put("temperature", modelConfig.temperature());
        if ("chat-completions".equalsIgnoreCase(modelConfig.endpointType())) {
            payload.put("messages", List.of(Map.of("role", "user", "content", String.join("\n\n", request.messages()))));
            payload.put("tools", request.tools().stream().map(this::toChatCompletionTool).toList());
            return payload;
        }
        payload.put("input", String.join("\n\n", request.messages()));
        payload.put("tools", request.tools().stream().map(this::toOpenAiTool).toList());
        return payload;
    }

    private Map<String, Object> toChatCompletionTool(ToolDefinition definition) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", definition.name());
        function.put("description", definition.description());
        function.put("parameters", definition.parameters());
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", function);
        return tool;
    }

    private Map<String, Object> toOpenAiTool(ToolDefinition definition) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("name", definition.name());
        tool.put("description", definition.description());
        tool.put("parameters", definition.parameters());
        return tool;
    }

    private ModelResponse parseResponse(String body, String endpointType) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        if ("chat-completions".equalsIgnoreCase(endpointType)) {
            return parseChatCompletionsResponse(root, body);
        }
        String id = text(root, "id");
        String finalAnswer = text(root, "output_text");
        List<ToolInvocation> toolInvocations = new ArrayList<>();
        JsonNode output = root.get("output");
        if (output != null && output.isArray()) {
            for (JsonNode item : output) {
                String type = text(item, "type");
                if ("function_call".equals(type)) {
                    String callId = text(item, "call_id");
                    String name = text(item, "name");
                    String arguments = text(item, "arguments");
                    toolInvocations.add(new ToolInvocation(callId, name, arguments));
                } else if (!StringUtils.hasText(finalAnswer) && "message".equals(type)) {
                    finalAnswer = extractMessageText(item);
                }
            }
        }
        return new ModelResponse(id, finalAnswer, toolInvocations, abbreviate(body, 2000));
    }

    private ModelResponse parseChatCompletionsResponse(JsonNode root, String body) {
        String id = text(root, "id");
        JsonNode message = root.at("/choices/0/message");
        String finalAnswer = text(message, "content");
        List<ToolInvocation> toolInvocations = new ArrayList<>();
        JsonNode toolCalls = message.get("tool_calls");
        if (toolCalls != null && toolCalls.isArray()) {
            for (JsonNode toolCall : toolCalls) {
                String callId = text(toolCall, "id");
                JsonNode function = toolCall.get("function");
                String name = text(function, "name");
                String arguments = text(function, "arguments");
                toolInvocations.add(new ToolInvocation(callId, name, arguments));
            }
        }
        return new ModelResponse(id, finalAnswer, toolInvocations, abbreviate(body, 2000));
    }

    private String extractMessageText(JsonNode item) {
        JsonNode content = item.get("content");
        if (content != null && content.isArray()) {
            StringBuilder text = new StringBuilder();
            for (JsonNode node : content) {
                String value = text(node, "text");
                if (StringUtils.hasText(value)) {
                    text.append(value);
                }
            }
            return text.toString();
        }
        return "";
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private String abbreviate(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
