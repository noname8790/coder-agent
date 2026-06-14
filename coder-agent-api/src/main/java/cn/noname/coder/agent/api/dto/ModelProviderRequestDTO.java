package cn.noname.coder.agent.api.dto;

public record ModelProviderRequestDTO(
        String modelKey,
        String displayName,
        String provider,
        String baseUrl,
        String apiKey,
        String modelName,
        String endpointType,
        Double temperature,
        Integer timeoutSeconds,
        Boolean streamingEnabled,
        Boolean toolCallingEnabled,
        Boolean defaultModel,
        ContextBudgetDTO budget
) {
}
