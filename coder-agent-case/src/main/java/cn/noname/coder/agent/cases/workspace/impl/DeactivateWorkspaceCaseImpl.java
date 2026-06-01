package cn.noname.coder.agent.cases.workspace.impl;

import cn.noname.coder.agent.api.dto.WorkspaceResponseDTO;
import cn.noname.coder.agent.cases.workspace.IDeactivateWorkspaceCase;
import cn.noname.coder.agent.cases.workspace.WorkspaceAssembler;
import cn.noname.coder.agent.domain.agent.adapter.repository.IWorkspaceRepository;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceStatus;
import cn.noname.coder.agent.types.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeactivateWorkspaceCaseImpl implements IDeactivateWorkspaceCase {

    private final IWorkspaceRepository workspaceRepository;
    private final WorkspaceAssembler assembler = new WorkspaceAssembler();

    @Override
    public WorkspaceResponseDTO deactivate(String workspaceKey) {
        log.info("开始停用 workspace workspaceKey={}", workspaceKey);
        var workspace = workspaceRepository.findActiveByWorkspaceKey(workspaceKey)
                .orElseThrow(() -> new AppException("WORKSPACE_NOT_FOUND", "workspace 不存在或不可用：" + workspaceKey));
        LocalDateTime now = LocalDateTime.now();
        workspace.setStatus(WorkspaceStatus.INACTIVE);
        workspace.setUpdatedAt(now);
        workspace.setDeletedAt(now);
        workspaceRepository.update(workspace);
        log.info("已停用 workspace workspaceKey={} deletedAt={}", workspaceKey, now);
        return assembler.toDTO(workspace);
    }
}
