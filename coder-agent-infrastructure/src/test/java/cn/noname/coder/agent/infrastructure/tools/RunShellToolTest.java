package cn.noname.coder.agent.infrastructure.tools;

import cn.noname.coder.agent.domain.workspace.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import cn.noname.coder.agent.types.enums.CallStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunShellToolTest {

    @TempDir
    Path workspaceRoot;

    @Test
    void shouldRejectDangerousTokenGivenChainedCommand() {
        // Given shell 工具使用默认危险 token 策略
        RunShellTool tool = new RunShellTool(new AgentRuntimeProperties());

        // When 模型请求链式危险命令
        var result = tool.execute("run_1", new WorkspaceDescriptor("repo", workspaceRoot),
                "{\"command\":\"git status && git push\"}");

        // Then 返回 REJECTED
        assertEquals(CallStatus.REJECTED, result.status());
        assertEquals("DANGEROUS_COMMAND", result.errorMessage());
    }

    @Test
    void shouldRejectCommandOutsideWhitelist() {
        // Given shell 工具使用默认白名单
        RunShellTool tool = new RunShellTool(new AgentRuntimeProperties());

        // When 模型请求非白名单命令
        var result = tool.execute("run_1", new WorkspaceDescriptor("repo", workspaceRoot),
                "{\"command\":\"whoami\"}");

        // Then 命令被拒绝
        assertEquals(CallStatus.REJECTED, result.status());
        assertEquals("COMMAND_NOT_ALLOWED", result.errorMessage());
    }

    @Test
    void shouldAllowGitInitGivenDefaultWhitelist() {
        // Given shell 工具使用默认白名单
        RunShellTool tool = new RunShellTool(new AgentRuntimeProperties());

        // When 模型请求初始化本地 Git 仓库
        var result = tool.execute("run_1", new WorkspaceDescriptor("repo", workspaceRoot),
                "{\"command\":\"git init\"}");

        // Then 命令可执行，并创建 .git 目录
        assertEquals(CallStatus.SUCCESS, result.status(), result.summary());
        assertTrue(Files.isDirectory(workspaceRoot.resolve(".git")));
    }

    @Test
    void shouldAllowLocalGitCleanupCommandsGivenDefaultWhitelist() {
        // Given shell 工具使用默认白名单和危险 token 策略
        RunShellTool tool = new RunShellTool(new AgentRuntimeProperties());

        // When 模型请求本地 Git 清理/回滚命令
        var reset = tool.execute("run_1", new WorkspaceDescriptor("repo", workspaceRoot),
                "{\"command\":\"git reset --soft HEAD~1\"}");
        var rm = tool.execute("run_1", new WorkspaceDescriptor("repo", workspaceRoot),
                "{\"command\":\"git rm -r --cached .coder\"}");
        var clean = tool.execute("run_1", new WorkspaceDescriptor("repo", workspaceRoot),
                "{\"command\":\"git clean -fd .coder\"}");

        // Then 命令进入执行层，不再被白名单或危险 token 策略提前拦截
        assertNotEquals(CallStatus.REJECTED, reset.status(), reset.summary());
        assertNotEquals("DANGEROUS_COMMAND", reset.errorMessage());
        assertNotEquals(CallStatus.REJECTED, rm.status(), rm.summary());
        assertNotEquals("DANGEROUS_COMMAND", rm.errorMessage());
        assertNotEquals(CallStatus.REJECTED, clean.status(), clean.summary());
        assertNotEquals("DANGEROUS_COMMAND", clean.errorMessage());
    }

    @Test
    void shouldAllowGitRestoreGivenDefaultWhitelist() {
        // Given shell 工具使用默认白名单和危险 token 策略
        RunShellTool tool = new RunShellTool(new AgentRuntimeProperties());

        // When 模型请求本地 Git restore 命令
        var restore = tool.execute("run_1", new WorkspaceDescriptor("repo", workspaceRoot),
                "{\"command\":\"git restore --staged .coder\"}");

        // Then 命令进入执行层，不被白名单提前拦截
        assertNotEquals(CallStatus.REJECTED, restore.status(), restore.summary());
        assertNotEquals("COMMAND_NOT_ALLOWED", restore.errorMessage());
        assertNotEquals("DANGEROUS_COMMAND", restore.errorMessage());
    }

    @Test
    void shouldAllowCdPrefixGivenCommandRunsInWorkspaceSubDirectory() throws Exception {
        // Given workspace 子目录存在
        Path subDir = Files.createDirectories(workspaceRoot.resolve("demo"));
        RunShellTool tool = new RunShellTool(new AgentRuntimeProperties());

        // When 使用 cd 前缀切换到子目录再执行白名单命令
        var result = tool.execute("run_1", new WorkspaceDescriptor("repo", workspaceRoot),
                "{\"command\":\"cd demo; java -version\"}");

        // Then 命令成功执行且未被危险 token 拦截
        assertEquals(CallStatus.SUCCESS, result.status());
        assertTrue(Files.isDirectory(subDir));
        assertTrue(result.summary().contains("version") || result.summary().contains("openjdk"));
    }

    @Test
    void shouldHandleCdOnlyWithoutStartingShellProcess() throws Exception {
        // Given workspace 子目录存在
        Files.createDirectories(workspaceRoot.resolve("demo"));
        RunShellTool tool = new RunShellTool(new AgentRuntimeProperties());

        // When 模型只请求切换目录
        var result = tool.execute("run_1", new WorkspaceDescriptor("repo", workspaceRoot),
                "{\"command\":\"cd demo\"}");

        // Then 工具直接返回目录确认结果，不进入 PowerShell 子进程
        assertEquals(CallStatus.SUCCESS, result.status());
        assertEquals(0, result.exitCode());
        assertTrue(result.summary().contains("demo"));
    }

    @Test
    void shouldReturnFailedGivenAllowedCommandExitsNonZero() {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.getTools().getAllowedCommandPrefixes().add("java -bad-option");
        RunShellTool tool = new RunShellTool(properties);

        var result = tool.execute("run_1", new WorkspaceDescriptor("repo", workspaceRoot),
                "{\"command\":\"java -bad-option\"}");

        assertEquals(CallStatus.FAILED, result.status());
        assertNotEquals(0, result.exitCode());
        assertTrue(result.summary().contains("Unrecognized option") || result.summary().contains("Error"));
    }

    @Test
    void shouldConsumeLargeOutputWhileProcessIsRunning() throws Exception {
        // Given 命令会输出大量内容，足以暴露 stdout 管道未及时消费导致的 waitFor 卡死问题
        Files.writeString(workspaceRoot.resolve("big-output.bat"),
                "@echo off\r\nfor /L %%i in (1,1,20000) do echo line %%i\r\n");
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.getTools().setShellTimeoutSeconds(15);
        properties.getTools().getAllowedCommandPrefixes().add("cmd /c");
        RunShellTool tool = new RunShellTool(properties);

        // When 执行大输出命令
        var result = tool.execute("run_1", new WorkspaceDescriptor("repo", workspaceRoot),
                "{\"command\":\"cmd /c big-output.bat\"}");

        // Then 命令正常结束，并且可以读取到尾部输出
        assertEquals(CallStatus.SUCCESS, result.status());
        assertEquals(0, result.exitCode());
        assertTrue(result.summary().contains("line 20000"));
    }
}
