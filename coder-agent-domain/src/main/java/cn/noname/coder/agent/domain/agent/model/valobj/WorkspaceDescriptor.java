package cn.noname.coder.agent.domain.agent.model.valobj;

import java.nio.file.Path;
import java.util.List;

/**
 * 工作区描述，API 只暴露 workspaceKey，内部携带能力用于工具权限判断。
 */
public record WorkspaceDescriptor(String workspaceKey, Path rootPath, List<WorkspaceCapability> capabilities) {

    public WorkspaceDescriptor(String workspaceKey, Path rootPath) {
        this(workspaceKey, rootPath, WorkspaceCapability.conservativeDefaults());
    }

    public boolean hasCapability(WorkspaceCapability capability) {
        return capabilities != null && capabilities.contains(capability);
    }
}
