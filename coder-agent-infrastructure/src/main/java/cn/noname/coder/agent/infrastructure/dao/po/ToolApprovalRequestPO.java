package cn.noname.coder.agent.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tool_approval_request")
public class ToolApprovalRequestPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String approvalId;
    private String runId;
    private String workspaceKey;
    private String toolName;
    private String argumentsJson;
    private String riskSummary;
    private String diffSummary;
    private String status;
    private LocalDateTime requestedAt;
    private LocalDateTime decidedAt;
    private String decisionReason;
}
