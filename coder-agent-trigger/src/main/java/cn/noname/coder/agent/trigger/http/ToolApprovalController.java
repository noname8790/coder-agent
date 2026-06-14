package cn.noname.coder.agent.trigger.http;

import cn.noname.coder.agent.api.dto.DecideToolApprovalRequestDTO;
import cn.noname.coder.agent.api.dto.ToolApprovalListResponseDTO;
import cn.noname.coder.agent.api.dto.ToolApprovalResponseDTO;
import cn.noname.coder.agent.cases.approval.IToolApprovalCase;
import cn.noname.coder.agent.types.common.Response;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tool-approvals")
@RequiredArgsConstructor
public class ToolApprovalController {

    private final IToolApprovalCase toolApprovalCase;

    @GetMapping("/pending/{runId}")
    public Response<ToolApprovalListResponseDTO> listPending(@PathVariable("runId") String runId) {
        return Response.ok(toolApprovalCase.listPending(runId));
    }

    @PostMapping("/{approvalId}/approve")
    public Response<ToolApprovalResponseDTO> approve(@PathVariable("approvalId") String approvalId,
                                                     @RequestBody(required = false) DecideToolApprovalRequestDTO request) {
        return Response.ok(toolApprovalCase.approve(approvalId, request == null ? null : request.reason()));
    }

    @PostMapping("/{approvalId}/reject")
    public Response<ToolApprovalResponseDTO> reject(@PathVariable("approvalId") String approvalId,
                                                    @RequestBody(required = false) DecideToolApprovalRequestDTO request) {
        return Response.ok(toolApprovalCase.reject(approvalId, request == null ? null : request.reason()));
    }
}
