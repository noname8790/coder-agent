package cn.noname.coder.agent.cases.agent;

import cn.noname.coder.agent.domain.agent.adapter.port.IWorkspacePort;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRecordRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRunRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IRunChangeRepository;
import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.agent.model.entity.RunChangeSet;
import cn.noname.coder.agent.domain.agent.model.entity.RunFileChange;
import cn.noname.coder.agent.domain.agent.model.valobj.ChangedFile;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RunChangeServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldRecordRevertAndRestoreFileChange() throws Exception {
        // Given 一个已经被 Agent 修改过的文件
        Path file = tempDir.resolve("src/App.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "after");
        AgentRun run = AgentRun.builder()
                .runId("run_1")
                .workspaceKey("demo")
                .conversationId("conv_1")
                .build();
        WorkspaceDescriptor workspace = new WorkspaceDescriptor("demo", tempDir);
        InMemoryRunChangeRepository changeRepository = new InMemoryRunChangeRepository();
        IAgentRunRepository runRepository = mock(IAgentRunRepository.class);
        IWorkspacePort workspacePort = mock(IWorkspacePort.class);
        IAgentRecordRepository recordRepository = mock(IAgentRecordRepository.class);
        when(runRepository.findByRunId("run_1")).thenReturn(Optional.of(run));
        when(workspacePort.resolve("demo")).thenReturn(Optional.of(workspace));
        RunChangeService service = new RunChangeService(changeRepository, runRepository, workspacePort, recordRepository);

        // When 记录变更后执行撤销
        service.record(workspace, run, List.of(new ChangedFile("src/App.java", "MODIFY",
                null, null, 1, "before", "after")));
        var revert = service.revert("run_1");

        // Then 文件恢复为修改前内容
        assertThat(revert.status()).isEqualTo("REVERTED");
        assertThat(Files.readString(file)).isEqualTo("before");

        // When 再执行还原
        var restore = service.restore("run_1");

        // Then 文件恢复为 Agent 修改后的内容
        assertThat(restore.status()).isEqualTo("APPLIED");
        assertThat(Files.readString(file)).isEqualTo("after");
    }

    @Test
    void shouldDeleteAddedFileWhenRevertingAddChange() throws Exception {
        // Given 一个由 Agent 新增的文件
        Path file = tempDir.resolve("new-file.txt");
        Files.writeString(file, "created");
        AgentRun run = AgentRun.builder()
                .runId("run_add")
                .workspaceKey("demo")
                .conversationId("conv_1")
                .build();
        WorkspaceDescriptor workspace = new WorkspaceDescriptor("demo", tempDir);
        InMemoryRunChangeRepository changeRepository = new InMemoryRunChangeRepository();
        IAgentRunRepository runRepository = mock(IAgentRunRepository.class);
        IWorkspacePort workspacePort = mock(IWorkspacePort.class);
        IAgentRecordRepository recordRepository = mock(IAgentRecordRepository.class);
        when(runRepository.findByRunId("run_add")).thenReturn(Optional.of(run));
        when(workspacePort.resolve("demo")).thenReturn(Optional.of(workspace));
        RunChangeService service = new RunChangeService(changeRepository, runRepository, workspacePort, recordRepository);

        // When 记录新增文件并撤销
        service.record(workspace, run, List.of(new ChangedFile("new-file.txt", "ADD",
                null, null, 1, "", "created")));
        var revert = service.revert("run_add");

        // Then 新增文件被删除，而不是保留为空文件
        assertThat(revert.status()).isEqualTo("REVERTED");
        assertThat(file).doesNotExist();
    }

    private static class InMemoryRunChangeRepository implements IRunChangeRepository {

        private final Map<String, RunChangeSet> changeSets = new HashMap<>();
        private final Map<String, List<RunFileChange>> fileChanges = new HashMap<>();

        @Override
        public void saveChangeSet(RunChangeSet changeSet, List<RunFileChange> files) {
            changeSets.put(changeSet.getRunId(), changeSet);
            fileChanges.put(changeSet.getRunId(), files);
        }

        @Override
        public Optional<RunChangeSet> findChangeSet(String runId) {
            return Optional.ofNullable(changeSets.get(runId));
        }

        @Override
        public List<RunFileChange> listFileChanges(String runId) {
            return fileChanges.getOrDefault(runId, List.of());
        }

        @Override
        public List<RunChangeSet> listByConversationId(String conversationId) {
            return changeSets.values().stream()
                    .filter(changeSet -> conversationId.equals(changeSet.getConversationId()))
                    .toList();
        }

        @Override
        public void updateChangeSet(RunChangeSet changeSet) {
            changeSets.put(changeSet.getRunId(), changeSet);
        }
    }
}
