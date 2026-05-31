package cn.noname.coder.agent.domain.agent.service;

import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.types.enums.AgentRunStatus;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 运行状态机领域服务，集中处理状态流转和耗时计算。
 */
public class AgentRunDomainService {

    public void start(AgentRun run) {
        run.setStatus(AgentRunStatus.RUNNING);
        run.setStartedAt(LocalDateTime.now());
    }

    public void succeed(AgentRun run, String finalAnswer) {
        run.setStatus(AgentRunStatus.SUCCEEDED);
        run.setFinalAnswer(finalAnswer);
        finish(run);
    }

    public void fail(AgentRun run, String reason) {
        run.setStatus(AgentRunStatus.FAILED);
        run.setFailureReason(reason);
        finish(run);
    }

    public void cancel(AgentRun run) {
        run.setStatus(AgentRunStatus.CANCELLED);
        finish(run);
    }

    public void reject(AgentRun run, String reason) {
        run.setStatus(AgentRunStatus.REJECTED);
        run.setFailureReason(reason);
        finish(run);
    }

    private void finish(AgentRun run) {
        LocalDateTime endedAt = LocalDateTime.now();
        run.setEndedAt(endedAt);
        LocalDateTime startedAt = run.getStartedAt() == null ? run.getCreatedAt() : run.getStartedAt();
        if (startedAt != null) {
            run.setDurationMs(Duration.between(startedAt, endedAt).toMillis());
        }
    }
}
