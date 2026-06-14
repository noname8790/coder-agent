package cn.noname.coder.agent.domain.agent.adapter.repository;

import cn.noname.coder.agent.domain.agent.model.entity.ModelProvider;

import java.util.List;
import java.util.Optional;

public interface IModelProviderRepository {

    void save(ModelProvider provider);

    void update(ModelProvider provider);

    Optional<ModelProvider> findByModelKey(String modelKey);

    Optional<ModelProvider> findDefaultEnabled();

    List<ModelProvider> listAll();

    List<ModelProvider> listEnabled();

    void clearDefaultModel();

    void deleteByModelKey(String modelKey);
}
