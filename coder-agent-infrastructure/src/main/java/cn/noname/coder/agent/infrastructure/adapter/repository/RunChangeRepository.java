package cn.noname.coder.agent.infrastructure.adapter.repository;

import cn.noname.coder.agent.domain.agent.adapter.repository.IRunChangeRepository;
import cn.noname.coder.agent.domain.agent.model.entity.RunChangeSet;
import cn.noname.coder.agent.domain.agent.model.entity.RunFileChange;
import cn.noname.coder.agent.infrastructure.dao.IRunChangeSetDao;
import cn.noname.coder.agent.infrastructure.dao.IRunFileChangeDao;
import cn.noname.coder.agent.infrastructure.dao.po.RunChangeSetPO;
import cn.noname.coder.agent.infrastructure.dao.po.RunFileChangePO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RunChangeRepository implements IRunChangeRepository {

    private final IRunChangeSetDao changeSetDao;
    private final IRunFileChangeDao fileChangeDao;

    @Override
    public void saveChangeSet(RunChangeSet changeSet, List<RunFileChange> files) {
        RunChangeSetPO existing = changeSetDao.selectOne(new LambdaQueryWrapper<RunChangeSetPO>()
                .eq(RunChangeSetPO::getRunId, changeSet.getRunId()));
        RunChangeSetPO po = toPo(changeSet);
        if (existing == null) {
            changeSetDao.insert(po);
            changeSet.setId(po.getId());
        } else {
            po.setId(existing.getId());
            changeSetDao.updateById(po);
            fileChangeDao.delete(new LambdaQueryWrapper<RunFileChangePO>().eq(RunFileChangePO::getRunId, changeSet.getRunId()));
        }
        if (files != null) {
            for (RunFileChange file : files) {
                RunFileChangePO filePo = toPo(file);
                fileChangeDao.insert(filePo);
                file.setId(filePo.getId());
            }
        }
    }

    @Override
    public Optional<RunChangeSet> findChangeSet(String runId) {
        return Optional.ofNullable(changeSetDao.selectOne(new LambdaQueryWrapper<RunChangeSetPO>()
                        .eq(RunChangeSetPO::getRunId, runId)))
                .map(this::toEntity);
    }

    @Override
    public List<RunFileChange> listFileChanges(String runId) {
        return fileChangeDao.selectList(new LambdaQueryWrapper<RunFileChangePO>()
                        .eq(RunFileChangePO::getRunId, runId)
                        .orderByAsc(RunFileChangePO::getId))
                .stream()
                .map(this::toEntity)
                .toList();
    }

    @Override
    public List<RunChangeSet> listByConversationId(String conversationId) {
        return changeSetDao.selectList(new LambdaQueryWrapper<RunChangeSetPO>()
                        .eq(RunChangeSetPO::getConversationId, conversationId)
                        .orderByAsc(RunChangeSetPO::getCreatedAt))
                .stream()
                .map(this::toEntity)
                .toList();
    }

    @Override
    public void updateChangeSet(RunChangeSet changeSet) {
        RunChangeSetPO po = toPo(changeSet);
        if (po.getId() == null) {
            RunChangeSetPO existing = changeSetDao.selectOne(new LambdaQueryWrapper<RunChangeSetPO>()
                    .eq(RunChangeSetPO::getRunId, changeSet.getRunId()));
            if (existing != null) {
                po.setId(existing.getId());
            }
        }
        changeSetDao.updateById(po);
    }

    @Override
    public void deleteByRunIds(Collection<String> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return;
        }
        fileChangeDao.delete(new LambdaQueryWrapper<RunFileChangePO>().in(RunFileChangePO::getRunId, runIds));
        changeSetDao.delete(new LambdaQueryWrapper<RunChangeSetPO>().in(RunChangeSetPO::getRunId, runIds));
    }

    private RunChangeSetPO toPo(RunChangeSet entity) {
        RunChangeSetPO po = new RunChangeSetPO();
        po.setId(entity.getId());
        po.setRunId(entity.getRunId());
        po.setWorkspaceKey(entity.getWorkspaceKey());
        po.setConversationId(entity.getConversationId());
        po.setStatus(entity.getStatus());
        po.setReversible(entity.getReversible());
        po.setFailureReason(entity.getFailureReason());
        po.setCreatedAt(entity.getCreatedAt());
        po.setUpdatedAt(entity.getUpdatedAt());
        return po;
    }

    private RunFileChangePO toPo(RunFileChange entity) {
        RunFileChangePO po = new RunFileChangePO();
        po.setId(entity.getId());
        po.setRunId(entity.getRunId());
        po.setFilePath(entity.getFilePath());
        po.setChangeType(entity.getChangeType());
        po.setBeforeHash(entity.getBeforeHash());
        po.setAfterHash(entity.getAfterHash());
        po.setBeforeSnapshotPath(entity.getBeforeSnapshotPath());
        po.setAfterSnapshotPath(entity.getAfterSnapshotPath());
        po.setReversible(entity.getReversible());
        po.setIrreversibleReason(entity.getIrreversibleReason());
        po.setCreatedAt(entity.getCreatedAt());
        return po;
    }

    private RunChangeSet toEntity(RunChangeSetPO po) {
        return RunChangeSet.builder()
                .id(po.getId())
                .runId(po.getRunId())
                .workspaceKey(po.getWorkspaceKey())
                .conversationId(po.getConversationId())
                .status(po.getStatus())
                .reversible(po.getReversible())
                .failureReason(po.getFailureReason())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }

    private RunFileChange toEntity(RunFileChangePO po) {
        return RunFileChange.builder()
                .id(po.getId())
                .runId(po.getRunId())
                .filePath(po.getFilePath())
                .changeType(po.getChangeType())
                .beforeHash(po.getBeforeHash())
                .afterHash(po.getAfterHash())
                .beforeSnapshotPath(po.getBeforeSnapshotPath())
                .afterSnapshotPath(po.getAfterSnapshotPath())
                .reversible(po.getReversible())
                .irreversibleReason(po.getIrreversibleReason())
                .createdAt(po.getCreatedAt())
                .build();
    }
}
