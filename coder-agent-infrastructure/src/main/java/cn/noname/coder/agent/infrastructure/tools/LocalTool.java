package cn.noname.coder.agent.infrastructure.tools;

import cn.noname.coder.agent.domain.agent.model.valobj.ToolDefinition;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolResult;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;

/**
 * 本地工具统一接口。
 */
public interface LocalTool {

    ToolDefinition definition();

    ToolResult execute(String runId, WorkspaceDescriptor workspace, String argumentsJson);
}
