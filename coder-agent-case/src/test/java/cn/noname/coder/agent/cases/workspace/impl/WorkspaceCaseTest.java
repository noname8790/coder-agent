package cn.noname.coder.agent.cases.workspace.impl;

import cn.noname.coder.agent.api.dto.CreateWorkspaceRequestDTO;
import cn.noname.coder.agent.domain.agent.adapter.port.IVectorMemoryPort;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentConversationRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRecordRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRunRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IContextSnapshotRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IMemoryRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IToolApprovalRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IWorkspaceRepository;
import cn.noname.coder.agent.domain.agent.model.entity.AgentConversation;
import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.agent.model.entity.Workspace;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceStatus;
import cn.noname.coder.agent.types.exception.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkspaceCaseTest {

    @TempDir
    Path workspaceRoot;

    @Test
    void shouldCreateWorkspaceGivenValidAbsoluteDirectory() {
        // Given 一个存在的本地绝对目录
        InMemoryWorkspaceRepository repository = new InMemoryWorkspaceRepository();
        CreateWorkspaceCaseImpl createCase = new CreateWorkspaceCaseImpl(repository);

        // When 注册 workspace
        var response = createCase.create(new CreateWorkspaceRequestDTO("demo", workspaceRoot.toString()));

        // Then 保存规范化路径
        assertEquals("demo", response.workspaceKey());
        assertEquals(workspaceRoot.toAbsolutePath().normalize().toString(), response.rootPath());
        assertEquals("ACTIVE", response.status());
    }

    @Test
    void shouldEnsureCoderDirectoryIgnoredGivenWorkspaceCreated() throws Exception {
        // Given 一个没有 .gitignore 的本地 workspace
        InMemoryWorkspaceRepository repository = new InMemoryWorkspaceRepository();
        CreateWorkspaceCaseImpl createCase = new CreateWorkspaceCaseImpl(repository);

        // When 注册 workspace
        createCase.create(new CreateWorkspaceRequestDTO("demo", workspaceRoot.toString()));

        // Then 自动忽略 .coder 运行工件目录
        Path gitignore = workspaceRoot.resolve(".gitignore");
        assertTrue(Files.exists(gitignore));
        assertTrue(Files.readString(gitignore).lines().anyMatch(".coder/"::equals));
    }

    @Test
    void shouldRejectWorkspaceGivenRelativeOrMissingPath() {
        // Given workspace 注册用例
        CreateWorkspaceCaseImpl createCase = new CreateWorkspaceCaseImpl(new InMemoryWorkspaceRepository());

        // When / Then 相对路径或不存在路径被拒绝
        AppException relative = assertThrows(AppException.class,
                () -> createCase.create(new CreateWorkspaceRequestDTO("bad", "relative/path")));
        assertEquals("WORKSPACE_PATH_INVALID", relative.getCode());

        AppException missing = assertThrows(AppException.class,
                () -> createCase.create(new CreateWorkspaceRequestDTO("missing", workspaceRoot.resolve("missing").toString())));
        assertEquals("WORKSPACE_PATH_INVALID", missing.getCode());
    }

    @Test
    void shouldRejectWorkspaceGivenDuplicateKey() {
        // Given 已存在 active workspace
        InMemoryWorkspaceRepository repository = new InMemoryWorkspaceRepository();
        CreateWorkspaceCaseImpl createCase = new CreateWorkspaceCaseImpl(repository);
        createCase.create(new CreateWorkspaceRequestDTO("demo", workspaceRoot.toString()));

        // When 重复注册 / Then 拒绝
        AppException error = assertThrows(AppException.class,
                () -> createCase.create(new CreateWorkspaceRequestDTO("demo", workspaceRoot.toString())));
        assertEquals("WORKSPACE_ALREADY_EXISTS", error.getCode());
    }

    @Test
    void shouldDeactivateWorkspaceGivenActiveWorkspace() {
        // Given 已注册 workspace
        InMemoryWorkspaceRepository repository = new InMemoryWorkspaceRepository();
        CreateWorkspaceCaseImpl createCase = new CreateWorkspaceCaseImpl(repository);
        DeactivateWorkspaceCaseImpl deactivateCase = new DeactivateWorkspaceCaseImpl(repository);
        createCase.create(new CreateWorkspaceRequestDTO("demo", workspaceRoot.toString()));

        // When 停用 workspace
        var response = deactivateCase.deactivate("demo");

        // Then 逻辑停用后不再出现在 active 查询中
        assertEquals("INACTIVE", response.status());
        assertTrue(repository.findActiveByWorkspaceKey("demo").isEmpty());
    }

    @Test
    void shouldCleanupWorkspaceRelatedDataGivenWorkspaceDeleted() {
        // Given 一个已注册 workspace 以及关联运行和会话
        InMemoryWorkspaceRepository repository = new InMemoryWorkspaceRepository();
        Workspace workspace = Workspace.builder()
                .workspaceKey("demo")
                .rootPath(workspaceRoot)
                .status(WorkspaceStatus.ACTIVE)
                .build();
        repository.save(workspace);
        IAgentRunRepository runRepository = mock(IAgentRunRepository.class);
        IAgentConversationRepository conversationRepository = mock(IAgentConversationRepository.class);
        IAgentRecordRepository recordRepository = mock(IAgentRecordRepository.class);
        IContextSnapshotRepository contextSnapshotRepository = mock(IContextSnapshotRepository.class);
        IMemoryRepository memoryRepository = mock(IMemoryRepository.class);
        IVectorMemoryPort vectorMemoryPort = mock(IVectorMemoryPort.class);
        IToolApprovalRepository approvalRepository = mock(IToolApprovalRepository.class);
        when(runRepository.listByWorkspaceKey("demo")).thenReturn(List.of(
                AgentRun.builder().runId("run_1").workspaceKey("demo").build(),
                AgentRun.builder().runId("run_2").workspaceKey("demo").build()));
        when(conversationRepository.listConversations("demo")).thenReturn(List.of(
                AgentConversation.builder().conversationId("conv_1").workspaceKey("demo").build()));
        DeactivateWorkspaceCaseImpl deactivateCase = new DeactivateWorkspaceCaseImpl(repository, runRepository,
                conversationRepository, recordRepository, contextSnapshotRepository, memoryRepository,
                vectorMemoryPort, approvalRepository);

        // When 删除 workspace
        deactivateCase.deactivate("demo");

        // Then 关联运行、会话、上下文和记忆都被清理
        List<String> runIds = List.of("run_1", "run_2");
        verify(contextSnapshotRepository).deleteByRunIds(runIds);
        verify(memoryRepository).deleteByRunIds("demo", runIds);
        verify(vectorMemoryPort).deleteByRunIds("demo", runIds);
        verify(approvalRepository).deleteByRunIds(runIds);
        verify(recordRepository).deleteByRunIds(runIds);
        verify(runRepository).deleteByRunIds(runIds);
        verify(memoryRepository).deleteByWorkspaceKey("demo");
        verify(vectorMemoryPort).deleteByWorkspaceKey("demo");
        verify(conversationRepository).deleteMessagesByConversationId("conv_1");
        verify(conversationRepository).deleteConversation("conv_1");
    }

    @Test
    void shouldReactivateWorkspaceGivenSameKeyWasDeactivated() {
        // Given workspace 已被逻辑停用
        InMemoryWorkspaceRepository repository = new InMemoryWorkspaceRepository();
        CreateWorkspaceCaseImpl createCase = new CreateWorkspaceCaseImpl(repository);
        DeactivateWorkspaceCaseImpl deactivateCase = new DeactivateWorkspaceCaseImpl(repository);
        createCase.create(new CreateWorkspaceRequestDTO("demo", workspaceRoot.toString()));
        deactivateCase.deactivate("demo");

        // When 使用同一个 workspaceKey 再次注册
        var response = createCase.create(new CreateWorkspaceRequestDTO("demo", workspaceRoot.toString()));

        // Then 复用原记录并重新激活，避免唯一键冲突
        assertEquals("ACTIVE", response.status());
        assertNull(repository.findByWorkspaceKey("demo").orElseThrow().getDeletedAt());
    }

    @Test
    void shouldRejectWorkspaceGivenSameKeyStillActive() {
        // Given workspace 仍处于启用状态
        InMemoryWorkspaceRepository repository = new InMemoryWorkspaceRepository();
        CreateWorkspaceCaseImpl createCase = new CreateWorkspaceCaseImpl(repository);
        createCase.create(new CreateWorkspaceRequestDTO("demo", workspaceRoot.toString()));

        // When 使用同一个 workspaceKey 再次注册 / Then 拒绝
        AppException error = assertThrows(AppException.class,
                () -> createCase.create(new CreateWorkspaceRequestDTO("demo", workspaceRoot.toString())));
        assertEquals("WORKSPACE_ALREADY_EXISTS", error.getCode());
    }

    @Test
    void shouldRejectWorkspaceGivenFilePath() throws Exception {
        // Given 一个文件路径而非目录
        Path file = workspaceRoot.resolve("pom.xml");
        Files.writeString(file, "<project/>");
        CreateWorkspaceCaseImpl createCase = new CreateWorkspaceCaseImpl(new InMemoryWorkspaceRepository());

        // When / Then 注册被拒绝
        AppException error = assertThrows(AppException.class,
                () -> createCase.create(new CreateWorkspaceRequestDTO("file", file.toString())));
        assertEquals("WORKSPACE_PATH_INVALID", error.getCode());
    }

    static class InMemoryWorkspaceRepository implements IWorkspaceRepository {
        private final Map<String, Workspace> records = new LinkedHashMap<>();

        @Override
        public void save(Workspace workspace) {
            records.put(workspace.getWorkspaceKey(), workspace);
        }

        @Override
        public void update(Workspace workspace) {
            records.put(workspace.getWorkspaceKey(), workspace);
        }

        @Override
        public Optional<Workspace> findActiveByWorkspaceKey(String workspaceKey) {
            return Optional.ofNullable(records.get(workspaceKey))
                    .filter(v -> v.getStatus() == WorkspaceStatus.ACTIVE);
        }

        @Override
        public Optional<Workspace> findByWorkspaceKey(String workspaceKey) {
            return Optional.ofNullable(records.get(workspaceKey));
        }

        @Override
        public List<Workspace> listActive() {
            return records.values().stream().filter(v -> v.getStatus() == WorkspaceStatus.ACTIVE).toList();
        }

        @Override
        public boolean existsByWorkspaceKey(String workspaceKey) {
            return records.containsKey(workspaceKey);
        }
    }
}
