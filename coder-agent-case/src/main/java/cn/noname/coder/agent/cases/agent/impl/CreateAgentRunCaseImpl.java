package cn.noname.coder.agent.cases.agent.impl;

import cn.noname.coder.agent.api.dto.CreateAgentRunRequestDTO;
import cn.noname.coder.agent.api.dto.CreateAgentRunResponseDTO;
import cn.noname.coder.agent.cases.agent.ICreateAgentRunCase;
import cn.noname.coder.agent.domain.agent.adapter.port.IArtifactPort;
import cn.noname.coder.agent.domain.agent.adapter.port.IModelConfigPort;
import cn.noname.coder.agent.domain.agent.adapter.port.IWorkspacePort;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentConversationRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRecordRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRunRepository;
import cn.noname.coder.agent.domain.agent.model.entity.AgentConversation;
import cn.noname.coder.agent.domain.agent.model.entity.AgentMessage;
import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.agent.model.entity.AuditEvent;
import cn.noname.coder.agent.domain.agent.model.entity.PermissionAudit;
import cn.noname.coder.agent.domain.agent.model.valobj.AgentPermissionLevel;
import cn.noname.coder.agent.domain.agent.model.valobj.ModelBackendConfig;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import cn.noname.coder.agent.types.enums.AgentRunStatus;
import cn.noname.coder.agent.types.enums.AuditEventType;
import cn.noname.coder.agent.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 创建运行用例：校验 workspace、并发预算、初始化数据库和工件目录。
 */
@Slf4j
@Service
public class CreateAgentRunCaseImpl implements ICreateAgentRunCase {

    private final IWorkspacePort workspacePort;
    private final IAgentRunRepository runRepository;
    private final IAgentRecordRepository recordRepository;
    private final IAgentConversationRepository conversationRepository;
    private final IArtifactPort artifactPort;
    private final IModelConfigPort modelConfigPort;
    private final AgentRuntimeProperties properties;
    private final AgentRunExecutor agentRunExecutor;
    private final TaskExecutor taskExecutor;

