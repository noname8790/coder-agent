package cn.noname.coder.agent.domain.agent.adapter.repository;

import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.types.enums.AgentRunStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * AgentRun 仓储接口由 Domain 定义，Infrastructure 负责 MySQL 实现。
 */
public interface IAgentRunRepository {

    void save(AgentRun run);

    void update(AgentRun run);

    Optional<AgentRun> findByRunId(String runId);

    default List<AgentRun> listByConversationId(String conversationId) {
        return List.of();
    }

    default List<AgentRun> listByWorkspaceKey(String workspaceKey) {
        return List.of();
    }

    long countByStatuses(Collection<AgentRunStatus> statuses);

    default long countByModelAndStatuses(String modelKey, Collection<AgentRunStatus> statuses) {
        return 0;
    }

    default void deleteByRunIds(Collection<String> runIds) {
    }
}
