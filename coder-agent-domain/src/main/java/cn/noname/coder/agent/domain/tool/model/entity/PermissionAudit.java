package cn.noname.coder.agent.domain.tool.model.entity;

import cn.noname.coder.agent.domain.tool.model.valobj.AgentPermissionLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 权限等级选择、高风险能力使用和权限拒绝的结构化审计。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionAudit {
    private Long id;
    private String runId;
    private String conversationId;
    private String workspaceKey;
    private AgentPermissionLevel permissionLevel;
    private String action;
    private String detail;
    private LocalDateTime createdAt;
}
