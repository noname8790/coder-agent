package cn.noname.coder.agent.infrastructure.tools;

import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;
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
}
