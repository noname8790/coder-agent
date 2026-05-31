package cn.noname.coder.agent.domain.agent.model.valobj;

public record ModelBackendConfig(
        String modelKey,
        String provider,
        String actualModel,
        String baseUrl,
        String apiKey,
        String endpointType,
        double temperature,
        int timeoutSeconds
) {

    public String auditName() {
        if (modelKey == null || modelKey.isBlank() || modelKey.equals(actualModel)) {
            return actualModel;
        }
        return modelKey + "/" + actualModel;
    }
}
