package cn.noname.coder.agent.cases.agent.impl;

import cn.noname.coder.agent.api.dto.CreateAgentRunRequestDTO;
import cn.noname.coder.agent.api.dto.CreateAgentRunResponseDTO;
import cn.noname.coder.agent.cases.agent.ICreateAgentRunCase;
import cn.noname.coder.agent.domain.agent.adapter.port.IArtifactPort;
import cn.noname.coder.agent.domain.agent.adapter.port.IModelConfigPort;
import cn.noname.coder.agent.domain.agent.adapter.port.IWorkspacePort;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRecordRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRunRepository;
import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.agent.model.entity.AuditEvent;
import cn.noname.coder.agent.domain.agent.model.valobj.ModelBackendConfig;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.domain.agent.model.valobj.AgentRunMode;
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
    private final IArtifactPort artifactPort;
    private final IModelConfigPort modelConfigPort;
    private final AgentRuntimeProperties properties;
    private final AgentRunExecutor agentRunExecutor;
    private final TaskExecutor taskExecutor;

    public CreateAgentRunCaseImpl(IWorkspacePort workspacePort,
                                  IAgentRunRepository runRepository,
                                  IAgentRecordRepository recordRepository,
                                  IArtifactPort artifactPort,
                                  IModelConfigPort modelConfigPort,
                                  AgentRuntimeProperties properties,
                                  AgentRunExecutor agentRunExecutor,
                                  @Qualifier("agentRunTaskExecutor") TaskExecutor taskExecutor) {
        this.workspacePort = workspacePort;
        this.runRepository = runRepository;
        this.recordRepository = recordRepository;
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

        ModelBackendConfig modelConfig = resolveModel(request);
        AgentRun run = buildRun(request, modelConfig.modelKey());
        log.info("创建 Agent 运行 runId={} workspaceKey={} model={} actualModel={}",
                run.getRunId(), run.getWorkspaceKey(), modelConfig.modelKey(), modelConfig.actualModel());
        log.info("Agent 运行模式 runId={} mode={} workspaceCapabilities={}",
                run.getRunId(), run.getMode(), workspace.capabilities());
        runRepository.save(run);
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

    private ModelBackendConfig resolveModel(CreateAgentRunRequestDTO request) {
        return modelConfigPort.resolve(request.model())
                .orElseThrow(() -> new AppException("MODEL_NOT_CONFIGURED", "模型未配置：" + request.model()));
    }

    private AgentRun buildRun(CreateAgentRunRequestDTO request, String modelKey) {
        AgentRuntimeProperties.Budget budget = properties.getBudget();
        AgentRunMode mode = parseMode(request.mode());
        return AgentRun.builder()
                .runId("run_" + UUID.randomUUID().toString().replace("-", ""))
                .workspaceKey(request.workspaceKey())
                .task(request.task())
                .model(modelKey)
                .mode(mode)
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

    private AgentRunMode parseMode(String value) {
        if (!StringUtils.hasText(value)) {
            return AgentRunMode.READ_ONLY;
        }
        try {
            return AgentRunMode.valueOf(value);
        } catch (Exception e) {
            throw new AppException("INVALID_RUN_MODE", "未知运行模式：" + value);
        }
    }
}
