package cn.noname.coder.agent.infrastructure.adapter.port;

import cn.noname.coder.agent.domain.tool.adapter.ToolGovernancePolicy;
import cn.noname.coder.agent.domain.tool.adapter.port.IToolGovernancePort;
import cn.noname.coder.agent.domain.tool.model.valobj.ToolDescriptor;
import cn.noname.coder.agent.domain.tool.model.valobj.ToolInvocation;
import cn.noname.coder.agent.domain.tool.model.valobj.ToolResult;
import cn.noname.coder.agent.infrastructure.tools.ToolJson;
import cn.noname.coder.agent.types.enums.CallStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class DefaultToolGovernancePort implements IToolGovernancePort, ToolGovernancePolicy {

    private static final Set<String> PATH_TOOLS = Set.of("read_file", "write_file", "apply_patch", "overwrite_file", "delete_file");

    @Override
    public ToolResult validateBeforeExecution(String runId, String workspaceKey, ToolInvocation invocation) {
        if (invocation == null || !StringUtils.hasText(invocation.name())) {
            return rejected("工具名不能为空", "INVALID_ARGUMENT");
        }
        Map<String, Object> args = ToolJson.parse(invocation.argumentsJson());
        ToolResult schemaRejected = validateSchema(invocation.name(), args);
        if (schemaRejected != null) {
            return schemaRejected;
        }
        ToolResult pathRejected = validatePath(invocation.name(), args);
        if (pathRejected != null) {
            return pathRejected;
        }
        return null;
    }

    @Override
    public ToolResult validateBeforeExecution(String runId,
                                              String workspaceKey,
                                              ToolDescriptor descriptor,
                                              ToolInvocation invocation) {
        return validateBeforeExecution(runId, workspaceKey, invocation);
    }

    @Override
    public ToolResult sanitizeAfterExecution(String runId, String workspaceKey, ToolInvocation invocation, ToolResult result) {
        if (result == null) {
            return null;
        }
        return new ToolResult(result.status(), redact(result.summary()), redact(result.fullOutput()),
                result.exitCode(), redact(result.errorMessage()), result.changedFiles(), result.testReport());
    }

    @Override
    public ToolResult sanitizeAfterExecution(String runId,
                                             String workspaceKey,
                                             ToolDescriptor descriptor,
                                             ToolInvocation invocation,
                                             ToolResult result) {
        return sanitizeAfterExecution(runId, workspaceKey, invocation, result);
    }

    private ToolResult validateSchema(String toolName, Map<String, Object> args) {
        if (PATH_TOOLS.contains(toolName) && !StringUtils.hasText(ToolJson.string(args, "path", ""))) {
            return rejected("path 参数不能为空", "INVALID_ARGUMENT");
        }
        if ("search_text".equals(toolName) && !StringUtils.hasText(ToolJson.string(args, "query", ""))) {
            return rejected("query 参数不能为空", "INVALID_ARGUMENT");
        }
        if ("run_shell".equals(toolName) && !StringUtils.hasText(ToolJson.string(args, "command", ""))) {
            return rejected("command 参数不能为空", "INVALID_ARGUMENT");
        }
        if (("write_file".equals(toolName) || "overwrite_file".equals(toolName))
                && !StringUtils.hasText(ToolJson.string(args, "content", ""))) {
            return rejected("content 参数不能为空", "INVALID_ARGUMENT");
        }
        return null;
    }

    private ToolResult validatePath(String toolName, Map<String, Object> args) {
        if (!PATH_TOOLS.contains(toolName)) {
            return null;
        }
        String path = ToolJson.string(args, "path", "").replace("\\", "/").toLowerCase();
        if (path.contains("../") || path.startsWith("..") || path.contains("/.git/") || path.startsWith(".git/")
                || path.contains("/.coder/") || path.startsWith(".coder/") || path.endsWith(".env") || path.contains("/.env")) {
            return rejected("敏感或越界路径被拒绝：" + path, "SENSITIVE_PATH_REJECTED");
        }
        return null;
    }

    private ToolResult rejected(String message, String code) {
        return new ToolResult(CallStatus.REJECTED, message, "", 1, code);
    }

    private String redact(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replaceAll("(?i)(api[_-]?key|token|password|credential)\\s*[:=]\\s*[^\\s,;\"}]+", "$1=[REDACTED]")
                .replaceAll("-----BEGIN [^-]+PRIVATE KEY-----[\\s\\S]*?-----END [^-]+PRIVATE KEY-----", "[REDACTED_PRIVATE_KEY]");
    }
}
