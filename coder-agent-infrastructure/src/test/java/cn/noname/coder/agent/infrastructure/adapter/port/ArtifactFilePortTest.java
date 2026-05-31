package cn.noname.coder.agent.infrastructure.adapter.port;

import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.types.enums.AgentRunStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ArtifactFilePortTest {

    @TempDir
    Path workspaceRoot;

    @Test
    void shouldAppendReadableJsonlGivenTraceEvent() throws Exception {
        // Given 已初始化运行工件目录
        ArtifactFilePort port = new ArtifactFilePort();
        WorkspaceDescriptor workspace = new WorkspaceDescriptor("repo", workspaceRoot);
        AgentRun run = AgentRun.builder()
                .runId("run_test")
                .workspaceKey("repo")
                .task("检查仓库")
                .model("test-model")
                .status(AgentRunStatus.CREATED)
                .createdAt(LocalDateTime.now())
                .build();
        port.initializeRun(workspace, run);

        // When 追加 trace 事件
        port.appendTrace(workspace, run.getRunId(), Map.of("type", "model_call", "status", "SUCCESS"));

        // Then JSONL 可逐行解析
        var events = port.readTrace(workspace, run.getRunId());
        assertEquals(1, events.size());
        assertEquals("model_call", events.get(0).get("type"));
    }

    @Test
    void shouldWriteFinalResultGivenTerminalRun() throws Exception {
        // Given 终态运行
        ArtifactFilePort port = new ArtifactFilePort();
        WorkspaceDescriptor workspace = new WorkspaceDescriptor("repo", workspaceRoot);
        AgentRun run = AgentRun.builder()
                .runId("run_final")
                .workspaceKey("repo")
                .status(AgentRunStatus.SUCCEEDED)
                .createdAt(LocalDateTime.now())
                .build();

        // When 写入 final-result.json
        port.writeFinalResult(workspace, run, Map.of("status", "SUCCEEDED", "attempts", 1));

        // Then 文件存在
        assertTrue(Files.exists(workspaceRoot.resolve(".coder/runs/run_final/final-result.json")));
    }
}
