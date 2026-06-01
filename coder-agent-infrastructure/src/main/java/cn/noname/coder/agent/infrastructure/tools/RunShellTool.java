package cn.noname.coder.agent.infrastructure.tools;

import cn.noname.coder.agent.domain.agent.model.valobj.ToolDefinition;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolResult;
import cn.noname.coder.agent.domain.agent.model.valobj.TestCommandReport;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import cn.noname.coder.agent.types.enums.CallStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Windows PowerShell-only Shell 工具，执行前先做白名单和危险 token 校验。
 */
@Component
@RequiredArgsConstructor
public class RunShellTool implements LocalTool {

    private final AgentRuntimeProperties properties;

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition("run_shell", "在 workspace 根目录执行受限 Windows PowerShell 命令",
                Map.of("type", "object", "properties", Map.of("command", Map.of("type", "string")), "required", new String[]{"command"}));
    }

    @Override
    public ToolResult execute(String runId, WorkspaceDescriptor workspace, String argumentsJson) {
        String command = ToolJson.string(ToolJson.parse(argumentsJson), "command", "").trim();
        if (command.isBlank()) {
            return rejected("command 不能为空", "INVALID_ARGUMENT");
        }
        String lower = " " + command.toLowerCase() + " ";
        for (String token : properties.getTools().getDangerousTokens()) {
            if (lower.contains(token.toLowerCase())) {
                return rejected("命令包含危险 token：" + token, "DANGEROUS_COMMAND");
            }
        }
        boolean allowed = properties.getTools().getAllowedCommandPrefixes().stream()
                .anyMatch(prefix -> command.equals(prefix) || command.startsWith(prefix + " "));
        if (!allowed) {
            return rejected("命令不在白名单中：" + command, "COMMAND_NOT_ALLOWED");
        }
        try {
            long start = System.currentTimeMillis();
            Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", command)
                    .directory(workspace.rootPath().toFile())
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(properties.getTools().getShellTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ToolResult(CallStatus.FAILED, "命令超时：" + command, "", 124, "TIMEOUT");
            }
            String output = readProcess(process);
            long latencyMs = System.currentTimeMillis() - start;
            TestCommandReport report = testReport(command, process.exitValue(), latencyMs, output);
            return new ToolResult(CallStatus.SUCCESS, output, output, process.exitValue(), null, java.util.List.of(), report);
        } catch (Exception e) {
            return new ToolResult(CallStatus.FAILED, "命令执行失败：" + e.getMessage(), "", 1, e.getMessage());
        }
    }

    private TestCommandReport testReport(String command, int exitCode, long latencyMs, String output) {
        String lower = command.toLowerCase();
        boolean testOrBuild = lower.contains(" test") || lower.endsWith("test")
                || lower.contains(" package") || lower.endsWith("package");
        if (!testOrBuild) {
            return null;
        }
        String status = exitCode == 0 ? "PASSED" : "FAILED";
        String summary = output == null ? "" : output.lines().limit(80).reduce("", (a, b) -> a + b + System.lineSeparator());
        return new TestCommandReport(command, exitCode, latencyMs, status, summary);
    }

    private ToolResult rejected(String summary, String code) {
        return new ToolResult(CallStatus.REJECTED, summary, "", 1, code);
    }

    private String readProcess(Process process) throws Exception {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }
        return output.toString();
    }
}
