package cn.noname.coder.agent.cases.agent.impl;

import cn.noname.coder.agent.cases.agent.AgentContextAssembler;
import cn.noname.coder.agent.domain.agent.adapter.port.IArtifactPort;
import cn.noname.coder.agent.domain.agent.adapter.port.IAgentRunEventPublisher;
import cn.noname.coder.agent.domain.agent.adapter.port.IModelConfigPort;
import cn.noname.coder.agent.domain.agent.adapter.port.IModelGateway;
import cn.noname.coder.agent.domain.agent.adapter.port.IToolGateway;
import cn.noname.coder.agent.domain.agent.adapter.port.IWorkspacePort;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRecordRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRunRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentConversationRepository;
import cn.noname.coder.agent.domain.agent.model.entity.AgentMessage;
import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.agent.model.entity.AgentStep;
import cn.noname.coder.agent.domain.agent.model.entity.AuditEvent;
import cn.noname.coder.agent.domain.agent.model.entity.ModelCall;
import cn.noname.coder.agent.domain.agent.model.entity.RunArtifact;
import cn.noname.coder.agent.domain.agent.model.entity.ToolCall;
import cn.noname.coder.agent.domain.agent.model.valobj.ModelRequest;
import cn.noname.coder.agent.domain.agent.model.valobj.ModelResponse;
import cn.noname.coder.agent.domain.agent.model.valobj.AgentRunEvent;
import cn.noname.coder.agent.domain.agent.model.valobj.AgentRunEventType;
import cn.noname.coder.agent.domain.agent.model.valobj.ModelBackendConfig;
import cn.noname.coder.agent.domain.agent.model.valobj.ChangedFile;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent 后台执行循环。首版聚焦真实模型调用、受限工具调用和可审计工件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRunExecutor {

    private final IAgentRunRepository runRepository;
    private final IAgentRecordRepository recordRepository;
    private final IAgentConversationRepository conversationRepository;
    private final IWorkspacePort workspacePort;
    private final IModelConfigPort modelConfigPort;
    private final IModelGateway modelGateway;
    private final IToolGateway toolGateway;
    private final IArtifactPort artifactPort;
    private final IAgentRunEventPublisher eventPublisher;
    private final AgentRuntimeProperties properties;
    private final AgentRunDomainService domainService = new AgentRunDomainService();
    private final AgentContextAssembler contextAssembler = new AgentContextAssembler();

    public void execute(String runId) {
        AgentRun run = runRepository.findByRunId(runId).orElse(null);
        if (run == null || run.getStatus().isTerminal()) {
            return;
        }
        WorkspaceDescriptor workspace = workspacePort.resolve(run.getWorkspaceKey()).orElse(null);
        if (workspace == null) {
            failWithoutWorkspace(run, "workspaceKey 未配置：" + run.getWorkspaceKey());
            return;
        }
        RunEditState editState = new RunEditState();

        try {
            log.info("Agent 开始运行 runId={} workspaceKey={} model={} workspaceRoot={}",
                    runId, run.getWorkspaceKey(), run.getModel(), workspace.rootPath());
            domainService.start(run);
            runRepository.update(run);
            trace(workspace, runId, AgentRunEventType.RUN_STARTED.code(), Map.of("status", AgentRunStatus.RUNNING.name()));
            recordStep(run, "run_started", "后台执行开始");

            List<String> messages = new ArrayList<>(contextAssembler.initialMessages(run, workspace));
            LocalDateTime deadline = run.getStartedAt().plusSeconds(run.getTimeoutSeconds());
            while (!run.getStatus().isTerminal()) {
                if (!checkBudget(run, deadline, workspace, editState)) {
                    break;
                }

                run.setStepCount(run.getStepCount() + 1);
                run.setModelCallCount(run.getModelCallCount() + 1);
                messages.add(contextAssembler.budgetLine(run));
                writeSnapshot(workspace, run, messages);

                ModelResponse response = callModel(run, messages, workspace);
                if (stopIfCancelled(run, workspace, editState, "after_model_call")) {
                    return;
                }
                if (response.hasToolCalls()) {
                    if (!executeTools(run, workspace, messages, response.toolInvocations(), editState)) {
                        return;
                    }
                } else {
                    String finalAnswer = response.finalAnswer() == null ? "模型未返回内容" : response.finalAnswer();
                    domainService.succeed(run, finalAnswer);
                    runRepository.update(run);
                    saveAgentMessage(run);
                    writeFinal(workspace, run, editState);
                    trace(workspace, runId, AgentRunEventType.RUN_FINISHED.code(), Map.of(
                            "status", run.getStatus().name(),
                            "finalAnswer", abbreviate(finalAnswer, 500)));
                    log.info("Agent 运行成功 runId={} modelCalls={} toolCalls={} durationMs={}",
                            runId, run.getModelCallCount(), run.getToolCallCount(), run.getDurationMs());
                    return;
                }
                runRepository.update(run);
            }
        } catch (Exception e) {
            log.error("Agent 运行失败 runId={}", runId, e);
            domainService.fail(run, abbreviate(e.getMessage(), 1000));
            runRepository.update(run);
            saveAgentMessage(run);
            writeFinal(workspace, run, editState);
            recordAudit(runId, AuditEventType.MODEL_CALL_FAILED, "Agent 执行异常", e.getMessage());
            trace(workspace, runId, AgentRunEventType.RUN_FINISHED.code(), Map.of(
                    "status", run.getStatus().name(),
                    "reason", abbreviate(e.getMessage(), 1000)));
        }
    }

    private boolean checkBudget(AgentRun run, LocalDateTime deadline, WorkspaceDescriptor workspace, RunEditState editState) {
        String reason = null;
        if (LocalDateTime.now().isAfter(deadline)) {
            reason = "运行超时：" + run.getTimeoutSeconds() + " 秒";
        } else if (run.getStepCount() >= run.getMaxSteps()) {
            reason = "步骤数耗尽：" + run.getMaxSteps();
        } else if (run.getModelCallCount() >= run.getMaxModelCalls()) {
            reason = "模型调用次数耗尽：" + run.getMaxModelCalls();
        } else if (run.getToolCallCount() >= run.getMaxToolCalls()) {
            reason = "工具调用次数耗尽：" + run.getMaxToolCalls();
        } else {
            AgentRun current = runRepository.findByRunId(run.getRunId()).orElse(run);
            if (current.getStatus() == AgentRunStatus.CANCELLED) {
                run.setStatus(AgentRunStatus.CANCELLED);
                runRepository.update(run);
                writeFinal(workspace, run, editState);
                trace(workspace, run.getRunId(), AgentRunEventType.RUN_FINISHED.code(), Map.of("status", "CANCELLED", "reason", "用户取消"));
                return false;
            }
        }
        if (reason == null) {
            return true;
        }
        log.warn("Agent 运行预算耗尽 runId={} reason={}", run.getRunId(), reason);
        domainService.fail(run, reason);
        runRepository.update(run);
        writeFinal(workspace, run, editState);
        recordAudit(run.getRunId(), AuditEventType.BUDGET_EXHAUSTED, "预算耗尽", reason);
        trace(workspace, run.getRunId(), AgentRunEventType.AUDIT_EVENT.code(), Map.of("reason", reason));
        trace(workspace, run.getRunId(), AgentRunEventType.RUN_FINISHED.code(), Map.of("status", run.getStatus().name(), "reason", reason));
        return false;
    }

    private boolean stopIfCancelled(AgentRun run, WorkspaceDescriptor workspace, RunEditState editState, String checkpoint) {
        AgentRun current = runRepository.findByRunId(run.getRunId()).orElse(run);
        if (current.getStatus() != AgentRunStatus.CANCELLED) {
            return false;
        }
        run.setStatus(AgentRunStatus.CANCELLED);
        run.setFinalAnswer(null);
        run.setFailureReason(current.getFailureReason());
        run.setEndedAt(current.getEndedAt());
        run.setDurationMs(current.getDurationMs());
        if (run.getEndedAt() == null) {
            domainService.cancel(run);
        }
        runRepository.update(run);
        writeFinal(workspace, run, editState);
        trace(workspace, run.getRunId(), AgentRunEventType.RUN_FINISHED.code(), Map.of("status", "CANCELLED", "reason", "用户取消", "checkpoint", checkpoint));
        log.info("Agent 运行已取消 runId={} checkpoint={}", run.getRunId(), checkpoint);
        return true;
    }

    private ModelResponse callModel(AgentRun run, List<String> messages, WorkspaceDescriptor workspace) {
        long start = System.currentTimeMillis();
        ModelBackendConfig auditModel = auditModel(run.getModel());
        try {
            log.info("调用 {} 模型 runId={} callNo={} provider={}",
                    auditModel.auditName(), run.getRunId(), run.getModelCallCount(), auditModel.provider());
            trace(workspace, run.getRunId(), AgentRunEventType.MODEL_CALL_STARTED.code(),
                    Map.of("callNo", run.getModelCallCount(), "model", auditModel.auditName()));
            ModelResponse response = modelGateway.call(new ModelRequest(run.getRunId(), run.getModel(), messages, toolGateway.definitions(run, workspace)));
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
            trace(workspace, run.getRunId(), AgentRunEventType.MODEL_CALL_COMPLETED.code(), Map.of("callNo", run.getModelCallCount(), "status", "SUCCESS"));
            log.info("模型调用完成 runId={} callNo={} latencyMs={} toolCallCount={} hasFinalAnswer={}",
                    run.getRunId(), run.getModelCallCount(), latencyMs, response.toolInvocations().size(), !response.hasToolCalls());
            return response;
        } catch (Exception e) {
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
            log.warn("模型调用失败 runId={} callNo={} model={} latencyMs={} reason={}",
                    run.getRunId(), run.getModelCallCount(), auditModel.auditName(), latencyMs, abbreviate(e.getMessage(), 300));
            trace(workspace, run.getRunId(), AgentRunEventType.MODEL_CALL_COMPLETED.code(), Map.of(
                    "callNo", run.getModelCallCount(),
                    "status", "FAILED",
                    "reason", abbreviate(e.getMessage(), 300)));
            throw e;
        }
    }

    private boolean executeTools(AgentRun run, WorkspaceDescriptor workspace, List<String> messages, List<ToolInvocation> invocations, RunEditState editState) {
        for (ToolInvocation invocation : invocations) {
            if (stopIfCancelled(run, workspace, editState, "before_tool_call")) {
                return false;
            }
            if (run.getToolCallCount() >= run.getMaxToolCalls()) {
                break;
            }
            long start = System.currentTimeMillis();
            run.setToolCallCount(run.getToolCallCount() + 1);
            log.info("执行工具 runId={} callNo={} tool={}", run.getRunId(), run.getToolCallCount(), invocation.name());
            trace(workspace, run.getRunId(), AgentRunEventType.TOOL_CALL_STARTED.code(),
                    Map.of("callNo", run.getToolCallCount(), "tool", invocation.name()));
            ToolResult result = toolGateway.execute(run, workspace, invocation);
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
            publishResultEvents(workspace, run, invocation, result);
            messages.add("工具结果 " + invocation.name() + "：" + abbreviate(summary, 2000));
            trace(workspace, run.getRunId(), AgentRunEventType.TOOL_CALL_COMPLETED.code(), Map.of(
                    "callNo", run.getToolCallCount(),
                    "tool", invocation.name(),
                    "status", result.status().name(),
                    "summary", abbreviate(summary, 500)
            ));
            if (stopIfCancelled(run, workspace, editState, "after_tool_call")) {
                return false;
            }
        }
        return true;
    }

    private void writeSnapshot(WorkspaceDescriptor workspace, AgentRun run, List<String> messages) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("runId", run.getRunId());
        snapshot.put("callNo", run.getModelCallCount());
        snapshot.put("budget", contextAssembler.budgetLine(run));
        snapshot.put("messages", messages.stream().map(v -> abbreviate(v, 1000)).toList());
        recordRepository.saveArtifact(artifactPort.writeContextSnapshot(workspace, run.getRunId(), run.getModelCallCount(), snapshot));
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
            recordAudit(run.getRunId(), AuditEventType.HIGH_RISK_TOOL_USED, "创建本地提交", hash == null ? result.summary() : hash);
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

    private void writeFinal(WorkspaceDescriptor workspace, AgentRun run, RunEditState editState) {
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
        result.put("permissionLevel", run.getPermissionLevel() == null ? "L1_READ_ONLY" : run.getPermissionLevel().name());
        result.put("conversationId", run.getConversationId());
        result.put("gitBranch", run.getGitBranch());
        result.put("commitHash", run.getCommitHash());
        result.put("changed", !editState.changedFiles.isEmpty());
        result.put("changedFileCount", editState.changedFiles.size());
        result.put("testStatus", testStatus(editState));
        result.put("reviewArtifacts", reviewArtifactPaths(run, editState));
        boolean l3 = run.getPermissionLevel() != null && "L3_REPO_WRITE".equals(run.getPermissionLevel().name());
        result.put("prDraftPath", editState.changedFiles.isEmpty() || !l3 ? null : ".coder/runs/" + run.getRunId() + "/pull-request.md");
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
        recordRepository.saveArtifact(artifactPort.writeFinalResult(workspace, run, result));
    }

    private void saveAgentMessage(AgentRun run) {
        if (run.getConversationId() == null || run.getConversationId().isBlank()) {
            return;
        }
        String content = run.getFinalAnswer();
        if (content == null || content.isBlank()) {
            content = run.getFailureReason() == null ? "运行结束：" + run.getStatus() : "运行失败：" + run.getFailureReason();
        }
        conversationRepository.saveMessage(AgentMessage.builder()
                .messageId("msg_" + java.util.UUID.randomUUID().toString().replace("-", ""))
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
            if (run.getPermissionLevel() != null && "L3_REPO_WRITE".equals(run.getPermissionLevel().name())) {
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
        String eventId = "evt_" + java.util.UUID.randomUUID().toString().replace("-", "");
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

    private String abbreviate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "...";
    }

    private static class RunEditState {
        private final List<ChangedFile> changedFiles = new ArrayList<>();
        private final List<TestCommandReport> testReports = new ArrayList<>();
    }
}
