package cn.noname.coder.agent.infrastructure.adapter.repository;

import cn.noname.coder.agent.domain.context.adapter.repository.IContextSnapshotRepository;
import cn.noname.coder.agent.domain.context.model.entity.ContextSnapshot;
import cn.noname.coder.agent.infrastructure.dao.IContextSnapshotDao;
import cn.noname.coder.agent.infrastructure.dao.po.ContextSnapshotPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ContextSnapshotRepository implements IContextSnapshotRepository {

    private final IContextSnapshotDao dao;

    @Override
    public void save(ContextSnapshot snapshot) {
        ContextSnapshotPO po = new ContextSnapshotPO();
        po.setSnapshotId(snapshot.getSnapshotId());
        po.setRunId(snapshot.getRunId());
        po.setWorkspaceKey(snapshot.getWorkspaceKey());
        po.setModelCallNo(snapshot.getModelCallNo());
        po.setModelKey(snapshot.getModelKey());
        po.setBudgetSource(snapshot.getBudgetSource());
        po.setRawEstimatedTokens(snapshot.getRawEstimatedTokens());
        po.setFinalEstimatedTokens(snapshot.getFinalEstimatedTokens());
        po.setCompressionRatio(snapshot.getCompressionRatio());
        po.setMemoryHitCount(snapshot.getMemoryHitCount());
        po.setStaleMemoryCount(snapshot.getStaleMemoryCount());
        po.setSelectedFileSummaryCount(snapshot.getSelectedFileSummaryCount());
        po.setSelectedRawSnippetCount(snapshot.getSelectedRawSnippetCount());
        po.setSnapshotPath(snapshot.getSnapshotPath());
        po.setSectionDetailJson(snapshot.getSectionDetailJson());
        po.setCreatedAt(snapshot.getCreatedAt());
        dao.insert(po);
    }

    @Override
    public List<ContextSnapshot> listByRunId(String runId) {
        return dao.selectList(new LambdaQueryWrapper<ContextSnapshotPO>()
                        .eq(ContextSnapshotPO::getRunId, runId)
                        .orderByAsc(ContextSnapshotPO::getModelCallNo))
                .stream()
                .map(this::toEntity)
                .toList();
    }

    @Override
    public void deleteByRunIds(Collection<String> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return;
        }
        dao.delete(new LambdaQueryWrapper<ContextSnapshotPO>()
                .in(ContextSnapshotPO::getRunId, runIds));
    }

    private ContextSnapshot toEntity(ContextSnapshotPO po) {
        return ContextSnapshot.builder()
                .id(po.getId())
                .snapshotId(po.getSnapshotId())
                .runId(po.getRunId())
                .workspaceKey(po.getWorkspaceKey())
                .modelCallNo(po.getModelCallNo())
                .modelKey(po.getModelKey())
                .budgetSource(po.getBudgetSource())
                .rawEstimatedTokens(po.getRawEstimatedTokens())
                .finalEstimatedTokens(po.getFinalEstimatedTokens())
                .compressionRatio(po.getCompressionRatio())
                .memoryHitCount(po.getMemoryHitCount())
                .staleMemoryCount(po.getStaleMemoryCount())
                .selectedFileSummaryCount(po.getSelectedFileSummaryCount())
                .selectedRawSnippetCount(po.getSelectedRawSnippetCount())
                .snapshotPath(po.getSnapshotPath())
                .sectionDetailJson(po.getSectionDetailJson())
                .createdAt(po.getCreatedAt())
                .build();
    }
}
