package cn.noname.coder.agent.domain.memory.model.valobj;

import java.util.List;

public record MemorySearchRequest(
        String workspaceKey,
        List<Double> queryEmbedding,
        int topK,
        double minScore
) {
}
