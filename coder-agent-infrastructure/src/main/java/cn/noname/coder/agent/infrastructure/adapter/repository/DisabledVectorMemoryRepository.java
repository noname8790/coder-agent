package cn.noname.coder.agent.infrastructure.adapter.repository;

import cn.noname.coder.agent.domain.agent.adapter.port.IVectorMemoryPort;
import cn.noname.coder.agent.domain.agent.model.entity.MemoryChunk;
import cn.noname.coder.agent.domain.agent.model.valobj.MemorySearchHit;
import cn.noname.coder.agent.domain.agent.model.valobj.MemorySearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Slf4j
@Repository
@ConditionalOnProperty(prefix = "coder-agent.pgvector", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledVectorMemoryRepository implements IVectorMemoryPort {

    @Override
    public void saveChunk(MemoryChunk chunk) {
        log.debug("pgvector 未启用，跳过记忆向量写入 workspaceKey={} memoryId={}",
                chunk == null ? null : chunk.getWorkspaceKey(),
                chunk == null ? null : chunk.getMemoryId());
    }

    @Override
    public List<MemorySearchHit> search(MemorySearchRequest request) {
        log.debug("pgvector 未启用，跳过记忆向量召回 workspaceKey={}",
                request == null ? null : request.workspaceKey());
        return List.of();
    }

    @Override
    public void markStale(String workspaceKey, String memoryId) {
        log.debug("pgvector 未启用，跳过记忆向量失效标记 workspaceKey={} memoryId={}", workspaceKey, memoryId);
    }

    @Override
    public void deleteByRunIds(String workspaceKey, Collection<String> runIds) {
        log.debug("pgvector 未启用，跳过记忆向量清理 workspaceKey={} runIds={}", workspaceKey, runIds);
    }
}
