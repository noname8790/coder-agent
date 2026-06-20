package cn.noname.coder.agent.cases.conversation.impl;

import cn.noname.coder.agent.api.dto.ConversationMessageDTO;
import cn.noname.coder.agent.api.dto.ConversationResponseDTO;
import cn.noname.coder.agent.api.dto.CreateConversationRequestDTO;
import cn.noname.coder.agent.api.dto.UpdateConversationMessageRequestDTO;
import cn.noname.coder.agent.cases.agent.DiffSummaryAssembler;
import cn.noname.coder.agent.cases.conversation.IConversationCase;
import cn.noname.coder.agent.cases.memory.MemoryService;
import cn.noname.coder.agent.domain.agent.adapter.port.IArtifactPort;
import cn.noname.coder.agent.domain.agent.adapter.port.IModelConfigPort;
import cn.noname.coder.agent.domain.agent.adapter.port.IWorkspacePort;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentConversationRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRecordRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRunRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IContextSnapshotRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IModelProviderRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IRunChangeRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IToolApprovalRepository;
import cn.noname.coder.agent.domain.agent.model.entity.AgentConversation;
import cn.noname.coder.agent.domain.agent.model.entity.AgentMessage;
import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.agent.model.entity.ModelProvider;
import cn.noname.coder.agent.domain.agent.model.valobj.AgentPermissionLevel;
import cn.noname.coder.agent.types.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 会话创建、查询和消息历史用例。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationCaseImpl implements IConversationCase {

    private final IAgentConversationRepository conversationRepository;
    private final IAgentRunRepository runRepository;
    private final IContextSnapshotRepository contextSnapshotRepository;
    private final MemoryService memoryService;
    private final IWorkspacePort workspacePort;
    private final IModelConfigPort modelConfigPort;
    private final IAgentRecordRepository recordRepository;
    private final IToolApprovalRepository toolApprovalRepository;
    private final IRunChangeRepository runChangeRepository;
    private final IModelProviderRepository modelProviderRepository;
    private final IArtifactPort artifactPort;
    private final DiffSummaryAssembler diffSummaryAssembler;

    @Override
    public ConversationResponseDTO create(CreateConversationRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.workspaceKey())) {
            throw new AppException("INVALID_ARGUMENT", "workspaceKey 不能为空");
        }
        workspacePort.resolve(request.workspaceKey())
                .orElseThrow(() -> new AppException("WORKSPACE_NOT_FOUND", "workspaceKey 未配置：" + request.workspaceKey()));
        String model = StringUtils.hasText(request.defaultModel()) ? request.defaultModel() : firstEnabledModelKey();
        if (StringUtils.hasText(model) && modelConfigPort.resolve(model).isEmpty()) {
            throw new AppException("MODEL_NOT_CONFIGURED", "模型未配置：" + model);
        }
        AgentPermissionLevel permissionLevel = parsePermissionLevel(request.lastPermissionLevel());
        LocalDateTime now = LocalDateTime.now();
        AgentConversation conversation = AgentConversation.builder()
                .conversationId("conv_" + UUID.randomUUID().toString().replace("-", ""))
                .workspaceKey(request.workspaceKey())
                .title(StringUtils.hasText(request.title()) ? request.title() : "新对话")
                .defaultModel(model)
                .lastModelKey(model)
                .lastPermissionLevel(permissionLevel)
                .createdAt(now)
                .updatedAt(now)
                .build();
        conversationRepository.saveConversation(conversation);
        log.info("创建 Agent 会话 conversationId={} workspaceKey={} lastPermissionLevel={}",
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
        List<String> runIds = runRepository.listByConversationId(conversationId).stream()
                .map(AgentRun::getRunId)
                .filter(StringUtils::hasText)
                .toList();
        clearRunContext(conversation.getWorkspaceKey(), runIds);
        conversationRepository.deleteMessagesByConversationId(conversationId);
        conversationRepository.deleteConversation(conversationId);
        log.info("删除 Agent 会话 conversationId={} workspaceKey={} cleanedRuns={}",
                conversation.getConversationId(), conversation.getWorkspaceKey(), runIds.size());
        return toDto(conversation);
    }

    @Override
    public ConversationMessageDTO updateMessage(String conversationId, String messageId, UpdateConversationMessageRequestDTO request) {
        AgentConversation conversation = findConversation(conversationId);
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
        clearRunContext(conversation.getWorkspaceKey(), runIdsOf(message));
        log.info("更新用户消息 conversationId={} messageId={} runId={}，已清理旧 Agent 回复和上下文",
                conversationId, messageId, message.getRunId());
        return toDto(message);
    }

    @Override
    public ConversationMessageDTO deleteMessage(String conversationId, String messageId) {
        AgentConversation conversation = findConversation(conversationId);
        AgentMessage message = findMessage(conversationId, messageId);
        if ("USER".equals(message.getRole())) {
            if (StringUtils.hasText(message.getRunId())) {
                conversationRepository.deleteMessagesByRunId(message.getRunId());
            } else {
                conversationRepository.deleteMessage(messageId);
            }
            clearRunContext(conversation.getWorkspaceKey(), runIdsOf(message));
            log.info("删除用户消息 conversationId={} messageId={} runId={}，连带删除同轮 Agent 回复和上下文",
                    conversationId, messageId, message.getRunId());
        } else {
            conversationRepository.deleteMessage(messageId);
            clearRunContext(conversation.getWorkspaceKey(), runIdsOf(message));
            log.info("删除 Agent 消息 conversationId={} messageId={} runId={}，已清理该回复上下文",
                    conversationId, messageId, message.getRunId());
        }
        return toDto(message);
    }

    private void clearRunContext(String workspaceKey, List<String> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return;
        }
        contextSnapshotRepository.deleteByRunIds(runIds);
        runChangeRepository.deleteByRunIds(runIds);
        memoryService.deleteRunMemories(workspaceKey, runIds);
        toolApprovalRepository.deleteByRunIds(runIds);
        recordRepository.deleteByRunIds(runIds);
        runRepository.deleteByRunIds(runIds);
    }

    private List<String> runIdsOf(AgentMessage message) {
        if (message == null || !StringUtils.hasText(message.getRunId())) {
            return List.of();
        }
        Set<String> runIds = new LinkedHashSet<>();
        runIds.add(message.getRunId());
        return List.copyOf(runIds);
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
                conversation.getLastModelKey(),
                conversation.getLastPermissionLevel() == null ? AgentPermissionLevel.DEFAULT.name() : conversation.getLastPermissionLevel().name(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt()
        );
    }

    private ConversationMessageDTO toDto(AgentMessage message) {
        AgentRun run = StringUtils.hasText(message.getRunId())
                ? runRepository.findByRunId(message.getRunId()).orElse(null)
                : null;
        var workspace = run == null ? null : workspacePort.resolve(run.getWorkspaceKey()).orElse(null);
        return new ConversationMessageDTO(
                message.getMessageId(),
                message.getConversationId(),
                message.getRunId(),
                message.getRole(),
                message.getContent(),
                run == null || run.getStatus() == null ? null : run.getStatus().name(),
                run == null ? null : run.getFailureReason(),
                message.getCreatedAt(),
                diffSummaryAssembler.load(artifactPort, workspace, message.getRunId()),
                message.getSequenceNo(),
                message.getVisibilityStatus(),
                message.getRolledBackByCheckpointId(),
                modelDisplayName(run)
        );
    }

    private String firstEnabledModelKey() {
        return modelProviderRepository.listEnabled().stream()
                .findFirst()
                .map(ModelProvider::getModelKey)
                .orElse(null);
    }

    private String modelDisplayName(AgentRun run) {
        if (run == null || !StringUtils.hasText(run.getModel())) {
            return null;
        }
        return modelProviderRepository.findByModelKey(run.getModel())
                .map(ModelProvider::getDisplayName)
                .filter(StringUtils::hasText)
                .orElse(run.getModel());
    }
}
