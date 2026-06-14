package cn.noname.coder.agent;

import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRuntimePropertiesBindingTest {

    @Test
    void shouldBindPgvectorAndEmbeddingGivenV4Configuration() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("coder-agent.pgvector.enabled", "true")
                .withProperty("coder-agent.pgvector.url", "jdbc:postgresql://127.0.0.1:15432/coder-agent")
                .withProperty("coder-agent.pgvector.username", "postgres")
                .withProperty("coder-agent.pgvector.password", "secret")
                .withProperty("coder-agent.pgvector.schema", "public")
                .withProperty("coder-agent.pgvector.table-prefix", "coder_agent")
                .withProperty("coder-agent.pgvector.vector-dimensions", "1024")
                .withProperty("coder-agent.pgvector.index-type", "hnsw")
                .withProperty("coder-agent.pgvector.similarity", "cosine")
                .withProperty("coder-agent.embedding.provider", "openai-compatible")
                .withProperty("coder-agent.embedding.base-url", "https://dashscope.aliyuncs.com/compatible-mode/v1")
                .withProperty("coder-agent.embedding.api-key", "embedding-key")
                .withProperty("coder-agent.embedding.model", "text-embedding-v4")
                .withProperty("coder-agent.embedding.endpoint-type", "embeddings")
                .withProperty("coder-agent.embedding.timeout-seconds", "120");

        AgentRuntimeProperties properties = Binder.get(environment)
                .bind("coder-agent", AgentRuntimeProperties.class)
                .get();

        assertTrue(properties.getPgvector().isEnabled());
        assertEquals("jdbc:postgresql://127.0.0.1:15432/coder-agent", properties.getPgvector().getUrl());
        assertEquals(1024, properties.getPgvector().getVectorDimensions());
        assertEquals("text-embedding-v4", properties.getEmbedding().getModel());
        assertEquals("embeddings", properties.getEmbedding().getEndpointType());
    }

    @Test
    void shouldUseContextDefaultsGivenNoExplicitBudgetConfiguration() {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();

        assertEquals(32000, properties.getContext().getMaxInputTokens());
        assertEquals(6000, properties.getContext().getMemoryBudgetTokens());
        assertEquals(6000, properties.getContext().getFileSummaryBudgetTokens());
        assertEquals(8000, properties.getContext().getRawSnippetBudgetTokens());
        assertEquals(4000, properties.getContext().getToolResultBudgetTokens());
        assertEquals(4000, properties.getContext().getRecentMessageBudgetTokens());
        assertEquals(4000, properties.getContext().getOutputReserveTokens());
    }

    @Test
    void shouldBindModelLevelBudgetGivenProviderConfiguration() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("coder-agent.model.models.custom.provider", "openai-compatible")
                .withProperty("coder-agent.model.models.custom.base-url", "https://example.test/v1")
                .withProperty("coder-agent.model.models.custom.api-key", "secret")
                .withProperty("coder-agent.model.models.custom.model", "custom-model")
                .withProperty("coder-agent.model.models.custom.endpoint-type", "responses")
                .withProperty("coder-agent.model.models.custom.streaming-enabled", "true")
                .withProperty("coder-agent.model.models.custom.tool-calling-enabled", "true")
                .withProperty("coder-agent.model.models.custom.budget.max-context-tokens", "128000")
                .withProperty("coder-agent.model.models.custom.budget.max-output-tokens", "8192")
                .withProperty("coder-agent.model.models.custom.budget.memory-budget-tokens", "12000")
                .withProperty("coder-agent.model.models.custom.budget.raw-file-budget-tokens", "16000");

        AgentRuntimeProperties properties = Binder.get(environment)
                .bind("coder-agent", AgentRuntimeProperties.class)
                .get();
        AgentRuntimeProperties.Backend backend = properties.getModel().getModels().get("custom");

        assertNotNull(backend);
        assertEquals("responses", backend.getEndpointType());
        assertTrue(backend.getStreamingEnabled());
        assertTrue(backend.getToolCallingEnabled());
        assertEquals(128000, backend.getBudget().getMaxContextTokens());
        assertEquals(8192, backend.getBudget().getMaxOutputTokens());
        assertEquals(12000, backend.getBudget().getMemoryBudgetTokens());
        assertEquals(16000, backend.getBudget().getRawFileBudgetTokens());
    }
}
