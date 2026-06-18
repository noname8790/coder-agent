package cn.noname.coder.agent.infrastructure.tools;

import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import cn.noname.coder.agent.types.enums.CallStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RunShellToolSmokeTest {

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

    private AgentRuntimeProperties properties() {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.getTools().setShellTimeoutSeconds(120);
        return properties;
    }
}
