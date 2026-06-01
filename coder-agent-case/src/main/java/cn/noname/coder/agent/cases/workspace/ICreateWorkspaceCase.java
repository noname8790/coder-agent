package cn.noname.coder.agent.cases.workspace;

import cn.noname.coder.agent.api.dto.CreateWorkspaceRequestDTO;
import cn.noname.coder.agent.api.dto.WorkspaceResponseDTO;

public interface ICreateWorkspaceCase {

    WorkspaceResponseDTO create(CreateWorkspaceRequestDTO request);
}
