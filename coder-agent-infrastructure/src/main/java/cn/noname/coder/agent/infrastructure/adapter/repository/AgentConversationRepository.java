package cn.noname.coder.agent.infrastructure.adapter.repository;

import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentConversationRepository;
import cn.noname.coder.agent.domain.agent.model.entity.AgentConversation;
import cn.noname.coder.agent.domain.agent.model.entity.AgentMessage;
import cn.noname.coder.agent.domain.agent.model.entity.PermissionAudit;
import cn.noname.coder.agent.domain.agent.model.valobj.AgentPermissionLevel;
import cn.noname.coder.agent.infrastructure.dao.IAgentConversationDao;
import cn.noname.coder.agent.infrastructure.dao.IAgentMessageDao;
import cn.noname.coder.agent.infrastructure.dao.IPermissionAuditDao;
import cn.noname.coder.agent.infrastructure.dao.po.AgentConversationPO;
import cn.noname.coder.agent.infrastructure.dao.po.AgentMessagePO;
import cn.noname.coder.agent.infrastructure.dao.po.PermissionAuditPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 会话、消息和权限审计 MySQL 仓储。
 */
@Repository
@RequiredArgsConstructor
public class AgentConversationRepository implements IAgentConversationRepository {

    private final IAgentConversationDao conversationDao;
    private final IAgentMessageDao messageDao;
    private final IPermissionAuditDao permissionAuditDao;

    @Override
    public void saveConversation(AgentConversation conversation) {
        AgentConversationPO po = toPo(conversation);
        conversationDao.insert(po);
        conversation.setId(po.getId());
    }

    @Override
    public void updateConversation(AgentConversation conversation) {
        conversationDao.updateById(toPo(conversation));
    }

    @Override
    public Optional<AgentConversation> findConversation(String conversationId) {
        AgentConversationPO po = conversationDao.selectOne(new LambdaQueryWrapper<AgentConversationPO>()
                .eq(AgentConversationPO::getConversationId, conversationId));
        return Optional.ofNullable(po).map(this::toEntity);
    }

    @Override
    public List<AgentConversation> listConversations(String workspaceKey) {
        LambdaQueryWrapper<AgentConversationPO> wrapper = new LambdaQueryWrapper<AgentConversationPO>()
                .orderByDesc(AgentConversationPO::getUpdatedAt);
        if (StringUtils.hasText(workspaceKey)) {
            wrapper.eq(AgentConversationPO::getWorkspaceKey, workspaceKey);
        }
        return conversationDao.selectList(wrapper).stream().map(this::toEntity).toList();
    }

    @Override
    public void deleteConversation(String conversationId) {
        conversationDao.delete(new LambdaQueryWrapper<AgentConversationPO>()
                .eq(AgentConversationPO::getConversationId, conversationId));
    }

    @Override
    public void saveMessage(AgentMessage message) {
        AgentMessagePO po = new AgentMessagePO();
        po.setMessageId(message.getMessageId());
        po.setConversationId(message.getConversationId());
        po.setRunId(message.getRunId());
        po.setRole(message.getRole());
        po.setContent(message.getContent());
        po.setCreatedAt(message.getCreatedAt());
        messageDao.insert(po);
        message.setId(po.getId());
    }

    @Override
    public Optional<AgentMessage> findMessage(String messageId) {
        AgentMessagePO po = messageDao.selectOne(new LambdaQueryWrapper<AgentMessagePO>()
                .eq(AgentMessagePO::getMessageId, messageId));
        return Optional.ofNullable(po).map(this::toMessageEntity);
    }

    @Override
    public void updateMessage(AgentMessage message) {
        AgentMessagePO po = new AgentMessagePO();
        po.setId(message.getId());
        po.setMessageId(message.getMessageId());
        po.setConversationId(message.getConversationId());
        po.setRunId(message.getRunId());
        po.setRole(message.getRole());
        po.setContent(message.getContent());
        po.setCreatedAt(message.getCreatedAt());
        messageDao.updateById(po);
    }

    @Override
    public void deleteMessage(String messageId) {
        messageDao.delete(new LambdaQueryWrapper<AgentMessagePO>()
                .eq(AgentMessagePO::getMessageId, messageId));
    }

    @Override
    public void deleteAgentMessagesByRunId(String runId) {
        if (!StringUtils.hasText(runId)) {
            return;
        }
        messageDao.delete(new LambdaQueryWrapper<AgentMessagePO>()
                .eq(AgentMessagePO::getRunId, runId)
                .eq(AgentMessagePO::getRole, "AGENT"));
    }

    @Override
    public void deleteMessagesByRunId(String runId) {
        if (!StringUtils.hasText(runId)) {
            return;
        }
        messageDao.delete(new LambdaQueryWrapper<AgentMessagePO>()
                .eq(AgentMessagePO::getRunId, runId));
    }

    @Override
    public void deleteMessagesByConversationId(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return;
        }
        messageDao.delete(new LambdaQueryWrapper<AgentMessagePO>()
                .eq(AgentMessagePO::getConversationId, conversationId));
    }

    @Override
    public List<AgentMessage> listMessages(String conversationId) {
        return messageDao.selectList(new LambdaQueryWrapper<AgentMessagePO>()
                        .eq(AgentMessagePO::getConversationId, conversationId)
                        .orderByAsc(AgentMessagePO::getCreatedAt))
                .stream()
                .map(this::toMessageEntity)
                .toList();
    }

    @Override
    public void savePermissionAudit(PermissionAudit audit) {
        PermissionAuditPO po = new PermissionAuditPO();
        po.setRunId(audit.getRunId());
        po.setConversationId(audit.getConversationId());
        po.setWorkspaceKey(audit.getWorkspaceKey());
        po.setPermissionLevel(audit.getPermissionLevel() == null ? null : audit.getPermissionLevel().name());
        po.setAction(audit.getAction());
        po.setDetail(audit.getDetail());
        po.setCreatedAt(audit.getCreatedAt());
        permissionAuditDao.insert(po);
        audit.setId(po.getId());
    }

    private AgentConversationPO toPo(AgentConversation conversation) {
        AgentConversationPO po = new AgentConversationPO();
        po.setId(conversation.getId());
        po.setConversationId(conversation.getConversationId());
        po.setWorkspaceKey(conversation.getWorkspaceKey());
        po.setTitle(conversation.getTitle());
        po.setDefaultModel(conversation.getDefaultModel());
        po.setLastPermissionLevel(conversation.getLastPermissionLevel() == null ? null : conversation.getLastPermissionLevel().name());
        po.setCreatedAt(conversation.getCreatedAt());
        po.setUpdatedAt(conversation.getUpdatedAt());
        return po;
    }

    private AgentConversation toEntity(AgentConversationPO po) {
        return AgentConversation.builder()
                .id(po.getId())
                .conversationId(po.getConversationId())
                .workspaceKey(po.getWorkspaceKey())
                .title(po.getTitle())
                .defaultModel(po.getDefaultModel())
                .lastPermissionLevel(AgentPermissionLevel.parse(po.getLastPermissionLevel()))
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }

    private AgentMessage toMessageEntity(AgentMessagePO po) {
        return AgentMessage.builder()
                .id(po.getId())
                .messageId(po.getMessageId())
                .conversationId(po.getConversationId())
                .runId(po.getRunId())
                .role(po.getRole())
                .content(po.getContent())
                .createdAt(po.getCreatedAt())
                .build();
    }
}
