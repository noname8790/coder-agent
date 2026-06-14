package cn.noname.coder.agent.api.dto;

import java.time.LocalDateTime;

public record ModelProviderResponseDTO(
        String modelKey,
        String displayName,
        String provider,
        String baseUrl,
        String apiKeyMasked,
        String modelName,
        String endpointType,
        Double temperature,
        Integer timeoutSeconds,
        Boolean streamingEnabled,
        Boolean toolCallingEnabled,
        Boolean defaultModel,
        String status,
        ContextBudgetDTO budget,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
