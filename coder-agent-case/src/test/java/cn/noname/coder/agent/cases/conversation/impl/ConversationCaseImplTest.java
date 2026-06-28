package cn.noname.coder.agent.cases.conversation.impl;

import cn.noname.coder.agent.cases.agent.DiffSummaryAssembler;
import cn.noname.coder.agent.api.dto.UpdateConversationRequestDTO;
import cn.noname.coder.agent.cases.memory.MemoryService;
import cn.noname.coder.agent.domain.model.adapter.port.IModelConfigPort;
import cn.noname.coder.agent.domain.memory.adapter.port.IVectorMemoryPort;
import cn.noname.coder.agent.domain.memory.adapter.repository.IMemoryRepository;
import cn.noname.coder.agent.domain.memory.model.entity.MemoryChunk;
import cn.noname.coder.agent.domain.memory.model.entity.MemoryItem;
import cn.noname.coder.agent.domain.memory.model.entity.MemoryRecall;
import cn.noname.coder.agent.domain.memory.model.valobj.MemorySearchHit;
import cn.noname.coder.agent.domain.memory.model.valobj.MemorySearchRequest;
import cn.noname.coder.agent.domain.context.adapter.repository.IContextSnapshotRepository;
import cn.noname.coder.agent.domain.context.model.entity.ContextSnapshot;
import cn.noname.coder.agent.domain.model.adapter.repository.IModelProviderRepository;
import cn.noname.coder.agent.domain.model.model.entity.ModelProvider;
import cn.noname.coder.agent.domain.model.model.valobj.EmbeddingResponse;
import cn.noname.coder.agent.domain.model.model.valobj.ModelBackendConfig;
import cn.noname.coder.agent.domain.tool.adapter.repository.IToolApprovalRepository;
import cn.noname.coder.agent.domain.tool.model.entity.PermissionAudit;
import cn.noname.coder.agent.domain.tool.model.entity.ToolApprovalRequest;
import cn.noname.coder.agent.domain.tool.model.entity.ToolCall;
import cn.noname.coder.agent.domain.tool.model.valobj.AgentPermissionLevel;
import cn.noname.coder.agent.domain.workspace.adapter.repository.IAgentConversationRepository;
import cn.noname.coder.agent.domain.workspace.adapter.repository.IRunChangeRepository;
import cn.noname.coder.agent.domain.workspace.adapter.port.IWorkspacePort;
import cn.noname.coder.agent.domain.workspace.model.entity.AgentConversation;
import cn.noname.coder.agent.domain.workspace.model.entity.AgentMessage;
import cn.noname.coder.agent.domain.workspace.model.entity.RunChangeSet;
import cn.noname.coder.agent.domain.workspace.model.entity.RunFileChange;
import cn.noname.coder.agent.domain.workspace.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.domain.agent.adapter.repository.*;
import cn.noname.coder.agent.domain.agent.model.entity.*;
import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import cn.noname.coder.agent.types.enums.AgentRunStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ConversationCaseImplTest {

    @Test
    void shouldUpdateConversationTitleGivenValidTitle() {
        // Given 已存在默认标题会话
        InMemoryConversationRepository conversationRepository = new InMemoryConversationRepository();
        ConversationCaseImpl useCase = new ConversationCaseImpl(
                conversationRepository,
                new InMemoryRunRepository(),
                new InMemoryContextSnapshotRepository(),
                null,
                new StubWorkspacePort(),
                new NoopModelConfigPort(),
                new InMemoryRecordRepository(),
                new InMemoryToolApprovalRepository(),
                new InMemoryRunChangeRepository(),
                new InMemoryModelProviderRepository(),
                org.mockito.Mockito.mock(cn.noname.coder.agent.domain.agent.adapter.port.IArtifactPort.class),
                org.mockito.Mockito.mock(DiffSummaryAssembler.class));
        conversationRepository.saveConversation(AgentConversation.builder()
                .conversationId("conv_1")
                .workspaceKey("repo-a")
                .title("新对话")
                .lastPermissionLevel(AgentPermissionLevel.DEFAULT)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        // When 更新标题
        useCase.update("conv_1", new UpdateConversationRequestDTO("阅读仓库结构"));

        // Then 标题持久化
        assertEquals("阅读仓库结构", conversationRepository.findConversation("conv_1").orElseThrow().getTitle());
    }

    @Test
    void shouldCascadeDeleteRunRecordsGivenConversationDeleted() {
        // Given 会话下存在两个运行
        InMemoryConversationRepository conversationRepository = new InMemoryConversationRepository();
        InMemoryRunRepository runRepository = new InMemoryRunRepository();
        InMemoryRecordRepository recordRepository = new InMemoryRecordRepository();
        InMemoryContextSnapshotRepository contextSnapshotRepository = new InMemoryContextSnapshotRepository();
        InMemoryToolApprovalRepository toolApprovalRepository = new InMemoryToolApprovalRepository();
        InMemoryRunChangeRepository runChangeRepository = new InMemoryRunChangeRepository();
        InMemoryModelProviderRepository modelProviderRepository = new InMemoryModelProviderRepository();
        RecordingMemoryRepository memoryRepository = new RecordingMemoryRepository();
        RecordingVectorMemoryPort vectorMemoryPort = new RecordingVectorMemoryPort();
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.getMemory().setEnabled(true);
        MemoryService memoryService = new MemoryService(
                memoryRepository,
                vectorMemoryPort,
                request -> new EmbeddingResponse("embedding", List.of(List.of(0.1, 0.2)), 1, 2),
                properties);
        ConversationCaseImpl useCase = new ConversationCaseImpl(
                conversationRepository,
                runRepository,
                contextSnapshotRepository,
                memoryService,
                new StubWorkspacePort(),
                new NoopModelConfigPort(),
                recordRepository,
                toolApprovalRepository,
                runChangeRepository,
                modelProviderRepository,
                org.mockito.Mockito.mock(cn.noname.coder.agent.domain.agent.adapter.port.IArtifactPort.class),
                org.mockito.Mockito.mock(DiffSummaryAssembler.class));
        AgentConversation conversation = AgentConversation.builder()
                .conversationId("conv_1")
                .workspaceKey("repo-a")
                .title("测试会话")
                .lastPermissionLevel(AgentPermissionLevel.READ_ONLY)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        conversationRepository.saveConversation(conversation);
        runRepository.save(newRun("run_1", "conv_1"));
        runRepository.save(newRun("run_2", "conv_1"));

        // When 删除会话
        useCase.delete("conv_1");

        // Then 所有关联 run 级数据都被清理
        assertEquals(List.of("run_1", "run_2"), runRepository.deletedRunIds);
        assertEquals(List.of("run_1", "run_2"), recordRepository.deletedRunIds);
        assertEquals(List.of("run_1", "run_2"), toolApprovalRepository.deletedRunIds);
        assertEquals(List.of("run_1", "run_2"), runChangeRepository.deletedRunIds);
        assertEquals(List.of("run_1", "run_2"), contextSnapshotRepository.deletedRunIds);
        assertEquals(List.of("run_1", "run_2"), memoryRepository.deletedRunIds);
        assertEquals(List.of("run_1", "run_2"), vectorMemoryPort.deletedRunIds);
    }

    private AgentRun newRun(String runId, String conversationId) {
        return AgentRun.builder()
                .runId(runId)
                .workspaceKey("repo-a")
                .conversationId(conversationId)
                .task("task")
                .model("model")
                .permissionLevel(AgentPermissionLevel.READ_ONLY)
                .status(AgentRunStatus.SUCCEEDED)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private static class InMemoryConversationRepository implements IAgentConversationRepository {
        private final Map<String, AgentConversation> conversations = new LinkedHashMap<>();

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
            return List.copyOf(conversations.values());
        }

        @Override
        public void deleteConversation(String conversationId) {
            conversations.remove(conversationId);
        }

        @Override
        public void saveMessage(AgentMessage message) {
        }

        @Override
        public Optional<AgentMessage> findMessage(String messageId) {
            return Optional.empty();
        }

        @Override
        public void updateMessage(AgentMessage message) {
        }

        @Override
        public void deleteMessage(String messageId) {
        }

        @Override
        public void deleteAgentMessagesByRunId(String runId) {
        }

        @Override
        public void deleteMessagesByRunId(String runId) {
        }

        @Override
        public void deleteMessagesByConversationId(String conversationId) {
        }

        @Override
        public List<AgentMessage> listMessages(String conversationId) {
            return List.of();
        }

        @Override
        public void savePermissionAudit(PermissionAudit audit) {
        }
    }

    private static class InMemoryRunRepository implements IAgentRunRepository {
        private final Map<String, AgentRun> runs = new LinkedHashMap<>();
        private final List<String> deletedRunIds = new ArrayList<>();

        @Override
        public void save(AgentRun run) {
            runs.put(run.getRunId(), run);
        }

        @Override
        public void update(AgentRun run) {
        }

        @Override
        public Optional<AgentRun> findByRunId(String runId) {
            return Optional.ofNullable(runs.get(runId));
        }

        @Override
        public List<AgentRun> listByConversationId(String conversationId) {
            return runs.values().stream()
                    .filter(run -> conversationId.equals(run.getConversationId()))
                    .toList();
        }

        @Override
        public void deleteByRunIds(Collection<String> runIds) {
            deletedRunIds.addAll(runIds);
        }

        @Override
        public long countByStatuses(Collection<AgentRunStatus> statuses) {
            return 0;
        }
    }

    private static class InMemoryRecordRepository implements IAgentRecordRepository {
        private final List<String> deletedRunIds = new ArrayList<>();

        @Override
        public void saveStep(AgentStep step) {
        }

        @Override
        public void saveModelCall(ModelCall modelCall) {
        }

        @Override
        public void saveToolCall(ToolCall toolCall) {
        }

        @Override
        public void saveAuditEvent(AuditEvent event) {
        }

        @Override
        public void saveArtifact(RunArtifact artifact) {
        }

        @Override
        public List<AuditEvent> listAuditEvents(String runId) {
            return List.of();
        }

        @Override
        public List<RunArtifact> listArtifacts(String runId) {
            return List.of();
        }

        @Override
        public void deleteByRunIds(Collection<String> runIds) {
            deletedRunIds.addAll(runIds);
        }
    }

    private static class InMemoryContextSnapshotRepository implements IContextSnapshotRepository {
        private final List<String> deletedRunIds = new ArrayList<>();

        @Override
        public void save(ContextSnapshot snapshot) {
        }

        @Override
        public List<ContextSnapshot> listByRunId(String runId) {
            return List.of();
        }

        @Override
        public void deleteByRunIds(Collection<String> runIds) {
            deletedRunIds.addAll(runIds);
        }
    }

    private static class InMemoryToolApprovalRepository implements IToolApprovalRepository {
        private final List<String> deletedRunIds = new ArrayList<>();

        @Override
        public void save(ToolApprovalRequest request) {
        }

        @Override
        public void update(ToolApprovalRequest request) {
        }

        @Override
        public Optional<ToolApprovalRequest> findByApprovalId(String approvalId) {
            return Optional.empty();
        }

        @Override
        public List<ToolApprovalRequest> listPending(String runId) {
            return List.of();
        }

        @Override
        public Optional<ToolApprovalRequest> findApproved(String runId, String toolName, String argumentsJson) {
            return Optional.empty();
        }

        @Override
        public List<ToolApprovalRequest> listRejectedPendingReturn(String runId) {
            return List.of();
        }

        @Override
        public void markReturned(String approvalId) {
        }

        @Override
        public void deleteByRunIds(Collection<String> runIds) {
            deletedRunIds.addAll(runIds);
        }
    }

    private static class InMemoryRunChangeRepository implements IRunChangeRepository {
        private final List<String> deletedRunIds = new ArrayList<>();

        @Override
        public void saveChangeSet(RunChangeSet changeSet, List<RunFileChange> files) {
        }

        @Override
        public void updateChangeSet(RunChangeSet changeSet) {
        }

        @Override
        public Optional<RunChangeSet> findChangeSet(String runId) {
            return Optional.empty();
        }

        @Override
        public List<RunFileChange> listFileChanges(String runId) {
            return List.of();
        }

        @Override
        public List<RunChangeSet> listByConversationId(String conversationId) {
            return List.of();
        }

        @Override
        public void deleteByRunIds(Collection<String> runIds) {
            deletedRunIds.addAll(runIds);
        }
    }

    private static class InMemoryModelProviderRepository implements IModelProviderRepository {
        @Override
        public void save(ModelProvider provider) {
        }

        @Override
        public void update(ModelProvider provider) {
        }

        @Override
        public Optional<ModelProvider> findByModelKey(String modelKey) {
            return Optional.empty();
        }

        @Override
        public Optional<ModelProvider> findDefaultEnabled() {
            return Optional.empty();
        }

        @Override
        public List<ModelProvider> listAll() {
            return List.of();
        }

        @Override
        public List<ModelProvider> listEnabled() {
            return List.of();
        }

        @Override
        public void clearDefaultModel() {
        }

        @Override
        public void deleteByModelKey(String modelKey) {
        }
    }

    private static class RecordingMemoryRepository implements IMemoryRepository {
        private final List<String> deletedRunIds = new ArrayList<>();

        @Override
        public void saveMemory(MemoryItem memory) {
        }

        @Override
        public void saveRecall(MemoryRecall recall) {
        }

        @Override
        public Optional<MemoryItem> findFreshFileMemory(String workspaceKey, String filePath, String contentHash) {
            return Optional.empty();
        }

        @Override
        public List<MemoryItem> listByWorkspace(String workspaceKey) {
            return List.of();
        }

        @Override
        public void markFileMemoriesStale(String workspaceKey, String filePath, String currentContentHash) {
        }

        @Override
        public void deleteByRunIds(String workspaceKey, Collection<String> runIds) {
            deletedRunIds.addAll(runIds);
        }
    }

    private static class RecordingVectorMemoryPort implements IVectorMemoryPort {
        private final List<String> deletedRunIds = new ArrayList<>();

        @Override
        public void saveChunk(MemoryChunk chunk) {
        }

        @Override
        public List<MemorySearchHit> search(MemorySearchRequest request) {
            return List.of();
        }

        @Override
        public void markStale(String workspaceKey, String memoryId) {
        }

        @Override
        public void deleteByRunIds(String workspaceKey, Collection<String> runIds) {
            deletedRunIds.addAll(runIds);
        }
    }

    private record NoopModelConfigPort() implements IModelConfigPort {
        @Override
        public ModelBackendConfig defaultModel() {
            return null;
        }

        @Override
        public Optional<ModelBackendConfig> resolve(String modelKey) {
            return Optional.empty();
        }
    }

    private record StubWorkspacePort() implements IWorkspacePort {
        @Override
        public Optional<WorkspaceDescriptor> resolve(String workspaceKey) {
            return Optional.of(new WorkspaceDescriptor(workspaceKey, java.nio.file.Path.of(".")));
        }

        @Override
        public java.nio.file.Path resolveInside(WorkspaceDescriptor workspace, String relativePath) {
            return workspace.rootPath().resolve(relativePath).normalize();
        }
    }
}
