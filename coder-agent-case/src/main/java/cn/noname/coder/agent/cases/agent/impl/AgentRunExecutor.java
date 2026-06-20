package cn.noname.coder.agent.cases.agent.impl;

import cn.noname.coder.agent.cases.agent.AgentContextAssembler;
import cn.noname.coder.agent.cases.context.ContextBudgetResolver;
import cn.noname.coder.agent.cases.agent.RunChangeService;
import cn.noname.coder.agent.cases.memory.MemoryService;
import cn.noname.coder.agent.domain.agent.adapter.port.IArtifactPort;
import cn.noname.coder.agent.domain.agent.adapter.port.IAgentRunEventPublisher;
import cn.noname.coder.agent.domain.agent.adapter.port.IContextEngine;
import cn.noname.coder.agent.domain.agent.adapter.port.IModelConfigPort;
import cn.noname.coder.agent.domain.agent.adapter.port.IStreamingModelGateway;
import cn.noname.coder.agent.domain.agent.adapter.port.IToolGateway;
import cn.noname.coder.agent.domain.agent.adapter.port.IWorkspacePort;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentConversationRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRecordRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRunRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IContextSnapshotRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IToolApprovalRepository;
import cn.noname.coder.agent.domain.agent.model.entity.AgentMessage;
import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.agent.model.entity.AgentStep;
import cn.noname.coder.agent.domain.agent.model.entity.AuditEvent;
import cn.noname.coder.agent.domain.agent.model.entity.ContextSnapshot;
import cn.noname.coder.agent.domain.agent.model.entity.ModelCall;
import cn.noname.coder.agent.domain.agent.model.entity.RunArtifact;
import cn.noname.coder.agent.domain.agent.model.entity.ToolApprovalRequest;
import cn.noname.coder.agent.domain.agent.model.entity.ToolCall;
import cn.noname.coder.agent.domain.agent.model.valobj.AgentRunEvent;
import cn.noname.coder.agent.domain.agent.model.valobj.AgentRunEventType;
import cn.noname.coder.agent.domain.agent.model.valobj.AgentPermissionLevel;
import cn.noname.coder.agent.domain.agent.model.valobj.ChangedFile;
import cn.noname.coder.agent.domain.agent.model.valobj.ContextAssemblyResult;
import cn.noname.coder.agent.domain.agent.model.valobj.ContextBudget;
import cn.noname.coder.agent.domain.agent.model.valobj.ContextCandidate;
import cn.noname.coder.agent.domain.agent.model.valobj.ContextCutReason;
import cn.noname.coder.agent.domain.agent.model.valobj.ContextLayer;
import cn.noname.coder.agent.domain.agent.model.valobj.ModelBackendConfig;
import cn.noname.coder.agent.domain.agent.model.valobj.ModelProtocolMessage;
import cn.noname.coder.agent.domain.agent.model.valobj.ModelRequest;
import cn.noname.coder.agent.domain.agent.model.valobj.ModelResponse;
import cn.noname.coder.agent.domain.agent.model.valobj.ModelStreamEvent;
import cn.noname.coder.agent.domain.agent.model.valobj.ModelStreamEventType;
import cn.noname.coder.agent.domain.agent.model.valobj.TestCommandReport;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolInvocation;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolResult;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.domain.agent.service.AgentRunDomainService;
import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import cn.noname.coder.agent.types.enums.AgentRunStatus;
import cn.noname.coder.agent.types.enums.ArtifactType;
import cn.noname.coder.agent.types.enums.AuditEventType;
import cn.noname.coder.agent.types.enums.CallStatus;
import cn.noname.coder.agent.types.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent 后台执行主循环。
 * v4 中统一走流式模型调用，并将工具规划轮次视为运行过程，不直接持久化为最终 Agent 消息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRunExecutor {

    private final IAgentRunRepository runRepository;
    private final IAgentRecordRepository recordRepository;
    private final IContextSnapshotRepository contextSnapshotRepository;
    private final IAgentConversationRepository conversationRepository;
    private final IToolApprovalRepository toolApprovalRepository;
    private final IWorkspacePort workspacePort;
    private final IModelConfigPort modelConfigPort;
    private final IStreamingModelGateway streamingModelGateway;
    private final IToolGateway toolGateway;
    private final IArtifactPort artifactPort;
    private final IAgentRunEventPublisher eventPublisher;
    private final IContextEngine contextEngine;
    private final ContextBudgetResolver contextBudgetResolver;
    private final MemoryService memoryService;
    private final AgentRunDraftService draftService;
    private final RunChangeService runChangeService;
    private final AgentRuntimeProperties properties;

    private final AgentRunDomainService domainService = new AgentRunDomainService();
    private final AgentContextAssembler contextAssembler = new AgentContextAssembler();

    public void execute(String runId) {
        AgentRun run = runRepository.findByRunId(runId).orElse(null);
        if (run == null) {
            return;
        }
        if (run.getStatus().isTerminal()) {
            if (run.getStatus() == AgentRunStatus.CANCELLED) {
                saveAgentMessage(run);
            }
            return;
        }

        WorkspaceDescriptor workspace = workspacePort.resolve(run.getWorkspaceKey()).orElse(null);
        if (workspace == null) {
            failWithoutWorkspace(run, "workspaceKey 未配置：" + run.getWorkspaceKey());
            return;
        }

        RunEditState editState = new RunEditState();
        List<ModelProtocolMessage> protocolMessages = new ArrayList<>();
        List<ContextCandidate> dynamicCandidates = new ArrayList<>();

        try {
            log.info("Agent 开始运行 runId={} workspaceKey={} model={} workspaceRoot={}",
                    run.getRunId(), run.getWorkspaceKey(), run.getModel(), workspace.rootPath());
            domainService.start(run);
            runRepository.update(run);
            trace(workspace, run.getRunId(), AgentRunEventType.RUN_STARTED.code(),
                    Map.of("status", AgentRunStatus.RUNNING.name()));
            recordStep(run, "run_started", "Agent 开始运行");

            while (!run.getStatus().isTerminal()) {
                if (!checkBudget(run, workspace, editState)) {
                    return;
                }
                if (stopIfCancelled(run, workspace, editState, "before_model_call")) {
                    return;
                }
                if (!executeApprovedToolCalls(run, workspace, dynamicCandidates, protocolMessages, editState)) {
                    return;
                }

                run.setStepCount(run.getStepCount() + 1);
                run.setModelCallCount(run.getModelCallCount() + 1);

                List<ContextCandidate> candidates = new ArrayList<>(contextAssembler.initialCandidates(run, workspace));
                candidates.add(contextAssembler.budgetCandidate(run));
                ContextCandidate recent = recentMessagesCandidate(run);
                if (recent != null) {
                    candidates.add(recent);
                }
                candidates.addAll(rejectedApprovalCandidates(run));
                candidates.addAll(approvedApprovalCandidates(run));
                candidates.addAll(memoryService.recallForRun(run, inactiveContextRunIds(run)));
                candidates.addAll(dynamicCandidates);

                ContextAssemblyResult context = assembleContext(run, candidates);
                writeSnapshot(workspace, run, context, editState);
                recordStep(run, "model_call", "第 " + run.getModelCallCount() + " 次模型调用");

                ModelResponse response = callModel(run, context.messages(), protocolMessages, workspace);
                if (stopIfCancelled(run, workspace, editState, "after_model_call")) {
                    return;
                }

                if (response.hasToolCalls()) {
                    // 工具规划轮次只作为运行草稿，不进入最终 assistant 持久消息。
                    draftService.clear(run.getRunId());
                    if (!executeTools(run, workspace, dynamicCandidates, protocolMessages, response.toolInvocations(), editState)) {
                        return;
                    }
                    runRepository.update(run);
                    continue;
                }

                String finalAnswer = AgentRunDraftService.sanitizeCompletedText(response.finalAnswer());
                if (!StringUtils.hasText(finalAnswer)) {
                    finalAnswer = draftService.content(run.getRunId());
                }
                domainService.succeed(run, finalAnswer);
                runRepository.update(run);
                saveAgentMessage(run);
                writeFinal(workspace, run, editState);
                trace(workspace, run.getRunId(), AgentRunEventType.RUN_FINISHED.code(), Map.of(
                        "status", run.getStatus().name(),
                        "finalAnswer", abbreviate(finalAnswer, 500)));
                log.info("Agent 运行完成 runId={} status={} modelCalls={} toolCalls={} durationMs={}",
                        run.getRunId(), run.getStatus(), run.getModelCallCount(), run.getToolCallCount(), run.getDurationMs());
                return;
            }
        } catch (AppException e) {
            if ("RUN_CANCELLED".equals(e.getCode())) {
                stopIfCancelled(run, workspace, editState, "during_model_stream");
                return;
            }
            failRun(run, workspace, editState, e);
        } catch (Throwable e) {
            failRun(run, workspace, editState, e);
        }
    }

    private boolean checkBudget(AgentRun run, WorkspaceDescriptor workspace, RunEditState editState) {
        String reason = null;
        LocalDateTime now = LocalDateTime.now();
        if (run.getStartedAt() != null && now.isAfter(run.getStartedAt().plusSeconds(run.getTimeoutSeconds()))) {
            reason = "运行超时：" + run.getTimeoutSeconds() + " 秒";
        } else if (run.getStepCount() >= run.getMaxSteps()) {
            reason = "达到最大步骤数：" + run.getMaxSteps();
        } else if (run.getModelCallCount() >= run.getMaxModelCalls()) {
            reason = "达到最大模型调用次数：" + run.getMaxModelCalls();
        } else if (run.getToolCallCount() >= run.getMaxToolCalls()) {
            reason = "达到最大工具调用次数：" + run.getMaxToolCalls();
        }
        if (reason == null) {
            return true;
        }
        failWithReason(run, workspace, editState, reason, AuditEventType.BUDGET_EXHAUSTED);
        return false;
    }

    private boolean stopIfCancelled(AgentRun run, WorkspaceDescriptor workspace, RunEditState editState, String checkpoint) {
        AgentRun current = runRepository.findByRunId(run.getRunId()).orElse(run);
        if (current.getStatus() != AgentRunStatus.CANCELLED) {
            return false;
        }
        if (run.getStatus() != AgentRunStatus.CANCELLED) {
            domainService.cancel(run);
        }
        run.setStatus(AgentRunStatus.CANCELLED);
        run.setFailureReason(current.getFailureReason());
        run.setEndedAt(current.getEndedAt());
        run.setDurationMs(current.getDurationMs());
        runRepository.update(run);
        saveAgentMessage(run);
        writeFinal(workspace, run, editState);
        trace(workspace, run.getRunId(), AgentRunEventType.RUN_FINISHED.code(), Map.of(
                "status", "CANCELLED",
                "reason", current.getFailureReason() == null ? "用户取消" : current.getFailureReason(),
                "checkpoint", checkpoint));
        log.info("Agent 运行已取消 runId={} checkpoint={}", run.getRunId(), checkpoint);
        return true;
    }

    private void failRun(AgentRun run, WorkspaceDescriptor workspace, RunEditState editState, Throwable e) {
        String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        log.error("Agent 运行异常 runId={}", run.getRunId(), e);
        failWithReason(run, workspace, editState, abbreviate(reason, 1000), AuditEventType.MODEL_CALL_FAILED);
    }

    private ModelResponse callModel(AgentRun run,
                                    List<String> messages,
                                    List<ModelProtocolMessage> protocolMessages,
                                    WorkspaceDescriptor workspace) {
        long start = System.currentTimeMillis();
        ModelBackendConfig auditModel = auditModel(run.getModel());
        List<ModelStreamEvent> events = new ArrayList<>();
        StringBuilder visibleAnswer = new StringBuilder();
        try {
            log.info("调用模型 runId={} callNo={} provider={} model={}",
                    run.getRunId(), run.getModelCallCount(), auditModel.provider(), auditModel.auditName());
            trace(workspace, run.getRunId(), AgentRunEventType.MODEL_CALL_STARTED.code(), Map.of(
                    "callNo", run.getModelCallCount(),
                    "model", auditModel.auditName()));

            streamingModelGateway.stream(
                    new ModelRequest(run.getRunId(),
                            run.getModel(),
                            messages,
                            toolGateway.definitions(run, workspace),
                            modelProtocolMessages(messages, protocolMessages)),
                    event -> {
                        events.add(event);
                        publishModelStreamEvent(run, workspace, event, visibleAnswer);
                        if (isCancelled(run.getRunId())) {
                            throw new AppException("RUN_CANCELLED", "用户取消");
                        }
                    });

            ModelResponse response = toModelResponse(events);
            long latencyMs = System.currentTimeMillis() - start;
            recordRepository.saveModelCall(ModelCall.builder()
                    .runId(run.getRunId())
                    .callNo(run.getModelCallCount())
                    .provider(auditModel.provider())
                    .model(auditModel.auditName())
                    .requestSummary(abbreviate(String.join("\n", messages), 1000))
                    .responseSummary(abbreviate(response.rawSummary(), 1000))
                    .status(CallStatus.SUCCESS)
                    .latencyMs(latencyMs)
                    .createdAt(LocalDateTime.now())
                    .build());
            trace(workspace, run.getRunId(), AgentRunEventType.MODEL_CALL_COMPLETED.code(), Map.of(
                    "callNo", run.getModelCallCount(),
                    "status", "SUCCESS"));
            log.info("模型调用完成 runId={} callNo={} latencyMs={} toolCallCount={} hasFinalAnswer={}",
                    run.getRunId(), run.getModelCallCount(), latencyMs, response.toolInvocations().size(), !response.hasToolCalls());
            return response;
        } catch (AppException e) {
            if ("RUN_CANCELLED".equals(e.getCode())) {
                trace(workspace, run.getRunId(), AgentRunEventType.ASSISTANT_MESSAGE_CANCELLED.code(),
                        Map.of("reason", "用户取消"));
                throw e;
            }
            failModelCall(run, workspace, messages, auditModel, start, e);
            throw e;
        } catch (Exception e) {
            failModelCall(run, workspace, messages, auditModel, start, e);
            throw e;
        }
    }

    private void failModelCall(AgentRun run,
                                        WorkspaceDescriptor workspace,
                                        List<String> messages,
                                        ModelBackendConfig auditModel,
                                        long start,
                                        Exception e) {
        long latencyMs = System.currentTimeMillis() - start;
        recordRepository.saveModelCall(ModelCall.builder()
                .runId(run.getRunId())
                .callNo(run.getModelCallCount())
                .provider(auditModel.provider())
                .model(auditModel.auditName())
                .requestSummary(abbreviate(String.join("\n", messages), 1000))
                .responseSummary("")
                .status(CallStatus.FAILED)
                .latencyMs(latencyMs)
                .errorMessage(abbreviate(e.getMessage(), 1000))
                .createdAt(LocalDateTime.now())
                .build());
        trace(workspace, run.getRunId(), AgentRunEventType.MODEL_STREAM_FAILURE.code(), Map.of(
                "callNo", run.getModelCallCount(),
                "reason", abbreviate(e.getMessage(), 300)));
        trace(workspace, run.getRunId(), AgentRunEventType.MODEL_CALL_COMPLETED.code(), Map.of(
                "callNo", run.getModelCallCount(),
                "status", "FAILED",
                "reason", abbreviate(e.getMessage(), 300)));
        log.warn("模型调用失败 runId={} callNo={} model={} latencyMs={} reason={}",
                run.getRunId(), run.getModelCallCount(), auditModel.auditName(), latencyMs, abbreviate(e.getMessage(), 300));
    }

    private void publishModelStreamEvent(AgentRun run,
                                         WorkspaceDescriptor workspace,
                                         ModelStreamEvent event,
                                         StringBuilder visibleAnswer) {
        switch (event.type()) {
            case ASSISTANT_MESSAGE_STARTED -> trace(workspace, run.getRunId(),
                    AgentRunEventType.ASSISTANT_MESSAGE_STARTED.code(),
                    Map.of("callNo", run.getModelCallCount()));
            case ASSISTANT_DELTA -> {
                String delta = event.contentDelta() == null ? "" : event.contentDelta();
                if (!delta.isEmpty()) {
                    draftService.appendVisibleDelta(run.getRunId(), delta);
                    visibleAnswer.append(AgentRunDraftService.sanitizeCompletedText(delta));
                    trace(workspace, run.getRunId(), AgentRunEventType.ASSISTANT_DELTA.code(),
                            Map.of("delta", abbreviate(delta, 300)));
                }
            }
            case MODEL_COMPLETED -> {
                if (!draftService.content(run.getRunId()).isBlank()) {
                    trace(workspace, run.getRunId(), AgentRunEventType.ASSISTANT_MESSAGE_COMPLETED.code(),
                            Map.of("length", draftService.content(run.getRunId()).length()));
                }
            }
            case MODEL_FAILED -> trace(workspace, run.getRunId(), AgentRunEventType.MODEL_STREAM_FAILURE.code(),
                    Map.of("reason", abbreviate(event.errorMessage(), 300)));
            default -> {
                // no-op
            }
        }
    }

    private boolean isCancelled(String runId) {
        return runRepository.findByRunId(runId)
                .map(AgentRun::getStatus)
                .filter(AgentRunStatus.CANCELLED::equals)
                .isPresent();
    }

    private ModelResponse toModelResponse(List<ModelStreamEvent> events) {
        StringBuilder answer = new StringBuilder();
        Map<String, ToolAccumulator> toolAccumulators = new LinkedHashMap<>();
        for (ModelStreamEvent event : events) {
            if (event.type() == ModelStreamEventType.ASSISTANT_DELTA && event.contentDelta() != null) {
                answer.append(event.contentDelta());
            } else if (event.type() == ModelStreamEventType.TOOL_CALL_STARTED) {
                String key = firstText(event.toolCallId(), event.toolName(), "tool_" + toolAccumulators.size());
                toolAccumulators.computeIfAbsent(key, ignored -> new ToolAccumulator(event.toolCallId(), event.toolName()));
            } else if (event.type() == ModelStreamEventType.TOOL_CALL_ARGUMENTS_DELTA) {
                String key = firstText(event.toolCallId(), event.toolName(), "tool_" + toolAccumulators.size());
                ToolAccumulator accumulator = toolAccumulators.computeIfAbsent(key,
                        ignored -> new ToolAccumulator(event.toolCallId(), event.toolName()));
                accumulator.append(event.argumentsDelta());
            }
        }
        List<ToolInvocation> invocations = toolAccumulators.values().stream()
                .filter(accumulator -> StringUtils.hasText(accumulator.name))
                .map(accumulator -> new ToolInvocation(accumulator.id, accumulator.name, accumulator.arguments.toString()))
                .toList();
        String rawSummary = events.stream()
                .map(event -> event.type().name()
                        + (event.contentDelta() == null ? "" : ":" + event.contentDelta())
                        + (event.toolName() == null ? "" : ":" + event.toolName()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        return new ModelResponse("", AgentRunDraftService.sanitizeCompletedText(answer.toString()), invocations,
                abbreviate(rawSummary, 2000));
    }

    private boolean executeTools(AgentRun run,
                                 WorkspaceDescriptor workspace,
                                 List<ContextCandidate> dynamicCandidates,
                                 List<ModelProtocolMessage> protocolMessages,
                                 List<ToolInvocation> invocations,
                                 RunEditState editState) {
        if (invocations != null && !invocations.isEmpty()) {
            protocolMessages.add(ModelProtocolMessage.assistantToolCalls(invocations, ""));
        }
        for (ToolInvocation invocation : invocations) {
            if (stopIfCancelled(run, workspace, editState, "before_tool_call")) {
                return false;
            }
            if (run.getToolCallCount() >= run.getMaxToolCalls()) {
                failWithReason(run, workspace, editState,
                        "达到最大工具调用次数：" + run.getMaxToolCalls(), AuditEventType.BUDGET_EXHAUSTED);
                return false;
            }
            if (pauseForApprovalIfNeeded(run, workspace, invocation)) {
                return false;
            }
            if (!allowToolInvocation(run, workspace, invocation, editState)) {
                return false;
            }

            long start = System.currentTimeMillis();
            run.setToolCallCount(run.getToolCallCount() + 1);
            log.info("执行工具 runId={} callNo={} tool={}", run.getRunId(), run.getToolCallCount(), invocation.name());
            trace(workspace, run.getRunId(), AgentRunEventType.TOOL_CALL_STARTED.code(), Map.of(
                    "callNo", run.getToolCallCount(),
                    "tool", invocation.name()));

            ToolResult result = toolGateway.execute(run, workspace, invocation);
            if (!recordToolResult(run, workspace, invocation, result, start, dynamicCandidates, protocolMessages, editState)) {
                return false;
            }
        }
        return true;
    }

    private boolean executeApprovedToolCalls(AgentRun run,
                                             WorkspaceDescriptor workspace,
                                             List<ContextCandidate> dynamicCandidates,
                                             List<ModelProtocolMessage> protocolMessages,
                                             RunEditState editState) {
        List<ToolApprovalRequest> approvals = toolApprovalRepository.listApprovedPendingExecution(run.getRunId());
        if (approvals.isEmpty()) {
            return true;
        }
        for (ToolApprovalRequest approval : approvals) {
            if (stopIfCancelled(run, workspace, editState, "before_approved_tool_call")) {
                return false;
            }
            if (run.getToolCallCount() >= run.getMaxToolCalls()) {
                failWithReason(run, workspace, editState,
                        "达到最大工具调用次数：" + run.getMaxToolCalls(), AuditEventType.BUDGET_EXHAUSTED);
                return false;
            }

            ToolInvocation invocation = new ToolInvocation(
                    "approval_" + approval.getApprovalId(),
                    approval.getToolName(),
                    approval.getArgumentsJson());
            protocolMessages.add(ModelProtocolMessage.assistantToolCalls(List.of(invocation), ""));

            if (!allowToolInvocation(run, workspace, invocation, editState)) {
                return false;
            }

            long start = System.currentTimeMillis();
            run.setToolCallCount(run.getToolCallCount() + 1);
            log.info("执行已批准工具 runId={} approvalId={} callNo={} tool={}",
                    run.getRunId(), approval.getApprovalId(), run.getToolCallCount(), invocation.name());
            trace(workspace, run.getRunId(), AgentRunEventType.TOOL_CALL_STARTED.code(), Map.of(
                    "callNo", run.getToolCallCount(),
                    "tool", invocation.name(),
                    "approvalId", approval.getApprovalId()));

            ToolResult result = toolGateway.execute(run, workspace, invocation);
            if (!recordToolResult(run, workspace, invocation, result, start, dynamicCandidates, protocolMessages, editState)) {
                return false;
            }
            toolApprovalRepository.markExecuted(approval.getApprovalId());
        }
        return true;
    }

    private boolean recordToolResult(AgentRun run,
                                     WorkspaceDescriptor workspace,
                                     ToolInvocation invocation,
                                     ToolResult result,
                                     long start,
                                     List<ContextCandidate> dynamicCandidates,
                                     List<ModelProtocolMessage> protocolMessages,
                                     RunEditState editState) {
        long latencyMs = System.currentTimeMillis() - start;
        RunArtifact outputArtifact = null;
        if (result.fullOutput() != null && result.fullOutput().length() > properties.getTools().getMaxToolInlineChars()) {
            outputArtifact = artifactPort.writeToolOutput(workspace, run.getRunId(), run.getToolCallCount(), result.fullOutput());
            recordRepository.saveArtifact(outputArtifact);
        }
        String summary = outputArtifact == null ? result.summary() : result.summary() + "\n完整输出：" + outputArtifact.getRelativePath();
        recordRepository.saveToolCall(ToolCall.builder()
                .runId(run.getRunId())
                .callNo(run.getToolCallCount())
                .toolName(invocation.name())
                .argumentsSummary(abbreviate(invocation.argumentsJson(), 1000))
                .resultSummary(abbreviate(summary, 1000))
                .exitCode(result.exitCode())
                .status(result.status())
                .latencyMs(latencyMs)
                .errorMessage(result.errorMessage())
                .createdAt(LocalDateTime.now())
                .build());
        log.info("工具执行完成 runId={} callNo={} tool={} status={} exitCode={} latencyMs={}",
                run.getRunId(), run.getToolCallCount(), invocation.name(), result.status(), result.exitCode(), latencyMs);

        if (result.status() == CallStatus.REJECTED) {
            recordAudit(run.getRunId(), auditType(result.errorMessage()), "工具调用被拒绝", summary);
        }

        collectEditState(run.getToolCallCount(), result, editState);
        collectGitState(run, invocation, result);
        memoryService.rememberToolResult(run, invocation, result);
        publishResultEvents(workspace, run, invocation, result);

        String observationKey = toolObservationKey(invocation);
        String observation = compressToolOutputForContext(run, toolObservation(invocation, result, summary));
        protocolMessages.add(ModelProtocolMessage.toolResult(invocation, observation));
        if (editState.seenToolObservationKeys.add(observationKey)) {
            dynamicCandidates.add(contextAssembler.toolResultCandidate(run, invocation.name(), observation));
        } else {
            log.info("跳过重复工具观察 runId={} tool={} arguments={}",
                    run.getRunId(), invocation.name(), redact(invocation.argumentsJson()));
        }

        trace(workspace, run.getRunId(), AgentRunEventType.TOOL_CALL_COMPLETED.code(), Map.of(
                "callNo", run.getToolCallCount(),
                "tool", invocation.name(),
                "status", result.status().name(),
                "summary", abbreviate(summary, 500)));

        if (stopIfCancelled(run, workspace, editState, "after_tool_call")) {
            return false;
        }
        if (stopIfToolBlocked(run, workspace, invocation, result, editState)) {
            return false;
        }
        return true;
    }

    private boolean allowToolInvocation(AgentRun run,
                                        WorkspaceDescriptor workspace,
                                        ToolInvocation invocation,
                                        RunEditState editState) {
        String key = toolObservationKey(invocation);
        int count = editState.toolInvocationCounts.merge(key, 1, Integer::sum);
        if (count <= 2) {
            return true;
        }
        String reason = "重复工具调用无法继续推进：tool=" + invocation.name()
                + ", arguments=" + normalizeToolArguments(redact(invocation.argumentsJson()));
        failWithReason(run, workspace, editState, reason, AuditEventType.TOOL_REJECTED);
        return false;
    }

    private boolean stopIfToolBlocked(AgentRun run,
                                      WorkspaceDescriptor workspace,
                                      ToolInvocation invocation,
                                      ToolResult result,
                                      RunEditState editState) {
        if (isShellTimeout(invocation, result)) {
            String reason = "Shell command timeout: " + commandFromJson(invocation.argumentsJson());
            failWithReason(run, workspace, editState, reason, AuditEventType.TOOL_TIMEOUT);
            return true;
        }
        if (result.status() == CallStatus.REJECTED) {
            editState.consecutiveRejectedToolCalls++;
            if (editState.consecutiveRejectedToolCalls >= 2) {
                String reason = "Consecutive tool calls rejected: " + result.summary();
                failWithReason(run, workspace, editState, reason, auditType(result.errorMessage()));
                return true;
            }
        } else {
            editState.consecutiveRejectedToolCalls = 0;
        }
        return false;
    }

    private boolean isShellTimeout(ToolInvocation invocation, ToolResult result) {
        if (!"run_shell".equals(invocation.name()) || result == null) {
            return false;
        }
        return Integer.valueOf(124).equals(result.exitCode())
                || "TIMEOUT".equalsIgnoreCase(result.errorMessage())
                || (result.summary() != null && result.summary().contains("TIMEOUT"));
    }

    private void failWithReason(AgentRun run,
                                WorkspaceDescriptor workspace,
                                RunEditState editState,
                                String reason,
                                AuditEventType auditType) {
        log.warn("Agent 运行失败 runId={} reason={}", run.getRunId(), reason);
        domainService.fail(run, reason);
        runRepository.update(run);
        saveAgentMessage(run);
        writeFinal(workspace, run, editState);
        recordAudit(run.getRunId(), auditType, "Agent 运行失败", reason);
        trace(workspace, run.getRunId(), AgentRunEventType.RUN_FINISHED.code(), Map.of(
                "status", run.getStatus().name(),
                "reason", reason));
    }

    private List<ContextCandidate> rejectedApprovalCandidates(AgentRun run) {
        List<ToolApprovalRequest> rejectedApprovals = toolApprovalRepository.listRejectedPendingReturn(run.getRunId());
        if (rejectedApprovals.isEmpty()) {
            return List.of();
        }
        List<ContextCandidate> candidates = new ArrayList<>();
        for (ToolApprovalRequest approval : rejectedApprovals) {
            String content = """
                    TOOL_APPROVAL_REJECTED
                    tool=%s
                    arguments=%s
                    reason=%s
                    instruction=用户已经拒绝这个高风险动作。不要用相同参数再次请求同一个工具；请选择更安全的路径，或明确说明当前阻塞原因。
                    """.formatted(
                    approval.getToolName(),
                    approval.getArgumentsJson() == null ? "{}" : approval.getArgumentsJson(),
                    approval.getDecisionReason() == null ? "user rejected" : approval.getDecisionReason());
            candidates.add(new ContextCandidate(
                    "approval_rejected_" + approval.getApprovalId(),
                    ContextLayer.TOOL_RESULT,
                    "被拒绝的高风险工具",
                    content,
                    Math.max(20, content.length() / 4),
                    92,
                    "tool_approval",
                    approval.getApprovalId(),
                    true,
                    ContextCutReason.NONE));
            toolApprovalRepository.markReturned(approval.getApprovalId());
            recordAudit(run.getRunId(), AuditEventType.TOOL_REJECTED, "已向 Agent 返回审批拒绝结果", content);
        }
        return candidates;
    }

    private List<ContextCandidate> approvedApprovalCandidates(AgentRun run) {
        List<ToolApprovalRequest> approvedApprovals = toolApprovalRepository.listApproved(run.getRunId());
        if (approvedApprovals.isEmpty()) {
            return List.of();
        }
        List<ContextCandidate> candidates = new ArrayList<>();
        for (ToolApprovalRequest approval : approvedApprovals) {
            String content = """
                    TOOL_APPROVAL_APPROVED
                    tool=%s
                    arguments=%s
                    instruction=用户已经批准这个高风险动作。请继续执行这一个工具和参数，不要再次请求同一审批。
                    """.formatted(
                    approval.getToolName(),
                    approval.getArgumentsJson() == null ? "{}" : approval.getArgumentsJson());
            candidates.add(new ContextCandidate(
                    "approval_approved_" + approval.getApprovalId(),
                    ContextLayer.TOOL_RESULT,
                    "已批准的高风险工具",
                    content,
                    Math.max(20, content.length() / 4),
                    93,
                    "tool_approval",
                    approval.getApprovalId(),
                    true,
                    ContextCutReason.NONE));
        }
        return candidates;
    }

    private boolean pauseForApprovalIfNeeded(AgentRun run, WorkspaceDescriptor workspace, ToolInvocation invocation) {
        if (!properties.getToolApproval().isEnabled() || !isHighRiskTool(invocation)) {
            return false;
        }
        AgentPermissionLevel permissionLevel = run.getPermissionLevel() == null ? AgentPermissionLevel.DEFAULT : run.getPermissionLevel();
        if (permissionLevel.bypassesApproval()) {
            recordAudit(run.getRunId(), AuditEventType.HIGH_RISK_TOOL_USED,
                    "Full control allowed high-risk tool",
                    "permissionLevel=" + permissionLevel.name() + ", tool=" + invocation.name()
                            + ", arguments=" + abbreviate(redact(invocation.argumentsJson()), 500));
            return false;
        }
        if (!permissionLevel.requiresApprovalForHighRiskTool()) {
            return false;
        }
        String safeArguments = normalizeApprovalArguments(redact(invocation.argumentsJson()));
        if (toolApprovalRepository.findApproved(run.getRunId(), invocation.name(), safeArguments).isPresent()) {
            return false;
        }
        ToolApprovalRequest pending = toolApprovalRepository.findPending(run.getRunId(), invocation.name(), safeArguments).orElse(null);
        if (pending == null) {
            pending = ToolApprovalRequest.builder()
                    .approvalId("apv_" + UUID.randomUUID().toString().replace("-", ""))
                    .runId(run.getRunId())
                    .workspaceKey(run.getWorkspaceKey())
                    .toolName(invocation.name())
                    .argumentsJson(safeArguments)
                    .riskSummary(riskSummary(invocation))
                    .diffSummary(diffSummary(invocation))
                    .status("PENDING")
                    .requestedAt(LocalDateTime.now())
                    .build();
            toolApprovalRepository.save(pending);
            recordWaitingApprovalToolCall(run, invocation, pending);
            recordAudit(run.getRunId(), AuditEventType.HIGH_RISK_TOOL_USED, "等待高风险工具审批", pending.getRiskSummary());
        }
        run.setStatus(AgentRunStatus.WAITING_APPROVAL);
        runRepository.update(run);
        trace(workspace, run.getRunId(), AgentRunEventType.TOOL_CALL_COMPLETED.code(), Map.of(
                "tool", invocation.name(),
                "status", "WAITING_APPROVAL",
                "approvalId", pending.getApprovalId(),
                "riskSummary", pending.getRiskSummary()));
        log.info("等待工具审批 runId={} approvalId={} tool={}",
                run.getRunId(), pending.getApprovalId(), invocation.name());
        return true;
    }

    private void recordWaitingApprovalToolCall(AgentRun run, ToolInvocation invocation, ToolApprovalRequest approval) {
        recordRepository.saveToolCall(ToolCall.builder()
                .runId(run.getRunId())
                .callNo(run.getToolCallCount() + 1)
                .toolName(invocation.name())
                .argumentsSummary(abbreviate(invocation.argumentsJson(), 1000))
                .resultSummary(abbreviate("等待审批 approvalId=" + approval.getApprovalId() + "；" + approval.getRiskSummary(), 1000))
                .exitCode(null)
                .status(CallStatus.WAITING_APPROVAL)
                .latencyMs(0L)
                .errorMessage(null)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private boolean isHighRiskTool(ToolInvocation invocation) {
        if (invocation == null) {
            return false;
        }
        if ("overwrite_file".equals(invocation.name())
                || "delete_file".equals(invocation.name())
                || "generate_pr_draft".equals(invocation.name())
                || "git_add".equals(invocation.name())
                || "git_commit".equals(invocation.name())) {
            return true;
        }
        if (!"run_shell".equals(invocation.name())) {
            return false;
        }
        String command = commandFromJson(invocation.argumentsJson()).toLowerCase();
        return command.startsWith("git commit")
                || command.startsWith("git reset")
                || command.startsWith("git rm")
                || command.startsWith("git clean")
                || command.startsWith("git restore");
    }

    private String riskSummary(ToolInvocation invocation) {
        if ("overwrite_file".equals(invocation.name())) {
            return "覆盖文件会修改已有文件内容";
        }
        if ("delete_file".equals(invocation.name())) {
            return "删除文件会移除工作区内文件";
        }
        return "本地 git commit 会创建新的提交记录";
    }

    private String diffSummary(ToolInvocation invocation) {
        String path = jsonField(invocation.argumentsJson(), "path");
        if (StringUtils.hasText(path)) {
            return "目标路径：" + path;
        }
        String command = commandFromJson(invocation.argumentsJson());
        return StringUtils.hasText(command) ? "命令：" + command : "";
    }

    private String normalizeApprovalArguments(String argumentsJson) {
        if (argumentsJson == null) {
            return "";
        }
        return argumentsJson.replace("\\\\", "/").replace("\\", "/").trim();
    }

    private ContextCandidate recentMessagesCandidate(AgentRun run) {
        if (run.getConversationId() == null || run.getConversationId().isBlank()) {
            return null;
        }
        List<AgentMessage> messages = conversationRepository.listMessages(run.getConversationId()).stream()
                .filter(message -> !run.getRunId().equals(message.getRunId()))
                .filter(message -> !"ROLLED_BACK".equals(message.getVisibilityStatus()))
                .filter(message -> StringUtils.hasText(message.getContent()))
                .toList();
        if (messages.isEmpty()) {
            return null;
        }
        int maxChars = Math.max(1000, properties.getContext().getRecentMessageBudgetTokens() * 3);
        List<AgentMessage> selected = new ArrayList<>();
        int usedChars = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            AgentMessage message = messages.get(i);
            int nextChars = message.getContent().length();
            if (!selected.isEmpty() && usedChars + nextChars > maxChars) {
                break;
            }
            selected.add(0, message);
            usedChars += nextChars;
        }
        return contextAssembler.recentMessagesCandidate(run, selected);
    }

    private List<String> inactiveContextRunIds(AgentRun run) {
        if (run.getConversationId() == null || run.getConversationId().isBlank()) {
            return List.of();
        }
        Set<String> runIds = new HashSet<>(runChangeService.revertedRunIds(run.getConversationId()));
        conversationRepository.listMessages(run.getConversationId())
                .stream()
                .filter(message -> "ROLLED_BACK".equals(message.getVisibilityStatus()))
                .map(AgentMessage::getRunId)
                .filter(StringUtils::hasText)
                .forEach(runIds::add);
        runIds.remove(run.getRunId());
        return new ArrayList<>(runIds);
    }

    private ContextAssemblyResult assembleContext(AgentRun run, List<ContextCandidate> candidates) {
        ModelBackendConfig modelConfig = auditModel(run.getModel());
        ContextBudget budget = contextBudgetResolver.resolve(modelConfig);
        return contextEngine.assemble(candidates, budget);
    }

    private void writeSnapshot(WorkspaceDescriptor workspace,
                               AgentRun run,
                               ContextAssemblyResult context,
                               RunEditState editState) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("runId", run.getRunId());
        snapshot.put("callNo", run.getModelCallCount());
        snapshot.put("model", run.getModel());
        snapshot.put("budgetSource", contextBudgetResolver.budgetSource(auditModel(run.getModel())));
        snapshot.put("rawEstimatedTokens", context.rawEstimatedTokens());
        snapshot.put("finalEstimatedTokens", context.finalEstimatedTokens());
        snapshot.put("compressionRatio", context.compressionRatio());
        snapshot.put("memoryHitCount", context.memoryHitCount());
        snapshot.put("staleMemoryCount", context.staleMemoryCount());
        snapshot.put("selected", context.selected().stream().map(this::snapshotCandidate).toList());
        snapshot.put("rejected", context.rejected().stream().map(this::snapshotCandidate).toList());
        RunArtifact artifact = artifactPort.writeContextSnapshot(workspace, run.getRunId(), run.getModelCallCount(), snapshot);
        recordRepository.saveArtifact(artifact);

        editState.latestRawContextTokens = context.rawEstimatedTokens();
        editState.latestFinalContextTokens = context.finalEstimatedTokens();
        editState.latestCompressionRatio = context.compressionRatio();
        editState.latestMemoryHitCount = context.memoryHitCount();
        editState.latestStaleMemoryCount = context.staleMemoryCount();
        editState.latestSelectedFileSummaryCount = countSelected(context, ContextLayer.FILE_SUMMARY);
        editState.latestSelectedRawSnippetCount = countSelected(context, ContextLayer.RAW_SNIPPET);

        contextSnapshotRepository.save(ContextSnapshot.builder()
                .snapshotId("ctx_" + UUID.randomUUID().toString().replace("-", ""))
                .runId(run.getRunId())
                .workspaceKey(run.getWorkspaceKey())
                .modelCallNo(run.getModelCallCount())
                .modelKey(run.getModel())
                .budgetSource(contextBudgetResolver.budgetSource(auditModel(run.getModel())))
                .rawEstimatedTokens(context.rawEstimatedTokens())
                .finalEstimatedTokens(context.finalEstimatedTokens())
                .compressionRatio(context.compressionRatio())
                .memoryHitCount(context.memoryHitCount())
                .staleMemoryCount(context.staleMemoryCount())
                .selectedFileSummaryCount(countSelected(context, ContextLayer.FILE_SUMMARY))
                .selectedRawSnippetCount(countSelected(context, ContextLayer.RAW_SNIPPET))
                .snapshotPath(artifact.getRelativePath())
                .createdAt(LocalDateTime.now())
                .build());
    }

    private Map<String, Object> snapshotCandidate(ContextCandidate candidate) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("candidateId", candidate.candidateId());
        item.put("layer", candidate.layer().name());
        item.put("title", candidate.title());
        item.put("estimatedTokens", candidate.estimatedTokens());
        item.put("required", candidate.required());
        item.put("priority", candidate.priority());
        item.put("sourceType", candidate.sourceType());
        item.put("sourceId", candidate.sourceId());
        item.put("cutReason", candidate.cutReason().name());
        item.put("contentPreview", abbreviate(candidate.content(), 500));
        return item;
    }

    private int countSelected(ContextAssemblyResult context, ContextLayer layer) {
        return (int) context.selected().stream().filter(candidate -> candidate.layer() == layer).count();
    }

    private List<ModelProtocolMessage> modelProtocolMessages(List<String> messages, List<ModelProtocolMessage> protocolMessages) {
        List<ModelProtocolMessage> result = new ArrayList<>();
        result.add(ModelProtocolMessage.user(String.join("\n\n", messages)));
        if (protocolMessages != null && !protocolMessages.isEmpty()) {
            result.addAll(protocolMessages);
        }
        return result;
    }

    private String compressToolOutputForContext(AgentRun run, String summary) {
        if (summary == null) {
            return "";
        }
        int budgetTokens = contextBudgetResolver.resolve(auditModel(run.getModel())).toolResultBudgetTokens();
        int maxChars = Math.max(1000, budgetTokens * 4);
        if (summary.length() <= maxChars) {
            return summary;
        }
        return summary.substring(0, maxChars)
                + "\n[tool output truncated; full output is stored under tool-output artifact]";
    }

    private String toolObservation(ToolInvocation invocation, ToolResult result, String summary) {
        StringBuilder content = new StringBuilder();
        content.append("TOOL_OBSERVATION").append('\n')
                .append("tool=").append(invocation.name()).append('\n')
                .append("arguments=").append(redact(invocation.argumentsJson())).append('\n')
                .append("status=").append(result.status()).append('\n')
                .append("exitCode=").append(result.exitCode() == null ? "" : result.exitCode()).append('\n');
        if (StringUtils.hasText(result.errorMessage())) {
            content.append("error=").append(result.errorMessage()).append('\n');
        }
        String safeSummary = StringUtils.hasText(summary) ? summary : "tool returned no visible content";
        content.append("summary=").append(safeSummary).append('\n');
        if (result.status() == CallStatus.SUCCESS) {
            content.append("instruction=tool call completed. Do not call the same tool with the same arguments again; continue based on status and summary.");
        } else {
            content.append("instruction=tool call failed. Do not mechanically repeat the same arguments; try a different path/tool or explain the blocker.");
        }
        return content.toString();
    }

    private String toolObservationKey(ToolInvocation invocation) {
        return invocation.name() + ":" + normalizeToolArguments(redact(invocation.argumentsJson()));
    }

    private String normalizeToolArguments(String argumentsJson) {
        if (argumentsJson == null) {
            return "";
        }
        return argumentsJson.replace("\\\\", "/").replace("\\", "/").trim();
    }

    private void collectEditState(int toolCallNo, ToolResult result, RunEditState editState) {
        if (result.changedFiles() != null && !result.changedFiles().isEmpty()) {
            for (ChangedFile file : result.changedFiles()) {
                editState.changedFiles.add(new ChangedFile(file.relativePath(), file.changeType(), file.beforeHash(),
                        file.afterHash(), toolCallNo, file.beforeContent(), file.afterContent()));
            }
        }
        if (result.testReport() != null) {
            editState.testReports.add(result.testReport());
        }
    }

    private void collectGitState(AgentRun run, ToolInvocation invocation, ToolResult result) {
        if (!"run_shell".equals(invocation.name()) || result.status() != CallStatus.SUCCESS) {
            return;
        }
        String command = commandFromJson(invocation.argumentsJson());
        String lower = command.toLowerCase();
        if (lower.startsWith("git checkout -b ")) {
            String branch = command.substring("git checkout -b ".length()).trim();
            run.setGitBranch(branch);
            recordAudit(run.getRunId(), AuditEventType.HIGH_RISK_TOOL_USED, "创建本地分支", branch);
            log.info("记录 Git 分支 runId={} branch={}", run.getRunId(), branch);
        } else if (lower.startsWith("git commit")) {
            String hash = parseCommitHash(result.fullOutput());
            run.setCommitHash(hash);
            recordAudit(run.getRunId(), AuditEventType.HIGH_RISK_TOOL_USED, "创建本地提交",
                    hash == null ? result.summary() : hash);
            log.info("记录 Git 提交 runId={} commitHash={}", run.getRunId(), hash);
        }
    }

    private void publishResultEvents(WorkspaceDescriptor workspace, AgentRun run, ToolInvocation invocation, ToolResult result) {
        if (result.changedFiles() != null) {
            for (ChangedFile file : result.changedFiles()) {
                trace(workspace, run.getRunId(), AgentRunEventType.FILE_CHANGED.code(), Map.of(
                        "relativePath", file.relativePath(),
                        "changeType", file.changeType()));
            }
        }
        if (result.testReport() != null) {
            trace(workspace, run.getRunId(), AgentRunEventType.TEST_REPORTED.code(), Map.of(
                    "command", result.testReport().command(),
                    "status", result.testReport().status(),
                    "exitCode", result.testReport().exitCode()));
        }
        if ("run_shell".equals(invocation.name()) && run.getCommitHash() != null
                && commandFromJson(invocation.argumentsJson()).toLowerCase().startsWith("git commit")) {
            trace(workspace, run.getRunId(), AgentRunEventType.GIT_COMMITTED.code(), Map.of(
                    "branch", run.getGitBranch() == null ? "" : run.getGitBranch(),
                    "commitHash", run.getCommitHash()));
        }
    }

    private String commandFromJson(String argumentsJson) {
        if (argumentsJson == null) {
            return "";
        }
        Matcher matcher = Pattern.compile("\"command\"\\s*:\\s*\"([^\"]+)\"").matcher(argumentsJson);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private String parseCommitHash(String output) {
        if (output == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("\\s([0-9a-f]{7,40})\\]").matcher(output);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String jsonField(String json, String field) {
        if (json == null) {
            return "";
        }
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"").matcher(json);
        return matcher.find() ? matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\") : "";
    }

    private void writeFinal(WorkspaceDescriptor workspace, AgentRun run, RunEditState editState) {
        runChangeService.record(workspace, run, editState.changedFiles);
        List<RunArtifact> reviewArtifacts = artifactPort.writeReviewArtifacts(workspace, run, editState.changedFiles, editState.testReports);
        reviewArtifacts.forEach(recordRepository::saveArtifact);
        reviewArtifacts.stream()
                .filter(artifact -> artifact.getArtifactType() == ArtifactType.PR_DRAFT)
                .findFirst()
                .ifPresent(artifact -> trace(workspace, run.getRunId(), AgentRunEventType.PR_DRAFT_GENERATED.code(),
                        Map.of("path", artifact.getRelativePath())));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", run.getRunId());
        result.put("status", run.getStatus().name());
        result.put("model", run.getModel());
        result.put("permissionLevel", run.getPermissionLevel() == null ? "DEFAULT" : run.getPermissionLevel().name());
        result.put("conversationId", run.getConversationId());
        result.put("gitBranch", run.getGitBranch());
        result.put("commitHash", run.getCommitHash());
        result.put("changed", !editState.changedFiles.isEmpty());
        result.put("changedFileCount", editState.changedFiles.size());
        result.put("testStatus", testStatus(editState));
        result.put("reviewArtifacts", reviewArtifactPaths(run, editState));
        boolean canGeneratePrDraft = run.getPermissionLevel() != null && run.getPermissionLevel().atLeast(AgentPermissionLevel.DEFAULT);
        result.put("prDraftPath", editState.changedFiles.isEmpty() || !canGeneratePrDraft ? null : ".coder/runs/" + run.getRunId() + "/pull-request.md");
        result.put("rollbackArtifacts", editState.changedFiles.isEmpty()
                ? List.of()
                : List.of(".coder/runs/" + run.getRunId() + "/rollback.patch", ".coder/runs/" + run.getRunId() + "/file-backup/"));
        modelConfigPort.resolve(run.getModel()).ifPresent(model -> {
            result.put("model_provider", model.provider());
            result.put("actual_model", model.actualModel());
        });
        result.put("finalAnswer", run.getFinalAnswer());
        result.put("failureReason", run.getFailureReason());
        result.put("attempts", run.getModelCallCount());
        result.put("tool_steps", run.getToolCallCount());
        result.put("model_calls", run.getModelCallCount());
        result.put("tool_calls", run.getToolCallCount());
        result.put("duration", run.getDurationMs());
        result.put("raw_context_tokens", editState.latestRawContextTokens);
        result.put("final_context_tokens", editState.latestFinalContextTokens);
        result.put("context_compression_ratio", editState.latestCompressionRatio);
        result.put("memory_hit_count", editState.latestMemoryHitCount);
        result.put("stale_memory_count", editState.latestStaleMemoryCount);
        result.put("selected_file_summary_count", editState.latestSelectedFileSummaryCount);
        result.put("selected_raw_snippet_count", editState.latestSelectedRawSnippetCount);
        recordRepository.saveArtifact(artifactPort.writeFinalResult(workspace, run, result));
        memoryService.rememberRunSummary(run);
    }

    private void saveAgentMessage(AgentRun run) {
        if (run.getConversationId() == null || run.getConversationId().isBlank()) {
            draftService.clear(run.getRunId());
            return;
        }
        String existingContent = currentAgentMessage(run)
                .map(AgentMessage::getContent)
                .orElse("");
        conversationRepository.deleteAgentMessagesByRunId(run.getRunId());

        String content = AgentRunDraftService.sanitizeCompletedText(run.getFinalAnswer());
        if (!StringUtils.hasText(content)) {
            String draftContent = draftService.content(run.getRunId());
            content = StringUtils.hasText(draftContent) ? draftContent : existingContent;
        }
        if (!StringUtils.hasText(content) && run.getStatus() == AgentRunStatus.FAILED) {
            String reason = StringUtils.hasText(run.getFailureReason()) ? run.getFailureReason() : "\u672a\u77e5\u539f\u56e0";
            content = "\u8fd0\u884c\u5931\u8d25\uff1a" + reason;
        }
        if (!StringUtils.hasText(content) && run.getStatus() == AgentRunStatus.CANCELLED) {
            content = "\u5df2\u53d6\u6d88";
        }
        if (StringUtils.hasText(content)) {
            saveNewAgentMessage(run, content);
        }
        draftService.clear(run.getRunId());
    }

    private java.util.Optional<AgentMessage> currentAgentMessage(AgentRun run) {
        if (run.getConversationId() == null || run.getConversationId().isBlank()) {
            return java.util.Optional.empty();
        }
        return conversationRepository.listMessages(run.getConversationId()).stream()
                .filter(message -> run.getRunId().equals(message.getRunId()))
                .filter(message -> "AGENT".equals(message.getRole()))
                .findFirst();
    }

    private void saveNewAgentMessage(AgentRun run, String content) {
        conversationRepository.saveMessage(AgentMessage.builder()
                .messageId("msg_" + UUID.randomUUID().toString().replace("-", ""))
                .conversationId(run.getConversationId())
                .runId(run.getRunId())
                .role("AGENT")
                .content(content)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private String testStatus(RunEditState editState) {
        if (editState.testReports.isEmpty()) {
            return "NOT_RUN";
        }
        boolean failed = editState.testReports.stream().anyMatch(report -> !"PASSED".equals(report.status()));
        return failed ? "FAILED" : "PASSED";
    }

    private List<String> reviewArtifactPaths(AgentRun run, RunEditState editState) {
        List<String> paths = new ArrayList<>();
        if (!editState.changedFiles.isEmpty()) {
            paths.add(".coder/runs/" + run.getRunId() + "/patch.diff");
            paths.add(".coder/runs/" + run.getRunId() + "/changed-files.json");
            paths.add(".coder/runs/" + run.getRunId() + "/rollback.patch");
            paths.add(".coder/runs/" + run.getRunId() + "/file-backup/");
            if (run.getPermissionLevel() != null && run.getPermissionLevel().atLeast(AgentPermissionLevel.DEFAULT)) {
                paths.add(".coder/runs/" + run.getRunId() + "/pull-request.md");
            }
        }
        if (!editState.testReports.isEmpty()) {
            paths.add(".coder/runs/" + run.getRunId() + "/test-report.json");
        }
        if (!editState.changedFiles.isEmpty() || !editState.testReports.isEmpty()) {
            paths.add(".coder/runs/" + run.getRunId() + "/review-summary.md");
        }
        return paths;
    }

    private ModelBackendConfig auditModel(String modelKey) {
        return modelConfigPort.resolve(modelKey)
                .orElse(new ModelBackendConfig(modelKey, "openai-compatible", modelKey, "", "", "", 0.2, 60));
    }

    private void recordStep(AgentRun run, String type, String summary) {
        recordRepository.saveStep(AgentStep.builder()
                .runId(run.getRunId())
                .stepNo(run.getStepCount())
                .stepType(type)
                .summary(summary)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private void recordAudit(String runId, AuditEventType type, String message, String detail) {
        recordRepository.saveAuditEvent(AuditEvent.builder()
                .runId(runId)
                .eventType(type)
                .message(message)
                .detail(detail)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private AuditEventType auditType(String code) {
        if ("PATH_ESCAPE".equals(code)) {
            return AuditEventType.PATH_ESCAPE;
        }
        if ("INVALID_ARGUMENT".equals(code) || "UNKNOWN_TOOL".equals(code)) {
            return AuditEventType.INVALID_ARGUMENT;
        }
        if ("DANGEROUS_COMMAND".equals(code)) {
            return AuditEventType.DANGEROUS_COMMAND;
        }
        if ("COMMAND_NOT_ALLOWED".equals(code)) {
            return AuditEventType.COMMAND_NOT_ALLOWED;
        }
        if ("CAPABILITY_REJECTED".equals(code) || "READ_ONLY_EDIT_REJECTED".equals(code)) {
            return AuditEventType.CAPABILITY_REJECTED;
        }
        if ("PERMISSION_REJECTED".equals(code)) {
            return AuditEventType.PERMISSION_REJECTED;
        }
        if ("PROTECTED_PATH".equals(code)) {
            return AuditEventType.PROTECTED_PATH_REJECTED;
        }
        return AuditEventType.TOOL_REJECTED;
    }

    private void trace(WorkspaceDescriptor workspace, String runId, String type, Map<String, Object> payload) {
        Map<String, Object> event = new LinkedHashMap<>();
        String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");
        event.put("eventId", eventId);
        event.put("runId", runId);
        event.put("time", LocalDateTime.now().toString());
        event.put("type", type);
        event.put("payload", payload);
        recordRepository.saveArtifact(artifactPort.appendTrace(workspace, runId, event));
        eventPublisher.publish(new AgentRunEvent(eventId, runId, type, LocalDateTime.now(), payload));
    }

    private void failWithoutWorkspace(AgentRun run, String reason) {
        domainService.fail(run, reason);
        runRepository.update(run);
        recordAudit(run.getRunId(), AuditEventType.WORKSPACE_REJECTED, "workspace 解析失败", reason);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String redact(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replaceAll("(?i)(api[_-]?key|token|password|credential)\\s*[:=]\\s*[^\\s,;\"}]+", "$1=[REDACTED]")
                .replaceAll("-----BEGIN [^-]+PRIVATE KEY-----[\\s\\S]*?-----END [^-]+PRIVATE KEY-----",
                        "[REDACTED_PRIVATE_KEY]");
    }

    private String abbreviate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "...";
    }

    private static class ToolAccumulator {
        private final String id;
        private final String name;
        private final StringBuilder arguments = new StringBuilder();

        private ToolAccumulator(String id, String name) {
            this.id = id;
            this.name = name;
        }

        private void append(String delta) {
            if (delta != null) {
                arguments.append(delta);
            }
        }
    }

    private static class RunEditState {
        private final List<ChangedFile> changedFiles = new ArrayList<>();
        private final List<TestCommandReport> testReports = new ArrayList<>();
        private final Set<String> seenToolObservationKeys = new HashSet<>();
        private final Map<String, Integer> toolInvocationCounts = new LinkedHashMap<>();
        private int consecutiveRejectedToolCalls;
        private int latestRawContextTokens;
        private int latestFinalContextTokens;
        private double latestCompressionRatio;
        private int latestMemoryHitCount;
        private int latestStaleMemoryCount;
        private int latestSelectedFileSummaryCount;
        private int latestSelectedRawSnippetCount;
    }
}
