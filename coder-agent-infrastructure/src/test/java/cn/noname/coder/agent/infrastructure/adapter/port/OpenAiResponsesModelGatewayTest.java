package cn.noname.coder.agent.infrastructure.adapter.port;

import cn.noname.coder.agent.domain.agent.model.valobj.ModelRequest;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolDefinition;
import cn.noname.coder.agent.infrastructure.gateway.OpenAiHttpGatewayService;
import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
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
            OpenAiResponsesModelGateway gateway = gateway(properties(server));

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
            OpenAiResponsesModelGateway gateway = gateway(properties(server));

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
            AgentRuntimeProperties properties = properties(server);
            properties.getModel().setEndpointType("chat-completions");
            OpenAiResponsesModelGateway gateway = gateway(properties);

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
            AgentRuntimeProperties properties = properties(server);
            properties.getModel().setEndpointType("chat-completions");
            properties.getModel().setTimeoutSeconds(15);
            OpenAiResponsesModelGateway gateway = gateway(properties);

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

            AgentRuntimeProperties properties = properties(defaultServer);
            properties.getModel().setDefaultModelKey("glm-5");
            properties.getModel().setEndpointType("chat-completions");

            AgentRuntimeProperties.Backend glm = new AgentRuntimeProperties.Backend();
            glm.setModel("glm-5");
            properties.getModel().getModels().put("glm-5", glm);

            AgentRuntimeProperties.Backend deepseek = new AgentRuntimeProperties.Backend();
            deepseek.setBaseUrl(deepseekServer.url("/compatible-mode/v1").toString());
            deepseek.setApiKey("deepseek-key");
            deepseek.setModel("deepseek-v4-flash");
            deepseek.setEndpointType("chat-completions");
            properties.getModel().getModels().put("deepseek-v4-flash", deepseek);

            OpenAiResponsesModelGateway gateway = gateway(properties);

            // When 指定 deepseek 模型 key 调用
            var response = gateway.call(new ModelRequest("run_1", "deepseek-v4-flash", List.of("hello"), List.of()));

            // Then 请求被路由到 deepseek 后端，并使用真实模型名和对应鉴权
            assertEquals("deepseek", response.finalAnswer());
            var request = deepseekServer.takeRequest();
            assertEquals("/compatible-mode/v1/chat/completions", request.getPath());
            assertEquals("Bearer deepseek-key", request.getHeader("Authorization"));
            assertEquals("deepseek-v4-flash", objectMapper.readTree(request.getBody().readUtf8()).get("model").asText());
            assertEquals(0, defaultServer.getRequestCount());
        }
    }

    private OpenAiResponsesModelGateway gateway(AgentRuntimeProperties properties) {
        return new OpenAiResponsesModelGateway(new ModelConfigPort(properties), new OpenAiHttpGatewayService());
    }

    private AgentRuntimeProperties properties(MockWebServer server) {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.getModel().setBaseUrl(server.url("/v1").toString());
        properties.getModel().setApiKey("test-key");
        properties.getModel().setModel("test-model");
        return properties;
    }
}
