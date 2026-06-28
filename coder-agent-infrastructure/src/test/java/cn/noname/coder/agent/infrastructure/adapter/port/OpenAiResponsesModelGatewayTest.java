package cn.noname.coder.agent.infrastructure.adapter.port;

import cn.noname.coder.agent.domain.model.adapter.port.IModelConfigPort;
import cn.noname.coder.agent.domain.model.model.valobj.ModelBackendConfig;
import cn.noname.coder.agent.domain.model.model.valobj.ModelProtocolMessage;
import cn.noname.coder.agent.domain.model.model.valobj.ModelRequest;
import cn.noname.coder.agent.domain.tool.model.valobj.ToolInvocation;
import cn.noname.coder.agent.domain.tool.model.valobj.ToolDefinition;
import cn.noname.coder.agent.infrastructure.gateway.OpenAiHttpGatewayService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiResponsesModelGatewayTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldParseFinalAnswerGivenResponsesOutputText() throws Exception {
        // Given OpenAI-compatible 服务返回 final answer
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"id\":\"resp_1\",\"output_text\":\"仓库包含多模块 Maven 项目\"}"));
            OpenAiResponsesModelGateway gateway = gateway(config("test-model", server, "responses", 60, "test-key", "test-model"));

            // When 调用模型网关
            var response = gateway.call(new ModelRequest("run_1", "test-model", List.of("hello"), List.of()));

            // Then 解析最终回答
            assertEquals("仓库包含多模块 Maven 项目", response.finalAnswer());
            assertFalse(response.hasToolCalls());
        }
    }

    @Test
    void shouldParseToolCallGivenFunctionCallOutput() throws Exception {
        // Given OpenAI-compatible 服务返回 function_call
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"id":"resp_2","output":[{"type":"function_call","call_id":"call_1","name":"list_files","arguments":"{\\"path\\":\\".\\"}"}]}
                            """));
            OpenAiResponsesModelGateway gateway = gateway(config("test-model", server, "responses", 60, "test-key", "test-model"));

            // When 调用模型网关
            var response = gateway.call(new ModelRequest("run_1", "test-model", List.of("hello"),
                    List.of(new ToolDefinition("list_files", "列目录", Map.of("type", "object")))));

            // Then 解析工具调用
            assertTrue(response.hasToolCalls());
            assertEquals("list_files", response.toolInvocations().getFirst().name());
            assertEquals("{\"path\":\".\"}", response.toolInvocations().getFirst().argumentsJson());
        }
    }

    @Test
    void shouldParseFinalAnswerGivenChatCompletionsResponse() throws Exception {
        // Given OpenAI-compatible Chat Completions 服务返回 final answer
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"id\":\"chatcmpl_1\",\"choices\":[{\"message\":{\"content\":\"仓库分析完成\"}}]}"));
            OpenAiResponsesModelGateway gateway = gateway(config("test-model", server, "chat-completions", 60, "test-key", "test-model"));

            // When 调用模型网关
            var response = gateway.call(new ModelRequest("run_1", "test-model", List.of("hello"), List.of()));

            // Then 解析最终回答，并调用 chat/completions 路径
            assertEquals("仓库分析完成", response.finalAnswer());
            assertEquals("/v1/chat/completions", server.takeRequest().getPath());
        }
    }

    @Test
    void shouldWaitForSlowModelResponseWithinConfiguredTimeout() throws Exception {
        // Given 模型在 OkHttp 默认读超时之后、配置超时之前返回
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"id\":\"chatcmpl_slow\",\"choices\":[{\"message\":{\"content\":\"slow answer\"}}]}")
                    .setBodyDelay(11, TimeUnit.SECONDS));
            OpenAiResponsesModelGateway gateway = gateway(config("test-model", server, "chat-completions", 15, "test-key", "test-model"));

            // When 调用慢响应模型
            var response = gateway.call(new ModelRequest("run_1", "test-model", List.of("hello"), List.of()));

            // Then 使用配置超时等待响应
            assertEquals("slow answer", response.finalAnswer());
        }
    }

    @Test
    void shouldRouteRequestByConfiguredModelKey() throws Exception {
        // Given 两个模型 key 指向不同后端配置
        try (MockWebServer defaultServer = new MockWebServer();
             MockWebServer deepseekServer = new MockWebServer()) {
            defaultServer.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"id\":\"chatcmpl_default\",\"choices\":[{\"message\":{\"content\":\"default\"}}]}"));
            deepseekServer.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"id\":\"chatcmpl_deepseek\",\"choices\":[{\"message\":{\"content\":\"deepseek\"}}]}"));

            TestModelConfigPort modelConfigPort = new TestModelConfigPort(Map.of(
                    "glm-5", config("glm-5", defaultServer, "chat-completions", 60, "default-key", "glm-5"),
                    "deepseek-v4-flash", config("deepseek-v4-flash", deepseekServer, "chat-completions", 60, "deepseek-key", "deepseek-v4-flash")
            ));
            OpenAiResponsesModelGateway gateway = gateway(modelConfigPort);

            // When 指定 deepseek 模型 key 调用
            var response = gateway.call(new ModelRequest("run_1", "deepseek-v4-flash", List.of("hello"), List.of()));

            // Then 请求被路由到 deepseek 后端，并使用真实模型名和对应鉴权
            assertEquals("deepseek", response.finalAnswer());
            var request = deepseekServer.takeRequest();
            assertEquals("/v1/chat/completions", request.getPath());
            assertEquals("Bearer deepseek-key", request.getHeader("Authorization"));
            assertEquals("deepseek-v4-flash", objectMapper.readTree(request.getBody().readUtf8()).get("model").asText());
            assertEquals(0, defaultServer.getRequestCount());
        }
    }

    @Test
    void shouldSendToolResultAsChatCompletionToolMessageGivenProtocolMessages() throws Exception {
        // Given a previous assistant tool call and a local tool result
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"id\":\"chatcmpl_tool_result\",\"choices\":[{\"message\":{\"content\":\"done\"}}]}"));
            OpenAiResponsesModelGateway gateway = gateway(config("test-model", server, "chat-completions", 60, "test-key", "test-model"));
            ToolInvocation invocation = new ToolInvocation("call_1", "list_files", "{\"path\":\"src/test/java\"}");

            // When the next model request is sent
            gateway.call(new ModelRequest("run_1", "test-model", List.of("system context"),
                    List.of(new ToolDefinition("list_files", "list files", Map.of("type", "object"))),
                    List.of(
                            ModelProtocolMessage.user("system context"),
                            ModelProtocolMessage.assistantToolCalls(List.of(invocation), ""),
                            ModelProtocolMessage.toolResult(invocation, "[D] cn\n[F] demoTest.java")
                    )));

            // Then the OpenAI-compatible chat payload uses standard assistant/tool messages.
            JsonNode messages = objectMapper.readTree(server.takeRequest().getBody().readUtf8()).get("messages");
            assertEquals("user", messages.get(0).get("role").asText());
            assertEquals("assistant", messages.get(1).get("role").asText());
            assertEquals("call_1", messages.get(1).get("tool_calls").get(0).get("id").asText());
            assertEquals("list_files", messages.get(1).get("tool_calls").get(0).get("function").get("name").asText());
            assertEquals("tool", messages.get(2).get("role").asText());
            assertEquals("call_1", messages.get(2).get("tool_call_id").asText());
            assertEquals("[D] cn\n[F] demoTest.java", messages.get(2).get("content").asText());
        }
    }

    private OpenAiResponsesModelGateway gateway(ModelBackendConfig config) {
        return gateway(new TestModelConfigPort(Map.of(config.modelKey(), config)));
    }

    private OpenAiResponsesModelGateway gateway(IModelConfigPort modelConfigPort) {
        return new OpenAiResponsesModelGateway(modelConfigPort, new OpenAiHttpGatewayService());
    }

    private ModelBackendConfig config(String modelKey,
                                      MockWebServer server,
                                      String endpointType,
                                      int timeoutSeconds,
                                      String apiKey,
                                      String actualModel) {
        return new ModelBackendConfig(
                modelKey,
                "openai-compatible",
                actualModel,
                server.url("/v1").toString(),
                apiKey,
                endpointType,
                0.2,
                timeoutSeconds);
    }

    record TestModelConfigPort(Map<String, ModelBackendConfig> configs) implements IModelConfigPort {
        @Override
        public ModelBackendConfig defaultModel() {
            return configs.values().iterator().next();
        }

        @Override
        public Optional<ModelBackendConfig> resolve(String modelKey) {
            if (modelKey == null || modelKey.isBlank()) {
                return Optional.of(defaultModel());
            }
            return Optional.ofNullable(configs.get(modelKey));
        }
    }
}
