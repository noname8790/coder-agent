package cn.noname.coder.agent.infrastructure.adapter.port;

import cn.noname.coder.agent.domain.model.adapter.port.IEmbeddingGateway;
import cn.noname.coder.agent.domain.model.model.valobj.EmbeddingRequest;
import cn.noname.coder.agent.domain.model.model.valobj.EmbeddingResponse;
import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import cn.noname.coder.agent.types.exception.AppException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OpenAiCompatibleEmbeddingGateway implements IEmbeddingGateway {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final AgentRuntimeProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public EmbeddingResponse embed(EmbeddingRequest request) {
        AgentRuntimeProperties.Embedding config = properties.getEmbedding();
        String model = StringUtils.hasText(request.model()) ? request.model() : config.getModel();
        if (!StringUtils.hasText(config.getBaseUrl()) || !StringUtils.hasText(config.getApiKey()) || !StringUtils.hasText(model)) {
            throw new AppException("EMBEDDING_NOT_CONFIGURED", "Embedding 配置不完整");
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("input", request.inputs());
            Request httpRequest = new Request.Builder()
                    .url(resolveUrl(config.getBaseUrl()))
                    .addHeader("Authorization", "Bearer " + config.getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(objectMapper.writeValueAsString(payload), JSON))
                    .build();
            try (Response response = client(config.getTimeoutSeconds()).newCall(httpRequest).execute()) {
                String body = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    throw new AppException("EMBEDDING_HTTP_ERROR", "Embedding 调用失败 HTTP " + response.code() + ": " + body);
                }
                return parse(model, body);
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException("EMBEDDING_FAILED", "Embedding 调用异常：" + e.getMessage());
        }
    }

    private EmbeddingResponse parse(String model, String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        List<List<Double>> embeddings = new ArrayList<>();
        for (JsonNode item : root.path("data")) {
            List<Double> vector = new ArrayList<>();
            for (JsonNode value : item.path("embedding")) {
                vector.add(value.asDouble());
            }
            embeddings.add(vector);
        }
        JsonNode usage = root.path("usage");
        return new EmbeddingResponse(model, embeddings,
                usage.path("prompt_tokens").isMissingNode() ? null : usage.path("prompt_tokens").asInt(),
                usage.path("total_tokens").isMissingNode() ? null : usage.path("total_tokens").asInt());
    }

    private String resolveUrl(String baseUrl) {
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return trimmed.endsWith("/embeddings") ? trimmed : trimmed + "/embeddings";
    }

    private OkHttpClient client(int timeoutSeconds) {
        Duration timeout = Duration.ofSeconds(timeoutSeconds <= 0 ? 120 : timeoutSeconds);
        return new OkHttpClient.Builder()
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .callTimeout(timeout)
                .build();
    }
}
