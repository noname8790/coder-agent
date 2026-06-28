package cn.noname.coder.agent.domain.tool.adapter;

import cn.noname.coder.agent.domain.tool.model.valobj.ToolDescriptor;
import cn.noname.coder.agent.domain.tool.model.valobj.ToolInvocation;
import cn.noname.coder.agent.domain.tool.model.valobj.ToolResult;

public interface ToolGovernancePolicy {

    ToolResult validateBeforeExecution(String runId,
                                       String workspaceKey,
                                       ToolDescriptor descriptor,
                                       ToolInvocation invocation);

    ToolResult sanitizeAfterExecution(String runId,
                                      String workspaceKey,
                                      ToolDescriptor descriptor,
                                      ToolInvocation invocation,
                                      ToolResult result);
}
