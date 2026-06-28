package cn.noname.coder.agent.cases.workspace;

import cn.noname.coder.agent.api.dto.WorkspaceResponseDTO;
import cn.noname.coder.agent.domain.workspace.model.entity.Workspace;

/**
 * workspace DTO 组装器。
 */
public class WorkspaceAssembler {

    public WorkspaceResponseDTO toDTO(Workspace workspace) {
        return new WorkspaceResponseDTO(
                workspace.getWorkspaceKey(),
                workspace.getRootPath().toString(),
                workspace.getStatus().name(),
                workspace.getCreatedAt(),
                workspace.getUpdatedAt(),
                workspace.getDeletedAt()
        );
    }
}
