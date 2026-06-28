package cn.noname.coder.agent.infrastructure.gateway;

import cn.noname.coder.agent.domain.model.model.valobj.ModelBackendConfig;
import cn.noname.coder.agent.types.exception.AppException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * OpenAI-compatible HTTP 网关。
 */
@Service
public class OpenAiHttpGatewayService {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public String postResponses(String body, ModelBackendConfig modelConfig) {
        try {
            Duration timeout = Duration.ofSeconds(modelConfig.timeoutSeconds());
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(timeout)
                    .readTimeout(timeout)
                    .writeTimeout(timeout)
                    .callTimeout(timeout)
                    .build();
            Request httpRequest = new Request.Builder()
                    .url(resolveUrl(modelConfig))
                    .addHeader("Authorization", "Bearer " + modelConfig.apiKey())
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body, JSON))
                    .build();
            try (Response response = client.newCall(httpRequest).execute()) {
                String responseBody = response.body() == null ? "" : response.body().string();
                if (!response.isSuccessful()) {
                    throw new AppException("MODEL_HTTP_ERROR", "模型调用失败 HTTP " + response.code() + ": " + responseBody);
                }
                return responseBody;
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException("MODEL_CALL_FAILED", "模型调用异常：" + e.getMessage());
        }
    }

    private String resolveUrl(ModelBackendConfig modelConfig) {
        if ("chat-completions".equalsIgnoreCase(modelConfig.endpointType())) {
            return resolveChatCompletionsUrl(modelConfig.baseUrl());
        }
        return resolveResponsesUrl(modelConfig.baseUrl());
    }

    private String resolveResponsesUrl(String baseUrl) {
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return trimmed.endsWith("/responses") ? trimmed : trimmed + "/responses";
    }

    private String resolveChatCompletionsUrl(String baseUrl) {
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return trimmed.endsWith("/chat/completions") ? trimmed : trimmed + "/chat/completions";
    }
}
