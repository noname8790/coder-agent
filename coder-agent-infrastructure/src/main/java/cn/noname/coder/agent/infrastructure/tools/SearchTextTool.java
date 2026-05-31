package cn.noname.coder.agent.infrastructure.tools;

import cn.noname.coder.agent.domain.agent.adapter.port.IWorkspacePort;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolDefinition;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolResult;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import cn.noname.coder.agent.types.enums.CallStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * 文本搜索工具：优先使用 rg，失败后 fallback 到 Java 文件扫描。
 */
@Component
@RequiredArgsConstructor
public class SearchTextTool implements LocalTool {

    private final IWorkspacePort workspacePort;
    private final AgentRuntimeProperties properties;

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition("search_text", "在 workspace 内搜索文本，优先 rg，失败 fallback Java 扫描",
                Map.of("type", "object",
                        "properties", Map.of("query", Map.of("type", "string"), "path", Map.of("type", "string")),
                        "required", new String[]{"query"}));
    }

    @Override
    public ToolResult execute(String runId, WorkspaceDescriptor workspace, String argumentsJson) {
        Map<String, Object> args = ToolJson.parse(argumentsJson);
        String query = ToolJson.string(args, "query", "");
        if (query.isBlank()) {
            return new ToolResult(CallStatus.REJECTED, "query 不能为空", "", 1, "INVALID_ARGUMENT");
        }
        Path base = workspacePort.resolveInside(workspace, ToolJson.string(args, "path", "."));
        ToolResult rg = tryRg(base, query);
        if (rg != null && rg.success()) {
            return rg;
        }
        return javaScan(base, query);
    }

    private ToolResult tryRg(Path base, String query) {
        try {
            Process process = new ProcessBuilder("rg", "--line-number", "--no-heading", query, base.toString())
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(20, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }
            String output = readProcess(process);
            if (process.exitValue() == 0 || process.exitValue() == 1) {
                return new ToolResult(CallStatus.SUCCESS, limitLines(output), output, process.exitValue(), null);
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private ToolResult javaScan(Path base, String query) {
        StringBuilder output = new StringBuilder("fallback=java_scan").append(System.lineSeparator());
        int[] count = {0};
        try (Stream<Path> stream = Files.walk(base)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> count[0] < properties.getTools().getMaxSearchResults())
                    .forEach(path -> scanFile(path, query, output, count));
            return new ToolResult(CallStatus.SUCCESS, output.toString(), output.toString(), 0, null);
        } catch (Exception e) {
            return new ToolResult(CallStatus.FAILED, "search_text 失败：" + e.getMessage(), output.toString(), 1, e.getMessage());
        }
    }

    private void scanFile(Path path, String query, StringBuilder output, int[] count) {
        try {
            if (Files.size(path) > properties.getTools().getMaxReadBytes()) {
                return;
            }
            int lineNo = 0;
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                lineNo++;
                if (line.contains(query)) {
                    output.append(path).append(":").append(lineNo).append(":").append(line.strip()).append(System.lineSeparator());
                    count[0]++;
                    if (count[0] >= properties.getTools().getMaxSearchResults()) {
                        return;
                    }
                }
            }
        } catch (Exception ignored) {
            // 跳过不可读或非 UTF-8 文件，避免单个文件阻断搜索。
        }
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

    private String limitLines(String output) {
        return output.lines()
                .limit(properties.getTools().getMaxSearchResults())
                .reduce("", (a, b) -> a + b + System.lineSeparator());
    }
}
