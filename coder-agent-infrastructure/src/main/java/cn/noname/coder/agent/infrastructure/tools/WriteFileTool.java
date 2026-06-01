package cn.noname.coder.agent.infrastructure.tools;

import cn.noname.coder.agent.domain.agent.adapter.port.IWorkspacePort;
import cn.noname.coder.agent.domain.agent.model.valobj.ChangedFile;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolDefinition;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolResult;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.types.enums.CallStatus;
import cn.noname.coder.agent.types.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 新建文本文件工具，不允许覆盖已有文件。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WriteFileTool implements LocalTool {

    private final IWorkspacePort workspacePort;
    private final ProtectedPathPolicy protectedPathPolicy;

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition("write_file", "在 workspace 内新建文本文件，不允许覆盖已有文件。参数：path、content。",
                Map.of("type", "object",
                        "properties", Map.of("path", Map.of("type", "string"), "content", Map.of("type", "string")),
                        "required", new String[]{"path", "content"}));
    }

    @Override
    public ToolResult execute(String runId, WorkspaceDescriptor workspace, String argumentsJson) {
        try {
            Map<String, Object> args = ToolJson.parse(argumentsJson);
            String relativePath = ToolJson.string(args, "path", "");
            String content = ToolJson.string(args, "content", "");
            if (relativePath.isBlank()) {
                return rejected("path 不能为空", "INVALID_ARGUMENT");
            }
            Path file = workspacePort.resolveInside(workspace, relativePath);
            protectedPathPolicy.assertEditable(workspace.rootPath(), file);
            if (Files.exists(file)) {
                return rejected("write_file 不允许覆盖已有文件：" + relativePath, "FILE_EXISTS");
            }
            TextFileSupport.assertTextFile(file);
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
            ChangedFile changed = new ChangedFile(relativePath.replace('\\', '/'), "ADD",
                    null, TextFileSupport.sha256(content), null, "", content);
            log.info("write_file 新建文件成功 runId={} path={} afterHash={}",
                    runId, changed.relativePath(), changed.afterHash());
            return new ToolResult(CallStatus.SUCCESS, "已新建文件：" + changed.relativePath(), content,
                    0, null, List.of(changed), null);
        } catch (AppException e) {
            log.warn("write_file 被拒绝 runId={} code={} message={}", runId, e.getCode(), e.getMessage());
            return new ToolResult(CallStatus.REJECTED, "write_file 被拒绝：" + e.getMessage(), "", 1, e.getCode());
        } catch (Exception e) {
            log.error("write_file 执行失败 runId={}", runId, e);
            return new ToolResult(CallStatus.FAILED, "write_file 失败：" + e.getMessage(), "", 1, e.getMessage());
        }
    }

    private ToolResult rejected(String summary, String code) {
        return new ToolResult(CallStatus.REJECTED, summary, "", 1, code);
    }
}
