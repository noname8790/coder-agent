package cn.noname.coder.agent.domain.tool.adapter;

import cn.noname.coder.agent.domain.tool.model.valobj.ToolDescriptor;
import cn.noname.coder.agent.domain.tool.model.valobj.ToolDefinition;
import cn.noname.coder.agent.domain.tool.model.valobj.ToolResult;
import cn.noname.coder.agent.domain.workspace.model.valobj.WorkspaceDescriptor;

public interface ToolHandler {

    ToolDescriptor descriptor();

    default ToolDefinition definition() {
        return descriptor().definition();
    }

    ToolResult execute(String runId, WorkspaceDescriptor workspace, String argumentsJson);
}
