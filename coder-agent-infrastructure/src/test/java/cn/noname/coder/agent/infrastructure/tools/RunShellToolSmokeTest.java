package cn.noname.coder.agent.infrastructure.tools;

import cn.noname.coder.agent.domain.workspace.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import cn.noname.coder.agent.types.enums.CallStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RunShellToolSmokeTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldExecuteCdGitAndMvnThroughRunShellToolGivenAgentTestDemoWorkspace() {
        // Given 本地实验项目存在
        Path workspaceRoot = Path.of("sandbox-projects", "agent-test-demo").toAbsolutePath().normalize();
        assumeTrue(Files.isDirectory(workspaceRoot), "sandbox-projects/agent-test-demo 不存在，跳过本地冒烟测试");
        RunShellTool tool = new RunShellTool(properties());
        WorkspaceDescriptor workspace = new WorkspaceDescriptor("agent-test-demo", workspaceRoot);

        // When/Then cd 命令只解析目录，不启动长时间子进程
        var cd = tool.execute("run_smoke", workspace, "{\"command\":\"cd .\"}");
        assertEquals(CallStatus.SUCCESS, cd.status());
        assertEquals(0, cd.exitCode());

        // When/Then git 命令可正常返回仓库状态
        var git = tool.execute("run_smoke", workspace, "{\"command\":\"git status\"}");
        assertEquals(CallStatus.SUCCESS, git.status());
        assertTrue(git.summary().contains("On branch") || git.summary().contains("位于分支"));

        // When/Then mvn test 通过工具执行，不因输出管道阻塞误超时
        var mvn = tool.execute("run_smoke", workspace, "{\"command\":\"mvn test\"}");
        assertEquals(CallStatus.SUCCESS, mvn.status(), mvn.summary());
        assertEquals(0, mvn.exitCode());
    }

    @Test
    void shouldInitializeGitRepositoryAndReadStatusThroughRunShellTool() {
        // Given 一个尚未初始化 Git 的本地 workspace
        Path workspaceRoot = tempDir.resolve("new-repo");
        assumeTrue(workspaceRoot.toFile().mkdirs() || Files.isDirectory(workspaceRoot));
        RunShellTool tool = new RunShellTool(properties());
        WorkspaceDescriptor workspace = new WorkspaceDescriptor("new-repo", workspaceRoot);

        // When 执行 git init
        var init = tool.execute("run_smoke", workspace, "{\"command\":\"git init\"}");

        // Then 初始化成功，后续标准 git status 可执行
        assertEquals(CallStatus.SUCCESS, init.status(), init.summary());
        assertTrue(Files.isDirectory(workspaceRoot.resolve(".git")));
        var status = tool.execute("run_smoke", workspace, "{\"command\":\"git status\"}");
        assertEquals(CallStatus.SUCCESS, status.status(), status.summary());
    }

    private AgentRuntimeProperties properties() {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.getTools().setShellTimeoutSeconds(120);
        return properties;
    }
}
