package cn.noname.coder.agent.domain.model.model.valobj;

import java.util.List;

public record EmbeddingResponse(
        String model,
        List<List<Double>> embeddings,
        Integer promptTokens,
        Integer totalTokens
) {
}
