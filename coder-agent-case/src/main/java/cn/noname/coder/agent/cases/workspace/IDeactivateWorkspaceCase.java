package cn.noname.coder.agent.cases.workspace;

import cn.noname.coder.agent.api.dto.WorkspaceResponseDTO;

public interface IDeactivateWorkspaceCase {

    WorkspaceResponseDTO deactivate(String workspaceKey);
}
