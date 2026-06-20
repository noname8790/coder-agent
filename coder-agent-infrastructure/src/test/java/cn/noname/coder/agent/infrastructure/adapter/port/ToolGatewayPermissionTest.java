package cn.noname.coder.agent.infrastructure.adapter.port;

import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.agent.model.valobj.AgentPermissionLevel;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolDefinition;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolInvocation;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolResult;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.domain.agent.adapter.port.IToolGovernancePort;
import cn.noname.coder.agent.infrastructure.tools.LocalTool;
import cn.noname.coder.agent.types.enums.AgentRunStatus;
import cn.noname.coder.agent.types.enums.CallStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ToolGatewayPermissionTest {

    @TempDir
    Path workspaceRoot;

    @Test
    void shouldRejectEditingToolGivenReadOnlyPermission() {
        // Given READ_ONLY 运行但请求编辑工具
        ToolGateway gateway = new ToolGateway(List.of(successTool("apply_patch")), noopGovernance());
        AgentRun run = run(AgentPermissionLevel.READ_ONLY);

        // When 调用 apply_patch / Then 被拒绝
        ToolResult result = gateway.execute(run, workspace(), new ToolInvocation("1", "apply_patch", "{}"));
        assertEquals(CallStatus.REJECTED, result.status());
        assertEquals("PERMISSION_REJECTED", result.errorMessage());
    }

    @Test
    void shouldAllowEditingToolGivenDefaultPermission() {
        // Given DEFAULT 运行
        ToolGateway gateway = new ToolGateway(List.of(successTool("apply_patch")), noopGovernance());

        // When 调用 apply_patch / Then 由权限等级放行
        ToolResult result = gateway.execute(run(AgentPermissionLevel.DEFAULT), workspace(),
                new ToolInvocation("1", "apply_patch", "{}"));
        assertEquals(CallStatus.SUCCESS, result.status());
    }

    @Test
    void shouldFilterDefinitionsByPermissionLevel() {
        // Given READ_ONLY 运行
        ToolGateway gateway = new ToolGateway(List.of(successTool("list_files"), successTool("apply_patch"), successTool("write_file")), noopGovernance());

        // When 获取工具定义
        List<String> names = gateway.definitions(run(AgentPermissionLevel.READ_ONLY), workspace())
                .stream().map(ToolDefinition::name).toList();

        // Then 编辑工具不可见
        assertTrue(names.contains("list_files"));
        assertFalse(names.contains("apply_patch"));
        assertFalse(names.contains("write_file"));
    }

    @Test
    void shouldRejectShellCommandGivenReadOnlyPermission() {
        // Given READ_ONLY 权限
        ToolGateway gateway = new ToolGateway(List.of(successTool("run_shell")), noopGovernance());

        // When 执行测试命令 / Then 被拒绝
        ToolResult result = gateway.execute(run(AgentPermissionLevel.READ_ONLY), workspace(),
                new ToolInvocation("1", "run_shell", "{\"command\":\"mvn test\"}"));
        assertEquals(CallStatus.REJECTED, result.status());
        assertEquals("PERMISSION_REJECTED", result.errorMessage());
    }

    @Test
    void shouldAllowDefaultGitWriteAndRejectPush() {
        // Given DEFAULT 权限
        ToolGateway gateway = new ToolGateway(List.of(successTool("run_shell")), noopGovernance());
        AgentRun run = run(AgentPermissionLevel.DEFAULT);

        // When 执行本地 commit / Then 允许；执行 push / Then 拒绝
        assertEquals(CallStatus.SUCCESS, gateway.execute(run, workspace(),
                new ToolInvocation("0", "run_shell", "{\"command\":\"git init\"}")).status());
        assertEquals(CallStatus.SUCCESS, gateway.execute(run, workspace(),
                new ToolInvocation("1", "run_shell", "{\"command\":\"git commit -m test\"}")).status());
        ToolResult push = gateway.execute(run, workspace(),
                new ToolInvocation("2", "run_shell", "{\"command\":\"git push\"}"));
        assertEquals(CallStatus.REJECTED, push.status());
        assertEquals("DANGEROUS_COMMAND", push.errorMessage());
    }

    @Test
    void shouldAllowLocalGitCleanupCommandsOutsideReadOnlyPermission() {
        // Given Git 清理/回滚命令属于本地仓库写入能力
        ToolGateway gateway = new ToolGateway(List.of(successTool("run_shell")), noopGovernance());

        // When READ_ONLY 执行本地 Git 清理命令 / Then 拒绝
        ToolResult readOnly = gateway.execute(run(AgentPermissionLevel.READ_ONLY), workspace(),
                new ToolInvocation("1", "run_shell", "{\"command\":\"git rm -r --cached .coder\"}"));
        assertEquals(CallStatus.REJECTED, readOnly.status());
        assertEquals("PERMISSION_REJECTED", readOnly.errorMessage());
        ToolResult restoreReadOnly = gateway.execute(run(AgentPermissionLevel.READ_ONLY), workspace(),
                new ToolInvocation("1b", "run_shell", "{\"command\":\"git restore --staged .coder\"}"));
        assertEquals(CallStatus.REJECTED, restoreReadOnly.status());
        assertEquals("PERMISSION_REJECTED", restoreReadOnly.errorMessage());

        // When DEFAULT/FULL_ACCESS 执行本地 Git 清理命令 / Then 交给后续审批或工具执行链路
        assertEquals(CallStatus.SUCCESS, gateway.execute(run(AgentPermissionLevel.DEFAULT), workspace(),
                new ToolInvocation("2", "run_shell", "{\"command\":\"git reset --soft HEAD~1\"}")).status());
        assertEquals(CallStatus.SUCCESS, gateway.execute(run(AgentPermissionLevel.DEFAULT), workspace(),
                new ToolInvocation("3", "run_shell", "{\"command\":\"git clean -fd .coder\"}")).status());
        assertEquals(CallStatus.SUCCESS, gateway.execute(run(AgentPermissionLevel.DEFAULT), workspace(),
                new ToolInvocation("3b", "run_shell", "{\"command\":\"git restore --staged .coder\"}")).status());
        assertEquals(CallStatus.SUCCESS, gateway.execute(run(AgentPermissionLevel.FULL_ACCESS), workspace(),
                new ToolInvocation("4", "run_shell", "{\"command\":\"git rm -r --cached .coder\"}")).status());
    }

    @Test
    void shouldEnsureCoderDirectoryIgnoredBeforeGitWriteCommand() throws Exception {
        // Given 已注册的活跃 workspace 还没有 .gitignore
        ToolGateway gateway = new ToolGateway(List.of(successTool("run_shell")), noopGovernance());

        // When Agent 执行 Git 写入命令
        ToolResult result = gateway.execute(run(AgentPermissionLevel.DEFAULT), workspace(),
                new ToolInvocation("1", "run_shell", "{\"command\":\"git add .\"}"));

        // Then 执行前会补齐 .coder 忽略规则，避免运行工件进入提交
        assertEquals(CallStatus.SUCCESS, result.status());
        assertTrue(Files.readString(workspaceRoot.resolve(".gitignore")).contains(".coder/"));
    }

    private AgentRun run(AgentPermissionLevel permissionLevel) {
        return AgentRun.builder()
                .runId("run_1")
                .permissionLevel(permissionLevel)
                .status(AgentRunStatus.RUNNING)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private WorkspaceDescriptor workspace() {
        return new WorkspaceDescriptor("repo", workspaceRoot);
    }

    private LocalTool successTool(String name) {
        return new LocalTool() {
            @Override
            public ToolDefinition definition() {
                return new ToolDefinition(name, name, java.util.Map.of());
            }

            @Override
            public ToolResult execute(String runId, WorkspaceDescriptor workspace, String argumentsJson) {
                return new ToolResult(CallStatus.SUCCESS, "ok", "ok", 0, null);
            }
        };
    }

    private IToolGovernancePort noopGovernance() {
        return new IToolGovernancePort() {
            @Override
            public ToolResult validateBeforeExecution(String runId, String workspaceKey, ToolInvocation invocation) {
                return null;
            }

            @Override
            public ToolResult sanitizeAfterExecution(String runId, String workspaceKey, ToolInvocation invocation, ToolResult result) {
                return result;
            }
        };
    }
}
