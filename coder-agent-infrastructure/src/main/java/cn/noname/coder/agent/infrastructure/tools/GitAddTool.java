package cn.noname.coder.agent.infrastructure.tools;

import cn.noname.coder.agent.domain.agent.model.valobj.ToolDefinition;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolResult;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.types.enums.CallStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class GitAddTool implements LocalTool {

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition("git_add", "执行 git add。参数：path，可选，默认 .。",
                Map.of("type", "object", "properties", Map.of("path", Map.of("type", "string"))));
    }

    @Override
    public ToolResult execute(String runId, WorkspaceDescriptor workspace, String argumentsJson) {
        String path = ToolJson.string(ToolJson.parse(argumentsJson), "path", ".").trim();
        if (path.contains("..") || targetsGitDirectory(path)) {
            return new ToolResult(CallStatus.REJECTED, "git_add 路径不允许: " + path, "", 1, "INVALID_ARGUMENT");
        }
        try {
            var result = GitToolSupport.git(workspace, Duration.ofSeconds(30), "add", "--", path);
            return new ToolResult(result.status(), result.output().isBlank() ? "git add 完成: " + path : result.output(),
                    result.output(), result.exitCode(), result.errorMessage());
        } catch (Exception e) {
            return new ToolResult(CallStatus.FAILED, "git_add 执行失败: " + e.getMessage(), "", 1, e.getMessage());
        }
    }

    private boolean targetsGitDirectory(String path) {
        String normalized = path.replace('\\', '/');
        return normalized.equals(".git")
                || normalized.startsWith(".git/")
                || normalized.contains("/.git/");
    }
}
