package cn.noname.coder.agent.cases.workspace.impl;

import cn.noname.coder.agent.api.dto.CreateWorkspaceRequestDTO;
import cn.noname.coder.agent.api.dto.WorkspaceResponseDTO;
import cn.noname.coder.agent.cases.workspace.ICreateWorkspaceCase;
import cn.noname.coder.agent.cases.workspace.WorkspaceAssembler;
import cn.noname.coder.agent.domain.agent.adapter.repository.IWorkspaceRepository;
import cn.noname.coder.agent.domain.agent.model.entity.Workspace;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceStatus;
import cn.noname.coder.agent.types.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 注册用户本地 workspace。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreateWorkspaceCaseImpl implements ICreateWorkspaceCase {

    private static final Pattern WINDOWS_DRIVE_RELATIVE_PATH = Pattern.compile("^[A-Za-z]:[^/\\\\].*");

    private final IWorkspaceRepository workspaceRepository;
    private final WorkspaceAssembler assembler = new WorkspaceAssembler();

    @Override
    public WorkspaceResponseDTO create(CreateWorkspaceRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.workspaceKey())) {
            throw new AppException("INVALID_ARGUMENT", "workspaceKey 不能为空");
        }
        log.info("开始注册 workspace workspaceKey={} rootPath={}", request.workspaceKey(), request.rootPath());
        var existing = workspaceRepository.findByWorkspaceKey(request.workspaceKey());
        if (existing.filter(workspace -> workspace.getStatus() == WorkspaceStatus.ACTIVE).isPresent()) {
            log.warn("注册 workspace 被拒绝，workspaceKey 已启用 workspaceKey={}", request.workspaceKey());
            throw new AppException("WORKSPACE_ALREADY_EXISTS", "workspaceKey 已存在：" + request.workspaceKey());
        }
        Path rootPath = normalizeRoot(request.rootPath());
        ensureCoderIgnored(rootPath);
        LocalDateTime now = LocalDateTime.now();
        if (existing.isPresent()) {
            Workspace workspace = existing.get();
            workspace.setRootPath(rootPath);
            workspace.setStatus(WorkspaceStatus.ACTIVE);
            workspace.setUpdatedAt(now);
            workspace.setDeletedAt(null);
            workspaceRepository.update(workspace);
            log.info("已重新激活 workspace workspaceKey={} rootPath={}",
                    workspace.getWorkspaceKey(), workspace.getRootPath());
            return assembler.toDTO(workspace);
        }
        Workspace workspace = Workspace.builder()
                .workspaceKey(request.workspaceKey())
                .rootPath(rootPath)
                .status(WorkspaceStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();
        workspaceRepository.save(workspace);
        log.info("已注册 workspace workspaceKey={} rootPath={}",
                workspace.getWorkspaceKey(), workspace.getRootPath());
        return assembler.toDTO(workspace);
    }

    private Path normalizeRoot(String rootPath) {
        if (!StringUtils.hasText(rootPath) || WINDOWS_DRIVE_RELATIVE_PATH.matcher(rootPath).matches()) {
            throw new AppException("WORKSPACE_PATH_INVALID", "rootPath 必须是合法本地绝对目录");
        }
        try {
            Path normalized = Path.of(rootPath).toAbsolutePath().normalize();
            if (!Path.of(rootPath).isAbsolute() || !Files.isDirectory(normalized)) {
                throw new AppException("WORKSPACE_PATH_INVALID", "rootPath 必须是存在的本地绝对目录");
            }
            return normalized;
        } catch (InvalidPathException e) {
            throw new AppException("WORKSPACE_PATH_INVALID", "rootPath 路径格式非法");
        }
    }

    private void ensureCoderIgnored(Path rootPath) {
        Path gitignore = rootPath.resolve(".gitignore");
        try {
            String existing = Files.exists(gitignore) ? Files.readString(gitignore, StandardCharsets.UTF_8) : "";
            boolean alreadyIgnored = existing.lines()
                    .map(String::trim)
                    .anyMatch(line -> ".coder/".equals(line) || ".coder".equals(line));
            if (alreadyIgnored) {
                return;
            }
            String separator = existing.isBlank() || existing.endsWith("\n") || existing.endsWith("\r\n")
                    ? ""
                    : System.lineSeparator();
            Files.writeString(gitignore, existing + separator + ".coder/" + System.lineSeparator(), StandardCharsets.UTF_8);
            log.info("已确保 workspace 忽略 .coder 运行工件 rootPath={}", rootPath);
        } catch (Exception e) {
            throw new AppException("WORKSPACE_GITIGNORE_UPDATE_FAILED", "写入 .gitignore 失败：" + e.getMessage());
        }
    }
}
