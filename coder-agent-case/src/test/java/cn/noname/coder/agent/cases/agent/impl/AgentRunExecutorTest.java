package cn.noname.coder.agent.cases.agent.impl;

import cn.noname.coder.agent.api.dto.CreateAgentRunRequestDTO;
import cn.noname.coder.agent.domain.agent.adapter.port.IArtifactPort;
import cn.noname.coder.agent.domain.agent.adapter.port.IAgentRunEventPublisher;
import cn.noname.coder.agent.domain.agent.adapter.port.IModelConfigPort;
import cn.noname.coder.agent.domain.agent.adapter.port.IModelGateway;
import cn.noname.coder.agent.domain.agent.adapter.port.IToolGateway;
import cn.noname.coder.agent.domain.agent.adapter.port.IWorkspacePort;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRecordRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentConversationRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRunRepository;
import cn.noname.coder.agent.domain.agent.model.entity.*;
import cn.noname.coder.agent.domain.agent.model.valobj.*;
import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import cn.noname.coder.agent.types.enums.AgentRunStatus;
import cn.noname.coder.agent.types.enums.ArtifactType;
import cn.noname.coder.agent.types.enums.CallStatus;
import cn.noname.coder.agent.types.exception.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AgentRunExecutorTest {

    @Test
    void shouldCreateRunGivenConfiguredModelKey() {
        // Given 请求指定了已配置的模型 key
        InMemoryRunRepository runRepository = new InMemoryRunRepository();
        InMemoryRecordRepository recordRepository = new InMemoryRecordRepository();
        CreateAgentRunCaseImpl createCase = new CreateAgentRunCaseImpl(
                new StubWorkspacePort(false), runRepository, recordRepository, new InMemoryConversationRepository(), new StubArtifactPort(),
                new StubModelConfigPort(), new AgentRuntimeProperties(), executor(runRepository, recordRepository,
                request -> new ModelResponse("resp_1", "ok", List.of(), "ok"),
                new NoopToolGateway(), new StubArtifactPort(), false), new SyncTaskExecutor());

        // When 创建运行
        var response = createCase.create(new CreateAgentRunRequestDTO("coder-agent", "分析仓库", "glm-5", null, null, null));

        // Then 保存模型 key
        AgentRun saved = runRepository.findByRunId(response.runId()).orElseThrow();
        assertEquals("glm-5", saved.getModel());
    }

    @Test
    void shouldRejectCreateRunGivenUnknownModelKey() {
        // Given 请求指定了未配置的模型 key
        InMemoryRunRepository runRepository = new InMemoryRunRepository();
        InMemoryRecordRepository recordRepository = new InMemoryRecordRepository();
        CreateAgentRunCaseImpl createCase = new CreateAgentRunCaseImpl(
                new StubWorkspacePort(false), runRepository, recordRepository, new InMemoryConversationRepository(), new StubArtifactPort(),
                new StubModelConfigPort(), new AgentRuntimeProperties(), executor(runRepository, recordRepository,
                request -> new ModelResponse("resp_1", "ok", List.of(), "ok"),
                new NoopToolGateway(), new StubArtifactPort(), false), new SyncTaskExecutor());

        // When 创建运行 / Then 拒绝且不写入 agent_run
        AppException ex = assertThrows(AppException.class, () ->
                createCase.create(new CreateAgentRunRequestDTO("coder-agent", "分析仓库", "unknown", null, null, null)));
        assertEquals("MODEL_NOT_CONFIGURED", ex.getCode());
        assertEquals(0, runRepository.runs.size());
    }

    @Test
    void shouldSucceedGivenModelReturnsFinalAnswer() {
        // Given 模型直接返回最终结论
        InMemoryRunRepository runRepository = new InMemoryRunRepository();
        InMemoryRecordRepository recordRepository = new InMemoryRecordRepository();
        AgentRun run = newRun("run_success", 20, 8, 16, 300);
        runRepository.save(run);
        AgentRunExecutor executor = executor(runRepository, recordRepository,
                request -> new ModelResponse("resp_1", "分析完成", List.of(), "final"),
                new NoopToolGateway(), new StubArtifactPort(), false);

        // When 执行 Agent 循环
        executor.execute(run.getRunId());

        // Then 运行成功结束并记录模型调用与 final-result 工件
        AgentRun saved = runRepository.findByRunId(run.getRunId()).orElseThrow();
        assertEquals(AgentRunStatus.SUCCEEDED, saved.getStatus());
        assertEquals("分析完成", saved.getFinalAnswer());
        assertEquals(1, recordRepository.modelCalls.size());
        assertTrue(recordRepository.artifacts.stream().anyMatch(v -> v.getArtifactType() == ArtifactType.FINAL_RESULT));
    }

    @Test
    void shouldFailGivenModelCallBudgetExhausted() {
        // Given 模型调用预算为 0
        InMemoryRunRepository runRepository = new InMemoryRunRepository();
        InMemoryRecordRepository recordRepository = new InMemoryRecordRepository();
        AgentRun run = newRun("run_budget", 20, 0, 16, 300);
        runRepository.save(run);
        CountingModelGateway modelGateway = new CountingModelGateway(new ModelResponse("resp_1", "不应调用", List.of(), "final"));
        AgentRunExecutor executor = executor(runRepository, recordRepository, modelGateway,
                new NoopToolGateway(), new StubArtifactPort(), false);

        // When 执行 Agent 循环
        executor.execute(run.getRunId());

        // Then 运行失败且不会调用模型
        AgentRun saved = runRepository.findByRunId(run.getRunId()).orElseThrow();
        assertEquals(AgentRunStatus.FAILED, saved.getStatus());
        assertEquals(0, modelGateway.calls);
        assertTrue(recordRepository.auditEvents.stream().anyMatch(v -> "BUDGET_EXHAUSTED".equals(v.getEventType().name())));
    }

    @Test
    void shouldCancelGivenRepositoryStatusChangedToCancelled() {
        // Given 运行在预算检查时被外部取消
        InMemoryRunRepository runRepository = new InMemoryRunRepository();
        InMemoryRecordRepository recordRepository = new InMemoryRecordRepository();
        AgentRun run = newRun("run_cancel", 20, 8, 16, 300);
        runRepository.save(run);
        AgentRunExecutor executor = executor(runRepository, recordRepository,
                request -> new ModelResponse("resp_1", "不应调用", List.of(), "final"),
                new NoopToolGateway(), new StubArtifactPort(), true);

        // When 执行 Agent 循环
        executor.execute(run.getRunId());

        // Then 运行进入取消状态并写入 final-result
        AgentRun saved = runRepository.findByRunId(run.getRunId()).orElseThrow();
        assertEquals(AgentRunStatus.CANCELLED, saved.getStatus());
        assertTrue(recordRepository.artifacts.stream().anyMatch(v -> v.getArtifactType() == ArtifactType.FINAL_RESULT));
    }

    @Test
    void shouldKeepCancelledGivenCancellationHappensDuringModelCall() {
        // Given 模型调用期间外部取消了运行
        InMemoryRunRepository runRepository = new InMemoryRunRepository();
        InMemoryRecordRepository recordRepository = new InMemoryRecordRepository();
        AgentRun run = newRun("run_cancel_during_model", 20, 8, 16, 300);
        runRepository.save(run);
        AgentRunExecutor executor = executor(runRepository, recordRepository,
                request -> {
                    runRepository.cancel(request.runId());
                    return new ModelResponse("resp_1", "不应覆盖为成功", List.of(), "final");
                },
                new NoopToolGateway(), new StubArtifactPort(), false);

        // When 执行 Agent 循环
        executor.execute(run.getRunId());

        // Then 最终状态保持取消，不会被模型最终回答覆盖成成功
        AgentRun saved = runRepository.findByRunId(run.getRunId()).orElseThrow();
        assertEquals(AgentRunStatus.CANCELLED, saved.getStatus());
        assertNull(saved.getFinalAnswer());
    }

    @Test
    void shouldSaveCancellationFeedbackGivenRunCancelled() {
        // Given 带对话的运行在模型调用期间被取消
        InMemoryRunRepository runRepository = new InMemoryRunRepository();
        InMemoryRecordRepository recordRepository = new InMemoryRecordRepository();
        InMemoryConversationRepository conversationRepository = new InMemoryConversationRepository();
        AgentRun run = newRun("run_cancel_message", 20, 8, 16, 300);
        run.setConversationId("conv_1");
        runRepository.save(run);
        AgentRunExecutor executor = new AgentRunExecutor(runRepository, recordRepository, conversationRepository,
                new StubWorkspacePort(false), new StubModelConfigPort(), request -> {
            runRepository.cancel(request.runId());
            return new ModelResponse("resp_1", "不应保存", List.of(), "final");
        }, new NoopToolGateway(), new StubArtifactPort(), new NoopEventPublisher(), new AgentRuntimeProperties());

        // When 执行 Agent 循环
        executor.execute(run.getRunId());

        // Then 取消会生成一条可展示的 Agent 反馈，避免前端最终消息消失
        assertEquals(AgentRunStatus.CANCELLED, runRepository.findByRunId(run.getRunId()).orElseThrow().getStatus());
        assertEquals(1, conversationRepository.messages.size());
        assertEquals("任务已取消", conversationRepository.messages.getFirst().getContent());
    }

    @Test
    void shouldFailGivenModelRequestsToolButToolBudgetIsExhausted() {
        // Given 模型请求工具，但工具预算为 0
        InMemoryRunRepository runRepository = new InMemoryRunRepository();
        InMemoryRecordRepository recordRepository = new InMemoryRecordRepository();
        AgentRun run = newRun("run_tool_budget_zero", 20, 8, 0, 300);
        runRepository.save(run);
        AgentRunExecutor executor = executor(runRepository, recordRepository,
                request -> new ModelResponse("resp_1", null,
                        List.of(new ToolInvocation("tool_1", "list_files", "{}")), "tool call"),
                new NoopToolGateway(), new StubArtifactPort(), false);

        // When 执行 Agent 循环
        executor.execute(run.getRunId());

        // Then 运行必须进入终态，不能停留在 RUNNING
        AgentRun saved = runRepository.findByRunId(run.getRunId()).orElseThrow();
        assertEquals(AgentRunStatus.FAILED, saved.getStatus());
        assertTrue(saved.getFailureReason().contains("工具"));
    }

    @Test
    void shouldFailGivenModelGatewayThrowsException() {
        // Given 模型网关异常
        InMemoryRunRepository runRepository = new InMemoryRunRepository();
        InMemoryRecordRepository recordRepository = new InMemoryRecordRepository();
        AgentRun run = newRun("run_failed", 20, 8, 16, 300);
        runRepository.save(run);
        AgentRunExecutor executor = executor(runRepository, recordRepository,
                request -> {
                    throw new IllegalStateException("model unavailable");
                },
                new NoopToolGateway(), new StubArtifactPort(), false);

        // When 执行 Agent 循环
        executor.execute(run.getRunId());

        // Then 运行失败且记录失败的模型调用
        AgentRun saved = runRepository.findByRunId(run.getRunId()).orElseThrow();
        assertEquals(AgentRunStatus.FAILED, saved.getStatus());
        assertTrue(saved.getFailureReason().contains("model unavailable"));
        assertEquals(CallStatus.FAILED, recordRepository.modelCalls.getFirst().getStatus());
    }

    @Test
    void shouldFailGivenModelGatewayThrowsError() {
        // Given 模型网关抛出 Error，模拟后台线程非 Exception 异常
        InMemoryRunRepository runRepository = new InMemoryRunRepository();
        InMemoryRecordRepository recordRepository = new InMemoryRecordRepository();
        AgentRun run = newRun("run_error", 20, 8, 16, 300);
        runRepository.save(run);
        AgentRunExecutor executor = executor(runRepository, recordRepository,
                request -> {
                    throw new AssertionError("fatal model error");
                },
                new NoopToolGateway(), new StubArtifactPort(), false);

        // When 执行 Agent 循环
        assertDoesNotThrow(() -> executor.execute(run.getRunId()));

        // Then 运行不能停留在 RUNNING
        AgentRun saved = runRepository.findByRunId(run.getRunId()).orElseThrow();
        assertEquals(AgentRunStatus.FAILED, saved.getStatus());
        assertTrue(saved.getFailureReason().contains("fatal model error"));
    }

    @Test
    void shouldRejectCreateRunGivenConcurrentLimitReached() {
        // Given 当前已达到并发上限
        InMemoryRunRepository runRepository = new InMemoryRunRepository();
        InMemoryRecordRepository recordRepository = new InMemoryRecordRepository();
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.getBudget().setMaxConcurrentRuns(1);
        properties.getModel().setModel("test-model");
        runRepository.save(newRun("run_existing", 20, 8, 16, 300));
        CreateAgentRunCaseImpl createCase = new CreateAgentRunCaseImpl(
                new StubWorkspacePort(false), runRepository, recordRepository, new InMemoryConversationRepository(), new StubArtifactPort(),
                new StubModelConfigPort(), properties, executor(runRepository, recordRepository,
                request -> new ModelResponse("resp_1", "ok", List.of(), "ok"),
                new NoopToolGateway(), new StubArtifactPort(), false), new SyncTaskExecutor());

        // When 创建新运行 / Then 返回并发限制错误
        AppException ex = assertThrows(AppException.class, () ->
                createCase.create(new CreateAgentRunRequestDTO("coder-agent", "分析仓库", null, null, null, null)));
        assertEquals("CONCURRENT_LIMIT", ex.getCode());
    }

    static class StubModelConfigPort implements IModelConfigPort {
        private final Set<String> modelKeys = Set.of("test-model", "glm-5", "qwen3.6-plus", "deepseek-v4-flash");

        @Override
        public ModelBackendConfig defaultModel() {
            return config("test-model");
        }

        @Override
        public Optional<ModelBackendConfig> resolve(String modelKey) {
            if (modelKey == null || modelKey.isBlank()) {
                return Optional.of(defaultModel());
            }
            return modelKeys.contains(modelKey) ? Optional.of(config(modelKey)) : Optional.empty();
        }

        private ModelBackendConfig config(String modelKey) {
            return new ModelBackendConfig(modelKey, "openai-compatible", modelKey,
                    "https://example.test/v1", "test-key", "chat-completions", 0.2, 60);
        }
    }

    private AgentRunExecutor executor(InMemoryRunRepository runRepository,
                                      InMemoryRecordRepository recordRepository,
                                      IModelGateway modelGateway,
                                      IToolGateway toolGateway,
                                      IArtifactPort artifactPort,
                                      boolean cancelOnSecondFind) {
        runRepository.cancelOnSecondFind = cancelOnSecondFind;
        return new AgentRunExecutor(runRepository, recordRepository, new InMemoryConversationRepository(), new StubWorkspacePort(false),
                new StubModelConfigPort(), modelGateway, toolGateway, artifactPort,
                new NoopEventPublisher(), new AgentRuntimeProperties());
    }

    private AgentRun newRun(String runId, int maxSteps, int maxModelCalls, int maxToolCalls, int timeoutSeconds) {
        return AgentRun.builder()
                .runId(runId)
                .workspaceKey("coder-agent")
                .task("分析仓库")
                .model("test-model")
                .status(AgentRunStatus.CREATED)
                .maxSteps(maxSteps)
                .maxModelCalls(maxModelCalls)
                .maxToolCalls(maxToolCalls)
                .timeoutSeconds(timeoutSeconds)
                .stepCount(0)
                .modelCallCount(0)
                .toolCallCount(0)
                .createdAt(LocalDateTime.now())
                .build();
    }

    static class InMemoryRunRepository implements IAgentRunRepository {
        private final Map<String, AgentRun> runs = new LinkedHashMap<>();
        private boolean cancelOnSecondFind;
        private final Map<String, Integer> findCounts = new HashMap<>();

        @Override
        public void save(AgentRun run) {
            runs.put(run.getRunId(), cloneRun(run));
        }

        @Override
        public void update(AgentRun run) {
            runs.put(run.getRunId(), cloneRun(run));
        }

        @Override
        public Optional<AgentRun> findByRunId(String runId) {
            int count = findCounts.merge(runId, 1, Integer::sum);
            AgentRun run = runs.get(runId);
            if (cancelOnSecondFind && count >= 2 && run != null) {
                run.setStatus(AgentRunStatus.CANCELLED);
            }
            return Optional.ofNullable(run).map(this::cloneRun);
        }

        void cancel(String runId) {
            AgentRun run = runs.get(runId);
            if (run != null) {
                run.setStatus(AgentRunStatus.CANCELLED);
            }
        }

        @Override
        public long countByStatuses(Collection<AgentRunStatus> statuses) {
            return runs.values().stream().filter(v -> statuses.contains(v.getStatus())).count();
        }

        private AgentRun cloneRun(AgentRun run) {
            return AgentRun.builder()
                    .id(run.getId())
                    .runId(run.getRunId())
                    .workspaceKey(run.getWorkspaceKey())
                    .conversationId(run.getConversationId())
                    .task(run.getTask())
                    .model(run.getModel())
                    .permissionLevel(run.getPermissionLevel())
                    .sourceMessageId(run.getSourceMessageId())
                    .status(run.getStatus())
                    .finalAnswer(run.getFinalAnswer())
                    .failureReason(run.getFailureReason())
                    .gitBranch(run.getGitBranch())
                    .commitHash(run.getCommitHash())
                    .maxSteps(run.getMaxSteps())
                    .maxModelCalls(run.getMaxModelCalls())
                    .maxToolCalls(run.getMaxToolCalls())
                    .timeoutSeconds(run.getTimeoutSeconds())
                    .stepCount(run.getStepCount())
                    .modelCallCount(run.getModelCallCount())
                    .toolCallCount(run.getToolCallCount())
                    .createdAt(run.getCreatedAt())
                    .startedAt(run.getStartedAt())
                    .endedAt(run.getEndedAt())
                    .durationMs(run.getDurationMs())
                    .build();
        }
    }

    static class InMemoryRecordRepository implements IAgentRecordRepository {
        private final List<AgentStep> steps = new ArrayList<>();
        private final LinkedList<ModelCall> modelCalls = new LinkedList<>();
        private final List<ToolCall> toolCalls = new ArrayList<>();
        private final List<AuditEvent> auditEvents = new ArrayList<>();
        private final List<RunArtifact> artifacts = new ArrayList<>();

        @Override
        public void saveStep(AgentStep step) {
            steps.add(step);
        }

        @Override
        public void saveModelCall(ModelCall modelCall) {
            modelCalls.add(modelCall);
        }

        @Override
        public void saveToolCall(ToolCall toolCall) {
            toolCalls.add(toolCall);
        }

        @Override
        public void saveAuditEvent(AuditEvent event) {
            auditEvents.add(event);
        }

        @Override
        public void saveArtifact(RunArtifact artifact) {
            artifacts.add(artifact);
        }

        @Override
        public List<AuditEvent> listAuditEvents(String runId) {
            return auditEvents.stream().filter(v -> runId.equals(v.getRunId())).toList();
        }

        @Override
        public List<RunArtifact> listArtifacts(String runId) {
            return artifacts.stream().filter(v -> runId.equals(v.getRunId())).toList();
        }
    }

    static class InMemoryConversationRepository implements IAgentConversationRepository {
        private final Map<String, AgentConversation> conversations = new HashMap<>();
        private final List<AgentMessage> messages = new ArrayList<>();
        private final List<PermissionAudit> audits = new ArrayList<>();

        @Override
        public void saveConversation(AgentConversation conversation) {
            conversations.put(conversation.getConversationId(), conversation);
        }

        @Override
        public void updateConversation(AgentConversation conversation) {
            conversations.put(conversation.getConversationId(), conversation);
        }

        @Override
        public Optional<AgentConversation> findConversation(String conversationId) {
            return Optional.ofNullable(conversations.get(conversationId));
        }

        @Override
        public List<AgentConversation> listConversations(String workspaceKey) {
            return conversations.values().stream().toList();
        }

        @Override
        public void deleteConversation(String conversationId) {
            conversations.remove(conversationId);
        }

        @Override
        public void saveMessage(AgentMessage message) {
            messages.add(message);
        }

        @Override
        public Optional<AgentMessage> findMessage(String messageId) {
            return messages.stream().filter(v -> messageId.equals(v.getMessageId())).findFirst();
        }

        @Override
        public void updateMessage(AgentMessage message) {
            deleteMessage(message.getMessageId());
            messages.add(message);
        }

        @Override
        public void deleteMessage(String messageId) {
            messages.removeIf(v -> messageId.equals(v.getMessageId()));
        }

        @Override
        public void deleteAgentMessagesByRunId(String runId) {
            messages.removeIf(v -> runId != null && runId.equals(v.getRunId()) && "AGENT".equals(v.getRole()));
        }

        @Override
        public void deleteMessagesByRunId(String runId) {
            messages.removeIf(v -> runId != null && runId.equals(v.getRunId()));
        }

        @Override
        public List<AgentMessage> listMessages(String conversationId) {
            return messages.stream().filter(v -> conversationId.equals(v.getConversationId())).toList();
        }

        @Override
        public void savePermissionAudit(PermissionAudit audit) {
            audits.add(audit);
        }
    }

    static class NoopEventPublisher implements IAgentRunEventPublisher {
        @Override
        public void publish(AgentRunEvent event) {
        }

        @Override
        public List<AgentRunEvent> history(String runId) {
            return List.of();
        }

        @Override
        public java.util.concurrent.BlockingQueue<AgentRunEvent> subscribe(String runId) {
            return new java.util.concurrent.LinkedBlockingQueue<>();
        }

        @Override
        public void unsubscribe(String runId, java.util.concurrent.BlockingQueue<AgentRunEvent> queue) {
        }
    }

    static class CountingModelGateway implements IModelGateway {
        private final ModelResponse response;
        private int calls;

        CountingModelGateway(ModelResponse response) {
            this.response = response;
        }

        @Override
        public ModelResponse call(ModelRequest request) {
            calls++;
            return response;
        }
    }

    record StubWorkspacePort(boolean missing) implements IWorkspacePort {
        @Override
        public Optional<WorkspaceDescriptor> resolve(String workspaceKey) {
            if (missing) {
                return Optional.empty();
            }
            return Optional.of(new WorkspaceDescriptor(workspaceKey, Path.of("E:/IdeaProjects/coder-agent").toAbsolutePath().normalize()));
        }

        @Override
        public Path resolveInside(WorkspaceDescriptor workspace, String relativePath) {
            return workspace.rootPath().resolve(relativePath).normalize();
        }
    }

    static class NoopToolGateway implements IToolGateway {
        @Override
        public List<ToolDefinition> definitions() {
            return List.of();
        }

        @Override
        public ToolResult execute(String runId, WorkspaceDescriptor workspace, ToolInvocation invocation) {
            return new ToolResult(CallStatus.SUCCESS, "ok", "ok", 0, null);
        }
    }

    static class StubArtifactPort implements IArtifactPort {
        @Override
        public List<RunArtifact> initializeRun(WorkspaceDescriptor workspace, AgentRun run) {
            return List.of(artifact(run.getRunId(), ArtifactType.RUN_META, ".coder/runs/" + run.getRunId() + "/run-meta.json"));
        }

        @Override
        public RunArtifact appendTrace(WorkspaceDescriptor workspace, String runId, Map<String, Object> event) {
            return artifact(runId, ArtifactType.TRACE, ".coder/runs/" + runId + "/trace.jsonl");
        }

        @Override
        public RunArtifact writeContextSnapshot(WorkspaceDescriptor workspace, String runId, int callNo, Map<String, Object> snapshot) {
            return artifact(runId, ArtifactType.CONTEXT_SNAPSHOT, ".coder/runs/" + runId + "/context-snapshot/" + callNo + ".json");
        }

        @Override
        public RunArtifact writeToolOutput(WorkspaceDescriptor workspace, String runId, int callNo, String output) {
            return artifact(runId, ArtifactType.TOOL_OUTPUT, ".coder/runs/" + runId + "/tool-output/" + callNo + ".txt");
        }

        @Override
        public RunArtifact writeFinalResult(WorkspaceDescriptor workspace, AgentRun run, Map<String, Object> result) {
            return artifact(run.getRunId(), ArtifactType.FINAL_RESULT, ".coder/runs/" + run.getRunId() + "/final-result.json");
        }

        @Override
        public List<Map<String, Object>> readTrace(WorkspaceDescriptor workspace, String runId) {
            return List.of();
        }

        private RunArtifact artifact(String runId, ArtifactType type, String path) {
            return RunArtifact.builder()
                    .runId(runId)
                    .artifactType(type)
                    .relativePath(path)
                    .fileSize(1L)
                    .createdAt(LocalDateTime.now())
                    .build();
        }
    }
}
