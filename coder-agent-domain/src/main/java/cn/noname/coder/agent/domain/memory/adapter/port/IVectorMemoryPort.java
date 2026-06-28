package cn.noname.coder.agent.domain.memory.adapter.port;

import cn.noname.coder.agent.domain.memory.model.entity.MemoryChunk;
import cn.noname.coder.agent.domain.memory.model.valobj.MemorySearchHit;
import cn.noname.coder.agent.domain.memory.model.valobj.MemorySearchRequest;

import java.util.Collection;
import java.util.List;

public interface IVectorMemoryPort {

    void saveChunk(MemoryChunk chunk);

    List<MemorySearchHit> search(MemorySearchRequest request);

    void markStale(String workspaceKey, String memoryId);

    default void deleteByMemoryIds(String workspaceKey, Collection<String> memoryIds) {
    }

    default void deleteByRunIds(String workspaceKey, Collection<String> runIds) {
    }

    default void deleteByWorkspaceKey(String workspaceKey) {
    }
}
