package cn.noname.coder.agent.domain.agent.adapter.port;

import cn.noname.coder.agent.domain.agent.model.valobj.ToolInvocation;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolResult;

public interface IToolGovernancePort {

    ToolResult validateBeforeExecution(String runId, String workspaceKey, ToolInvocation invocation);

    ToolResult sanitizeAfterExecution(String runId, String workspaceKey, ToolInvocation invocation, ToolResult result);
}
