package cn.noname.coder.agent.domain.workspace.model.valobj;

import java.nio.file.Path;

/**
 * 工作区描述。
 */
public record WorkspaceDescriptor(String workspaceKey, Path rootPath) {
}
