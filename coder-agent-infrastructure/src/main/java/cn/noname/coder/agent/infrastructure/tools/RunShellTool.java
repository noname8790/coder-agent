package cn.noname.coder.agent.infrastructure.tools;

import cn.noname.coder.agent.domain.tool.model.valobj.TestCommandReport;
import cn.noname.coder.agent.domain.tool.model.valobj.ToolDefinition;
import cn.noname.coder.agent.domain.tool.model.valobj.ToolResult;
import cn.noname.coder.agent.domain.workspace.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import cn.noname.coder.agent.types.enums.CallStatus;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 仅支持 Windows PowerShell 的受限 shell 工具。
 */
@Component
public class RunShellTool implements LocalTool {

    private static final Pattern CD_AND_COMMAND = Pattern.compile(
            "^\\s*cd\\s+([\"']?)(.+?)\\1\\s*(?:;|&&)\\s*(.+)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CD_ONLY = Pattern.compile(
            "^\\s*cd\\s+([\"']?)(.+?)\\1\\s*$",
            Pattern.CASE_INSENSITIVE);

    private final AgentRuntimeProperties properties;

    public RunShellTool(AgentRuntimeProperties properties) {
        this.properties = properties;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "run_shell",
                "在 workspace 根目录或其子目录中执行受限的 Windows PowerShell 命令",
                Map.of(
                        "type", "object",
                        "properties", Map.of("command", Map.of("type", "string")),
                        "required", new String[]{"command"}));
    }

    @Override
    public ToolResult execute(String runId, WorkspaceDescriptor workspace, String argumentsJson) {
        String rawCommand = ToolJson.string(ToolJson.parse(argumentsJson), "command", "").trim();
        if (rawCommand.isBlank()) {
            return rejected("command 不能为空", "INVALID_ARGUMENT");
        }

        try {
            ResolvedCommand resolved = resolveCommand(workspace, rawCommand);
            if (resolved.directoryOnly()) {
                Path relative = workspace.rootPath().relativize(resolved.workingDirectory());
                String output = "已切换工作目录? " + (relative.toString().isBlank() ? "." : relative);
                return new ToolResult(CallStatus.SUCCESS, output, output, 0, null);
            }
            String validationError = validateCommand(resolved.command());
            if (validationError != null) {
                return rejected(validationError, validationCode(validationError));
            }

            long start = System.currentTimeMillis();
            Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", resolved.command())
                    .directory(resolved.workingDirectory().toFile())
                    .redirectErrorStream(true)
                    .start();
            CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(() -> readProcessSafely(process));
            boolean finished = process.waitFor(properties.getTools().getShellTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                String output = awaitOutput(outputFuture);
                String summary = "命令超时: " + rawCommand
                        + (output.isBlank() ? "" : System.lineSeparator() + output);
                return new ToolResult(CallStatus.FAILED, summary, output, 124, "TIMEOUT");
            }

            String output = awaitOutput(outputFuture);
            long latencyMs = System.currentTimeMillis() - start;
            int exitCode = process.exitValue();
            TestCommandReport report = testReport(rawCommand, exitCode, latencyMs, output);
            CallStatus status = exitCode == 0 ? CallStatus.SUCCESS : CallStatus.FAILED;
            String errorMessage = exitCode == 0 ? null : "COMMAND_EXIT_" + exitCode;
            return new ToolResult(status, output, output, exitCode, errorMessage, java.util.List.of(), report);
        } catch (IllegalArgumentException e) {
            return rejected(e.getMessage(), "INVALID_ARGUMENT");
        } catch (Exception e) {
            return new ToolResult(CallStatus.FAILED, "命令执行失败: " + e.getMessage(), "", 1, e.getMessage());
        }
    }

    private ResolvedCommand resolveCommand(WorkspaceDescriptor workspace, String rawCommand) throws Exception {
        Matcher combined = CD_AND_COMMAND.matcher(rawCommand);
        if (combined.matches()) {
            Path workingDirectory = resolveWorkingDirectory(workspace.rootPath(), combined.group(2));
            return new ResolvedCommand(workingDirectory, combined.group(3).trim(), false);
        }
        Matcher cdOnly = CD_ONLY.matcher(rawCommand);
        if (cdOnly.matches()) {
            Path workingDirectory = resolveWorkingDirectory(workspace.rootPath(), cdOnly.group(2));
            return new ResolvedCommand(workingDirectory, "", true);
        }
        return new ResolvedCommand(workspace.rootPath(), rawCommand, false);
    }

    private Path resolveWorkingDirectory(Path workspaceRoot, String requestedPath) throws Exception {
        String normalized = requestedPath.replace("/", "\\").trim();
        Path candidate = Path.of(normalized);
        Path resolved = candidate.isAbsolute()
                ? candidate.normalize()
                : workspaceRoot.resolve(normalized).normalize();
        Path normalizedRoot = workspaceRoot.normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("cd 路径越界: " + requestedPath);
        }
        if (!Files.isDirectory(resolved)) {
            throw new IllegalArgumentException("cd 目标不是目录: " + requestedPath);
        }
        return resolved;
    }

    private String validateCommand(String command) {
        String normalized = command.trim().toLowerCase();
        String lower = " " + normalized + " ";
        for (String token : properties.getTools().getDangerousTokens()) {
            if (shouldIgnoreDangerousToken(normalized, token)) {
                continue;
            }
            if (lower.contains(token.toLowerCase())) {
                return "命令包含危险 token: " + token;
            }
        }
        boolean allowed = properties.getTools().getAllowedCommandPrefixes().stream()
                .anyMatch(prefix -> command.equals(prefix) || command.startsWith(prefix + " "));
        if (!allowed) {
            return "命令不在白名单中: " + command;
        }
        return null;
    }

    private boolean shouldIgnoreDangerousToken(String command, String token) {
        return " rm ".equals(token) && (command.equals("git rm") || command.startsWith("git rm "));
    }

    private String validationCode(String message) {
        if (message.startsWith("命令包含危险 token")) {
            return "DANGEROUS_COMMAND";
        }
        if (message.startsWith("命令不在白名单中")) {
            return "COMMAND_NOT_ALLOWED";
        }
        return "INVALID_ARGUMENT";
    }

    private TestCommandReport testReport(String command, int exitCode, long latencyMs, String output) {
        String lower = command.toLowerCase();
        boolean testOrBuild = lower.contains(" test")
                || lower.endsWith("test")
                || lower.contains(" package")
                || lower.endsWith("package")
                || lower.contains(" compile")
                || lower.endsWith("compile");
        if (!testOrBuild) {
            return null;
        }
        String status = exitCode == 0 ? "PASSED" : "FAILED";
        String summary = output == null
                ? ""
                : output.lines().limit(80).reduce("", (a, b) -> a + b + System.lineSeparator());
        return new TestCommandReport(command, exitCode, latencyMs, status, summary);
    }

    private ToolResult rejected(String summary, String code) {
        return new ToolResult(CallStatus.REJECTED, summary, "", 1, code);
    }

    private String readProcess(Process process) throws Exception {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }
        return output.toString();
    }

    private String readProcessSafely(Process process) {
        try {
            return readProcess(process);
        } catch (Exception e) {
            return "读取命令输出失败: " + e.getMessage();
        }
    }

    private String awaitOutput(CompletableFuture<String> outputFuture) {
        try {
            return outputFuture.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return "读取命令输出超时: " + e.getMessage();
        }
    }

    private record ResolvedCommand(Path workingDirectory, String command, boolean directoryOnly) {
    }
}
