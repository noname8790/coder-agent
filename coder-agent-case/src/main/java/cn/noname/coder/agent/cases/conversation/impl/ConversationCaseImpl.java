package cn.noname.coder.agent.cases.conversation.impl;

import cn.noname.coder.agent.api.dto.ConversationMessageDTO;
import cn.noname.coder.agent.api.dto.ConversationResponseDTO;
import cn.noname.coder.agent.api.dto.CreateConversationRequestDTO;
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

    private AgentConversation findConversation(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            throw new AppException("INVALID_ARGUMENT", "conversationId 不能为空");
        }
        return conversationRepository.findConversation(conversationId)
                .orElseThrow(() -> new AppException("CONVERSATION_NOT_FOUND", "会话不存在：" + conversationId));
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
