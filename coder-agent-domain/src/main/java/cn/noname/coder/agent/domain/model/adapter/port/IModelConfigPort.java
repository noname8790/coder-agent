package cn.noname.coder.agent.domain.model.adapter.port;

import cn.noname.coder.agent.domain.model.model.valobj.ModelBackendConfig;

import java.util.Optional;

public interface IModelConfigPort {

    ModelBackendConfig defaultModel();

    Optional<ModelBackendConfig> resolve(String modelKey);
}
