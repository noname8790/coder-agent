package cn.noname.coder.agent.domain.agent.adapter.port;

import cn.noname.coder.agent.domain.agent.model.valobj.ModelBackendConfig;

import java.util.Optional;

public interface IModelConfigPort {

    ModelBackendConfig defaultModel();

    Optional<ModelBackendConfig> resolve(String modelKey);
}
