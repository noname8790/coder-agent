package cn.noname.coder.agent.domain.tool.adapter.port;

import cn.noname.coder.agent.domain.tool.model.valobj.ToolInvocation;
import cn.noname.coder.agent.domain.tool.model.valobj.ToolResult;

public interface IToolGovernancePort {

    ToolResult validateBeforeExecution(String runId, String workspaceKey, ToolInvocation invocation);

    ToolResult sanitizeAfterExecution(String runId, String workspaceKey, ToolInvocation invocation, ToolResult result);
}
