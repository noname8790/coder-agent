package cn.noname.coder.agent.trigger.http;

import cn.noname.coder.agent.api.dto.CreateWorkspaceRequestDTO;
import cn.noname.coder.agent.api.dto.WorkspaceListResponseDTO;
import cn.noname.coder.agent.api.dto.WorkspaceResponseDTO;
import cn.noname.coder.agent.cases.workspace.ICreateWorkspaceCase;
import cn.noname.coder.agent.cases.workspace.IDeactivateWorkspaceCase;
import cn.noname.coder.agent.cases.workspace.IQueryWorkspaceCase;
import cn.noname.coder.agent.types.common.Response;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * workspace 管理 API。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final ICreateWorkspaceCase createWorkspaceCase;
    private final IQueryWorkspaceCase queryWorkspaceCase;
    private final IDeactivateWorkspaceCase deactivateWorkspaceCase;

    @PostMapping
    public Response<WorkspaceResponseDTO> create(@RequestBody CreateWorkspaceRequestDTO request) {
        return Response.ok(createWorkspaceCase.create(request));
    }

    @GetMapping
    public Response<WorkspaceListResponseDTO> list() {
        return Response.ok(queryWorkspaceCase.listActive());
    }

    @GetMapping("/{workspaceKey}")
    public Response<WorkspaceResponseDTO> query(@PathVariable("workspaceKey") String workspaceKey) {
        return Response.ok(queryWorkspaceCase.query(workspaceKey));
    }

    @DeleteMapping("/{workspaceKey}")
    public Response<WorkspaceResponseDTO> deactivate(@PathVariable("workspaceKey") String workspaceKey) {
        return Response.ok(deactivateWorkspaceCase.deactivate(workspaceKey));
    }
}
