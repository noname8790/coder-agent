package cn.noname.coder.agent.infrastructure.adapter.port;

import cn.noname.coder.agent.domain.agent.adapter.port.IModelConfigPort;
import cn.noname.coder.agent.domain.agent.model.valobj.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiStreamingModelGatewayTest {

    @Test
    void shouldParseChatCompletionsStreamingTextDelta() throws Exception {
        // Given Chat Completions streaming 返回文本 delta
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("""
                            data: {"choices":[{"delta":{"role":"assistant"}}]}

                            data: {"choices":[{"delta":{"content":"你好"}}]}

                            data: {"choices":[{"delta":{"content":"，世界"}}]}

                            data: {"choices":[{"finish_reason":"stop","delta":{}}]}

                            """));
            OpenAiStreamingModelGateway gateway = gateway(config(server, "chat-completions"));
            List<ModelStreamEvent> events = new ArrayList<>();

            // When 调用流式网关
            gateway.stream(new ModelRequest("run_1", "glm-5", List.of("hello"), List.of()), events::add);

            // Then 解析 assistant started、delta 和 completed
            assertAll(
                    () -> assertEquals(ModelStreamEventType.ASSISTANT_MESSAGE_STARTED, events.get(0).type()),
                    () -> assertEquals("你好", events.get(1).contentDelta()),
                    () -> assertEquals("，世界", events.get(2).contentDelta()),
                    () -> assertEquals(ModelStreamEventType.MODEL_COMPLETED, events.get(3).type()),
                    () -> assertEquals("/v1/chat/completions", server.takeRequest().getPath())
            );
        }
    }

    @Test
    void shouldIgnoreChatCompletionsReasoningContentDelta() throws Exception {
        // Given 兼容模型返回 reasoning_content 和 content
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("""
                            data: {"choices":[{"delta":{"role":"assistant"}}]}

                            data: {"choices":[{"delta":{"reasoning_content":"正在分析"}}]}

                            data: {"choices":[{"delta":{"content":"最终回答"}}]}

                            data: {"choices":[{"finish_reason":"stop","delta":{}}]}

                            """));
            OpenAiStreamingModelGateway gateway = gateway(config(server, "chat-completions"));
            List<ModelStreamEvent> events = new ArrayList<>();

            // When 调用流式网关
            gateway.stream(new ModelRequest("run_1", "qwen3.6-plus", List.of("hello"), List.of()), events::add);

            // Then 只把 content 作为用户可见正文推送，reasoning_content 不展示
            assertAll(
                    () -> assertEquals(ModelStreamEventType.ASSISTANT_MESSAGE_STARTED, events.get(0).type()),
                    () -> assertEquals("最终回答", events.get(1).contentDelta()),
                    () -> assertEquals(ModelStreamEventType.MODEL_COMPLETED, events.get(2).type())
            );
        }
    }

    @Test
    void shouldHideThinkTagContentAndOnlyEmitVisibleChatDelta() throws Exception {
        // Given 思考模型把推理过程混入 content，并用 think 标签包裹
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("""
                            data: {"choices":[{"delta":{"role":"assistant"}}]}

                            data: {"choices":[{"delta":{"content":"<think>Let me inspect the repository."}}]}

                            data: {"choices":[{"delta":{"content":"I need to call list_files.</think>"}}]}

                            data: {"choices":[{"delta":{"content":"我会先读取目录结构。"}}]}

                            data: {"choices":[{"finish_reason":"stop","delta":{}}]}

                            """));
            OpenAiStreamingModelGateway gateway = gateway(config(server, "chat-completions"));
            List<ModelStreamEvent> events = new ArrayList<>();

            // When 调用流式网关
            gateway.stream(new ModelRequest("run_1", "deepseek-v4-flash", List.of("hello"), List.of()), events::add);

            // Then 只推送用户可见正文，不展示 think 内容
            List<String> deltas = events.stream()
                    .filter(event -> event.type() == ModelStreamEventType.ASSISTANT_DELTA)
                    .map(ModelStreamEvent::contentDelta)
                    .toList();
            assertEquals(List.of("我会先读取目录结构。"), deltas);
        }
    }

    @Test
    void shouldParseChatCompletionsStreamingToolCallDelta() throws Exception {
        // Given Chat Completions streaming 返回 tool_calls delta
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("""
                            data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"list_files","arguments":"{\\"path\\""}}]}}]}

                            data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":":\\".\\"}"}}]}}]}

                            data: {"choices":[{"finish_reason":"tool_calls","delta":{}}]}

                            """));
            OpenAiStreamingModelGateway gateway = gateway(config(server, "chat-completions"));
            List<ModelStreamEvent> events = new ArrayList<>();

            // When 调用流式网关
            gateway.stream(new ModelRequest("run_1", "glm-5", List.of("hello"),
                    List.of(new ToolDefinition("list_files", "列目录", Map.of("type", "object")))), events::add);

            // Then 解析工具开始、参数增量、工具完成和模型完成
            assertAll(
                    () -> assertEquals(ModelStreamEventType.TOOL_CALL_STARTED, events.get(0).type()),
                    () -> assertEquals("call_1", events.get(0).toolCallId()),
                    () -> assertEquals("list_files", events.get(0).toolName()),
                    () -> assertEquals("{\"path\"", events.get(1).argumentsDelta()),
                    () -> assertEquals(":\".\"}", events.get(2).argumentsDelta()),
                    () -> assertEquals(ModelStreamEventType.TOOL_CALL_COMPLETED, events.get(3).type()),
                    () -> assertEquals(ModelStreamEventType.MODEL_COMPLETED, events.get(4).type())
            );
        }
    }

    @Test
    void shouldParseResponsesStreamingTextAndToolEventsWithoutHostedState() throws Exception {
        // Given Responses API streaming 返回文本和 function call 事件
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("""
                            event: response.output_item.added
                            data: {"item":{"type":"message"}}

                            event: response.output_text.delta
                            data: {"delta":"正在检查"}

                            event: response.output_item.added
                            data: {"item":{"type":"function_call","call_id":"call_1","name":"list_files"}}

                            event: response.function_call_arguments.delta
                            data: {"delta":"{\\"path\\":\\".\\"}"}

                            event: response.output_item.done
                            data: {"item":{"type":"function_call"}}

                            event: response.completed
                            data: {}

                            """));
            OpenAiStreamingModelGateway gateway = gateway(config(server, "responses"));
            List<ModelStreamEvent> events = new ArrayList<>();

            // When 调用流式网关
            gateway.stream(new ModelRequest("run_1", "glm-5", List.of("hello"),
                    List.of(new ToolDefinition("list_files", "列目录", Map.of("type", "object")))), events::add);

            // Then 解析为内部事件，且请求体不包含 OpenAI hosted state 字段
            String body = server.takeRequest().getBody().readUtf8();
            assertAll(
                    () -> assertEquals(ModelStreamEventType.ASSISTANT_MESSAGE_STARTED, events.get(0).type()),
                    () -> assertEquals("正在检查", events.get(1).contentDelta()),
                    () -> assertEquals(ModelStreamEventType.TOOL_CALL_STARTED, events.get(2).type()),
                    () -> assertEquals("{\"path\":\".\"}", events.get(3).argumentsDelta()),
                    () -> assertEquals(ModelStreamEventType.TOOL_CALL_COMPLETED, events.get(4).type()),
                    () -> assertEquals(ModelStreamEventType.MODEL_COMPLETED, events.get(5).type()),
                    () -> assertFalse(body.contains("previous_response_id")),
                    () -> assertFalse(body.contains("\"store\"")),
                    () -> assertTrue(body.contains("\"stream\":true"))
            );
        }
    }

    @Test
    void shouldEmitFailureGivenStreamingHttpError() throws Exception {
        // Given 模型服务返回错误
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(500).setBody("bad gateway"));
            OpenAiStreamingModelGateway gateway = gateway(config(server, "chat-completions"));
            List<ModelStreamEvent> events = new ArrayList<>();

            // When 调用流式网关 / Then 记录失败事件
            assertThrows(cn.noname.coder.agent.types.exception.AppException.class,
                    () -> gateway.stream(new ModelRequest("run_1", "glm-5", List.of("hello"), List.of()), events::add));
            assertEquals(ModelStreamEventType.MODEL_FAILED, events.getFirst().type());
        }
    }

    private OpenAiStreamingModelGateway gateway(ModelBackendConfig config) {
        return new OpenAiStreamingModelGateway(new TestModelConfigPort(config));
    }

    private ModelBackendConfig config(MockWebServer server, String endpointType) {
        return new ModelBackendConfig(
                "glm-5",
                "openai-compatible",
                "glm-5",
                server.url("/v1").toString(),
                "test-key",
                endpointType,
                0.2,
                60,
                true,
                true,
                null);
    }

    record TestModelConfigPort(ModelBackendConfig config) implements IModelConfigPort {
        @Override
        public ModelBackendConfig defaultModel() {
            return config;
        }

        @Override
        public Optional<ModelBackendConfig> resolve(String modelKey) {
            return Optional.of(config);
        }
    }
}
