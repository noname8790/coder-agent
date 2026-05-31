package cn.noname.coder.agent.infrastructure.adapter.port;

import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModelConfigPortTest {

    @Test
    void shouldResolveConfiguredModelGivenModelKey() {
        // Given 配置了多个可选模型
        AgentRuntimeProperties properties = multiModelProperties();
        ModelConfigPort port = new ModelConfigPort(properties);

        // When 按模型 key 解析
        var config = port.resolve("deepseek-v4-flash").orElseThrow();

        // Then 返回该 key 对应的完整后端配置
        assertEquals("deepseek-v4-flash", config.modelKey());
        assertEquals("deepseek-v4-flash", config.actualModel());
        assertEquals("https://deepseek.example/v1", config.baseUrl());
        assertEquals("deepseek-key", config.apiKey());
        assertEquals("chat-completions", config.endpointType());
        assertEquals(120, config.timeoutSeconds());
    }

    @Test
    void shouldResolveDefaultModelGivenBlankModelKey() {
        // Given 默认模型 key 为 glm-5
        AgentRuntimeProperties properties = multiModelProperties();
        ModelConfigPort port = new ModelConfigPort(properties);

        // When 未指定模型
        var config = port.defaultModel();

        // Then 使用默认模型
        assertEquals("glm-5", config.modelKey());
        assertEquals("glm-5", config.actualModel());
    }

    @Test
    void shouldReturnEmptyGivenUnknownModelKey() {
        // Given 多模型配置中不存在 unknown
        AgentRuntimeProperties properties = multiModelProperties();
        ModelConfigPort port = new ModelConfigPort(properties);

        // When 解析未知模型 key / Then 返回空
        assertTrue(port.resolve("unknown").isEmpty());
    }

    @Test
    void shouldResolveLegacySingleModelGivenNoModelsConfigured() {
        // Given 只配置旧单模型字段
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.getModel().setBaseUrl("https://legacy.example/v1");
        properties.getModel().setApiKey("legacy-key");
        properties.getModel().setModel("qwen3.6-plus");
        properties.getModel().setEndpointType("chat-completions");
        properties.getModel().setTimeoutSeconds(180);
        ModelConfigPort port = new ModelConfigPort(properties);

        // When 解析默认模型
        var config = port.defaultModel();

        // Then 使用旧字段构造默认后端配置
        assertEquals("qwen3.6-plus", config.modelKey());
        assertEquals("qwen3.6-plus", config.actualModel());
        assertEquals("https://legacy.example/v1", config.baseUrl());
        assertEquals("legacy-key", config.apiKey());
        assertEquals("chat-completions", config.endpointType());
        assertEquals(180, config.timeoutSeconds());
    }

    private AgentRuntimeProperties multiModelProperties() {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.getModel().setDefaultModelKey("glm-5");
        properties.getModel().setBaseUrl("https://dashscope.example/v1");
        properties.getModel().setApiKey("default-key");
        properties.getModel().setEndpointType("chat-completions");
        properties.getModel().setTimeoutSeconds(180);

        AgentRuntimeProperties.Backend glm = new AgentRuntimeProperties.Backend();
        glm.setModel("glm-5");
        properties.getModel().getModels().put("glm-5", glm);

        AgentRuntimeProperties.Backend deepseek = new AgentRuntimeProperties.Backend();
        deepseek.setBaseUrl("https://deepseek.example/v1");
        deepseek.setApiKey("deepseek-key");
        deepseek.setModel("deepseek-v4-flash");
        deepseek.setEndpointType("chat-completions");
        deepseek.setTimeoutSeconds(120);
        properties.getModel().getModels().put("deepseek-v4-flash", deepseek);
        return properties;
    }
}
