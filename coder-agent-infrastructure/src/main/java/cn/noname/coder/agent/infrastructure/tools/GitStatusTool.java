package cn.noname.coder.agent.infrastructure.tools;

import cn.noname.coder.agent.domain.agent.model.valobj.ToolDefinition;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolResult;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.types.enums.CallStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class GitStatusTool implements LocalTool {

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition("git_status", "查看当前 workspace 的 Git 状态。无需参数。",
                Map.of("type", "object", "properties", Map.of()));
    }

    @Override
    public ToolResult execute(String runId, WorkspaceDescriptor workspace, String argumentsJson) {
        try {
            var result = GitToolSupport.git(workspace, Duration.ofSeconds(20), "status", "--short");
            String summary = result.output().isBlank() ? "Git 工作区干净。" : result.output();
            return new ToolResult(result.status(), summary, result.output(), result.exitCode(), result.errorMessage());
        } catch (Exception e) {
            return new ToolResult(CallStatus.FAILED,
                    "git_status 执行失败: " + e.getMessage(), "", 1, e.getMessage());
        }
    }
}
