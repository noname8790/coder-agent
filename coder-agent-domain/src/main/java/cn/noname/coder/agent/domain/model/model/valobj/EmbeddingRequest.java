package cn.noname.coder.agent.domain.model.model.valobj;

import java.util.List;

public record EmbeddingRequest(
        String model,
        List<String> inputs
) {
}
