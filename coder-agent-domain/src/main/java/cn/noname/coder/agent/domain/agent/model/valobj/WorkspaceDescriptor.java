package cn.noname.coder.agent.domain.agent.model.valobj;

import java.nio.file.Path;

/**
 * 服务端配置的工作区描述，API 只暴露 workspaceKey。
 */
public record WorkspaceDescriptor(String workspaceKey, Path rootPath) {
}
