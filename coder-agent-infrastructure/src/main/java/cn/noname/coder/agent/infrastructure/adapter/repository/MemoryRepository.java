package cn.noname.coder.agent.infrastructure.adapter.repository;

import cn.noname.coder.agent.domain.agent.adapter.repository.IMemoryRepository;
import cn.noname.coder.agent.domain.agent.model.entity.MemoryItem;
import cn.noname.coder.agent.domain.agent.model.entity.MemoryRecall;
import cn.noname.coder.agent.infrastructure.dao.IMemoryItemDao;
import cn.noname.coder.agent.infrastructure.dao.IMemoryRecallDao;
import cn.noname.coder.agent.infrastructure.dao.po.MemoryItemPO;
import cn.noname.coder.agent.infrastructure.dao.po.MemoryRecallPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MemoryRepository implements IMemoryRepository {

    private final IMemoryItemDao memoryItemDao;
    private final IMemoryRecallDao memoryRecallDao;

    @Override
    public void saveMemory(MemoryItem memory) {
        memoryItemDao.insert(toPo(memory));
    }

    @Override
    public void saveRecall(MemoryRecall recall) {
        MemoryRecallPO po = new MemoryRecallPO();
        po.setRecallId(recall.getRecallId());
        po.setRunId(recall.getRunId());
        po.setWorkspaceKey(recall.getWorkspaceKey());
        po.setQueryText(recall.getQueryText());
        po.setTopK(recall.getTopK());
        po.setMinScore(recall.getMinScore());
        po.setHitCount(recall.getHitCount());
        po.setSelectedCount(recall.getSelectedCount());
        po.setDetailJson(recall.getDetailJson());
        po.setCreatedAt(recall.getCreatedAt());
        memoryRecallDao.insert(po);
    }

    @Override
    public Optional<MemoryItem> findFreshFileMemory(String workspaceKey, String filePath, String contentHash) {
        return Optional.ofNullable(memoryItemDao.selectOne(new LambdaQueryWrapper<MemoryItemPO>()
                .eq(MemoryItemPO::getWorkspaceKey, workspaceKey)
                .eq(MemoryItemPO::getFilePath, filePath)
                .eq(MemoryItemPO::getContentHash, contentHash)
                .eq(MemoryItemPO::getFreshnessStatus, "FRESH")
                .last("LIMIT 1")))
                .map(this::toEntity);
    }

    @Override
    public List<MemoryItem> listByWorkspace(String workspaceKey) {
        return memoryItemDao.selectList(new LambdaQueryWrapper<MemoryItemPO>()
                        .eq(MemoryItemPO::getWorkspaceKey, workspaceKey))
                .stream()
                .map(this::toEntity)
                .toList();
    }

    @Override
    public void markFileMemoriesStale(String workspaceKey, String filePath, String currentContentHash) {
        LambdaUpdateWrapper<MemoryItemPO> wrapper = new LambdaUpdateWrapper<MemoryItemPO>()
                .eq(MemoryItemPO::getWorkspaceKey, workspaceKey)
                .eq(MemoryItemPO::getFilePath, filePath)
                .eq(MemoryItemPO::getFreshnessStatus, "FRESH")
                .set(MemoryItemPO::getFreshnessStatus, "STALE")
                .set(MemoryItemPO::getUpdatedAt, LocalDateTime.now());
        if (currentContentHash != null && !currentContentHash.isBlank()) {
            wrapper.ne(MemoryItemPO::getContentHash, currentContentHash);
        }
        memoryItemDao.update(wrapper);
    }

    @Override
    public void deleteByRunIds(String workspaceKey, Collection<String> runIds) {
        if (workspaceKey == null || workspaceKey.isBlank() || runIds == null || runIds.isEmpty()) {
            return;
        }
        for (String runId : runIds) {
            if (runId == null || runId.isBlank()) {
                continue;
            }
            memoryItemDao.delete(new LambdaQueryWrapper<MemoryItemPO>()
                    .eq(MemoryItemPO::getWorkspaceKey, workspaceKey)
                    .and(wrapper -> wrapper.eq(MemoryItemPO::getSourceId, runId)
                            .or()
                            .likeRight(MemoryItemPO::getSourceId, runId + ":")));
        }
        memoryRecallDao.delete(new LambdaQueryWrapper<MemoryRecallPO>()
                .in(MemoryRecallPO::getRunId, runIds));
    }

    @Override
    public void deleteByWorkspaceKey(String workspaceKey) {
        if (workspaceKey == null || workspaceKey.isBlank()) {
            return;
        }
        memoryItemDao.delete(new LambdaQueryWrapper<MemoryItemPO>()
                .eq(MemoryItemPO::getWorkspaceKey, workspaceKey));
        memoryRecallDao.delete(new LambdaQueryWrapper<MemoryRecallPO>()
                .eq(MemoryRecallPO::getWorkspaceKey, workspaceKey));
    }

    private MemoryItemPO toPo(MemoryItem memory) {
        MemoryItemPO po = new MemoryItemPO();
        po.setId(memory.getId());
        po.setMemoryId(memory.getMemoryId());
        po.setWorkspaceKey(memory.getWorkspaceKey());
        po.setSourceType(memory.getSourceType());
        po.setSourceId(memory.getSourceId());
        po.setFilePath(memory.getFilePath());
        po.setContentHash(memory.getContentHash());
        po.setFileMtime(memory.getFileMtime());
        po.setSummaryVersion(memory.getSummaryVersion());
        po.setTitle(memory.getTitle());
        po.setSummary(memory.getSummary());
        po.setMetadataJson(memory.getMetadataJson());
        po.setFreshnessStatus(memory.getFreshnessStatus());
        po.setCreatedAt(memory.getCreatedAt());
        po.setUpdatedAt(memory.getUpdatedAt());
        return po;
    }

    private MemoryItem toEntity(MemoryItemPO po) {
        return MemoryItem.builder()
                .id(po.getId())
                .memoryId(po.getMemoryId())
                .workspaceKey(po.getWorkspaceKey())
                .sourceType(po.getSourceType())
                .sourceId(po.getSourceId())
                .filePath(po.getFilePath())
                .contentHash(po.getContentHash())
                .fileMtime(po.getFileMtime())
                .summaryVersion(po.getSummaryVersion())
                .title(po.getTitle())
                .summary(po.getSummary())
                .metadataJson(po.getMetadataJson())
                .freshnessStatus(po.getFreshnessStatus())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }
}
