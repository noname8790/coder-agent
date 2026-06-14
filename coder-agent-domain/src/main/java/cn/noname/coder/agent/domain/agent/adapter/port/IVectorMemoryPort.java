package cn.noname.coder.agent.domain.agent.adapter.port;

import cn.noname.coder.agent.domain.agent.model.entity.MemoryChunk;
import cn.noname.coder.agent.domain.agent.model.valobj.MemorySearchHit;
import cn.noname.coder.agent.domain.agent.model.valobj.MemorySearchRequest;

import java.util.Collection;
import java.util.List;

public interface IVectorMemoryPort {

    void saveChunk(MemoryChunk chunk);

    List<MemorySearchHit> search(MemorySearchRequest request);

    void markStale(String workspaceKey, String memoryId);

    default void deleteByRunIds(String workspaceKey, Collection<String> runIds) {
    }

    default void deleteByWorkspaceKey(String workspaceKey) {
    }
}
