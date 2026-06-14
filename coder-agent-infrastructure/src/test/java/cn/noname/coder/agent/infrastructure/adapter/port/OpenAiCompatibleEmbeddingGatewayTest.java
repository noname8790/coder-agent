package cn.noname.coder.agent.infrastructure.adapter.port;

import cn.noname.coder.agent.domain.agent.model.valobj.EmbeddingRequest;
import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import cn.noname.coder.agent.types.exception.AppException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiCompatibleEmbeddingGatewayTest {

    @Test
    void shouldCallOpenAiCompatibleEmbeddingsGivenGlobalEmbeddingConfig() throws Exception {
        // Given OpenAI-compatible embeddings endpoint
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"data":[{"embedding":[0.1,0.2,0.3]}],"usage":{"prompt_tokens":3,"total_tokens":3}}
                            """));
            AgentRuntimeProperties properties = properties(server.url("/v1").toString());
            OpenAiCompatibleEmbeddingGateway gateway = new OpenAiCompatibleEmbeddingGateway(properties);

            // When 生成 embedding
            var response = gateway.embed(new EmbeddingRequest(null, List.of("hello")));

            // Then 使用全局 model 和 /embeddings URL
            assertEquals("text-embedding-v4", response.model());
            assertEquals(List.of(0.1, 0.2, 0.3), response.embeddings().getFirst());
            assertEquals("/v1/embeddings", server.takeRequest().getPath());
        }
    }

    @Test
    void shouldRejectGivenEmbeddingConfigMissing() {
        // Given embedding 配置缺失
        OpenAiCompatibleEmbeddingGateway gateway = new OpenAiCompatibleEmbeddingGateway(new AgentRuntimeProperties());

        // When 调用 / Then 返回配置错误
        AppException ex = assertThrows(AppException.class,
                () -> gateway.embed(new EmbeddingRequest(null, List.of("hello"))));
        assertEquals("EMBEDDING_NOT_CONFIGURED", ex.getCode());
    }

    private AgentRuntimeProperties properties(String baseUrl) {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.getEmbedding().setBaseUrl(baseUrl);
        properties.getEmbedding().setApiKey("test-key");
        properties.getEmbedding().setModel("text-embedding-v4");
        return properties;
    }
}
