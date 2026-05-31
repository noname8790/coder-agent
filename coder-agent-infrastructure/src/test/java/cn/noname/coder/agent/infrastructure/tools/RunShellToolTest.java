package cn.noname.coder.agent.infrastructure.tools;

import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import cn.noname.coder.agent.types.enums.CallStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RunShellToolTest {

    @TempDir
    Path workspaceRoot;

    @Test
    void shouldRejectDangerousTokenGivenChainedCommand() {
        // Given Shell 工具使用默认危险 token 策略
        RunShellTool tool = new RunShellTool(new AgentRuntimeProperties());

        // When 模型请求链式命令
        var result = tool.execute("run_1", new WorkspaceDescriptor("repo", workspaceRoot), "{\"command\":\"git status && git push\"}");

        // Then 不启动本地进程并返回 REJECTED
        assertEquals(CallStatus.REJECTED, result.status());
        assertEquals("DANGEROUS_COMMAND", result.errorMessage());
    }

    @Test
    void shouldRejectCommandOutsideWhitelist() {
        // Given Shell 工具使用默认白名单
        RunShellTool tool = new RunShellTool(new AgentRuntimeProperties());

        // When 模型请求非白名单命令
        var result = tool.execute("run_1", new WorkspaceDescriptor("repo", workspaceRoot), "{\"command\":\"whoami\"}");

        // Then 命令被拒绝
        assertEquals(CallStatus.REJECTED, result.status());
        assertEquals("COMMAND_NOT_ALLOWED", result.errorMessage());
    }
}
