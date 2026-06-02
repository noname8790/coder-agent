package cn.noname.coder.agent.infrastructure.adapter.port;

import cn.noname.coder.agent.domain.agent.adapter.port.IWorkspacePort;
import cn.noname.coder.agent.domain.agent.adapter.repository.IWorkspaceRepository;
import cn.noname.coder.agent.domain.agent.model.entity.Workspace;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import cn.noname.coder.agent.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
@Slf4j
public class WorkspacePort implements IWorkspacePort {

    private static final Pattern WINDOWS_DRIVE_RELATIVE_PATH = Pattern.compile("^[A-Za-z]:[^/\\\\].*");

    private final AgentRuntimeProperties properties;
    private final Optional<IWorkspaceRepository> workspaceRepository;

    public WorkspacePort(AgentRuntimeProperties properties) {
        this(properties, Optional.empty());
    }

    @Autowired
    public WorkspacePort(AgentRuntimeProperties properties, Optional<IWorkspaceRepository> workspaceRepository) {
        this.properties = properties;
        this.workspaceRepository = workspaceRepository;
    }

    @Override
    public Optional<WorkspaceDescriptor> resolve(String workspaceKey) {
        Optional<WorkspaceDescriptor> registered = workspaceRepository
                .flatMap(repository -> repository.findActiveByWorkspaceKey(workspaceKey))
                .map(this::toDescriptor);
        if (registered.isPresent()) {
            log.info("解析 workspace 成功 source=database workspaceKey={} rootPath={}",
                    workspaceKey, registered.get().rootPath());
            return registered;
        }
        String path = properties.getWorkspaces().get(workspaceKey);
        if (path == null || path.isBlank()) {
            log.warn("解析 workspace 失败，未找到配置 workspaceKey={}", workspaceKey);
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
            log.info("解析 workspace 成功 source=config workspaceKey={} rootPath={}", workspaceKey, root);
            return Optional.of(new WorkspaceDescriptor(workspaceKey, root));
        } catch (InvalidPathException e) {
            throw new AppException("WORKSPACE_PATH_INVALID", "workspaceRoot 路径格式非法：" + workspaceKey);
        }
    }

    private WorkspaceDescriptor toDescriptor(Workspace workspace) {
        Path root = workspace.getRootPath().toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new AppException("WORKSPACE_PATH_INVALID", "workspaceRoot 不存在或不是目录：" + workspace.getWorkspaceKey());
        }
        return new WorkspaceDescriptor(workspace.getWorkspaceKey(), root);
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
