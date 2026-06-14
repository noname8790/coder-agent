package cn.noname.coder.agent.domain.agent.adapter.port;

import cn.noname.coder.agent.domain.agent.model.valobj.EmbeddingRequest;
import cn.noname.coder.agent.domain.agent.model.valobj.EmbeddingResponse;

public interface IEmbeddingGateway {

    EmbeddingResponse embed(EmbeddingRequest request);
}
