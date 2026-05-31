package cn.noname.coder.agent.infrastructure.adapter.port;

import cn.noname.coder.agent.domain.agent.adapter.port.IWorkspacePort;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import cn.noname.coder.agent.types.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Workspace 解析和路径隔离实现。
 */
@Component
@RequiredArgsConstructor
public class WorkspacePort implements IWorkspacePort {

    private static final Pattern WINDOWS_DRIVE_RELATIVE_PATH = Pattern.compile("^[A-Za-z]:[^/\\\\].*");

    private final AgentRuntimeProperties properties;

    @Override
    public Optional<WorkspaceDescriptor> resolve(String workspaceKey) {
        String path = properties.getWorkspaces().get(workspaceKey);
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        if (WINDOWS_DRIVE_RELATIVE_PATH.matcher(path).matches()) {
            throw new AppException("WORKSPACE_PATH_INVALID", "workspaceRoot 不是合法绝对路径，请在 .env 中使用正斜杠或双反斜杠：" + workspaceKey);
        }
        try {
            Path root = Path.of(path).toAbsolutePath().normalize();
            if (!Files.isDirectory(root)) {
                throw new AppException("WORKSPACE_PATH_INVALID", "workspaceRoot 不存在或不是目录：" + workspaceKey);
            }
            return Optional.of(new WorkspaceDescriptor(workspaceKey, root));
        } catch (InvalidPathException e) {
            throw new AppException("WORKSPACE_PATH_INVALID", "workspaceRoot 路径格式非法：" + workspaceKey);
        }
    }

    @Override
    public Path resolveInside(WorkspaceDescriptor workspace, String relativePath) {
        String path = relativePath == null || relativePath.isBlank() ? "." : relativePath;
        Path root = workspace.rootPath().toAbsolutePath().normalize();
        Path resolved = root.resolve(path).normalize();
        if (!resolved.startsWith(root)) {
            throw new AppException("PATH_ESCAPE", "路径越界：" + relativePath);
        }
        return resolved;
    }
}
