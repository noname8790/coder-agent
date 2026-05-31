package cn.noname.coder.agent.infrastructure.tools;

import cn.noname.coder.agent.domain.agent.adapter.port.IWorkspacePort;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolDefinition;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolResult;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import cn.noname.coder.agent.types.enums.CallStatus;
import cn.noname.coder.agent.types.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * 读取文本文件工具，限制文件类型和最大读取大小。
 */
@Component
@RequiredArgsConstructor
public class ReadFileTool implements LocalTool {

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            ".java", ".xml", ".yml", ".yaml", ".md", ".txt", ".json", ".properties", ".sql", ".gitignore", ".gradle", ".pom"
    );

    private final IWorkspacePort workspacePort;
    private final AgentRuntimeProperties properties;

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition("read_file", "读取 workspace 内文本文件内容",
                Map.of("type", "object", "properties", Map.of("path", Map.of("type", "string")), "required", new String[]{"path"}));
    }

    @Override
    public ToolResult execute(String runId, WorkspaceDescriptor workspace, String argumentsJson) {
        try {
            Path file = workspacePort.resolveInside(workspace, ToolJson.string(ToolJson.parse(argumentsJson), "path", ""));
            if (!Files.isRegularFile(file)) {
                return new ToolResult(CallStatus.FAILED, "路径不是文件：" + file, "", 1, "NOT_FILE");
            }
            if (!isTextFile(file)) {
                return new ToolResult(CallStatus.REJECTED, "拒绝读取非文本文件：" + file.getFileName(), "", 1, "NON_TEXT_FILE");
            }
            long size = Files.size(file);
            int maxBytes = properties.getTools().getMaxReadBytes();
            byte[] bytes = Files.readAllBytes(file);
            String content = new String(bytes, 0, (int) Math.min(bytes.length, maxBytes), StandardCharsets.UTF_8);
            String summary = size > maxBytes ? content + "\n...[内容已截断，文件大小 " + size + " bytes]" : content;
            return new ToolResult(CallStatus.SUCCESS, summary, new String(bytes, StandardCharsets.UTF_8), 0, null);
        } catch (AppException e) {
            return new ToolResult(CallStatus.REJECTED, "read_file 被拒绝：" + e.getMessage(), "", 1, e.getCode());
        } catch (Exception e) {
            return new ToolResult(CallStatus.REJECTED, "read_file 被拒绝：" + e.getMessage(), "", 1, e.getMessage());
        }
    }

    private boolean isTextFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return TEXT_EXTENSIONS.stream().anyMatch(name::endsWith) || !name.contains(".");
    }
}
