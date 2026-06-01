package cn.noname.coder.agent.infrastructure.tools;

import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceCapability;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.infrastructure.adapter.port.WorkspacePort;
import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import cn.noname.coder.agent.types.enums.CallStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SafeEditingToolTest {

    @TempDir
    Path workspaceRoot;

    @Test
    void shouldModifyExistingTextFileGivenMatchingSearchReplace() throws Exception {
        // Given workspace 内已有文本文件
        Path file = workspaceRoot.resolve("src/App.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "class App { String name = \"old\"; }");
        ApplyPatchTool tool = new ApplyPatchTool(new WorkspacePort(new AgentRuntimeProperties()), new ProtectedPathPolicy());

        // When 调用 apply_patch 修改已有文件
        var result = tool.execute("run_1", workspace(), "{\"path\":\"src/App.java\",\"search\":\"old\",\"replace\":\"new\"}");

        // Then 文件被修改且返回变更摘要
        assertEquals(CallStatus.SUCCESS, result.status());
        assertEquals("class App { String name = \"new\"; }", Files.readString(file));
        assertEquals("MODIFY", result.changedFiles().getFirst().changeType());
    }

    @Test
    void shouldRejectApplyPatchGivenProtectedPathOrMissingFile() {
        // Given apply_patch 工具
        ApplyPatchTool tool = new ApplyPatchTool(new WorkspacePort(new AgentRuntimeProperties()), new ProtectedPathPolicy());

        // When / Then 敏感路径和不存在文件均被拒绝
        assertEquals(CallStatus.REJECTED, tool.execute("run_1", workspace(),
                "{\"path\":\".env\",\"search\":\"A\",\"replace\":\"B\"}").status());
        assertEquals(CallStatus.REJECTED, tool.execute("run_1", workspace(),
                "{\"path\":\"missing.txt\",\"search\":\"A\",\"replace\":\"B\"}").status());
    }

    @Test
    void shouldWriteNewFileOnlyGivenAllowedPath() throws Exception {
        // Given write_file 工具
        WriteFileTool tool = new WriteFileTool(new WorkspacePort(new AgentRuntimeProperties()), new ProtectedPathPolicy());

        // When 新建文件
        var result = tool.execute("run_1", workspace(), "{\"path\":\"docs/note.md\",\"content\":\"hello\"}");

        // Then 文件创建成功且覆盖已有文件会被拒绝
        assertEquals(CallStatus.SUCCESS, result.status());
        assertEquals("ADD", result.changedFiles().getFirst().changeType());
        assertEquals("hello", Files.readString(workspaceRoot.resolve("docs/note.md")));
        assertEquals(CallStatus.REJECTED, tool.execute("run_1", workspace(),
                "{\"path\":\"docs/note.md\",\"content\":\"again\"}").status());
    }

    private WorkspaceDescriptor workspace() {
        return new WorkspaceDescriptor("repo", workspaceRoot, List.of(
                WorkspaceCapability.READ_REPOSITORY,
                WorkspaceCapability.ADD_FILE,
                WorkspaceCapability.MODIFY_FILE));
    }
}
