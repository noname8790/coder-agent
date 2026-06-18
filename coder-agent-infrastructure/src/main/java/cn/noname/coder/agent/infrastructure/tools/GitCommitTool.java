package cn.noname.coder.agent.infrastructure.tools;

import cn.noname.coder.agent.domain.agent.model.valobj.ToolDefinition;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolResult;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.types.enums.CallStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class GitCommitTool implements LocalTool {

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition("git_commit", "创建本地 Git commit。参数：message。",
                Map.of("type", "object",
                        "properties", Map.of("message", Map.of("type", "string")),
                        "required", new String[]{"message"}));
    }

    @Override
    public ToolResult execute(String runId, WorkspaceDescriptor workspace, String argumentsJson) {
        String message = ToolJson.string(ToolJson.parse(argumentsJson), "message", "").trim();
        if (message.isBlank()) {
            return new ToolResult(CallStatus.REJECTED, "message 参数不能为空", "", 1, "INVALID_ARGUMENT");
        }
        try {
            var result = GitToolSupport.git(workspace, Duration.ofSeconds(60), "commit", "-m", message);
            return new ToolResult(result.status(), result.output(), result.output(), result.exitCode(), result.errorMessage());
        } catch (Exception e) {
            return new ToolResult(CallStatus.FAILED, "git_commit 执行失败: " + e.getMessage(), "", 1, e.getMessage());
        }
    }
}
