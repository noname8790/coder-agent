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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;

/**
 * 列目录工具，只返回受限数量的目录项摘要。
 */
@Component
@RequiredArgsConstructor
public class ListFilesTool implements LocalTool {

    private final IWorkspacePort workspacePort;
    private final AgentRuntimeProperties properties;

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition("list_files", "列出 workspace 内指定目录的文件摘要",
                Map.of("type", "object", "properties", Map.of("path", Map.of("type", "string")), "required", new String[]{"path"}));
    }

    @Override
    public ToolResult execute(String runId, WorkspaceDescriptor workspace, String argumentsJson) {
        try {
            Path dir = workspacePort.resolveInside(workspace, ToolJson.string(ToolJson.parse(argumentsJson), "path", "."));
            if (!Files.isDirectory(dir)) {
                return new ToolResult(CallStatus.FAILED, "路径不是目录：" + dir, "", 1, "NOT_DIRECTORY");
            }
            StringBuilder output = new StringBuilder();
            try (var stream = Files.list(dir)) {
                stream.sorted(Comparator.comparing(Path::toString))
                        .limit(properties.getTools().getMaxListEntries())
                        .forEach(path -> output.append(Files.isDirectory(path) ? "[D] " : "[F] ")
                                .append(dir.relativize(path))
                                .append(System.lineSeparator()));
            }
            return new ToolResult(CallStatus.SUCCESS, output.toString(), output.toString(), 0, null);
        } catch (AppException e) {
            return new ToolResult(CallStatus.REJECTED, "list_files 被拒绝：" + e.getMessage(), "", 1, e.getCode());
        } catch (Exception e) {
            return new ToolResult(CallStatus.REJECTED, "list_files 被拒绝：" + e.getMessage(), "", 1, e.getMessage());
        }
    }
}
