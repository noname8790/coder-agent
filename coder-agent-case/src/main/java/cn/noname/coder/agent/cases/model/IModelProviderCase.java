package cn.noname.coder.agent.cases.model;

import cn.noname.coder.agent.api.dto.ModelProviderListResponseDTO;
import cn.noname.coder.agent.api.dto.ModelProviderRequestDTO;
import cn.noname.coder.agent.api.dto.ModelProviderResponseDTO;

public interface IModelProviderCase {

    ModelProviderListResponseDTO list(boolean enabledOnly);

    ModelProviderResponseDTO query(String modelKey);

    ModelProviderResponseDTO create(ModelProviderRequestDTO request);

    ModelProviderResponseDTO update(String modelKey, ModelProviderRequestDTO request);

    ModelProviderResponseDTO enable(String modelKey);

    ModelProviderResponseDTO disable(String modelKey);

    ModelProviderResponseDTO setDefault(String modelKey);

    ModelProviderResponseDTO delete(String modelKey);
}
