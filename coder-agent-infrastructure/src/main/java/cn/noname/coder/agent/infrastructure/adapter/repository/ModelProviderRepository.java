package cn.noname.coder.agent.infrastructure.adapter.repository;

import cn.noname.coder.agent.domain.model.adapter.repository.IModelProviderRepository;
import cn.noname.coder.agent.domain.model.model.entity.ModelProvider;
import cn.noname.coder.agent.domain.context.model.valobj.ContextBudget;
import cn.noname.coder.agent.infrastructure.dao.IModelProviderDao;
import cn.noname.coder.agent.infrastructure.dao.po.ModelProviderPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ModelProviderRepository implements IModelProviderRepository {

    private final IModelProviderDao dao;

    @Override
    public void save(ModelProvider provider) {
        dao.insert(toPo(provider));
    }

    @Override
    public void update(ModelProvider provider) {
        dao.updateById(toPo(provider));
    }

    @Override
    public Optional<ModelProvider> findByModelKey(String modelKey) {
        return Optional.ofNullable(dao.selectOne(new LambdaQueryWrapper<ModelProviderPO>()
                .eq(ModelProviderPO::getModelKey, modelKey)))
                .map(this::toEntity);
    }

    @Override
    public Optional<ModelProvider> findDefaultEnabled() {
        return Optional.ofNullable(dao.selectOne(new LambdaQueryWrapper<ModelProviderPO>()
                .eq(ModelProviderPO::getDefaultModel, true)
                .eq(ModelProviderPO::getStatus, "ENABLED")
                .last("LIMIT 1")))
                .map(this::toEntity);
    }

    @Override
    public List<ModelProvider> listAll() {
        return dao.selectList(new LambdaQueryWrapper<ModelProviderPO>()
                        .orderByDesc(ModelProviderPO::getDefaultModel)
                        .orderByAsc(ModelProviderPO::getModelKey))
                .stream()
                .map(this::toEntity)
                .toList();
    }

    @Override
    public List<ModelProvider> listEnabled() {
        return dao.selectList(new LambdaQueryWrapper<ModelProviderPO>()
                        .eq(ModelProviderPO::getStatus, "ENABLED")
                        .orderByDesc(ModelProviderPO::getDefaultModel)
                        .orderByAsc(ModelProviderPO::getModelKey))
                .stream()
                .map(this::toEntity)
                .toList();
    }

    @Override
    public void clearDefaultModel() {
        dao.update(null, new LambdaUpdateWrapper<ModelProviderPO>()
                .set(ModelProviderPO::getDefaultModel, false)
                .eq(ModelProviderPO::getDefaultModel, true));
    }

    @Override
    public void deleteByModelKey(String modelKey) {
        dao.delete(new LambdaQueryWrapper<ModelProviderPO>().eq(ModelProviderPO::getModelKey, modelKey));
    }

    private ModelProviderPO toPo(ModelProvider entity) {
        ModelProviderPO po = new ModelProviderPO();
        po.setId(entity.getId());
        po.setModelKey(entity.getModelKey());
        po.setDisplayName(entity.getDisplayName());
        po.setProvider(entity.getProvider());
        po.setBaseUrl(entity.getBaseUrl());
        po.setApiKeyCipher(entity.getApiKeyCipher());
        po.setModelName(entity.getModelName());
        po.setEndpointType(entity.getEndpointType());
        po.setTemperature(entity.getTemperature());
        po.setTimeoutSeconds(entity.getTimeoutSeconds());
        po.setStreamingEnabled(entity.getStreamingEnabled());
        po.setToolCallingEnabled(entity.getToolCallingEnabled());
        po.setDefaultModel(entity.getDefaultModel());
        po.setStatus(entity.getStatus());
        if (entity.getBudget() != null) {
            po.setMaxContextTokens(entity.getBudget().maxContextTokens());
            po.setMaxOutputTokens(entity.getBudget().maxOutputTokens());
            po.setInputBudgetTokens(entity.getBudget().inputBudgetTokens());
            po.setMemoryBudgetTokens(entity.getBudget().memoryBudgetTokens());
            po.setFileSummaryBudgetTokens(entity.getBudget().fileSummaryBudgetTokens());
            po.setRawFileBudgetTokens(entity.getBudget().rawFileBudgetTokens());
            po.setToolResultBudgetTokens(entity.getBudget().toolResultBudgetTokens());
            po.setRecentMessageBudgetTokens(entity.getBudget().recentMessageBudgetTokens());
        }
        po.setCreatedAt(entity.getCreatedAt());
        po.setUpdatedAt(entity.getUpdatedAt());
        return po;
    }

    private ModelProvider toEntity(ModelProviderPO po) {
        return ModelProvider.builder()
                .id(po.getId())
                .modelKey(po.getModelKey())
                .displayName(po.getDisplayName())
                .provider(po.getProvider())
                .baseUrl(po.getBaseUrl())
                .apiKeyCipher(po.getApiKeyCipher())
                .modelName(po.getModelName())
                .endpointType(po.getEndpointType())
                .temperature(po.getTemperature())
                .timeoutSeconds(po.getTimeoutSeconds())
                .streamingEnabled(po.getStreamingEnabled())
                .toolCallingEnabled(po.getToolCallingEnabled())
                .defaultModel(po.getDefaultModel())
                .status(po.getStatus())
                .budget(new ContextBudget(
                        po.getMaxContextTokens(),
                        po.getMaxOutputTokens(),
                        po.getInputBudgetTokens(),
                        po.getMemoryBudgetTokens(),
                        po.getFileSummaryBudgetTokens(),
                        po.getRawFileBudgetTokens(),
                        po.getToolResultBudgetTokens(),
                        po.getRecentMessageBudgetTokens()))
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }
}
