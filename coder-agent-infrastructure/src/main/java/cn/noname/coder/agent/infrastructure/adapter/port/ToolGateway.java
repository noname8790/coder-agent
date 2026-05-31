package cn.noname.coder.agent.infrastructure.adapter.port;

import cn.noname.coder.agent.domain.agent.adapter.port.IToolGateway;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolDefinition;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolInvocation;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolResult;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.infrastructure.tools.LocalTool;
import cn.noname.coder.agent.types.enums.CallStatus;
import cn.noname.coder.agent.types.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工具注册表和执行入口。
 */
@Component
@RequiredArgsConstructor
public class ToolGateway implements IToolGateway {

    private final List<LocalTool> localTools;

    @Override
    public List<ToolDefinition> definitions() {
        return localTools.stream().map(LocalTool::definition).toList();
    }

    @Override
    public ToolResult execute(String runId, WorkspaceDescriptor workspace, ToolInvocation invocation) {
        Map<String, LocalTool> registry = localTools.stream()
                .collect(Collectors.toMap(tool -> tool.definition().name(), Function.identity()));
        LocalTool tool = registry.get(invocation.name());
        if (tool == null) {
            return new ToolResult(CallStatus.REJECTED, "未知工具：" + invocation.name(), "", 1, "UNKNOWN_TOOL");
        }
        try {
            return tool.execute(runId, workspace, invocation.argumentsJson());
        } catch (AppException e) {
            return new ToolResult(CallStatus.REJECTED, e.getMessage(), "", 1, e.getCode());
        } catch (Exception e) {
            return new ToolResult(CallStatus.FAILED, "工具执行异常：" + e.getMessage(), "", 1, e.getMessage());
        }
    }
}
