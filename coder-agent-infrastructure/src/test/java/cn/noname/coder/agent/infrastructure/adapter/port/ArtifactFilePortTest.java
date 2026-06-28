package cn.noname.coder.agent.infrastructure.adapter.port;

import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.tool.model.valobj.AgentPermissionLevel;
import cn.noname.coder.agent.domain.workspace.model.valobj.ChangedFile;
import cn.noname.coder.agent.domain.tool.model.valobj.TestCommandReport;
import cn.noname.coder.agent.domain.workspace.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.types.enums.AgentRunStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
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

    @Test
    void shouldWriteReviewArtifactsGivenChangedFilesAndTestReports() throws Exception {
        // Given 一次 EDIT 运行产生文件变更和测试报告
        ArtifactFilePort port = new ArtifactFilePort();
        WorkspaceDescriptor workspace = new WorkspaceDescriptor("repo", workspaceRoot);
        AgentRun run = AgentRun.builder()
                .runId("run_edit")
                .workspaceKey("repo")
                .task("修改代码")
                .model("test-model")
                .permissionLevel(AgentPermissionLevel.DEFAULT)
                .status(AgentRunStatus.SUCCEEDED)
                .createdAt(LocalDateTime.now())
                .build();

        // When 写入审查工件
        var artifacts = port.writeReviewArtifacts(workspace, run,
                List.of(new ChangedFile("src/App.java", "MODIFY", "old", "new", 1, "old", "new")),
                List.of(new TestCommandReport("mvn test", 0, 100L, "PASSED", "ok")));

        // Then diff、变更清单、测试报告和审查摘要都存在
        assertEquals(7, artifacts.size());
        assertTrue(Files.exists(workspaceRoot.resolve(".coder/runs/run_edit/patch.diff")));
        assertTrue(Files.exists(workspaceRoot.resolve(".coder/runs/run_edit/changed-files.json")));
        assertTrue(Files.exists(workspaceRoot.resolve(".coder/runs/run_edit/test-report.json")));
        assertTrue(Files.exists(workspaceRoot.resolve(".coder/runs/run_edit/review-summary.md")));
        assertTrue(Files.exists(workspaceRoot.resolve(".coder/runs/run_edit/pull-request.md")));
        String changedFilesJson = Files.readString(workspaceRoot.resolve(".coder/runs/run_edit/changed-files.json"));
        assertTrue(changedFilesJson.contains("\"addedLines\" : 1"));
        assertTrue(changedFilesJson.contains("\"deletedLines\" : 1"));
        assertTrue(changedFilesJson.contains("\"patchSnippet\""));
        assertTrue(changedFilesJson.contains("- 1     old"));
        assertTrue(changedFilesJson.contains("+ 1     new"));
    }

    @Test
    void shouldWriteOverwriteReviewArtifactsGivenWholeFileContentChanged() throws Exception {
        ArtifactFilePort port = new ArtifactFilePort();
        WorkspaceDescriptor workspace = new WorkspaceDescriptor("repo", workspaceRoot);
        AgentRun run = AgentRun.builder()
                .runId("run_overwrite")
                .workspaceKey("repo")
                .task("覆盖测试文件")
                .model("test-model")
                .permissionLevel(AgentPermissionLevel.DEFAULT)
                .status(AgentRunStatus.SUCCEEDED)
                .createdAt(LocalDateTime.now())
                .build();
        String before = String.join("\n",
                "package cn.noname.demo;",
                "",
                "class PowerCalculatorTest {",
                "    void oldTest() {}",
                "}");
        String after = String.join("\n",
                "package cn.noname.demo;",
                "",
                "class PowerCalculatorTest {",
                "    void newTest() {}",
                "    void anotherTest() {}",
                "}");

        port.writeReviewArtifacts(workspace, run,
                List.of(new ChangedFile("src/test/java/cn/noname/demo/PowerCalculatorTest.java",
                        "OVERWRITE", "old", "new", 1, before, after)),
                List.of());

        String changedFilesJson = Files.readString(workspaceRoot.resolve(".coder/runs/run_overwrite/changed-files.json"));
        assertTrue(changedFilesJson.contains("\"changeType\" : \"OVERWRITE\""));
        assertTrue(changedFilesJson.contains("\"addedLines\" : 2"));
        assertTrue(changedFilesJson.contains("\"deletedLines\" : 1"));
        assertTrue(changedFilesJson.contains("oldTest"));
        assertTrue(changedFilesJson.contains("newTest"));
        assertTrue(changedFilesJson.contains("anotherTest"), changedFilesJson);
    }
}
