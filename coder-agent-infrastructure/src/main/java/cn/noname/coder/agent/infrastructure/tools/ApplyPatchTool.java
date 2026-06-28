package cn.noname.coder.agent.infrastructure.tools;

import cn.noname.coder.agent.domain.workspace.adapter.port.IWorkspacePort;
import cn.noname.coder.agent.domain.workspace.model.valobj.ChangedFile;
import cn.noname.coder.agent.domain.tool.model.valobj.ToolDefinition;
import cn.noname.coder.agent.domain.tool.model.valobj.ToolResult;
import cn.noname.coder.agent.domain.workspace.model.valobj.WorkspaceDescriptor;
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
 * 安全补丁工具：通过 search/replace 修改已有文本文件。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApplyPatchTool implements LocalTool {

    private final IWorkspacePort workspacePort;
    private final ProtectedPathPolicy protectedPathPolicy;

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition("apply_patch", "修改 workspace 内已有文本文件。参数：path、search、replace；不支持删除文件或覆盖整文件。",
                Map.of("type", "object",
                        "properties", Map.of(
                                "path", Map.of("type", "string"),
                                "search", Map.of("type", "string"),
                                "replace", Map.of("type", "string")),
                        "required", new String[]{"path", "search", "replace"}));
    }

    @Override
    public ToolResult execute(String runId, WorkspaceDescriptor workspace, String argumentsJson) {
        try {
            Map<String, Object> args = ToolJson.parse(argumentsJson);
            String relativePath = ToolJson.string(args, "path", "");
            String search = ToolJson.string(args, "search", null);
            String replace = ToolJson.string(args, "replace", null);
            if (relativePath.isBlank() || search == null || search.isEmpty() || replace == null) {
                return rejected("apply_patch 需要 path、非空 search 和 replace", "INVALID_PATCH");
            }
            Path file = workspacePort.resolveInside(workspace, relativePath);
            protectedPathPolicy.assertEditable(workspace.rootPath(), file);
            if (!Files.isRegularFile(file)) {
                return rejected("apply_patch 只能修改已存在文件：" + relativePath, "FILE_NOT_FOUND");
            }
            TextFileSupport.assertTextFile(file);
            String before = TextFileSupport.read(file);
            String after = replaceOncePreservingLineEndings(before, search, replace);
            if (after == null) {
                return rejected("search 内容未在文件中找到：" + relativePath, "PATCH_NOT_MATCHED");
            }
            Files.writeString(file, after, StandardCharsets.UTF_8);
            ChangedFile changed = new ChangedFile(relativePath.replace('\\', '/'), "MODIFY",
                    TextFileSupport.sha256(before), TextFileSupport.sha256(after), null, before, after);
            log.info("apply_patch 修改文件成功 runId={} path={} beforeHash={} afterHash={}",
                    runId, changed.relativePath(), changed.beforeHash(), changed.afterHash());
            return new ToolResult(CallStatus.SUCCESS, "已修改文件：" + changed.relativePath(), after,
                    0, null, List.of(changed), null);
        } catch (AppException e) {
            log.warn("apply_patch 被拒绝 runId={} code={} message={}", runId, e.getCode(), e.getMessage());
            return new ToolResult(CallStatus.REJECTED, "apply_patch 被拒绝：" + e.getMessage(), "", 1, e.getCode());
        } catch (Exception e) {
            log.error("apply_patch 执行失败 runId={}", runId, e);
            return new ToolResult(CallStatus.FAILED, "apply_patch 失败：" + e.getMessage(), "", 1, e.getMessage());
        }
    }

    private ToolResult rejected(String summary, String code) {
        return new ToolResult(CallStatus.REJECTED, summary, "", 1, code);
    }

    private String replaceOncePreservingLineEndings(String before, String search, String replace) {
        if (before.contains(search)) {
            return before.replace(search, replace);
        }
        String lineEnding = before.contains("\r\n") ? "\r\n" : "\n";
        String normalizedBefore = normalizeLineEndings(before);
        String normalizedSearch = normalizeLineEndings(search);
        if (!normalizedBefore.contains(normalizedSearch)) {
            return null;
        }
        String normalizedAfter = normalizedBefore.replace(normalizedSearch, normalizeLineEndings(replace));
        return "\r\n".equals(lineEnding) ? normalizedAfter.replace("\n", "\r\n") : normalizedAfter;
    }

    private String normalizeLineEndings(String content) {
        return content.replace("\r\n", "\n").replace("\r", "\n");
    }
}
