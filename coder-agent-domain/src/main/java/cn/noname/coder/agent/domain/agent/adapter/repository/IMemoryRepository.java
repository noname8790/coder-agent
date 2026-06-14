package cn.noname.coder.agent.domain.agent.adapter.repository;

import cn.noname.coder.agent.domain.agent.model.entity.MemoryItem;
import cn.noname.coder.agent.domain.agent.model.entity.MemoryRecall;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IMemoryRepository {

    void saveMemory(MemoryItem memory);

    void saveRecall(MemoryRecall recall);

    Optional<MemoryItem> findFreshFileMemory(String workspaceKey, String filePath, String contentHash);

    List<MemoryItem> listByWorkspace(String workspaceKey);

    void markFileMemoriesStale(String workspaceKey, String filePath, String currentContentHash);

    default void deleteByRunIds(String workspaceKey, Collection<String> runIds) {
    }

    default void deleteByWorkspaceKey(String workspaceKey) {
    }
}
