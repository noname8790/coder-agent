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
 * 覆盖文本文件工具，在默认权限审批通过后可执行，完全控制权限下可直接执行。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OverwriteFileTool implements LocalTool {

    private final IWorkspacePort workspacePort;
    private final ProtectedPathPolicy protectedPathPolicy;

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition("overwrite_file", "覆盖 workspace 内已有文本文件。参数：path、content。",
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
            if (Files.isDirectory(file)) {
                return rejected("overwrite_file 不能覆盖目录：" + relativePath, "INVALID_ARGUMENT");
            }
            if (!Files.isRegularFile(file)) {
                return rejected("overwrite_file 只能覆盖已存在文件：" + relativePath, "FILE_NOT_FOUND");
            }
            TextFileSupport.assertTextFile(file);
            String before = TextFileSupport.read(file);
            Files.writeString(file, content, StandardCharsets.UTF_8);
            ChangedFile changed = new ChangedFile(relativePath.replace('\\', '/'), "OVERWRITE",
                    TextFileSupport.sha256(before), TextFileSupport.sha256(content), null, before, content);
            log.info("overwrite_file 覆盖文件成功 runId={} path={} beforeHash={} afterHash={}",
                    runId, changed.relativePath(), changed.beforeHash(), changed.afterHash());
            return new ToolResult(CallStatus.SUCCESS, "已覆盖文件：" + changed.relativePath(), content,
                    0, null, List.of(changed), null);
        } catch (AppException e) {
            log.warn("overwrite_file 被拒绝 runId={} code={} message={}", runId, e.getCode(), e.getMessage());
            return new ToolResult(CallStatus.REJECTED, "overwrite_file 被拒绝：" + e.getMessage(), "", 1, e.getCode());
        } catch (Exception e) {
            log.error("overwrite_file 执行失败 runId={}", runId, e);
            return new ToolResult(CallStatus.FAILED, "overwrite_file 失败：" + e.getMessage(), "", 1, e.getMessage());
        }
    }

    private ToolResult rejected(String summary, String code) {
        return new ToolResult(CallStatus.REJECTED, summary, "", 1, code);
    }
}
