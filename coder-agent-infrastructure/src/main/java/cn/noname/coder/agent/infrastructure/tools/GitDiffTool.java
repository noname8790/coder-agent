package cn.noname.coder.agent.infrastructure.tools;

import cn.noname.coder.agent.domain.agent.model.valobj.ToolDefinition;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolResult;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.types.enums.CallStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class GitDiffTool implements LocalTool {

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition("git_diff", "查看当前 workspace 相对 HEAD 的 Git diff。无需参数。",
                Map.of("type", "object", "properties", Map.of()));
    }

    @Override
    public ToolResult execute(String runId, WorkspaceDescriptor workspace, String argumentsJson) {
        try {
            var result = GitToolSupport.git(workspace, Duration.ofSeconds(120), "diff", "--stat");
            String summary = result.output().isBlank() ? "没有未提交 diff。" : result.output();
            var full = GitToolSupport.git(workspace, Duration.ofSeconds(120), "diff", "--", ".");
            return new ToolResult(result.status(), summary, full.output(), result.exitCode(), result.errorMessage(),
                    GitToolSupport.changedFiles(workspace), null);
        } catch (Exception e) {
            return new ToolResult(CallStatus.FAILED, "git_diff 执行失败: " + e.getMessage(), "", 1, e.getMessage());
        }
    }
}
