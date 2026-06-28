package cn.noname.coder.agent.infrastructure.adapter.port;

import cn.noname.coder.agent.domain.model.adapter.port.IApiKeyCipherPort;
import cn.noname.coder.agent.domain.model.adapter.port.IModelConfigPort;
import cn.noname.coder.agent.domain.model.adapter.repository.IModelProviderRepository;
import cn.noname.coder.agent.domain.model.model.entity.ModelProvider;
import cn.noname.coder.agent.domain.model.model.valobj.ModelBackendConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ModelConfigPort implements IModelConfigPort {

    private final IModelProviderRepository repository;
    private final IApiKeyCipherPort apiKeyCipherPort;

    @Override
    public ModelBackendConfig defaultModel() {
        return repository.findDefaultEnabled()
                .or(() -> repository.listEnabled().stream().findFirst())
                .map(this::toBackendConfig)
                .orElseThrow(() -> new IllegalStateException("未配置默认模型"));
    }

    @Override
    public Optional<ModelBackendConfig> resolve(String modelKey) {
        if (!StringUtils.hasText(modelKey)) {
            return repository.findDefaultEnabled()
                    .or(() -> repository.listEnabled().stream().findFirst())
                    .map(this::toBackendConfig);
        }
        return repository.findByModelKey(modelKey)
                .filter(provider -> "ENABLED".equals(provider.getStatus()))
                .map(this::toBackendConfig);
    }

    private ModelBackendConfig toBackendConfig(ModelProvider provider) {
        return new ModelBackendConfig(
                provider.getModelKey(),
                provider.getProvider(),
                provider.getModelName(),
                provider.getBaseUrl(),
                apiKeyCipherPort.decrypt(provider.getApiKeyCipher()),
                provider.getEndpointType(),
                provider.getTemperature() == null ? 0.2 : provider.getTemperature(),
                provider.getTimeoutSeconds() == null ? 180 : provider.getTimeoutSeconds(),
                provider.getStreamingEnabled() == null || provider.getStreamingEnabled(),
                provider.getToolCallingEnabled() == null || provider.getToolCallingEnabled(),
                provider.getBudget()
        );
    }
}
