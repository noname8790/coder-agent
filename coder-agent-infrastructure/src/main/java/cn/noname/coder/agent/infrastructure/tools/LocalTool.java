package cn.noname.coder.agent.infrastructure.tools;

import cn.noname.coder.agent.domain.tool.adapter.ToolHandler;
import cn.noname.coder.agent.domain.tool.model.valobj.AgentPermissionLevel;
import cn.noname.coder.agent.domain.tool.model.valobj.ToolDescriptor;
import cn.noname.coder.agent.domain.tool.model.valobj.ToolDefinition;
import cn.noname.coder.agent.domain.tool.model.valobj.ToolRiskLevel;
import cn.noname.coder.agent.domain.tool.model.valobj.ToolResult;

/**
 * 本地工具统一接口。
 */
public interface LocalTool extends ToolHandler {

    ToolDefinition definition();

    @Override
    default ToolDescriptor descriptor() {
        return ToolDescriptor.fromDefinition(definition(), riskLevel(), requiredPermission());
    }

    default ToolRiskLevel riskLevel() {
        return GitOperationStrategy.fromToolName(definition().name())
                .map(GitOperationStrategy::riskLevel)
                .orElseGet(() -> switch (definition().name()) {
            case "write_file", "apply_patch" -> ToolRiskLevel.SAFE_WRITE;
            case "overwrite_file", "delete_file", "run_shell" -> ToolRiskLevel.HIGH_RISK;
            default -> ToolRiskLevel.READ_ONLY;
        });
    }

    default AgentPermissionLevel requiredPermission() {
        return switch (riskLevel()) {
            case READ_ONLY -> AgentPermissionLevel.READ_ONLY;
            case SAFE_WRITE, HIGH_RISK -> AgentPermissionLevel.DEFAULT;
        };
    }
}
