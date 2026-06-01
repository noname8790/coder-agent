package cn.noname.coder.agent.cases.workspace.impl;

import cn.noname.coder.agent.api.dto.WorkspaceListResponseDTO;
import cn.noname.coder.agent.api.dto.WorkspaceResponseDTO;
import cn.noname.coder.agent.cases.workspace.IQueryWorkspaceCase;
import cn.noname.coder.agent.cases.workspace.WorkspaceAssembler;
import cn.noname.coder.agent.domain.agent.adapter.repository.IWorkspaceRepository;
import cn.noname.coder.agent.types.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueryWorkspaceCaseImpl implements IQueryWorkspaceCase {

    private final IWorkspaceRepository workspaceRepository;
    private final WorkspaceAssembler assembler = new WorkspaceAssembler();

    @Override
    public WorkspaceListResponseDTO listActive() {
        var workspaces = workspaceRepository.listActive().stream().map(assembler::toDTO).toList();
        log.info("查询 active workspace 列表 count={}", workspaces.size());
        return new WorkspaceListResponseDTO(workspaces);
    }

    @Override
    public WorkspaceResponseDTO query(String workspaceKey) {
        log.info("查询 workspace 详情 workspaceKey={}", workspaceKey);
        return workspaceRepository.findActiveByWorkspaceKey(workspaceKey)
                .map(assembler::toDTO)
                .orElseThrow(() -> new AppException("WORKSPACE_NOT_FOUND", "workspace 不存在或不可用：" + workspaceKey));
    }
}
