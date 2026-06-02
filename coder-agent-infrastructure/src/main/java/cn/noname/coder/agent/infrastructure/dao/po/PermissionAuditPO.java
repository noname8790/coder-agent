package cn.noname.coder.agent.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * agent_permission_audit 表持久化对象。
 */
@Data
@TableName("agent_permission_audit")
public class PermissionAuditPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String runId;
    private String conversationId;
    private String workspaceKey;
    private String permissionLevel;
    private String action;
    private String detail;
    private LocalDateTime createdAt;
}
