package cn.noname.coder.agent.cases.conversation.impl;

import cn.noname.coder.agent.api.dto.ConversationMessageDTO;
import cn.noname.coder.agent.api.dto.ConversationResponseDTO;
import cn.noname.coder.agent.api.dto.CreateConversationRequestDTO;
import cn.noname.coder.agent.api.dto.UpdateConversationMessageRequestDTO;
import cn.noname.coder.agent.cases.conversation.IConversationCase;
import cn.noname.coder.agent.domain.agent.adapter.port.IModelConfigPort;
import cn.noname.coder.agent.domain.agent.adapter.port.IWorkspacePort;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentConversationRepository;
import cn.noname.coder.agent.domain.agent.model.entity.AgentConversation;
import cn.noname.coder.agent.domain.agent.model.entity.AgentMessage;
import cn.noname.coder.agent.domain.agent.model.valobj.AgentPermissionLevel;
import cn.noname.coder.agent.types.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 会话创建、查询和消息历史用例。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationCaseImpl implements IConversationCase {

    private final IAgentConversationRepository conversationRepository;
    private final IWorkspacePort workspacePort;
    private final IModelConfigPort modelConfigPort;

    @Override
    public ConversationResponseDTO create(CreateConversationRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.workspaceKey())) {
            throw new AppException("INVALID_ARGUMENT", "workspaceKey 不能为空");
        }
        workspacePort.resolve(request.workspaceKey())
                .orElseThrow(() -> new AppException("WORKSPACE_NOT_FOUND", "workspaceKey 未配置：" + request.workspaceKey()));
        String model = StringUtils.hasText(request.defaultModel()) ? request.defaultModel() : null;
        if (StringUtils.hasText(model) && modelConfigPort.resolve(model).isEmpty()) {
            throw new AppException("MODEL_NOT_CONFIGURED", "模型未配置：" + model);
        }
        AgentPermissionLevel permissionLevel = parsePermissionLevel(request.defaultPermissionLevel());
        LocalDateTime now = LocalDateTime.now();
        AgentConversation conversation = AgentConversation.builder()
                .conversationId("conv_" + UUID.randomUUID().toString().replace("-", ""))
                .workspaceKey(request.workspaceKey())
                .title(StringUtils.hasText(request.title()) ? request.title() : "新对话")
                .defaultModel(model)
                .defaultPermissionLevel(permissionLevel)
                .createdAt(now)
                .updatedAt(now)
                .build();
        conversationRepository.saveConversation(conversation);
        log.info("创建 Agent 会话 conversationId={} workspaceKey={} defaultPermissionLevel={}",
                conversation.getConversationId(), conversation.getWorkspaceKey(), permissionLevel);
        return toDto(conversation);
    }

    @Override
    public List<ConversationResponseDTO> list(String workspaceKey) {
        return conversationRepository.listConversations(workspaceKey).stream().map(this::toDto).toList();
    }

    @Override
    public ConversationResponseDTO query(String conversationId) {
        return toDto(findConversation(conversationId));
    }

    @Override
    public List<ConversationMessageDTO> messages(String conversationId) {
        findConversation(conversationId);
        return conversationRepository.listMessages(conversationId).stream().map(this::toDto).toList();
    }

    @Override
    public ConversationResponseDTO delete(String conversationId) {
        AgentConversation conversation = findConversation(conversationId);
        conversationRepository.deleteConversation(conversationId);
        log.info("删除 Agent 会话 conversationId={} workspaceKey={}，保留关联运行和消息记录",
                conversation.getConversationId(), conversation.getWorkspaceKey());
        return toDto(conversation);
    }

    @Override
    public ConversationMessageDTO updateMessage(String conversationId, String messageId, UpdateConversationMessageRequestDTO request) {
        findConversation(conversationId);
        if (request == null || !StringUtils.hasText(request.content())) {
            throw new AppException("INVALID_ARGUMENT", "消息内容不能为空");
        }
        AgentMessage message = findMessage(conversationId, messageId);
        if (!"USER".equals(message.getRole())) {
            throw new AppException("MESSAGE_NOT_EDITABLE", "Agent 消息不能修改");
        }
        message.setContent(request.content());
        conversationRepository.updateMessage(message);
        conversationRepository.deleteAgentMessagesByRunId(message.getRunId());
        log.info("更新用户消息 conversationId={} messageId={} runId={}，已清理旧 Agent 回复",
                conversationId, messageId, message.getRunId());
        return toDto(message);
    }

    @Override
    public ConversationMessageDTO deleteMessage(String conversationId, String messageId) {
        findConversation(conversationId);
        AgentMessage message = findMessage(conversationId, messageId);
        if ("USER".equals(message.getRole())) {
            if (StringUtils.hasText(message.getRunId())) {
                conversationRepository.deleteMessagesByRunId(message.getRunId());
            } else {
                conversationRepository.deleteMessage(messageId);
            }
            log.info("删除用户消息 conversationId={} messageId={} runId={}，连带删除同轮 Agent 回复",
                    conversationId, messageId, message.getRunId());
        } else {
            conversationRepository.deleteMessage(messageId);
            log.info("删除 Agent 消息 conversationId={} messageId={} runId={}",
                    conversationId, messageId, message.getRunId());
        }
        return toDto(message);
    }

    private AgentConversation findConversation(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            throw new AppException("INVALID_ARGUMENT", "conversationId 不能为空");
        }
        return conversationRepository.findConversation(conversationId)
                .orElseThrow(() -> new AppException("CONVERSATION_NOT_FOUND", "会话不存在：" + conversationId));
    }

    private AgentMessage findMessage(String conversationId, String messageId) {
        if (!StringUtils.hasText(messageId)) {
            throw new AppException("INVALID_ARGUMENT", "messageId 不能为空");
        }
        AgentMessage message = conversationRepository.findMessage(messageId)
                .orElseThrow(() -> new AppException("MESSAGE_NOT_FOUND", "消息不存在：" + messageId));
        if (!conversationId.equals(message.getConversationId())) {
            throw new AppException("MESSAGE_CONVERSATION_MISMATCH", "消息所属会话与请求不一致");
        }
        return message;
    }

    private AgentPermissionLevel parsePermissionLevel(String value) {
        try {
            return AgentPermissionLevel.parse(value);
        } catch (Exception e) {
            throw new AppException("INVALID_PERMISSION_LEVEL", "未知权限等级：" + value);
        }
    }

    private ConversationResponseDTO toDto(AgentConversation conversation) {
        return new ConversationResponseDTO(
                conversation.getConversationId(),
                conversation.getWorkspaceKey(),
                conversation.getTitle(),
                conversation.getDefaultModel(),
                conversation.getDefaultPermissionLevel() == null ? AgentPermissionLevel.L1_READ_ONLY.name() : conversation.getDefaultPermissionLevel().name(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt()
        );
    }

    private ConversationMessageDTO toDto(AgentMessage message) {
        return new ConversationMessageDTO(
                message.getMessageId(),
                message.getConversationId(),
                message.getRunId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
