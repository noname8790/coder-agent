package cn.noname.coder.agent.cases.model.impl;

import cn.noname.coder.agent.api.dto.ContextBudgetDTO;
import cn.noname.coder.agent.api.dto.ModelProviderListResponseDTO;
import cn.noname.coder.agent.api.dto.ModelProviderRequestDTO;
import cn.noname.coder.agent.api.dto.ModelProviderResponseDTO;
import cn.noname.coder.agent.cases.model.IModelProviderCase;
import cn.noname.coder.agent.domain.agent.adapter.port.IApiKeyCipherPort;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRunRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IModelProviderRepository;
import cn.noname.coder.agent.domain.agent.model.entity.ModelProvider;
import cn.noname.coder.agent.domain.agent.model.valobj.ContextBudget;
import cn.noname.coder.agent.types.enums.AgentRunStatus;
import cn.noname.coder.agent.types.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelProviderCaseImpl implements IModelProviderCase {

    private static final String ENABLED = "ENABLED";
    private static final String DISABLED = "DISABLED";

    private final IModelProviderRepository repository;
    private final IAgentRunRepository runRepository;
    private final IApiKeyCipherPort apiKeyCipherPort;

    @Override
    public ModelProviderListResponseDTO list(boolean enabledOnly) {
        List<ModelProviderResponseDTO> models = (enabledOnly ? repository.listEnabled() : repository.listAll())
                .stream()
                .map(this::toDto)
                .toList();
        return new ModelProviderListResponseDTO(models);
    }

    @Override
    public ModelProviderResponseDTO query(String modelKey) {
        return repository.findByModelKey(modelKey)
                .map(this::toDto)
                .orElseThrow(() -> new AppException("MODEL_NOT_CONFIGURED", "模型未配置：" + modelKey));
    }

    @Override
    public ModelProviderResponseDTO create(ModelProviderRequestDTO request) {
        validate(request, true);
        repository.findByModelKey(request.modelKey()).ifPresent(existing -> {
            throw new AppException("MODEL_ALREADY_EXISTS", "模型配置已存在：" + request.modelKey());
        });
        LocalDateTime now = LocalDateTime.now();
        ModelProvider provider = fromRequest(request, null);
        provider.setStatus(ENABLED);
        provider.setCreatedAt(now);
        provider.setUpdatedAt(now);
        if (Boolean.TRUE.equals(provider.getDefaultModel())) {
            repository.clearDefaultModel();
        }
        repository.save(provider);
        log.info("模型配置已创建 modelKey={} endpointType={} streamingEnabled={} toolCallingEnabled={}",
                provider.getModelKey(), provider.getEndpointType(), provider.getStreamingEnabled(), provider.getToolCallingEnabled());
        return toDto(provider);
    }

    @Override
    public ModelProviderResponseDTO update(String modelKey, ModelProviderRequestDTO request) {
        ModelProvider existing = mustFind(modelKey);
        validate(request, false);
        ModelProvider updated = fromRequest(request, existing);
        updated.setId(existing.getId());
        updated.setModelKey(existing.getModelKey());
        updated.setStatus(existing.getStatus());
        updated.setCreatedAt(existing.getCreatedAt());
        updated.setUpdatedAt(LocalDateTime.now());
        if (!StringUtils.hasText(request.apiKey())) {
            updated.setApiKeyCipher(existing.getApiKeyCipher());
        }
        if (Boolean.TRUE.equals(updated.getDefaultModel())) {
            repository.clearDefaultModel();
        }
        repository.update(updated);
        log.info("模型配置已更新 modelKey={} endpointType={} streamingEnabled={} toolCallingEnabled={}",
                updated.getModelKey(), updated.getEndpointType(), updated.getStreamingEnabled(), updated.getToolCallingEnabled());
        return toDto(updated);
    }

    @Override
    public ModelProviderResponseDTO enable(String modelKey) {
        ModelProvider provider = mustFind(modelKey);
        provider.setStatus(ENABLED);
        provider.setUpdatedAt(LocalDateTime.now());
        repository.update(provider);
        log.info("模型配置已启用 modelKey={}", modelKey);
        return toDto(provider);
    }

    @Override
    public ModelProviderResponseDTO disable(String modelKey) {
        ModelProvider provider = mustFind(modelKey);
        rejectIfInUse(provider);
        provider.setStatus(DISABLED);
        provider.setDefaultModel(false);
        provider.setUpdatedAt(LocalDateTime.now());
        repository.update(provider);
        log.info("模型配置已停用 modelKey={}", modelKey);
        return toDto(provider);
    }

    @Override
    public ModelProviderResponseDTO setDefault(String modelKey) {
        ModelProvider provider = mustFind(modelKey);
        if (!ENABLED.equals(provider.getStatus())) {
            throw new AppException("MODEL_DISABLED", "停用模型不能设为默认：" + modelKey);
        }
        repository.clearDefaultModel();
        provider.setDefaultModel(true);
        provider.setUpdatedAt(LocalDateTime.now());
        repository.update(provider);
        log.info("默认模型已切换 modelKey={}", modelKey);
        return toDto(provider);
    }

    @Override
    public ModelProviderResponseDTO delete(String modelKey) {
        ModelProvider provider = mustFind(modelKey);
        rejectIfInUse(provider);
        if (Boolean.TRUE.equals(provider.getDefaultModel())) {
            throw new AppException("MODEL_DELETE_REJECTED", "默认模型不能删除：" + modelKey);
        }
        repository.deleteByModelKey(modelKey);
        log.info("模型配置已删除 modelKey={}", modelKey);
        return toDto(provider);
    }

    private void rejectIfInUse(ModelProvider provider) {
        long running = runRepository.countByModelAndStatuses(provider.getModelKey(),
                List.of(AgentRunStatus.CREATED, AgentRunStatus.RUNNING, AgentRunStatus.WAITING_APPROVAL));
        if (running > 0) {
            throw new AppException("MODEL_IN_USE", "模型正在被运行中任务使用：" + provider.getModelKey());
        }
    }

    private ModelProvider mustFind(String modelKey) {
        return repository.findByModelKey(modelKey)
                .orElseThrow(() -> new AppException("MODEL_NOT_CONFIGURED", "模型未配置：" + modelKey));
    }

    private void validate(ModelProviderRequestDTO request, boolean create) {
        if (request == null) {
            throw new AppException("INVALID_ARGUMENT", "模型配置不能为空");
        }
        if (create && !StringUtils.hasText(request.modelKey())) {
            throw new AppException("INVALID_ARGUMENT", "modelKey 不能为空");
        }
        if (!StringUtils.hasText(request.displayName())
                || !StringUtils.hasText(request.baseUrl())
                || !StringUtils.hasText(request.modelName())
                || !StringUtils.hasText(request.endpointType())) {
            throw new AppException("INVALID_ARGUMENT", "模型显示名称、baseUrl、modelName 和 endpointType 不能为空");
        }
        if (!"chat-completions".equals(request.endpointType()) && !"responses".equals(request.endpointType())) {
            throw new AppException("INVALID_ENDPOINT_TYPE", "endpointType 仅支持 chat-completions 或 responses");
        }
        if (Boolean.FALSE.equals(request.streamingEnabled())) {
            throw new AppException("STREAMING_REQUIRED", "v4 模型配置必须支持 streaming");
        }
    }

    private ModelProvider fromRequest(ModelProviderRequestDTO request, ModelProvider existing) {
        return ModelProvider.builder()
                .modelKey(existing == null ? request.modelKey() : existing.getModelKey())
                .displayName(request.displayName())
                .provider(StringUtils.hasText(request.provider()) ? request.provider() : "openai-compatible")
                .baseUrl(request.baseUrl())
                .apiKeyCipher(StringUtils.hasText(request.apiKey()) ? apiKeyCipherPort.encrypt(request.apiKey()) : "")
                .modelName(request.modelName())
                .endpointType(request.endpointType())
                .temperature(request.temperature() == null ? 0.2 : request.temperature())
                .timeoutSeconds(request.timeoutSeconds() == null ? 180 : request.timeoutSeconds())
                .streamingEnabled(request.streamingEnabled() == null || request.streamingEnabled())
                .toolCallingEnabled(request.toolCallingEnabled() == null || request.toolCallingEnabled())
                .defaultModel(Boolean.TRUE.equals(request.defaultModel()))
                .budget(toBudget(request.budget()))
                .build();
    }

    private ContextBudget toBudget(ContextBudgetDTO dto) {
        if (dto == null) {
            return null;
        }
        return new ContextBudget(
                dto.maxContextTokens(),
                dto.maxOutputTokens(),
                dto.inputBudgetTokens(),
                dto.memoryBudgetTokens(),
                dto.fileSummaryBudgetTokens(),
                dto.rawFileBudgetTokens(),
                dto.toolResultBudgetTokens(),
                dto.recentMessageBudgetTokens());
    }

    private ModelProviderResponseDTO toDto(ModelProvider provider) {
        return new ModelProviderResponseDTO(
                provider.getModelKey(),
                provider.getDisplayName(),
                provider.getProvider(),
                provider.getBaseUrl(),
                apiKeyCipherPort.mask(provider.getApiKeyCipher()),
                provider.getModelName(),
                provider.getEndpointType(),
                provider.getTemperature(),
                provider.getTimeoutSeconds(),
                provider.getStreamingEnabled(),
                provider.getToolCallingEnabled(),
                provider.getDefaultModel(),
                provider.getStatus(),
                toDto(provider.getBudget()),
                provider.getCreatedAt(),
                provider.getUpdatedAt());
    }

    private ContextBudgetDTO toDto(ContextBudget budget) {
        if (budget == null) {
            return null;
        }
        return new ContextBudgetDTO(
                budget.maxContextTokens(),
                budget.maxOutputTokens(),
                budget.inputBudgetTokens(),
                budget.memoryBudgetTokens(),
                budget.fileSummaryBudgetTokens(),
                budget.rawFileBudgetTokens(),
                budget.toolResultBudgetTokens(),
                budget.recentMessageBudgetTokens());
    }
}
