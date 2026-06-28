package cn.noname.coder.agent.domain.workspace.adapter.port;

import cn.noname.coder.agent.domain.workspace.model.valobj.WorkspaceDescriptor;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Workspace 端口负责 workspaceKey 解析和路径边界校验。
 */
public interface IWorkspacePort {

    Optional<WorkspaceDescriptor> resolve(String workspaceKey);

    Path resolveInside(WorkspaceDescriptor workspace, String relativePath);
}
