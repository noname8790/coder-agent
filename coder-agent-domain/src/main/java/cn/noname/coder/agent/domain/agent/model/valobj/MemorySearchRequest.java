package cn.noname.coder.agent.domain.agent.model.valobj;

import java.util.List;

public record MemorySearchRequest(
        String workspaceKey,
        List<Double> queryEmbedding,
        int topK,
        double minScore
) {
}
