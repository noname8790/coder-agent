package cn.noname.coder.agent.domain.agent.adapter.port;

import cn.noname.coder.agent.domain.agent.model.valobj.ToolDefinition;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolInvocation;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolResult;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;

import java.util.List;

/**
 * 工具网关统一注册和执行首版四类本地工具。
 */
public interface IToolGateway {

    List<ToolDefinition> definitions();

    ToolResult execute(String runId, WorkspaceDescriptor workspace, ToolInvocation invocation);
}
