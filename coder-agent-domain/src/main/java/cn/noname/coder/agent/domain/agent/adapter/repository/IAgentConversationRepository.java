package cn.noname.coder.agent.domain.agent.adapter.repository;

import cn.noname.coder.agent.domain.agent.model.entity.AgentConversation;
import cn.noname.coder.agent.domain.agent.model.entity.AgentMessage;
import cn.noname.coder.agent.domain.agent.model.entity.PermissionAudit;

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

    void saveMessage(AgentMessage message);

    List<AgentMessage> listMessages(String conversationId);

    void savePermissionAudit(PermissionAudit audit);
}
