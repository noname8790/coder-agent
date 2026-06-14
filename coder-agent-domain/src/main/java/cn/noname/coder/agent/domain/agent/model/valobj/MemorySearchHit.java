package cn.noname.coder.agent.domain.agent.model.valobj;

public record MemorySearchHit(
        String chunkId,
        String memoryId,
        String workspaceKey,
        String content,
        double score,
        String metadataJson
) {
}
