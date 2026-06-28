package cn.noname.coder.agent.domain.workspace.adapter.repository;

import cn.noname.coder.agent.domain.workspace.model.entity.AgentConversation;
import cn.noname.coder.agent.domain.workspace.model.entity.AgentMessage;
import cn.noname.coder.agent.domain.tool.model.entity.PermissionAudit;

import java.util.List;
import java.util.Optional;

/**
 * 会话、消息和权限审计仓储端口。
 */
public interface IAgentConversationRepository {

    void saveConversation(AgentConversation conversation);

    void updateConversation(AgentConversation conversation);

    Optional<AgentConversation> findConversation(String conversationId);

    List<AgentConversation> listConversations(String workspaceKey);

    void deleteConversation(String conversationId);

    void saveMessage(AgentMessage message);

    Optional<AgentMessage> findMessage(String messageId);

    default long nextMessageSequence(String conversationId) {
        return System.currentTimeMillis();
    }

    void updateMessage(AgentMessage message);

    default void markMessagesRolledBackAfter(String conversationId, long messageSeq, String checkpointId) {
    }

    void deleteMessage(String messageId);

    void deleteAgentMessagesByRunId(String runId);

    void deleteMessagesByRunId(String runId);

    default void deleteMessagesByConversationId(String conversationId) {
    }

    List<AgentMessage> listMessages(String conversationId);

    void savePermissionAudit(PermissionAudit audit);
}
