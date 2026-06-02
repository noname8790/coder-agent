package cn.noname.coder.agent.infrastructure.adapter.port;

import cn.noname.coder.agent.domain.agent.adapter.port.IToolGateway;
import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.agent.model.valobj.AgentPermissionLevel;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolDefinition;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolInvocation;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolResult;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.infrastructure.tools.LocalTool;
import cn.noname.coder.agent.infrastructure.tools.ToolJson;
import cn.noname.coder.agent.types.enums.CallStatus;
import cn.noname.coder.agent.types.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工具注册表和执行入口。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ToolGateway implements IToolGateway {

    private final List<LocalTool> localTools;

    @Override
    public List<ToolDefinition> definitions() {
        return localTools.stream().map(LocalTool::definition).toList();
    }

    @Override
    public List<ToolDefinition> definitions(AgentRun run, WorkspaceDescriptor workspace) {
        List<ToolDefinition> definitions = localTools.stream()
                .filter(tool -> canAdvertise(tool.definition().name(), run))
                .map(LocalTool::definition)
                .toList();
        log.info("组装工具定义 runId={} permissionLevel={} workspaceKey={} tools={}",
                run.getRunId(), permissionLevel(run), workspace.workspaceKey(),
                definitions.stream().map(ToolDefinition::name).toList());
        return definitions;
    }

    @Override
    public ToolResult execute(String runId, WorkspaceDescriptor workspace, ToolInvocation invocation) {
        return executeInternal(null, runId, workspace, invocation);
    }

    @Override
    public ToolResult execute(AgentRun run, WorkspaceDescriptor workspace, ToolInvocation invocation) {
        return executeInternal(run, run.getRunId(), workspace, invocation);
    }

    private ToolResult executeInternal(AgentRun run, String runId, WorkspaceDescriptor workspace, ToolInvocation invocation) {
        Map<String, LocalTool> registry = localTools.stream()
                .collect(Collectors.toMap(tool -> tool.definition().name(), Function.identity()));
        LocalTool tool = registry.get(invocation.name());
        if (tool == null) {
            log.warn("工具调用被拒绝 runId={} tool={} reason=UNKNOWN_TOOL", runId, invocation.name());
            return new ToolResult(CallStatus.REJECTED, "未知工具：" + invocation.name(), "", 1, "UNKNOWN_TOOL");
        }
        ToolResult rejected = rejectIfNotAllowed(run, invocation);
        if (rejected != null) {
            log.warn("工具调用被拒绝 runId={} tool={} code={} summary={}",
                    runId, invocation.name(), rejected.errorMessage(), rejected.summary());
            return rejected;
        }
        try {
            ToolResult result = tool.execute(runId, workspace, invocation.argumentsJson());
            log.info("工具网关执行完成 runId={} tool={} status={} exitCode={}",
                    runId, invocation.name(), result.status(), result.exitCode());
            return result;
        } catch (AppException e) {
            log.warn("工具调用被业务拒绝 runId={} tool={} code={} message={}",
                    runId, invocation.name(), e.getCode(), e.getMessage());
            return new ToolResult(CallStatus.REJECTED, e.getMessage(), "", 1, e.getCode());
        } catch (Exception e) {
            log.error("工具调用异常 runId={} tool={}", runId, invocation.name(), e);
            return new ToolResult(CallStatus.FAILED, "工具执行异常：" + e.getMessage(), "", 1, e.getMessage());
        }
    }

    private boolean canAdvertise(String toolName, AgentRun run) {
        if (run == null) {
            return true;
        }
        AgentPermissionLevel permissionLevel = permissionLevel(run);
        if (!permissionLevel.canAdvertise(toolName)) {
            return false;
        }
        if ("apply_patch".equals(toolName) || "write_file".equals(toolName)) {
            return permissionLevel.atLeast(AgentPermissionLevel.L2_SAFE_EDIT);
        }
        if (Set.of("overwrite_file", "delete_file", "generate_pr_draft").contains(toolName)) {
            return permissionLevel.atLeast(AgentPermissionLevel.L3_REPO_WRITE);
        }
        return true;
    }

    private ToolResult rejectIfNotAllowed(AgentRun run, ToolInvocation invocation) {
        if (run == null) {
            return null;
        }
        String toolName = invocation.name();
        AgentPermissionLevel permissionLevel = permissionLevel(run);
        if (!permissionLevel.canAdvertise(toolName)) {
            return rejected("当前权限等级禁止工具：" + toolName, "PERMISSION_REJECTED");
        }
        if ("run_shell".equals(toolName)) {
            return rejectShellIfNotAllowed(permissionLevel, invocation.argumentsJson());
        }
        return null;
    }

    private ToolResult rejectShellIfNotAllowed(AgentPermissionLevel permissionLevel, String argumentsJson) {
        String command = ToolJson.string(ToolJson.parse(argumentsJson), "command", "").trim().toLowerCase();
        if (command.startsWith("git push") || command.startsWith("git clean") || command.startsWith("git reset --hard")) {
            return rejected("第三版禁止高风险 Git 操作：" + command, "DANGEROUS_COMMAND");
        }
        if (isGitWriteCommand(command) && !permissionLevel.atLeast(AgentPermissionLevel.L3_REPO_WRITE)) {
            return rejected("当前权限等级禁止 Git 写入命令：" + command, "PERMISSION_REJECTED");
        }
        if (isTestCommand(command) && !permissionLevel.atLeast(AgentPermissionLevel.L2_SAFE_EDIT)) {
            return rejected("当前权限等级禁止运行测试", "PERMISSION_REJECTED");
        }
        if (isBuildCommand(command) && !permissionLevel.atLeast(AgentPermissionLevel.L2_SAFE_EDIT)) {
            return rejected("当前权限等级禁止运行构建", "PERMISSION_REJECTED");
        }
        return null;
    }

    private boolean isGitWriteCommand(String command) {
        return command.startsWith("git checkout -b")
                || command.startsWith("git add")
                || command.startsWith("git commit");
    }

    private boolean isTestCommand(String command) {
        return command.equals("mvn test")
                || command.equals("mvn -q test")
                || command.equals("mvn clean test")
                || (command.startsWith("mvn -pl ") && command.contains(" test"));
    }

    private boolean isBuildCommand(String command) {
        return command.equals("mvn package") || command.equals("mvn clean package")
                || (command.startsWith("mvn -pl ") && command.contains(" package"));
    }

    private ToolResult rejected(String message, String code) {
        return new ToolResult(CallStatus.REJECTED, message, "", 1, code);
    }

    private AgentPermissionLevel permissionLevel(AgentRun run) {
        return run.getPermissionLevel() == null ? AgentPermissionLevel.L1_READ_ONLY : run.getPermissionLevel();
    }
}
