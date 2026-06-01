package cn.noname.coder.agent.infrastructure.adapter.port;

import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.agent.model.valobj.*;
import cn.noname.coder.agent.infrastructure.tools.LocalTool;
import cn.noname.coder.agent.types.enums.AgentRunStatus;
import cn.noname.coder.agent.types.enums.CallStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ToolGatewayPermissionTest {

    @TempDir
    Path workspaceRoot;

    @Test
    void shouldRejectEditingToolGivenReadOnlyMode() {
        // Given READ_ONLY 运行但请求编辑工具
        ToolGateway gateway = new ToolGateway(List.of(successTool("apply_patch")));
        AgentRun run = run(AgentRunMode.READ_ONLY);
        WorkspaceDescriptor workspace = workspace(WorkspaceCapability.MODIFY_FILE);

        // When 调用 apply_patch / Then 被拒绝
        ToolResult result = gateway.execute(run, workspace, new ToolInvocation("1", "apply_patch", "{}"));
        assertEquals(CallStatus.REJECTED, result.status());
        assertEquals("READ_ONLY_EDIT_REJECTED", result.errorMessage());
    }

    @Test
    void shouldRejectEditingToolGivenMissingCapability() {
        // Given EDIT 运行但 workspace 缺少 MODIFY_FILE
        ToolGateway gateway = new ToolGateway(List.of(successTool("apply_patch")));

        // When 调用 apply_patch / Then 被拒绝
        ToolResult result = gateway.execute(run(AgentRunMode.EDIT), workspace(WorkspaceCapability.READ_REPOSITORY),
                new ToolInvocation("1", "apply_patch", "{}"));
        assertEquals(CallStatus.REJECTED, result.status());
        assertEquals("CAPABILITY_REJECTED", result.errorMessage());
    }

    @Test
    void shouldFilterDefinitionsByModeAndCapability() {
        // Given READ_ONLY 运行
        ToolGateway gateway = new ToolGateway(List.of(successTool("list_files"), successTool("apply_patch"), successTool("write_file")));

        // When 获取工具定义
        List<String> names = gateway.definitions(run(AgentRunMode.READ_ONLY), workspace(WorkspaceCapability.READ_REPOSITORY, WorkspaceCapability.ADD_FILE, WorkspaceCapability.MODIFY_FILE))
                .stream().map(ToolDefinition::name).toList();

        // Then 编辑工具不可见
        assertTrue(names.contains("list_files"));
        assertFalse(names.contains("apply_patch"));
        assertFalse(names.contains("write_file"));
    }

    @Test
    void shouldRejectShellCommandGivenMissingCapability() {
        // Given workspace 没有 RUN_TEST
        ToolGateway gateway = new ToolGateway(List.of(successTool("run_shell")));

        // When 执行测试命令 / Then 被拒绝
        ToolResult result = gateway.execute(run(AgentRunMode.EDIT), workspace(WorkspaceCapability.GIT_READ),
                new ToolInvocation("1", "run_shell", "{\"command\":\"mvn test\"}"));
        assertEquals(CallStatus.REJECTED, result.status());
        assertEquals("CAPABILITY_REJECTED", result.errorMessage());
    }

    private AgentRun run(AgentRunMode mode) {
        return AgentRun.builder()
                .runId("run_1")
                .mode(mode)
                .status(AgentRunStatus.RUNNING)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private WorkspaceDescriptor workspace(WorkspaceCapability... capabilities) {
        return new WorkspaceDescriptor("repo", workspaceRoot, List.of(capabilities));
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
}
