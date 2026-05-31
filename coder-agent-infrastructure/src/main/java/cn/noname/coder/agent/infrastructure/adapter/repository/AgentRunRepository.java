package cn.noname.coder.agent.infrastructure.adapter.repository;

import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRunRepository;
import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.infrastructure.dao.IAgentRunDao;
import cn.noname.coder.agent.infrastructure.dao.po.AgentRunPO;
import cn.noname.coder.agent.types.enums.AgentRunStatus;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

/**
 * AgentRun MySQL 仓储实现，位于 adapter/repository，符合 DDD 六边形规范。
 */
@Repository
@RequiredArgsConstructor
public class AgentRunRepository implements IAgentRunRepository {

    private final IAgentRunDao agentRunDao;

    @Override
    public void save(AgentRun run) {
        AgentRunPO po = toPo(run);
        agentRunDao.insert(po);
        run.setId(po.getId());
    }

    @Override
    public void update(AgentRun run) {
        agentRunDao.updateById(toPo(run));
    }

    @Override
    public Optional<AgentRun> findByRunId(String runId) {
        AgentRunPO po = agentRunDao.selectOne(new LambdaQueryWrapper<AgentRunPO>().eq(AgentRunPO::getRunId, runId));
        return Optional.ofNullable(po).map(this::toEntity);
    }

    @Override
    public long countByStatuses(Collection<AgentRunStatus> statuses) {
        return agentRunDao.selectCount(new LambdaQueryWrapper<AgentRunPO>()
                .in(AgentRunPO::getStatus, statuses.stream().map(Enum::name).toList()));
    }

    private AgentRunPO toPo(AgentRun run) {
        AgentRunPO po = new AgentRunPO();
        po.setId(run.getId());
        po.setRunId(run.getRunId());
        po.setWorkspaceKey(run.getWorkspaceKey());
        po.setTask(run.getTask());
        po.setModel(run.getModel());
        po.setStatus(run.getStatus() == null ? null : run.getStatus().name());
        po.setFinalAnswer(run.getFinalAnswer());
        po.setFailureReason(run.getFailureReason());
        po.setMaxSteps(run.getMaxSteps());
        po.setMaxModelCalls(run.getMaxModelCalls());
        po.setMaxToolCalls(run.getMaxToolCalls());
        po.setTimeoutSeconds(run.getTimeoutSeconds());
        po.setStepCount(run.getStepCount());
        po.setModelCallCount(run.getModelCallCount());
        po.setToolCallCount(run.getToolCallCount());
        po.setCreatedAt(run.getCreatedAt());
        po.setStartedAt(run.getStartedAt());
        po.setEndedAt(run.getEndedAt());
        po.setDurationMs(run.getDurationMs());
        return po;
    }

    private AgentRun toEntity(AgentRunPO po) {
        return AgentRun.builder()
                .id(po.getId())
                .runId(po.getRunId())
                .workspaceKey(po.getWorkspaceKey())
                .task(po.getTask())
                .model(po.getModel())
                .status(AgentRunStatus.valueOf(po.getStatus()))
                .finalAnswer(po.getFinalAnswer())
                .failureReason(po.getFailureReason())
                .maxSteps(po.getMaxSteps())
                .maxModelCalls(po.getMaxModelCalls())
                .maxToolCalls(po.getMaxToolCalls())
                .timeoutSeconds(po.getTimeoutSeconds())
                .stepCount(po.getStepCount())
                .modelCallCount(po.getModelCallCount())
                .toolCallCount(po.getToolCallCount())
                .createdAt(po.getCreatedAt())
                .startedAt(po.getStartedAt())
                .endedAt(po.getEndedAt())
                .durationMs(po.getDurationMs())
                .build();
    }
}
