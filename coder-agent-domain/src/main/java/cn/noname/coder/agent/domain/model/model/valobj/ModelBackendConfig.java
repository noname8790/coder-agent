package cn.noname.coder.agent.domain.model.model.valobj;

import cn.noname.coder.agent.domain.context.model.valobj.ContextBudget;

public record ModelBackendConfig(
        String modelKey,
        String provider,
        String actualModel,
        String baseUrl,
        String apiKey,
        String endpointType,
        double temperature,
        int timeoutSeconds,
        boolean streamingEnabled,
        boolean toolCallingEnabled,
        ContextBudget budget
) {

    public ModelBackendConfig(String modelKey,
                              String provider,
                              String actualModel,
                              String baseUrl,
                              String apiKey,
                              String endpointType,
                              double temperature,
                              int timeoutSeconds) {
        this(modelKey, provider, actualModel, baseUrl, apiKey, endpointType, temperature, timeoutSeconds, true, true, null);
    }

    public String auditName() {
        if (modelKey == null || modelKey.isBlank() || modelKey.equals(actualModel)) {
            return actualModel;
        }
        return modelKey + "/" + actualModel;
    }
}
