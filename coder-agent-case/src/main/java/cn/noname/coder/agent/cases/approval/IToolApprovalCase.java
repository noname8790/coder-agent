package cn.noname.coder.agent.cases.approval;

import cn.noname.coder.agent.api.dto.ToolApprovalListResponseDTO;
import cn.noname.coder.agent.api.dto.ToolApprovalResponseDTO;

public interface IToolApprovalCase {

    ToolApprovalListResponseDTO listPending(String runId);

    ToolApprovalResponseDTO approve(String approvalId, String reason);

    ToolApprovalResponseDTO reject(String approvalId, String reason);
}
