package cn.noname.coder.agent.infrastructure.adapter.port;

import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.agent.model.valobj.AgentRunMode;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceCapability;
import cn.noname.coder.agent.domain.agent.adapter.port.IToolGateway;
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
                .filter(tool -> canAdvertise(tool.definition().name(), run, workspace))
                .map(LocalTool::definition)
                .toList();
        log.info("组装工具定义 runId={} mode={} workspaceKey={} tools={}",
                run.getRunId(), run.getMode(), workspace.workspaceKey(),
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
        ToolResult rejected = rejectIfNotAllowed(run, workspace, invocation);
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

    private boolean canAdvertise(String toolName, AgentRun run, WorkspaceDescriptor workspace) {
        if (run == null || workspace == null) {
            return true;
        }
        if (Set.of("list_files", "read_file", "search_text").contains(toolName)) {
            return workspace.hasCapability(WorkspaceCapability.READ_REPOSITORY);
        }
        if ("run_shell".equals(toolName)) {
            return workspace.hasCapability(WorkspaceCapability.GIT_READ)
                    || workspace.hasCapability(WorkspaceCapability.RUN_TEST)
                    || workspace.hasCapability(WorkspaceCapability.RUN_BUILD);
        }
        if ("apply_patch".equals(toolName)) {
            return run.getMode() == AgentRunMode.EDIT && workspace.hasCapability(WorkspaceCapability.MODIFY_FILE);
        }
        if ("write_file".equals(toolName)) {
            return run.getMode() == AgentRunMode.EDIT && workspace.hasCapability(WorkspaceCapability.ADD_FILE);
        }
        return true;
    }

    private ToolResult rejectIfNotAllowed(AgentRun run, WorkspaceDescriptor workspace, ToolInvocation invocation) {
        if (run == null) {
            return null;
        }
        String toolName = invocation.name();
        if (Set.of("list_files", "read_file", "search_text").contains(toolName)
                && !workspace.hasCapability(WorkspaceCapability.READ_REPOSITORY)) {
            return rejected("workspace 缺少读取仓库能力", "CAPABILITY_REJECTED");
        }
        if ("apply_patch".equals(toolName)) {
            if (run.getMode() != AgentRunMode.EDIT) {
                return rejected("READ_ONLY 模式禁止修改文件", "READ_ONLY_EDIT_REJECTED");
            }
            if (!workspace.hasCapability(WorkspaceCapability.MODIFY_FILE)) {
                return rejected("workspace 缺少修改文件能力", "CAPABILITY_REJECTED");
            }
        }
        if ("write_file".equals(toolName)) {
            if (run.getMode() != AgentRunMode.EDIT) {
                return rejected("READ_ONLY 模式禁止新增文件", "READ_ONLY_EDIT_REJECTED");
            }
            if (!workspace.hasCapability(WorkspaceCapability.ADD_FILE)) {
                return rejected("workspace 缺少新增文件能力", "CAPABILITY_REJECTED");
            }
        }
        if ("run_shell".equals(toolName)) {
            return rejectShellIfNotAllowed(workspace, invocation.argumentsJson());
        }
        return null;
    }

    private ToolResult rejectShellIfNotAllowed(WorkspaceDescriptor workspace, String argumentsJson) {
        String command = ToolJson.string(ToolJson.parse(argumentsJson), "command", "").trim().toLowerCase();
        if (command.startsWith("git commit") || command.startsWith("git push") || command.startsWith("git reset")
                || command.startsWith("git branch")) {
            return rejected("第二版禁止高风险 Git 操作：" + command, "DANGEROUS_COMMAND");
        }
        if ((command.equals("git status") || command.startsWith("git diff") || command.startsWith("git log"))
                && !workspace.hasCapability(WorkspaceCapability.GIT_READ)) {
            return rejected("workspace 缺少 Git 只读能力", "CAPABILITY_REJECTED");
        }
        if (isTestCommand(command) && !workspace.hasCapability(WorkspaceCapability.RUN_TEST)) {
            return rejected("workspace 缺少运行测试能力", "CAPABILITY_REJECTED");
        }
        if (isBuildCommand(command) && !workspace.hasCapability(WorkspaceCapability.RUN_BUILD)) {
            return rejected("workspace 缺少运行构建能力", "CAPABILITY_REJECTED");
        }
        return null;
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
}
