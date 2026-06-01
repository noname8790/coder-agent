package cn.noname.coder.agent.cases.workspace;

import cn.noname.coder.agent.api.dto.WorkspaceListResponseDTO;
import cn.noname.coder.agent.api.dto.WorkspaceResponseDTO;

public interface IQueryWorkspaceCase {

    WorkspaceListResponseDTO listActive();

    WorkspaceResponseDTO query(String workspaceKey);
}
