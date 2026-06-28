package cn.noname.coder.agent.domain.model.adapter.port;

import cn.noname.coder.agent.domain.model.model.valobj.EmbeddingRequest;
import cn.noname.coder.agent.domain.model.model.valobj.EmbeddingResponse;

public interface IEmbeddingGateway {

    EmbeddingResponse embed(EmbeddingRequest request);
}