    public CreateAgentRunCaseImpl(IWorkspacePort workspacePort,
                                  IAgentRunRepository runRepository,
                                  IAgentRecordRepository recordRepository,
                                  IAgentConversationRepository conversationRepository,
                                  IArtifactPort artifactPort,
                                  IModelConfigPort modelConfigPort,
                                  AgentRuntimeProperties properties,
                                  AgentRunExecutor agentRunExecutor,
                                  @Qualifier("agentRunTaskExecutor") TaskExecutor taskExecutor) {
        this.workspacePort = workspacePort;
        this.runRepository = runRepository;
        this.recordRepository = recordRepository;
        this.conversationRepository = conversationRepository;
        this.artifactPort = artifactPort;
        this.modelConfigPort = modelConfigPort;
        this.properties = properties;
        this.agentRunExecutor = agentRunExecutor;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public CreateAgentRunResponseDTO create(CreateAgentRunRequestDTO request) {
        validateRequest(request);
        WorkspaceDescriptor workspace = workspacePort.resolve(request.workspaceKey())
                .orElseThrow(() -> {
                    recordRepository.saveAuditEvent(AuditEvent.builder()
                            .runId("N/A")
                            .eventType(AuditEventType.WORKSPACE_REJECTED)
                            .message("未知 workspaceKey")
                            .detail(request.workspaceKey())
                            .createdAt(LocalDateTime.now())
                            .build());
                    return new AppException("WORKSPACE_NOT_FOUND", "workspaceKey 未配置：" + request.workspaceKey());
                });

        long running = runRepository.countByStatuses(List.of(AgentRunStatus.CREATED, AgentRunStatus.RUNNING));
        if (running >= properties.getBudget().getMaxConcurrentRuns()) {
            throw new AppException("CONCURRENT_LIMIT", "当前运行数已达到上限：" + properties.getBudget().getMaxConcurrentRuns());
        }

        AgentConversation conversation = resolveConversation(request);
        ModelBackendConfig modelConfig = resolveModel(request, conversation);
        AgentPermissionLevel permissionLevel = resolvePermissionLevel(request, conversation);
        AgentRun run = buildRun(request, modelConfig.modelKey(), conversation, permissionLevel);
        log.info("创建 Agent 运行 runId={} workspaceKey={} conversationId={} model={} actualModel={} permissionLevel={}",
                run.getRunId(), run.getWorkspaceKey(), run.getConversationId(), modelConfig.modelKey(), modelConfig.actualModel(), permissionLevel);
        log.info("Agent 权限等级 runId={} permissionLevel={}", run.getRunId(), permissionLevel);
        runRepository.save(run);
        saveUserMessageAndAudit(run, conversation, permissionLevel);
        artifactPort.initializeRun(workspace, run).forEach(recordRepository::saveArtifact);
        taskExecutor.execute(() -> agentRunExecutor.execute(run.getRunId()));
        log.info("Agent 运行已提交后台执行 runId={}", run.getRunId());
        return new CreateAgentRunResponseDTO(run.getRunId(), run.getStatus().name(), run.getCreatedAt());
    }

    private void validateRequest(CreateAgentRunRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.workspaceKey()) || !StringUtils.hasText(request.task())) {
            throw new AppException("INVALID_ARGUMENT", "workspaceKey 和 task 不能为空");
        }
    }

    private AgentConversation resolveConversation(CreateAgentRunRequestDTO request) {
        if (!StringUtils.hasText(request.conversationId())) {
            return null;
        }
        AgentConversation conversation = conversationRepository.findConversation(request.conversationId())
                .orElseThrow(() -> new AppException("CONVERSATION_NOT_FOUND", "会话不存在：" + request.conversationId()));
        if (!conversation.getWorkspaceKey().equals(request.workspaceKey())) {
            throw new AppException("CONVERSATION_WORKSPACE_MISMATCH", "会话所属 workspace 与请求不一致");
        }
        return conversation;
    }

    private ModelBackendConfig resolveModel(CreateAgentRunRequestDTO request, AgentConversation conversation) {
        String model = StringUtils.hasText(request.model())
                ? request.model()
                : conversation == null ? request.model() : conversation.getDefaultModel();
        return modelConfigPort.resolve(model)
                .orElseThrow(() -> new AppException("MODEL_NOT_CONFIGURED", "模型未配置：" + model));
    }

    private AgentPermissionLevel resolvePermissionLevel(CreateAgentRunRequestDTO request, AgentConversation conversation) {
        try {
            if (StringUtils.hasText(request.permissionLevel())) {
                return AgentPermissionLevel.parse(request.permissionLevel());
            }
            if (conversation != null && conversation.getDefaultPermissionLevel() != null) {
                return conversation.getDefaultPermissionLevel();
            }
            return AgentPermissionLevel.L1_READ_ONLY;
        } catch (Exception e) {
            throw new AppException("INVALID_PERMISSION_LEVEL", "未知权限等级：" + request.permissionLevel());
        }
    }

    private AgentRun buildRun(CreateAgentRunRequestDTO request,
                              String modelKey,
                              AgentConversation conversation,
                              AgentPermissionLevel permissionLevel) {
        AgentRuntimeProperties.Budget budget = properties.getBudget();
        return AgentRun.builder()
                .runId("run_" + UUID.randomUUID().toString().replace("-", ""))
                .workspaceKey(request.workspaceKey())
                .conversationId(conversation == null ? null : conversation.getConversationId())
                .task(request.task())
                .model(modelKey)
                .permissionLevel(permissionLevel)
                .status(AgentRunStatus.CREATED)
                .maxSteps(budget.getMaxSteps())
                .maxModelCalls(budget.getMaxModelCalls())
                .maxToolCalls(budget.getMaxToolCalls())
                .timeoutSeconds(budget.getTimeoutSeconds())
                .stepCount(0)
                .modelCallCount(0)
                .toolCallCount(0)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private void saveUserMessageAndAudit(AgentRun run, AgentConversation conversation, AgentPermissionLevel permissionLevel) {
        if (conversation != null) {
            conversationRepository.saveMessage(AgentMessage.builder()
                    .messageId("msg_" + UUID.randomUUID().toString().replace("-", ""))
                    .conversationId(conversation.getConversationId())
                    .runId(run.getRunId())
                    .role("USER")
                    .content(run.getTask())
                    .createdAt(LocalDateTime.now())
                    .build());
            conversation.setUpdatedAt(LocalDateTime.now());
            conversationRepository.updateConversation(conversation);
        }
        if (permissionLevel == AgentPermissionLevel.L3_REPO_WRITE) {
            conversationRepository.savePermissionAudit(PermissionAudit.builder()
                    .runId(run.getRunId())
                    .conversationId(run.getConversationId())
                    .workspaceKey(run.getWorkspaceKey())
                    .permissionLevel(permissionLevel)
                    .action("PERMISSION_LEVEL_SELECTED")
                    .detail("用户选择仓库写入权限")
                    .createdAt(LocalDateTime.now())
                    .build());
            recordRepository.saveAuditEvent(AuditEvent.builder()
                    .runId(run.getRunId())
                    .eventType(AuditEventType.PERMISSION_LEVEL_SELECTED)
                    .message("用户选择仓库写入权限")
                    .detail(permissionLevel.name())
                    .createdAt(LocalDateTime.now())
                    .build());
        }
    }

}
