package cn.noname.coder.agent.infrastructure.tools;

import cn.noname.coder.agent.domain.agent.model.valobj.ToolDefinition;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolResult;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.types.enums.CallStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class GitLogTool implements LocalTool {

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition("git_log", "查看最近 Git 提交。参数：limit，可选，默认 5。",
                Map.of("type", "object", "properties", Map.of("limit", Map.of("type", "integer"))));
    }

    @Override
    public ToolResult execute(String runId, WorkspaceDescriptor workspace, String argumentsJson) {
        int limit = Math.max(1, Math.min(20, ToolJson.integer(ToolJson.parse(argumentsJson), "limit", 5)));
        try {
            var result = GitToolSupport.git(workspace, Duration.ofSeconds(120), "log", "--oneline", "-n", String.valueOf(limit));
            return new ToolResult(result.status(), result.output(), result.output(), result.exitCode(), result.errorMessage());
        } catch (Exception e) {
            return new ToolResult(CallStatus.FAILED, "git_log 执行失败: " + e.getMessage(), "", 1, e.getMessage());
        }
    }
}
