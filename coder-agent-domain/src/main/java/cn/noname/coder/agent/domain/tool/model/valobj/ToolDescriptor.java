package cn.noname.coder.agent.domain.tool.model.valobj;

import java.time.Duration;
import java.util.Map;

public record ToolDescriptor(
        String name,
        String description,
        Map<String, Object> schema,
        ToolRiskLevel riskLevel,
        AgentPermissionLevel requiredPermission,
        Duration timeout,
        ToolApprovalPolicy approvalPolicy,
        ToolResultEvidencePolicy resultEvidencePolicy
) {

    public ToolDefinition definition() {
        return new ToolDefinition(name, description, schema);
    }

    public static ToolDescriptor fromDefinition(ToolDefinition definition,
                                                ToolRiskLevel riskLevel,
                                                AgentPermissionLevel requiredPermission) {
        return new ToolDescriptor(
                definition.name(),
                definition.description(),
                definition.parameters(),
                riskLevel,
                requiredPermission,
                Duration.ofSeconds(120),
                ToolApprovalPolicy.HIGH_RISK_ONLY,
                ToolResultEvidencePolicy.STORE_INLINE);
    }
}
