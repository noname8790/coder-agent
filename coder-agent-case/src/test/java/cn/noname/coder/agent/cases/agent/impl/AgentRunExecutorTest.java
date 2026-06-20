package cn.noname.coder.agent.cases.agent.impl;

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
import cn.noname.coder.agent.domain.agent.model.entity.RunArtifact;
import cn.noname.coder.agent.domain.agent.model.entity.ToolApprovalRequest;
import cn.noname.coder.agent.domain.agent.model.entity.ToolCall;
import cn.noname.coder.agent.domain.agent.model.valobj.AgentPermissionLevel;
import cn.noname.coder.agent.domain.agent.model.valobj.ContextAssemblyResult;
import cn.noname.coder.agent.domain.agent.model.valobj.ContextBudget;
import cn.noname.coder.agent.domain.agent.model.valobj.ContextCandidate;
import cn.noname.coder.agent.domain.agent.model.valobj.ModelBackendConfig;
import cn.noname.coder.agent.domain.agent.model.valobj.ModelRequest;
import cn.noname.coder.agent.domain.agent.model.valobj.ModelStreamEvent;
import cn.noname.coder.agent.domain.agent.model.valobj.ModelStreamEventType;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolInvocation;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolResult;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import cn.noname.coder.agent.types.enums.AgentRunStatus;
import cn.noname.coder.agent.types.enums.ArtifactType;
import cn.noname.coder.agent.types.enums.CallStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunExecutorTest {

    @Test
    void shouldSaveStreamingDraftAsFinalAgentMessageGivenModelReturnsAnswer() {
        TestHarness harness = new TestHarness(newRun("run_stream"));
        doAnswer(invocation -> {
            var consumer = invocation.<java.util.function.Consumer<ModelStreamEvent>>getArgument(1);
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.ASSISTANT_MESSAGE_STARTED, null, null, null, null, null));
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.ASSISTANT_DELTA, "hello ", null, null, null, null));
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.ASSISTANT_DELTA, "world", null, null, null, null));
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.MODEL_COMPLETED, null, null, null, null, null));
            return null;
        }).when(harness.streamingModelGateway).stream(any(), any());

        harness.executor.execute("run_stream");

        ArgumentCaptor<AgentMessage> messageCaptor = ArgumentCaptor.forClass(AgentMessage.class);
        verify(harness.conversationRepository).saveMessage(messageCaptor.capture());
        assertEquals("hello world", messageCaptor.getValue().getContent());
        assertEquals("", harness.draftService.content("run_stream"));
        assertEquals(AgentRunStatus.SUCCEEDED, harness.runRef.get().getStatus());
    }

    @Test
    void shouldKeepVisibleDraftGivenRunCancelledDuringStreaming() {
        TestHarness harness = new TestHarness(newRun("run_cancel"));
        doAnswer(invocation -> {
            var consumer = invocation.<java.util.function.Consumer<ModelStreamEvent>>getArgument(1);
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.ASSISTANT_MESSAGE_STARTED, null, null, null, null, null));
            harness.runRef.get().setStatus(AgentRunStatus.CANCELLED);
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.ASSISTANT_DELTA, "partial answer", null, null, null, null));
            return null;
        }).when(harness.streamingModelGateway).stream(any(), any());

        harness.executor.execute("run_cancel");

        ArgumentCaptor<AgentMessage> messageCaptor = ArgumentCaptor.forClass(AgentMessage.class);
        verify(harness.conversationRepository).saveMessage(messageCaptor.capture());
        assertEquals("partial answer", messageCaptor.getValue().getContent());
        assertEquals(AgentRunStatus.CANCELLED, harness.runRef.get().getStatus());
    }

    @Test
    void shouldNotPersistPlanningDraftGivenToolCallingLoopFailsByBudget() {
        AgentRun run = newRun("run_tool_loop");
        run.setMaxModelCalls(2);
        TestHarness harness = new TestHarness(run);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            var consumer = invocation.<java.util.function.Consumer<ModelStreamEvent>>getArgument(1);
            int call = calls.incrementAndGet();
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.ASSISTANT_MESSAGE_STARTED, null, null, null, null, null));
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.ASSISTANT_DELTA, "planning call " + call, null, null, null, null));
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.TOOL_CALL_STARTED, null, "tool_" + call, "read_file", null, null));
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.TOOL_CALL_ARGUMENTS_DELTA, null, "tool_" + call, "read_file",
                    "{\"path\":\"pom.xml\"}", null));
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.TOOL_CALL_COMPLETED, null, "tool_" + call, "read_file", null, null));
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.MODEL_COMPLETED, null, null, null, null, null));
            return null;
        }).when(harness.streamingModelGateway).stream(any(), any());
        when(harness.toolGateway.execute(any(AgentRun.class), any(), any()))
                .thenReturn(new ToolResult(CallStatus.SUCCESS, "ok", "ok", 0, null));

        harness.executor.execute("run_tool_loop");

        ArgumentCaptor<AgentMessage> messageCaptor = ArgumentCaptor.forClass(AgentMessage.class);
        verify(harness.conversationRepository).saveMessage(messageCaptor.capture());
        assertTrue(!messageCaptor.getValue().getContent().isBlank());
        assertEquals("", harness.draftService.content("run_tool_loop"));
        assertEquals(AgentRunStatus.FAILED, harness.runRef.get().getStatus());
        assertEquals(2, calls.get());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldProvideStructuredToolObservationGivenReadFileReturnsEmptyContent() {
        AgentRun run = newRun("run_empty_file");
        run.setMaxModelCalls(2);
        TestHarness harness = new TestHarness(run);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            var consumer = invocation.<java.util.function.Consumer<ModelStreamEvent>>getArgument(1);
            int call = calls.incrementAndGet();
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.ASSISTANT_MESSAGE_STARTED, null, null, null, null, null));
            if (call == 1) {
                consumer.accept(new ModelStreamEvent(ModelStreamEventType.TOOL_CALL_STARTED, null, "tool_1", "read_file", null, null));
                consumer.accept(new ModelStreamEvent(ModelStreamEventType.TOOL_CALL_ARGUMENTS_DELTA, null, "tool_1", "read_file",
                        "{\"path\":\"src/test/java/cn/noname/SimpleTest.java\"}", null));
            } else {
                consumer.accept(new ModelStreamEvent(ModelStreamEventType.ASSISTANT_DELTA, "empty file observed", null, null, null, null));
            }
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.MODEL_COMPLETED, null, null, null, null, null));
            return null;
        }).when(harness.streamingModelGateway).stream(any(), any());
        when(harness.toolGateway.execute(any(AgentRun.class), any(), any()))
                .thenReturn(new ToolResult(CallStatus.SUCCESS, "", "", 0, null));

        harness.executor.execute("run_empty_file");

        ArgumentCaptor<List<ContextCandidate>> candidatesCaptor = ArgumentCaptor.forClass(List.class);
        verify(harness.contextEngine, org.mockito.Mockito.atLeast(2)).assemble(candidatesCaptor.capture(), any());
        String secondCallContext = candidatesCaptor.getAllValues().get(1).stream()
                .map(ContextCandidate::content)
                .reduce("", (left, right) -> left + "\n" + right);
        assertTrue(secondCallContext.contains("TOOL_OBSERVATION"));
        assertTrue(secondCallContext.contains("tool=read_file"));
        assertTrue(secondCallContext.contains("\"path\":\"src/test/java/cn/noname/SimpleTest.java\""));
        assertTrue(secondCallContext.contains("status=SUCCESS"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldKeepLatestToolObservationRequiredGivenToolResultFeedsNextModelCall() {
        AgentRun run = newRun("run_required_tool_observation");
        run.setMaxModelCalls(2);
        TestHarness harness = new TestHarness(run);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            var consumer = invocation.<java.util.function.Consumer<ModelStreamEvent>>getArgument(1);
            int call = calls.incrementAndGet();
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.ASSISTANT_MESSAGE_STARTED, null, null, null, null, null));
            if (call == 1) {
                consumer.accept(new ModelStreamEvent(ModelStreamEventType.TOOL_CALL_STARTED, null, "tool_1", "list_files", null, null));
                consumer.accept(new ModelStreamEvent(ModelStreamEventType.TOOL_CALL_ARGUMENTS_DELTA, null, "tool_1", "list_files",
                        "{\"path\":\"E:\\\\IdeaProjects\\\\coder-agent\\\\sandbox-projects\\\\agent-test-demo\\\\src\\\\test\\\\java\"}", null));
            } else {
                consumer.accept(new ModelStreamEvent(ModelStreamEventType.ASSISTANT_DELTA, "saw directory result", null, null, null, null));
            }
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.MODEL_COMPLETED, null, null, null, null, null));
            return null;
        }).when(harness.streamingModelGateway).stream(any(), any());
        when(harness.toolGateway.execute(any(AgentRun.class), any(), any()))
                .thenReturn(new ToolResult(CallStatus.SUCCESS, "[D] cn\n[F] demoTest.java\n", "[D] cn\n[F] demoTest.java\n", 0, null));

        harness.executor.execute(run.getRunId());

        ArgumentCaptor<List<ContextCandidate>> candidatesCaptor = ArgumentCaptor.forClass(List.class);
        verify(harness.contextEngine, org.mockito.Mockito.atLeast(2)).assemble(candidatesCaptor.capture(), any());
        ContextCandidate toolObservation = candidatesCaptor.getAllValues().stream()
                .flatMap(List::stream)
                .filter(candidate -> candidate.content().contains("TOOL_OBSERVATION"))
                .findFirst()
                .orElseThrow();
        assertTrue(toolObservation.required());
        assertTrue(toolObservation.content().contains("工具调用") || toolObservation.content().contains("TOOL_OBSERVATION"));
        assertTrue(toolObservation.content().contains("相同参数") || toolObservation.content().contains("same"));
        assertTrue(toolObservation.content().contains("[F] demoTest.java"));
    }

    @Test
    void shouldSendStructuredToolProtocolGivenToolResultFeedsNextModelCall() {
        AgentRun run = newRun("run_tool_protocol");
        run.setMaxModelCalls(2);
        TestHarness harness = new TestHarness(run);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            var consumer = invocation.<java.util.function.Consumer<ModelStreamEvent>>getArgument(1);
            int call = calls.incrementAndGet();
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.ASSISTANT_MESSAGE_STARTED, null, null, null, null, null));
            if (call == 1) {
                consumer.accept(new ModelStreamEvent(ModelStreamEventType.TOOL_CALL_STARTED, null, "call_1", "list_files", null, null));
                consumer.accept(new ModelStreamEvent(ModelStreamEventType.TOOL_CALL_ARGUMENTS_DELTA, null, "call_1", "list_files",
                        "{\"path\":\"src/test/java\"}", null));
            } else {
                consumer.accept(new ModelStreamEvent(ModelStreamEventType.ASSISTANT_DELTA, "done", null, null, null, null));
            }
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.MODEL_COMPLETED, null, null, null, null, null));
            return null;
        }).when(harness.streamingModelGateway).stream(any(), any());
        when(harness.toolGateway.execute(any(AgentRun.class), any(), any()))
                .thenReturn(new ToolResult(CallStatus.SUCCESS, "[D] cn\n[F] demoTest.java", "[D] cn\n[F] demoTest.java", 0, null));

        harness.executor.execute(run.getRunId());

        ArgumentCaptor<ModelRequest> requestCaptor = ArgumentCaptor.forClass(ModelRequest.class);
        verify(harness.streamingModelGateway, org.mockito.Mockito.atLeast(2)).stream(requestCaptor.capture(), any());
        ModelRequest secondRequest = requestCaptor.getAllValues().get(1);
        assertEquals("assistant", secondRequest.protocolMessages().get(1).role());
        assertEquals("call_1", secondRequest.protocolMessages().get(1).toolCalls().getFirst().id());
        assertEquals("tool", secondRequest.protocolMessages().get(2).role());
        assertEquals("call_1", secondRequest.protocolMessages().get(2).toolCallId());
        assertTrue(secondRequest.protocolMessages().get(2).content().contains("[F] demoTest.java"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNotAppendDuplicateToolObservationGivenSameToolAndArgumentsRepeated() {
        AgentRun run = newRun("run_duplicate_observation");
        run.setMaxModelCalls(3);
        TestHarness harness = new TestHarness(run);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            var consumer = invocation.<java.util.function.Consumer<ModelStreamEvent>>getArgument(1);
            int call = calls.incrementAndGet();
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.ASSISTANT_MESSAGE_STARTED, null, null, null, null, null));
            if (call <= 2) {
                consumer.accept(new ModelStreamEvent(ModelStreamEventType.TOOL_CALL_STARTED, null, "tool_" + call, "read_file", null, null));
                consumer.accept(new ModelStreamEvent(ModelStreamEventType.TOOL_CALL_ARGUMENTS_DELTA, null, "tool_" + call, "read_file",
                        "{\"path\":\"src/test/java/cn/noname/SimpleTest.java\"}", null));
            } else {
                consumer.accept(new ModelStreamEvent(ModelStreamEventType.ASSISTANT_DELTA, "file content read", null, null, null, null));
            }
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.MODEL_COMPLETED, null, null, null, null, null));
            return null;
        }).when(harness.streamingModelGateway).stream(any(), any());
        when(harness.toolGateway.execute(any(AgentRun.class), any(), any()))
                .thenReturn(new ToolResult(CallStatus.SUCCESS, "文件内容：src/test/java/cn/noname/SimpleTest.java", "", 0, null));

        harness.executor.execute("run_duplicate_observation");

        ArgumentCaptor<List<ContextCandidate>> candidatesCaptor = ArgumentCaptor.forClass(List.class);
        verify(harness.contextEngine, org.mockito.Mockito.atLeast(2)).assemble(candidatesCaptor.capture(), any());
        long observationCount = candidatesCaptor.getAllValues().get(1).stream()
                .map(ContextCandidate::content)
                .filter(content -> content.contains("TOOL_OBSERVATION") && content.contains("tool=read_file"))
                .count();
        assertEquals(1, observationCount);
        assertEquals(3, calls.get());
    }

    @Test
    void shouldReusePendingApprovalGivenSameHighRiskToolRequestedAgain() {
        AgentRun run = newRun("run_pending_approval");
        run.setPermissionLevel(AgentPermissionLevel.DEFAULT);
        TestHarness harness = new TestHarness(run);
        doAnswer(invocation -> {
            var consumer = invocation.<java.util.function.Consumer<ModelStreamEvent>>getArgument(1);
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.ASSISTANT_MESSAGE_STARTED, null, null, null, null, null));
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.TOOL_CALL_STARTED, null, "tool_1", "delete_file", null, null));
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.TOOL_CALL_ARGUMENTS_DELTA, null, "tool_1", "delete_file",
                    "{\"path\":\"src\\\\test\\\\java\\\\cn\\\\noname\\\\SimpleTest.java\"}", null));
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.TOOL_CALL_COMPLETED, null, "tool_1", "delete_file", null, null));
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.MODEL_COMPLETED, null, null, null, null, null));
            return null;
        }).when(harness.streamingModelGateway).stream(any(), any());
        when(harness.toolApprovalRepository.findPending(eq("run_pending_approval"), eq("delete_file"),
                eq("{\"path\":\"src/test/java/cn/noname/SimpleTest.java\"}")))
                .thenReturn(Optional.of(ToolApprovalRequest.builder().approvalId("apv_1").riskSummary("delete file").status("PENDING").build()));

        harness.executor.execute("run_pending_approval");

        assertEquals(AgentRunStatus.WAITING_APPROVAL, harness.runRef.get().getStatus());
        verify(harness.toolApprovalRepository, never()).save(any());
    }

    @Test
    void shouldRecordToolCallGivenHighRiskToolWaitsApproval() {
        AgentRun run = newRun("run_waiting_approval_record");
        run.setPermissionLevel(AgentPermissionLevel.DEFAULT);
        TestHarness harness = new TestHarness(run);
        doAnswer(invocation -> {
            var consumer = invocation.<java.util.function.Consumer<ModelStreamEvent>>getArgument(1);
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.ASSISTANT_MESSAGE_STARTED, null, null, null, null, null));
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.TOOL_CALL_STARTED, null, "tool_1", "delete_file", null, null));
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.TOOL_CALL_ARGUMENTS_DELTA, null, "tool_1", "delete_file",
                    "{\"path\":\"src/test/java/cn/noname/SimpleTest.java\"}", null));
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.TOOL_CALL_COMPLETED, null, "tool_1", "delete_file", null, null));
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.MODEL_COMPLETED, null, null, null, null, null));
            return null;
        }).when(harness.streamingModelGateway).stream(any(), any());

        harness.executor.execute("run_waiting_approval_record");

        ArgumentCaptor<ToolCall> toolCallCaptor = ArgumentCaptor.forClass(ToolCall.class);
        verify(harness.recordRepository).saveToolCall(toolCallCaptor.capture());
        assertEquals("delete_file", toolCallCaptor.getValue().getToolName());
        assertEquals(CallStatus.WAITING_APPROVAL, toolCallCaptor.getValue().getStatus());
        assertEquals(AgentRunStatus.WAITING_APPROVAL, harness.runRef.get().getStatus());
    }

    @Test
    void shouldRequestApprovalGivenDefaultPermissionRunsGitCleanThroughShell() {
        AgentRun run = newRun("run_shell_git_clean_approval");
        run.setPermissionLevel(AgentPermissionLevel.DEFAULT);
        TestHarness harness = new TestHarness(run);
        doAnswer(invocation -> {
            var consumer = invocation.<java.util.function.Consumer<ModelStreamEvent>>getArgument(1);
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.ASSISTANT_MESSAGE_STARTED, null, null, null, null, null));
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.TOOL_CALL_STARTED, null, "tool_1", "run_shell", null, null));
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.TOOL_CALL_ARGUMENTS_DELTA, null, "tool_1", "run_shell",
                    "{\"command\":\"git clean -fd .coder\"}", null));
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.TOOL_CALL_COMPLETED, null, "tool_1", "run_shell", null, null));
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.MODEL_COMPLETED, null, null, null, null, null));
            return null;
        }).when(harness.streamingModelGateway).stream(any(), any());

        harness.executor.execute("run_shell_git_clean_approval");

        ArgumentCaptor<ToolApprovalRequest> approvalCaptor = ArgumentCaptor.forClass(ToolApprovalRequest.class);
        verify(harness.toolApprovalRepository).save(approvalCaptor.capture());
        assertEquals("run_shell", approvalCaptor.getValue().getToolName());
        assertEquals("{\"command\":\"git clean -fd .coder\"}", approvalCaptor.getValue().getArgumentsJson());
        assertEquals(AgentRunStatus.WAITING_APPROVAL, harness.runRef.get().getStatus());
        verify(harness.toolGateway, never()).execute(any(AgentRun.class), any(), any());
    }

    @Test
    void shouldExecuteApprovedToolBeforeNextModelCallGivenRunResumedFromApproval() {
        AgentRun run = newRun("run_approved_tool_resume");
        run.setPermissionLevel(AgentPermissionLevel.DEFAULT);
        TestHarness harness = new TestHarness(run);
        ToolApprovalRequest approval = ToolApprovalRequest.builder()
                .approvalId("apv_resume")
                .runId(run.getRunId())
                .workspaceKey(run.getWorkspaceKey())
                .toolName("delete_file")
                .argumentsJson("{\"path\":\"src/test/java/cn/noname/SimpleTest.java\"}")
                .status("APPROVED")
                .build();
        when(harness.toolApprovalRepository.listApprovedPendingExecution(run.getRunId()))
                .thenReturn(List.of(approval));
        when(harness.toolGateway.execute(any(AgentRun.class), any(), any()))
                .thenReturn(new ToolResult(CallStatus.SUCCESS, "已删除文件 src/test/java/cn/noname/SimpleTest.java", "", 0, null));
        doAnswer(invocation -> {
            var consumer = invocation.<java.util.function.Consumer<ModelStreamEvent>>getArgument(1);
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.ASSISTANT_MESSAGE_STARTED, null, null, null, null, null));
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.ASSISTANT_DELTA, "done", null, null, null, null));
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.MODEL_COMPLETED, null, null, null, null, null));
            return null;
        }).when(harness.streamingModelGateway).stream(any(), any());

        harness.executor.execute(run.getRunId());

        ArgumentCaptor<ToolInvocation> invocationCaptor = ArgumentCaptor.forClass(ToolInvocation.class);
        verify(harness.toolGateway).execute(any(AgentRun.class), any(), invocationCaptor.capture());
        assertEquals("delete_file", invocationCaptor.getValue().name());
        assertEquals("{\"path\":\"src/test/java/cn/noname/SimpleTest.java\"}", invocationCaptor.getValue().argumentsJson());
        verify(harness.toolApprovalRepository).markExecuted("apv_resume");
        assertEquals(AgentRunStatus.SUCCEEDED, harness.runRef.get().getStatus());
    }

    @Test
    void shouldFailGivenSameToolArgumentsRepeatedAfterSuccessfulObservation() {
        AgentRun run = newRun("run_repeated_tool_guard");
        run.setMaxModelCalls(5);
        TestHarness harness = new TestHarness(run);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            var consumer = invocation.<java.util.function.Consumer<ModelStreamEvent>>getArgument(1);
            int call = calls.incrementAndGet();
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.ASSISTANT_MESSAGE_STARTED, null, null, null, null, null));
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.ASSISTANT_DELTA, "call " + call, null, null, null, null));
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.TOOL_CALL_STARTED, null, "tool_" + call, "read_file", null, null));
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.TOOL_CALL_ARGUMENTS_DELTA, null, "tool_" + call, "read_file",
                    "{\"path\":\"src/test/java/cn/noname/SimpleTest.java\"}", null));
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.TOOL_CALL_COMPLETED, null, "tool_" + call, "read_file", null, null));
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.MODEL_COMPLETED, null, null, null, null, null));
            return null;
        }).when(harness.streamingModelGateway).stream(any(), any());
        when(harness.toolGateway.execute(any(AgentRun.class), any(), any()))
                .thenReturn(new ToolResult(CallStatus.SUCCESS, "读取成功", "", 0, null));

        harness.executor.execute("run_repeated_tool_guard");

        assertEquals(AgentRunStatus.FAILED, harness.runRef.get().getStatus());
        assertTrue(harness.runRef.get().getFailureReason().contains("重复工具调用")
                || harness.runRef.get().getFailureReason().contains("Repeated successful tool call cannot progress"));
        assertEquals(3, calls.get());
    }

    @Test
    void shouldFailImmediatelyGivenShellCommandTimeout() {
        AgentRun run = newRun("run_shell_timeout");
        run.setPermissionLevel(AgentPermissionLevel.DEFAULT);
        run.setMaxModelCalls(5);
        TestHarness harness = new TestHarness(run);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            calls.incrementAndGet();
            var consumer = invocation.<java.util.function.Consumer<ModelStreamEvent>>getArgument(1);
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.ASSISTANT_MESSAGE_STARTED, null, null, null, null, null));
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.TOOL_CALL_STARTED, null, "tool_1", "run_shell", null, null));
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.TOOL_CALL_ARGUMENTS_DELTA, null, "tool_1", "run_shell",
                    "{\"command\":\"mvn -q test\"}", null));
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.MODEL_COMPLETED, null, null, null, null, null));
            return null;
        }).when(harness.streamingModelGateway).stream(any(), any());
        when(harness.toolGateway.execute(any(AgentRun.class), any(), any()))
                .thenReturn(new ToolResult(CallStatus.FAILED, "命令超时: mvn -q test", "", 124, "TIMEOUT"));

        harness.executor.execute(run.getRunId());

        assertEquals(AgentRunStatus.FAILED, harness.runRef.get().getStatus());
        assertTrue(harness.runRef.get().getFailureReason().contains("Shell command timeout"));
        assertEquals(1, calls.get());
    }

    @Test
    void shouldFailGivenConsecutiveRejectedTools() {
        AgentRun run = newRun("run_rejected_tools");
        run.setPermissionLevel(AgentPermissionLevel.DEFAULT);
        run.setMaxModelCalls(5);
        TestHarness harness = new TestHarness(run);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            var consumer = invocation.<java.util.function.Consumer<ModelStreamEvent>>getArgument(1);
            int call = calls.incrementAndGet();
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.ASSISTANT_MESSAGE_STARTED, null, null, null, null, null));
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.TOOL_CALL_STARTED, null, "tool_" + call, "run_shell", null, null));
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.TOOL_CALL_ARGUMENTS_DELTA, null, "tool_" + call, "run_shell",
                    "{\"command\":\"bad-command-" + call + "\"}", null));
            consumer.accept(new ModelStreamEvent(ModelStreamEventType.MODEL_COMPLETED, null, null, null, null, null));
            return null;
        }).when(harness.streamingModelGateway).stream(any(), any());
        when(harness.toolGateway.execute(any(AgentRun.class), any(), any()))
                .thenReturn(new ToolResult(CallStatus.REJECTED, "command is not allowed", "", 1, "COMMAND_NOT_ALLOWED"));

        harness.executor.execute(run.getRunId());

        assertEquals(AgentRunStatus.FAILED, harness.runRef.get().getStatus());
        assertTrue(harness.runRef.get().getFailureReason().contains("Consecutive tool calls rejected"));
        assertEquals(2, calls.get());
    }

    private static AgentRun newRun(String runId) {
        return AgentRun.builder()
                .runId(runId)
                .workspaceKey("demo")
                .conversationId("conv_1")
                .task("test task")
                .model("glm-5")
                .status(AgentRunStatus.CREATED)
                .maxSteps(5)
                .maxModelCalls(5)
                .maxToolCalls(5)
                .timeoutSeconds(60)
                .stepCount(0)
                .modelCallCount(0)
                .toolCallCount(0)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private static RunArtifact artifact(String runId, ArtifactType type) {
        return RunArtifact.builder()
                .runId(runId)
                .artifactType(type)
                .relativePath(".coder/runs/" + runId + "/" + type.name().toLowerCase() + ".json")
                .fileSize(1L)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private static class TestHarness {
        private final AtomicReference<AgentRun> runRef;
        private final IStreamingModelGateway streamingModelGateway = mock(IStreamingModelGateway.class);
        private final IAgentConversationRepository conversationRepository = mock(IAgentConversationRepository.class);
        private final IToolGateway toolGateway = mock(IToolGateway.class);
        private final IToolApprovalRepository toolApprovalRepository = mock(IToolApprovalRepository.class);
        private final IAgentRecordRepository recordRepository = mock(IAgentRecordRepository.class);
        private final IContextEngine contextEngine = mock(IContextEngine.class);
        private final AgentRunDraftService draftService;
        private final AgentRunExecutor executor;

        private TestHarness(AgentRun run) {
            this.runRef = new AtomicReference<>(run);
            IAgentRunRepository runRepository = mock(IAgentRunRepository.class);
            IContextSnapshotRepository contextSnapshotRepository = mock(IContextSnapshotRepository.class);
            IWorkspacePort workspacePort = mock(IWorkspacePort.class);
            IModelConfigPort modelConfigPort = mock(IModelConfigPort.class);
            IArtifactPort artifactPort = mock(IArtifactPort.class);
            IAgentRunEventPublisher eventPublisher = mock(IAgentRunEventPublisher.class);
            ContextBudgetResolver budgetResolver = mock(ContextBudgetResolver.class);
            MemoryService memoryService = mock(MemoryService.class);
            RunChangeService runChangeService = mock(RunChangeService.class);
            this.draftService = new AgentRunDraftService(runRepository);

            when(runRepository.findByRunId(run.getRunId())).thenAnswer(ignored -> Optional.of(runRef.get()));
            when(runRepository.countByStatuses(any())).thenReturn(0L);
            when(workspacePort.resolve("demo")).thenReturn(Optional.of(new WorkspaceDescriptor("demo", Path.of(".").toAbsolutePath())));
            when(modelConfigPort.resolve(anyString())).thenReturn(Optional.of(modelConfig()));
            when(modelConfigPort.defaultModel()).thenReturn(modelConfig());
            when(toolGateway.definitions(any(), any())).thenReturn(List.of());
            when(toolApprovalRepository.listRejectedPendingReturn(anyString())).thenReturn(List.of());
            when(toolApprovalRepository.listApproved(anyString())).thenReturn(List.of());
            when(toolApprovalRepository.listApprovedPendingExecution(anyString())).thenReturn(List.of());
            when(toolApprovalRepository.findPending(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
            when(toolApprovalRepository.findApproved(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
            when(conversationRepository.listMessages("conv_1")).thenReturn(List.of());
            when(budgetResolver.resolve(any())).thenReturn(new ContextBudget(4096, 1024, 3000, 512, 512, 512, 512, 512));
            when(budgetResolver.budgetSource(any())).thenReturn("TEST");
            when(memoryService.recallForRun(any())).thenReturn(List.of());
            when(contextEngine.assemble(any(), any())).thenReturn(new ContextAssemblyResult(
                    List.of(), List.of(), List.of("system", "task"), 100, 80, 0.2, 0, 0, "TEST", null));
            when(artifactPort.appendTrace(any(), anyString(), any())).thenAnswer(invocation -> artifact(invocation.getArgument(1), ArtifactType.TRACE));
            when(artifactPort.writeContextSnapshot(any(), any(), anyInt(), any())).thenAnswer(invocation -> artifact(invocation.getArgument(1), ArtifactType.CONTEXT_SNAPSHOT));
            when(artifactPort.writeFinalResult(any(), any(), any())).thenAnswer(invocation -> artifact(((AgentRun) invocation.getArgument(1)).getRunId(), ArtifactType.FINAL_RESULT));
            when(artifactPort.writeReviewArtifacts(any(), any(), any(), any())).thenReturn(List.of());

            this.executor = new AgentRunExecutor(runRepository, recordRepository, contextSnapshotRepository, conversationRepository,
                    toolApprovalRepository, workspacePort, modelConfigPort, streamingModelGateway, toolGateway,
                    artifactPort, eventPublisher, contextEngine, budgetResolver, memoryService, draftService,
                    runChangeService, new AgentRuntimeProperties());
        }

        private ModelBackendConfig modelConfig() {
            return new ModelBackendConfig("glm-5", "openai-compatible", "glm-5",
                    "https://example.test/v1", "test-key", "chat-completions", 0.2, 60);
        }
    }
}
