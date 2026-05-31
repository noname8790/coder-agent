package cn.noname.coder.agent.infrastructure.adapter.port;

import cn.noname.coder.agent.domain.agent.adapter.port.IModelConfigPort;
import cn.noname.coder.agent.domain.agent.model.valobj.ModelBackendConfig;
import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ModelConfigPort implements IModelConfigPort {

    private static final String DEFAULT_PROVIDER = "openai-compatible";

    private final AgentRuntimeProperties properties;

    @Override
    public ModelBackendConfig defaultModel() {
        AgentRuntimeProperties.Model model = properties.getModel();
        String defaultKey = defaultModelKey(model);
        if (!model.getModels().isEmpty()) {
            AgentRuntimeProperties.Backend backend = model.getModels().get(defaultKey);
            if (backend == null) {
                Map.Entry<String, AgentRuntimeProperties.Backend> first = model.getModels().entrySet().iterator().next();
                return resolveBackend(first.getKey(), first.getValue(), model);
            }
            return resolveBackend(defaultKey, backend, model);
        }
        return legacyBackend(defaultKey, model);
    }

    @Override
    public Optional<ModelBackendConfig> resolve(String modelKey) {
        if (!StringUtils.hasText(modelKey)) {
            return Optional.of(defaultModel());
        }
        AgentRuntimeProperties.Model model = properties.getModel();
        if (!model.getModels().isEmpty()) {
            AgentRuntimeProperties.Backend backend = model.getModels().get(modelKey);
            return backend == null ? Optional.empty() : Optional.of(resolveBackend(modelKey, backend, model));
        }
        String defaultKey = defaultModelKey(model);
        if (modelKey.equals(defaultKey) || modelKey.equals(model.getModel())) {
            return Optional.of(legacyBackend(defaultKey, model));
        }
        return Optional.empty();
    }

    private ModelBackendConfig resolveBackend(String key,
                                              AgentRuntimeProperties.Backend backend,
                                              AgentRuntimeProperties.Model defaults) {
        String actualModel = firstText(backend.getModel(), key);
        return new ModelBackendConfig(
                key,
                firstText(backend.getProvider(), DEFAULT_PROVIDER),
                actualModel,
                firstText(backend.getBaseUrl(), defaults.getBaseUrl()),
                firstText(backend.getApiKey(), defaults.getApiKey()),
                firstText(backend.getEndpointType(), defaults.getEndpointType()),
                backend.getTemperature() == null ? defaults.getTemperature() : backend.getTemperature(),
                backend.getTimeoutSeconds() == null ? defaults.getTimeoutSeconds() : backend.getTimeoutSeconds()
        );
    }

    private ModelBackendConfig legacyBackend(String key, AgentRuntimeProperties.Model model) {
        String actualModel = firstText(model.getModel(), key);
        return new ModelBackendConfig(
                firstText(key, actualModel),
                DEFAULT_PROVIDER,
                actualModel,
                model.getBaseUrl(),
                model.getApiKey(),
                model.getEndpointType(),
                model.getTemperature(),
                model.getTimeoutSeconds()
        );
    }

    private String defaultModelKey(AgentRuntimeProperties.Model model) {
        return firstText(model.getDefaultModelKey(), model.getModel(), "default");
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }
}
