package cn.noname.coder.agent.cases.agent.impl;

import cn.noname.coder.agent.api.dto.CreateAgentRunRequestDTO;
import cn.noname.coder.agent.cases.memory.MemoryService;
import cn.noname.coder.agent.domain.agent.adapter.port.IArtifactPort;
import cn.noname.coder.agent.domain.agent.adapter.port.IModelConfigPort;
import cn.noname.coder.agent.domain.agent.adapter.port.IWorkspacePort;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentConversationRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRecordRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRunRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IContextSnapshotRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IRunChangeRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IToolApprovalRepository;
import cn.noname.coder.agent.domain.agent.model.entity.AgentConversation;
import cn.noname.coder.agent.domain.agent.model.entity.AgentMessage;
import cn.noname.coder.agent.domain.agent.model.valobj.AgentPermissionLevel;
import cn.noname.coder.agent.domain.agent.model.valobj.ModelBackendConfig;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import cn.noname.coder.agent.types.enums.AgentRunStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.SyncTaskExecutor;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreateAgentRunCaseImplTest {

    @Test
    void shouldClearOldRunContextGivenSourceMessageRerun() {
        IWorkspacePort workspacePort = mock(IWorkspacePort.class);
        IAgentRunRepository runRepository = mock(IAgentRunRepository.class);
        IAgentRecordRepository recordRepository = mock(IAgentRecordRepository.class);
        IAgentConversationRepository conversationRepository = mock(IAgentConversationRepository.class);
        IArtifactPort artifactPort = mock(IArtifactPort.class);
        IModelConfigPort modelConfigPort = mock(IModelConfigPort.class);
        IContextSnapshotRepository contextSnapshotRepository = mock(IContextSnapshotRepository.class);
        IRunChangeRepository runChangeRepository = mock(IRunChangeRepository.class);
        MemoryService memoryService = mock(MemoryService.class);
        IToolApprovalRepository toolApprovalRepository = mock(IToolApprovalRepository.class);
        AgentRunExecutor executor = mock(AgentRunExecutor.class);
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.getBudget().setMaxConcurrentRuns(2);

        AgentConversation conversation = AgentConversation.builder()
                .conversationId("conv_1")
                .workspaceKey("demo")
                .defaultModel("glm-5")
                .lastPermissionLevel(AgentPermissionLevel.READ_ONLY)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        AgentMessage source = AgentMessage.builder()
                .messageId("msg_1")
                .conversationId("conv_1")
                .runId("run_old")
                .role("USER")
                .content("old task")
                .createdAt(LocalDateTime.now())
                .build();

        when(workspacePort.resolve("demo")).thenReturn(Optional.of(new WorkspaceDescriptor("demo", Path.of(".").toAbsolutePath())));
        when(runRepository.countByStatuses(List.of(AgentRunStatus.CREATED, AgentRunStatus.RUNNING, AgentRunStatus.WAITING_APPROVAL))).thenReturn(0L);
        when(conversationRepository.findConversation("conv_1")).thenReturn(Optional.of(conversation));
        when(conversationRepository.findMessage("msg_1")).thenReturn(Optional.of(source));
        when(modelConfigPort.resolve("glm-5")).thenReturn(Optional.of(new ModelBackendConfig("glm-5", "openai-compatible", "glm-5",
                "https://example.test/v1", "test-key", "chat-completions", 0.2, 60)));
        CreateAgentRunCaseImpl createCase = new CreateAgentRunCaseImpl(workspacePort, runRepository, recordRepository,
                conversationRepository, artifactPort, modelConfigPort, contextSnapshotRepository, runChangeRepository, memoryService,
                toolApprovalRepository, properties, executor, new SyncTaskExecutor());

        createCase.create(new CreateAgentRunRequestDTO("demo", "new task", "glm-5", "conv_1", "READ_ONLY", "msg_1"));

        ArgumentCaptor<AgentMessage> messageCaptor = ArgumentCaptor.forClass(AgentMessage.class);
        verify(conversationRepository).updateMessage(messageCaptor.capture());
        String newRunId = messageCaptor.getValue().getRunId();
        assertEquals("new task", messageCaptor.getValue().getContent());
        verify(conversationRepository).deleteAgentMessagesByRunId("run_old");
        verify(contextSnapshotRepository).deleteByRunIds(List.of("run_old"));
        verify(runChangeRepository).deleteByRunIds(List.of("run_old"));
        verify(memoryService).deleteRunMemories("demo", List.of("run_old"));
        verify(toolApprovalRepository).deleteByRunIds(List.of("run_old"));
        verify(recordRepository).deleteByRunIds(List.of("run_old"));
        verify(runRepository).deleteByRunIds(List.of("run_old"));
        verify(executor).execute(newRunId);
    }
}
