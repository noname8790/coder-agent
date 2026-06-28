package cn.noname.coder.agent.infrastructure.adapter.repository;

import cn.noname.coder.agent.domain.workspace.adapter.repository.IAgentCheckpointRepository;
import cn.noname.coder.agent.domain.workspace.model.entity.AgentCheckpoint;
import cn.noname.coder.agent.infrastructure.dao.IAgentCheckpointDao;
import cn.noname.coder.agent.infrastructure.dao.po.AgentCheckpointPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AgentCheckpointRepository implements IAgentCheckpointRepository {

    private final IAgentCheckpointDao dao;

    @Override
    public void save(AgentCheckpoint checkpoint) {
        AgentCheckpointPO po = toPo(checkpoint);
        dao.insert(po);
        checkpoint.setId(po.getId());
    }

    @Override
    public void update(AgentCheckpoint checkpoint) {
        dao.updateById(toPo(checkpoint));
    }

    @Override
    public Optional<AgentCheckpoint> findByCheckpointId(String checkpointId) {
        return Optional.ofNullable(dao.selectOne(new LambdaQueryWrapper<AgentCheckpointPO>()
                        .eq(AgentCheckpointPO::getCheckpointId, checkpointId)))
                .map(this::toEntity);
    }

    @Override
    public Optional<AgentCheckpoint> findByMessageId(String messageId) {
        return Optional.ofNullable(dao.selectOne(new LambdaQueryWrapper<AgentCheckpointPO>()
                        .eq(AgentCheckpointPO::getMessageId, messageId)))
                .map(this::toEntity);
    }

    @Override
    public Optional<AgentCheckpoint> latestRolledBack(String conversationId) {
        return Optional.ofNullable(dao.selectOne(new LambdaQueryWrapper<AgentCheckpointPO>()
                        .eq(AgentCheckpointPO::getConversationId, conversationId)
                        .eq(AgentCheckpointPO::getRollbackStatus, "ROLLED_BACK")
                        .orderByDesc(AgentCheckpointPO::getRollbackAt)
                        .last("LIMIT 1")))
                .map(this::toEntity);
    }

    private AgentCheckpointPO toPo(AgentCheckpoint entity) {
        AgentCheckpointPO po = new AgentCheckpointPO();
        po.setId(entity.getId());
        po.setCheckpointId(entity.getCheckpointId());
        po.setConversationId(entity.getConversationId());
        po.setWorkspaceKey(entity.getWorkspaceKey());
        po.setMessageId(entity.getMessageId());
        po.setRunId(entity.getRunId());
        po.setMessageSeq(entity.getMessageSeq());
        po.setRollbackStatus(entity.getRollbackStatus());
        po.setCreatedAt(entity.getCreatedAt());
        po.setRollbackAt(entity.getRollbackAt());
        return po;
    }

    private AgentCheckpoint toEntity(AgentCheckpointPO po) {
        return AgentCheckpoint.builder()
                .id(po.getId())
                .checkpointId(po.getCheckpointId())
                .conversationId(po.getConversationId())
                .workspaceKey(po.getWorkspaceKey())
                .messageId(po.getMessageId())
                .runId(po.getRunId())
                .messageSeq(po.getMessageSeq())
                .rollbackStatus(po.getRollbackStatus())
                .createdAt(po.getCreatedAt())
                .rollbackAt(po.getRollbackAt())
                .build();
    }
}
