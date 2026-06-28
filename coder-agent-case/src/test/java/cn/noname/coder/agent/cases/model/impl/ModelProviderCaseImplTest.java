package cn.noname.coder.agent.cases.model.impl;

import cn.noname.coder.agent.api.dto.ModelProviderRequestDTO;
import cn.noname.coder.agent.domain.model.adapter.port.IApiKeyCipherPort;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRunRepository;
import cn.noname.coder.agent.domain.model.adapter.repository.IModelProviderRepository;
import cn.noname.coder.agent.domain.model.model.entity.ModelProvider;
import cn.noname.coder.agent.types.enums.AgentRunStatus;
import cn.noname.coder.agent.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ModelProviderCaseImplTest {

    @Test
    void shouldCreateModelProviderGivenValidRequest() {
        // Given 合法模型配置
        InMemoryModelProviderRepository repository = new InMemoryModelProviderRepository();
        ModelProviderCaseImpl useCase = newUseCase(repository, new StubRunRepository(0));

        // When 创建模型配置
        var response = useCase.create(request("glm-5", "GLM 5", true));

        // Then API Key 密文入库，查询响应只返回脱敏值
        ModelProvider saved = repository.providers.get("glm-5");
        assertAll(
                () -> assertEquals("glm-5", response.modelKey()),
                () -> assertEquals("****1234", response.apiKeyMasked()),
                () -> assertTrue(saved.getApiKeyCipher().startsWith("test:")),
                () -> assertNotEquals("sk-test-1234", saved.getApiKeyCipher()),
                () -> assertTrue(saved.getDefaultModel()),
                () -> assertEquals("ENABLED", saved.getStatus())
        );
    }

    @Test
    void shouldSwitchDefaultGivenAnotherModelSelected() {
        // Given 已存在两个模型
        InMemoryModelProviderRepository repository = new InMemoryModelProviderRepository();
        ModelProviderCaseImpl useCase = newUseCase(repository, new StubRunRepository(0));
        useCase.create(request("glm-5", "GLM 5", true));
        useCase.create(request("qwen", "Qwen", false));

        // When 切换默认模型
        useCase.setDefault("qwen");

        // Then 只有新模型是默认模型
        assertAll(
                () -> assertFalse(repository.providers.get("glm-5").getDefaultModel()),
                () -> assertTrue(repository.providers.get("qwen").getDefaultModel())
        );
    }

    @Test
    void shouldRejectDeleteGivenDefaultModel() {
        // Given 默认模型
        InMemoryModelProviderRepository repository = new InMemoryModelProviderRepository();
        ModelProviderCaseImpl useCase = newUseCase(repository, new StubRunRepository(0));
        useCase.create(request("glm-5", "GLM 5", true));

        // When 删除默认模型 / Then 拒绝删除
        AppException ex = assertThrows(AppException.class, () -> useCase.delete("glm-5"));
        assertEquals("MODEL_DELETE_REJECTED", ex.getCode());
    }

    @Test
    void shouldRejectDeleteGivenRunningRunUsesModel() {
        // Given 模型被运行中任务引用
        InMemoryModelProviderRepository repository = new InMemoryModelProviderRepository();
        ModelProviderCaseImpl useCase = newUseCase(repository, new StubRunRepository(1));
        useCase.create(request("glm-5", "GLM 5", false));

        // When 删除模型 / Then 拒绝删除
        AppException ex = assertThrows(AppException.class, () -> useCase.delete("glm-5"));
        assertEquals("MODEL_IN_USE", ex.getCode());
    }

    @Test
    void shouldRejectCreateGivenStreamingDisabled() {
        // Given streaming 被关闭的模型配置
        ModelProviderCaseImpl useCase = newUseCase(new InMemoryModelProviderRepository(), new StubRunRepository(0));
        ModelProviderRequestDTO request = new ModelProviderRequestDTO(
                "legacy", "Legacy", "openai-compatible", "https://example.com/v1",
                "sk-test-1234", "legacy", "chat-completions", 0.2, 180,
                false, true, false, null);

        // When 创建模型 / Then v4 拒绝非 streaming 模型
        AppException ex = assertThrows(AppException.class, () -> useCase.create(request));
        assertEquals("STREAMING_REQUIRED", ex.getCode());
    }

    @Test
    void shouldUpdateModelKeyGivenModelKeyChanged() {
        // Given 已存在的模型配置
        InMemoryModelProviderRepository repository = new InMemoryModelProviderRepository();
        ModelProviderCaseImpl useCase = newUseCase(repository, new StubRunRepository(0));
        useCase.create(request("qwen3.6-plus", "Qwen Plus", false));

        // When 修改 Model Key
        ModelProviderRequestDTO update = request("qwen-max", "Qwen Max", false);
        var response = useCase.update("qwen3.6-plus", update);

        // Then 新 key 生效，旧 key 不再可查
        assertAll(
                () -> assertEquals("qwen-max", response.modelKey()),
                () -> assertTrue(repository.findByModelKey("qwen3.6-plus").isEmpty()),
                () -> assertTrue(repository.findByModelKey("qwen-max").isPresent())
        );
    }

    @Test
    void shouldKeepApiKeyCipherGivenUpdateWithoutApiKey() {
        // Given 已存在的模型配置
        InMemoryModelProviderRepository repository = new InMemoryModelProviderRepository();
        ModelProviderCaseImpl useCase = newUseCase(repository, new StubRunRepository(0));
        useCase.create(request("glm-5", "GLM", false));
        String oldCipher = repository.findByModelKey("glm-5").orElseThrow().getApiKeyCipher();

        // When 更新展示名但不提交 API Key
        ModelProviderRequestDTO update = new ModelProviderRequestDTO(
                "glm-5", "GLM 4.7", "openai-compatible", "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "", "glm-4.7", "chat-completions", 0.2, 180,
                true, true, false, null);
        useCase.update("glm-5", update);

        // Then 密文保持不变
        assertEquals(oldCipher, repository.findByModelKey("glm-5").orElseThrow().getApiKeyCipher());
    }

    private ModelProviderCaseImpl newUseCase(InMemoryModelProviderRepository repository, IAgentRunRepository runRepository) {
        return new ModelProviderCaseImpl(repository, runRepository, new TestApiKeyCipherPort());
    }

    private ModelProviderRequestDTO request(String modelKey, String displayName, boolean defaultModel) {
        return new ModelProviderRequestDTO(
                modelKey, displayName, "openai-compatible", "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "sk-test-1234", modelKey, "chat-completions", 0.2, 180,
                true, true, defaultModel, null);
    }

    static class InMemoryModelProviderRepository implements IModelProviderRepository {
        private final Map<String, ModelProvider> providers = new LinkedHashMap<>();
        private long nextId = 1L;

        @Override
        public void save(ModelProvider provider) {
            if (provider.getId() == null) {
                provider.setId(nextId++);
            }
            providers.put(provider.getModelKey(), provider);
        }

        @Override
        public void update(ModelProvider provider) {
            providers.entrySet().removeIf(entry -> provider.getId() != null && provider.getId().equals(entry.getValue().getId()));
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
            return new ArrayList<>(providers.values());
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

    record StubRunRepository(long runningCount) implements IAgentRunRepository {
        @Override
        public void save(cn.noname.coder.agent.domain.agent.model.entity.AgentRun run) {
        }

        @Override
        public void update(cn.noname.coder.agent.domain.agent.model.entity.AgentRun run) {
        }

        @Override
        public Optional<cn.noname.coder.agent.domain.agent.model.entity.AgentRun> findByRunId(String runId) {
            return Optional.empty();
        }

        @Override
        public long countByStatuses(Collection<AgentRunStatus> statuses) {
            return 0;
        }

        @Override
        public long countByModelAndStatuses(String modelKey, Collection<AgentRunStatus> statuses) {
            return runningCount;
        }
    }

    static class TestApiKeyCipherPort implements IApiKeyCipherPort {
        @Override
        public String encrypt(String plainText) {
            return "test:" + Base64.getEncoder().encodeToString(plainText.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String decrypt(String cipherText) {
            return new String(Base64.getDecoder().decode(cipherText.substring("test:".length())), StandardCharsets.UTF_8);
        }

        @Override
        public String mask(String plainText) {
            String value = decrypt(plainText);
            return "****" + value.substring(value.length() - 4);
        }
    }
}
