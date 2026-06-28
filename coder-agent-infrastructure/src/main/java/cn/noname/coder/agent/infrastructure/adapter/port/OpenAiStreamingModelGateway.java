package cn.noname.coder.agent.infrastructure.adapter.port;

import cn.noname.coder.agent.domain.model.adapter.port.IModelConfigPort;
import cn.noname.coder.agent.domain.model.adapter.port.IStreamingModelGateway;
import cn.noname.coder.agent.domain.model.model.valobj.ModelBackendConfig;
import cn.noname.coder.agent.domain.model.model.valobj.ModelProtocolMessage;
import cn.noname.coder.agent.domain.model.model.valobj.ModelRequest;
import cn.noname.coder.agent.domain.model.model.valobj.ModelStreamEvent;
import cn.noname.coder.agent.domain.model.model.valobj.ModelStreamEventType;
import cn.noname.coder.agent.domain.tool.model.valobj.ToolDefinition;
import cn.noname.coder.agent.types.exception.AppException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import okhttp3.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.Reader;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class OpenAiStreamingModelGateway implements IStreamingModelGateway {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final IModelConfigPort modelConfigPort;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void stream(ModelRequest request, Consumer<ModelStreamEvent> eventConsumer) {
        ModelBackendConfig modelConfig = modelConfigPort.resolve(request.model())
                .orElseThrow(() -> new AppException("MODEL_NOT_CONFIGURED", "模型未配置：" + request.model()));
        if (!modelConfig.streamingEnabled()) {
            throw new AppException("STREAMING_REQUIRED", "模型未启用 streaming：" + modelConfig.modelKey());
        }
        try {
            String body = objectMapper.writeValueAsString(buildPayload(request, modelConfig));
            Request httpRequest = new Request.Builder()
                    .url(resolveUrl(modelConfig))
                    .addHeader("Authorization", "Bearer " + modelConfig.apiKey())
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body, JSON))
                    .build();
            OkHttpClient client = client(modelConfig.timeoutSeconds());
            try (Response response = client.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    String responseBody = response.body() == null ? "" : response.body().string();
                    emitFailed(eventConsumer, "HTTP " + response.code() + ": " + responseBody);
                    throw new AppException("MODEL_HTTP_ERROR", "模型流式调用失败 HTTP " + response.code() + ": " + responseBody);
                }
                if (response.body() == null) {
                    emitFailed(eventConsumer, "empty response body");
                    throw new AppException("MODEL_STREAM_FAILED", "模型流式调用响应为空");
                }
                parseSse(response.body().charStream(), modelConfig.endpointType(), eventConsumer);
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            emitFailed(eventConsumer, e.getMessage());
            throw new AppException("MODEL_STREAM_FAILED", "模型流式调用异常：" + e.getMessage());
        }
    }

    private OkHttpClient client(int timeoutSeconds) {
        Duration timeout = Duration.ofSeconds(timeoutSeconds <= 0 ? 180 : timeoutSeconds);
        return new OkHttpClient.Builder()
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .callTimeout(timeout)
                .build();
    }

    private Map<String, Object> buildPayload(ModelRequest request, ModelBackendConfig modelConfig) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelConfig.actualModel());
        payload.put("temperature", modelConfig.temperature());
        payload.put("stream", true);
        if ("chat-completions".equalsIgnoreCase(modelConfig.endpointType())) {
            payload.put("messages", chatMessages(request));
            payload.put("tools", request.tools().stream().map(this::toChatCompletionTool).toList());
            return payload;
        }
        payload.put("input", String.join("\n\n", request.messages()));
        payload.put("tools", request.tools().stream().map(this::toResponsesTool).toList());
        return payload;
    }

    private List<Map<String, Object>> chatMessages(ModelRequest request) {
        if (request.protocolMessages() == null || request.protocolMessages().isEmpty()) {
            return List.of(Map.of("role", "user", "content", String.join("\n\n", request.messages())));
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        for (ModelProtocolMessage message : request.protocolMessages()) {
            if ("assistant".equals(message.role()) && message.toolCalls() != null && !message.toolCalls().isEmpty()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("role", "assistant");
                item.put("content", message.content() == null ? "" : message.content());
                item.put("tool_calls", message.toolCalls().stream().map(invocation -> {
                    Map<String, Object> function = new LinkedHashMap<>();
                    function.put("name", invocation.name());
                    function.put("arguments", invocation.argumentsJson() == null ? "{}" : invocation.argumentsJson());
                    Map<String, Object> toolCall = new LinkedHashMap<>();
                    toolCall.put("id", StringUtils.hasText(invocation.id()) ? invocation.id() : "call_" + Math.abs(Objects.hash(invocation.name(), invocation.argumentsJson())));
                    toolCall.put("type", "function");
                    toolCall.put("function", function);
                    return toolCall;
                }).toList());
                messages.add(item);
            } else if ("tool".equals(message.role())) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("role", "tool");
                item.put("tool_call_id", message.toolCallId());
                item.put("content", message.content() == null ? "" : message.content());
                messages.add(item);
            } else {
                messages.add(Map.of("role", "user", "content", message.content() == null ? "" : message.content()));
            }
        }
        return messages;
    }

    private Map<String, Object> toChatCompletionTool(ToolDefinition definition) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", definition.name());
        function.put("description", definition.description());
        function.put("parameters", definition.parameters());
        return Map.of("type", "function", "function", function);
    }

    private Map<String, Object> toResponsesTool(ToolDefinition definition) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("name", definition.name());
        tool.put("description", definition.description());
        tool.put("parameters", definition.parameters());
        return tool;
    }

    private String resolveUrl(ModelBackendConfig modelConfig) {
        String trimmed = modelConfig.baseUrl().endsWith("/")
                ? modelConfig.baseUrl().substring(0, modelConfig.baseUrl().length() - 1)
                : modelConfig.baseUrl();
        if ("chat-completions".equalsIgnoreCase(modelConfig.endpointType())) {
            return trimmed.endsWith("/chat/completions") ? trimmed : trimmed + "/chat/completions";
        }
        return trimmed.endsWith("/responses") ? trimmed : trimmed + "/responses";
    }

    private void parseSse(String sseBody, String endpointType, Consumer<ModelStreamEvent> consumer) throws Exception {
        parseSse(new java.io.StringReader(sseBody), endpointType, consumer);
    }

    private void parseSse(Reader sseReader, String endpointType, Consumer<ModelStreamEvent> consumer) throws Exception {
        BufferedReader reader = new BufferedReader(sseReader);
        String line;
        String eventName = "";
        StringBuilder data = new StringBuilder();
        ChatToolState chatToolState = new ChatToolState();
        ResponsesToolState responsesToolState = new ResponsesToolState();
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                consumeSseEvent(endpointType, eventName, data.toString(), consumer, chatToolState, responsesToolState);
                eventName = "";
                data.setLength(0);
                continue;
            }
            if (line.startsWith("event:")) {
                eventName = line.substring("event:".length()).trim();
            } else if (line.startsWith("data:")) {
                if (!data.isEmpty()) {
                    data.append('\n');
                }
                data.append(line.substring("data:".length()).trim());
            }
        }
        if (!data.isEmpty()) {
            consumeSseEvent(endpointType, eventName, data.toString(), consumer, chatToolState, responsesToolState);
        }
    }

    private void consumeSseEvent(String endpointType,
                                 String eventName,
                                 String data,
                                 Consumer<ModelStreamEvent> consumer,
                                 ChatToolState chatToolState,
                                 ResponsesToolState responsesToolState) throws Exception {
        if (!StringUtils.hasText(data) || "[DONE]".equals(data)) {
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.MODEL_COMPLETED, null, null, null, null, null));
            return;
        }
        JsonNode root = objectMapper.readTree(data);
        if ("chat-completions".equalsIgnoreCase(endpointType)) {
            consumeChatCompletions(root, consumer, chatToolState);
        } else {
            consumeResponses(eventName, root, consumer, responsesToolState);
        }
    }

    private void consumeChatCompletions(JsonNode root, Consumer<ModelStreamEvent> consumer, ChatToolState state) {
        JsonNode choice = root.at("/choices/0");
        JsonNode delta = choice.get("delta");
        if (delta != null && "assistant".equals(text(delta, "role"))) {
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.ASSISTANT_MESSAGE_STARTED, null, null, null, null, null));
        }
        String content = text(delta, "content");
        if (StringUtils.hasText(content)) {
            String visibleContent = state.filterVisibleContent(content);
            if (StringUtils.hasText(visibleContent)) {
                consumer.accept(new ModelStreamEvent(ModelStreamEventType.ASSISTANT_DELTA, visibleContent, null, null, null, null));
            }
        }
        JsonNode toolCalls = delta == null ? null : delta.get("tool_calls");
        if (toolCalls != null && toolCalls.isArray()) {
            for (JsonNode toolCall : toolCalls) {
                int index = toolCall.has("index") ? toolCall.get("index").asInt() : 0;
                String id = text(toolCall, "id");
                JsonNode function = toolCall.get("function");
                String name = text(function, "name");
                String arguments = text(function, "arguments");
                state.update(index, id, name);
                if (StringUtils.hasText(id) || StringUtils.hasText(name)) {
                    consumer.accept(new ModelStreamEvent(ModelStreamEventType.TOOL_CALL_STARTED, null,
                            state.id(index), state.name(index), null, null));
                }
                if (StringUtils.hasText(arguments)) {
                    consumer.accept(new ModelStreamEvent(ModelStreamEventType.TOOL_CALL_ARGUMENTS_DELTA, null,
                            state.id(index), state.name(index), arguments, null));
                }
            }
        }
        String finishReason = text(choice, "finish_reason");
        if ("tool_calls".equals(finishReason)) {
            state.ids().forEach(index -> consumer.accept(new ModelStreamEvent(ModelStreamEventType.TOOL_CALL_COMPLETED,
                    null, state.id(index), state.name(index), null, null)));
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.MODEL_COMPLETED, null, null, null, null, null));
        } else if ("stop".equals(finishReason)) {
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.MODEL_COMPLETED, null, null, null, null, null));
        }
    }

    private void consumeResponses(String eventName,
                                  JsonNode root,
                                  Consumer<ModelStreamEvent> consumer,
                                  ResponsesToolState state) {
        if ("response.output_text.delta".equals(eventName)) {
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.ASSISTANT_DELTA, text(root, "delta"), null, null, null, null));
            return;
        }
        if ("response.output_item.added".equals(eventName)) {
            JsonNode item = root.get("item");
            if (item != null && "function_call".equals(text(item, "type"))) {
                state.id = text(item, "call_id");
                state.name = text(item, "name");
                consumer.accept(new ModelStreamEvent(ModelStreamEventType.TOOL_CALL_STARTED, null, state.id, state.name, null, null));
            } else if (item != null && "message".equals(text(item, "type"))) {
                consumer.accept(new ModelStreamEvent(ModelStreamEventType.ASSISTANT_MESSAGE_STARTED, null, null, null, null, null));
            }
            return;
        }
        if ("response.function_call_arguments.delta".equals(eventName)) {
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.TOOL_CALL_ARGUMENTS_DELTA, null,
                    state.id, state.name, text(root, "delta"), null));
            return;
        }
        if ("response.output_item.done".equals(eventName) && StringUtils.hasText(state.id)) {
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.TOOL_CALL_COMPLETED, null, state.id, state.name, null, null));
            return;
        }
        if ("response.completed".equals(eventName)) {
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.MODEL_COMPLETED, null, null, null, null, null));
            return;
        }
        if ("error".equals(eventName)) {
            emitFailed(consumer, root.toString());
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) {
            return "";
        }
        return node.get(field).asText();
    }

    private void emitFailed(Consumer<ModelStreamEvent> consumer, String error) {
        consumer.accept(new ModelStreamEvent(ModelStreamEventType.MODEL_FAILED, null, null, null, null, error));
    }

    private static class ChatToolState {
        private final Map<Integer, String> ids = new LinkedHashMap<>();
        private final Map<Integer, String> names = new LinkedHashMap<>();
        private boolean insideThink;

        void update(int index, String id, String name) {
            if (StringUtils.hasText(id)) {
                ids.put(index, id);
            }
            if (StringUtils.hasText(name)) {
                names.put(index, name);
            }
        }

        String id(int index) {
            return ids.get(index);
        }

        String name(int index) {
            return names.get(index);
        }

        Set<Integer> ids() {
            return ids.keySet();
        }

        String filterVisibleContent(String content) {
            if (!StringUtils.hasText(content)) {
                return "";
            }
            String remaining = content;
            StringBuilder visible = new StringBuilder();
            while (!remaining.isEmpty()) {
                if (insideThink) {
                    int end = indexOfIgnoreCase(remaining, "</think>");
                    if (end < 0) {
                        return visible.toString();
                    }
                    remaining = remaining.substring(end + "</think>".length());
                    insideThink = false;
                    continue;
                }
                int start = indexOfIgnoreCase(remaining, "<think");
                if (start < 0) {
                    visible.append(remaining);
                    break;
                }
                visible.append(remaining, 0, start);
                int startTagEnd = remaining.indexOf('>', start);
                if (startTagEnd < 0) {
                    insideThink = true;
                    break;
                }
                remaining = remaining.substring(startTagEnd + 1);
                insideThink = true;
            }
            return visible.toString();
        }

        private int indexOfIgnoreCase(String value, String pattern) {
            return value.toLowerCase(Locale.ROOT).indexOf(pattern.toLowerCase(Locale.ROOT));
        }
    }

    private static class ResponsesToolState {
        private String id;
        private String name;
    }
}
