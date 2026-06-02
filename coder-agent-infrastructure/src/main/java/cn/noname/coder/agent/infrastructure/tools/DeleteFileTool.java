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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 删除文本文件工具，仅在 L3 权限下可见和可执行。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeleteFileTool implements LocalTool {

    private final IWorkspacePort workspacePort;
    private final ProtectedPathPolicy protectedPathPolicy;

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition("delete_file", "删除 workspace 内普通文本文件。参数：path；禁止删除目录和受保护路径。",
                Map.of("type", "object",
                        "properties", Map.of("path", Map.of("type", "string")),
                        "required", new String[]{"path"}));
    }

    @Override
    public ToolResult execute(String runId, WorkspaceDescriptor workspace, String argumentsJson) {
        try {
            Map<String, Object> args = ToolJson.parse(argumentsJson);
            String relativePath = ToolJson.string(args, "path", "");
            if (relativePath.isBlank()) {
                return rejected("path 不能为空", "INVALID_ARGUMENT");
            }
            Path file = workspacePort.resolveInside(workspace, relativePath);
            protectedPathPolicy.assertEditable(workspace.rootPath(), file);
            if (Files.isDirectory(file)) {
                return rejected("delete_file 禁止删除目录：" + relativePath, "INVALID_ARGUMENT");
            }
            if (!Files.isRegularFile(file)) {
                return rejected("delete_file 只能删除已存在文件：" + relativePath, "FILE_NOT_FOUND");
            }
            TextFileSupport.assertTextFile(file);
            String before = TextFileSupport.read(file);
            Files.delete(file);
            ChangedFile changed = new ChangedFile(relativePath.replace('\\', '/'), "DELETE",
                    TextFileSupport.sha256(before), null, null, before, "");
            log.info("delete_file 删除文件成功 runId={} path={} beforeHash={}",
                    runId, changed.relativePath(), changed.beforeHash());
            return new ToolResult(CallStatus.SUCCESS, "已删除文件：" + changed.relativePath(), before,
                    0, null, List.of(changed), null);
        } catch (AppException e) {
            log.warn("delete_file 被拒绝 runId={} code={} message={}", runId, e.getCode(), e.getMessage());
            return new ToolResult(CallStatus.REJECTED, "delete_file 被拒绝：" + e.getMessage(), "", 1, e.getCode());
        } catch (Exception e) {
            log.error("delete_file 执行失败 runId={}", runId, e);
            return new ToolResult(CallStatus.FAILED, "delete_file 失败：" + e.getMessage(), "", 1, e.getMessage());
        }
    }

    private ToolResult rejected(String summary, String code) {
        return new ToolResult(CallStatus.REJECTED, summary, "", 1, code);
    }
}
