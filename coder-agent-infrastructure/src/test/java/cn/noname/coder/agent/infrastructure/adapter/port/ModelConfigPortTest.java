package cn.noname.coder.agent.infrastructure.adapter.port;

import cn.noname.coder.agent.domain.agent.adapter.port.IApiKeyCipherPort;
import cn.noname.coder.agent.domain.agent.adapter.repository.IModelProviderRepository;
import cn.noname.coder.agent.domain.agent.model.entity.ModelProvider;
import cn.noname.coder.agent.domain.agent.model.valobj.ContextBudget;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ModelConfigPortTest {

    @Test
    void shouldResolveConfiguredModelGivenModelKey() {
        // Given 数据库中配置了多个启用模型
        InMemoryModelProviderRepository repository = repository();
        ModelConfigPort port = new ModelConfigPort(repository, new TestApiKeyCipherPort());

        // When 按模型 key 解析
        var config = port.resolve("deepseek-v4-flash").orElseThrow();

        // Then 返回该 key 对应的完整后端配置
        assertEquals("deepseek-v4-flash", config.modelKey());
        assertEquals("deepseek-chat", config.actualModel());
        assertEquals("https://deepseek.example/v1", config.baseUrl());
        assertEquals("deepseek-key", config.apiKey());
        assertEquals("chat-completions", config.endpointType());
        assertEquals(120, config.timeoutSeconds());
        assertTrue(config.streamingEnabled());
        assertFalse(config.toolCallingEnabled());
    }

    @Test
    void shouldResolveDefaultModelGivenBlankModelKey() {
        // Given 数据库中存在默认模型
        ModelConfigPort port = new ModelConfigPort(repository(), new TestApiKeyCipherPort());

        // When 未指定模型
        var config = port.resolve(null).orElseThrow();

        // Then 使用默认模型
        assertEquals("glm-5", config.modelKey());
        assertEquals("glm-5", config.actualModel());
    }

    @Test
    void shouldReturnEmptyGivenUnknownModelKey() {
        // Given 数据库中不存在 unknown
        ModelConfigPort port = new ModelConfigPort(repository(), new TestApiKeyCipherPort());

        // When 解析未知模型 key / Then 返回空
        assertTrue(port.resolve("unknown").isEmpty());
    }

    @Test
    void shouldReturnEmptyGivenNoDatabaseModelConfigured() {
        // Given 数据库中没有模型配置
        ModelConfigPort port = new ModelConfigPort(new InMemoryModelProviderRepository(), new TestApiKeyCipherPort());

        // When 解析默认模型 / Then 不再回退到 application.yml 静态模型配置
        assertTrue(port.resolve(null).isEmpty());
    }

    @Test
    void shouldExposeModelLevelBudgetGivenProviderConfiguredBudget() {
        // Given 模型配置带有上下文预算
        ModelConfigPort port = new ModelConfigPort(repository(), new TestApiKeyCipherPort());

        // When 解析模型
        var config = port.resolve("deepseek-v4-flash").orElseThrow();

        // Then 透出模型级预算
        assertEquals(16000, config.budget().maxContextTokens());
        assertEquals(2000, config.budget().memoryBudgetTokens());
    }

    private InMemoryModelProviderRepository repository() {
        InMemoryModelProviderRepository repository = new InMemoryModelProviderRepository();
        repository.providers.put("glm-5", ModelProvider.builder()
                .modelKey("glm-5")
                .displayName("GLM 5")
                .provider("openai-compatible")
                .baseUrl("https://glm.example/v1")
                .apiKeyCipher("cipher:glm-key")
                .modelName("glm-5")
                .endpointType("chat-completions")
                .temperature(0.2)
                .timeoutSeconds(180)
                .streamingEnabled(true)
                .toolCallingEnabled(true)
                .defaultModel(true)
                .status("ENABLED")
                .build());
        repository.providers.put("deepseek-v4-flash", ModelProvider.builder()
                .modelKey("deepseek-v4-flash")
                .displayName("DeepSeek V4 Flash")
                .provider("openai-compatible")
                .baseUrl("https://deepseek.example/v1")
                .apiKeyCipher("cipher:deepseek-key")
                .modelName("deepseek-chat")
                .endpointType("chat-completions")
                .temperature(0.1)
                .timeoutSeconds(120)
                .streamingEnabled(true)
                .toolCallingEnabled(false)
                .defaultModel(false)
                .status("ENABLED")
                .budget(new ContextBudget(16000, 4096, 12000, 2000, 1500, 2000, 1000, 1500))
                .build());
        return repository;
    }

    static class InMemoryModelProviderRepository implements IModelProviderRepository {
        private final Map<String, ModelProvider> providers = new LinkedHashMap<>();

        @Override
        public void save(ModelProvider provider) {
            providers.put(provider.getModelKey(), provider);
        }

        @Override
        public void update(ModelProvider provider) {
            providers.put(provider.getModelKey(), provider);
        }

        @Override
        public Optional<ModelProvider> findByModelKey(String modelKey) {
            return Optional.ofNullable(providers.get(modelKey));
        }

        @Override
        public Optional<ModelProvider> findDefaultEnabled() {
            return providers.values().stream()
                    .filter(provider -> Boolean.TRUE.equals(provider.getDefaultModel()))
                    .filter(provider -> "ENABLED".equals(provider.getStatus()))
                    .findFirst();
        }

        @Override
        public List<ModelProvider> listAll() {
            return providers.values().stream().toList();
        }

        @Override
        public List<ModelProvider> listEnabled() {
            return providers.values().stream()
                    .filter(provider -> "ENABLED".equals(provider.getStatus()))
                    .toList();
        }

        @Override
        public void clearDefaultModel() {
            providers.values().forEach(provider -> provider.setDefaultModel(false));
        }

        @Override
        public void deleteByModelKey(String modelKey) {
            providers.remove(modelKey);
        }
    }

    static class TestApiKeyCipherPort implements IApiKeyCipherPort {
        @Override
        public String encrypt(String plainText) {
            return "cipher:" + plainText;
        }

        @Override
        public String decrypt(String cipherText) {
            return cipherText == null ? "" : cipherText.replace("cipher:", "");
        }

        @Override
        public String mask(String plainText) {
            String value = decrypt(plainText);
            return "****" + value.substring(Math.max(0, value.length() - 4));
        }
    }
}
