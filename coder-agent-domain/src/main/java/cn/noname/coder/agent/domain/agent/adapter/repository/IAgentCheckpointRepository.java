package cn.noname.coder.agent.domain.agent.adapter.repository;

import cn.noname.coder.agent.domain.agent.model.entity.AgentCheckpoint;

import java.util.Optional;

/**
 * 会话检查点仓储端口。
 */
public interface IAgentCheckpointRepository {

    void save(AgentCheckpoint checkpoint);

    void update(AgentCheckpoint checkpoint);

    Optional<AgentCheckpoint> findByCheckpointId(String checkpointId);

    Optional<AgentCheckpoint> findByMessageId(String messageId);

    Optional<AgentCheckpoint> latestRolledBack(String conversationId);
}
