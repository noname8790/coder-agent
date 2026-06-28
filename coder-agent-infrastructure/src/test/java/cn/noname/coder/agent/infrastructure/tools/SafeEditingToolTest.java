package cn.noname.coder.agent.infrastructure.tools;

import cn.noname.coder.agent.domain.workspace.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.infrastructure.adapter.port.WorkspacePort;
import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import cn.noname.coder.agent.types.enums.CallStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
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
    void shouldModifyCrLfFileGivenLfSearchReplace() throws Exception {
        // Given workspace 内已有 CRLF 换行的文本文件
        Path file = workspaceRoot.resolve("src/main/java/cn/noname/demo/Calculator.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "class Calculator {\r\n    public int square(int value) {\r\n    return value * value;\r\n}\r\n}");
        ApplyPatchTool tool = new ApplyPatchTool(new WorkspacePort(new AgentRuntimeProperties()), new ProtectedPathPolicy());

        // When search/replace 参数使用 JSON 常见的 LF 换行
        String args = """
                {"path":"src/main/java/cn/noname/demo/Calculator.java","search":"public int square(int value) {\\n    return value * value;\\n}","replace":"public int modulo(int left, int right) {\\n    return left % right;\\n}"}
                """;
        var result = tool.execute("run_1", workspace(), args);

        // Then 能匹配并保持原文件 CRLF 风格
        assertEquals(CallStatus.SUCCESS, result.status());
        String content = Files.readString(file);
        assertTrue(content.contains("public int modulo(int left, int right) {\r\n    return left % right;\r\n}"));
        assertFalse(content.contains("\n    return left % right;\n}"));
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

    @Test
    void shouldOverwriteExistingTextFileAndRejectProtectedPath() throws Exception {
        // Given workspace 内已有文本文件
        Path file = workspaceRoot.resolve("README.md");
        Files.writeString(file, "old");
        OverwriteFileTool tool = new OverwriteFileTool(new WorkspacePort(new AgentRuntimeProperties()), new ProtectedPathPolicy());

        // When 覆盖文件 / Then 记录 OVERWRITE 变更和前后 hash
        var result = tool.execute("run_1", workspace(), "{\"path\":\"README.md\",\"content\":\"new\"}");
        assertEquals(CallStatus.SUCCESS, result.status());
        assertEquals("new", Files.readString(file));
        assertEquals("OVERWRITE", result.changedFiles().getFirst().changeType());
        assertNotNull(result.changedFiles().getFirst().beforeHash());
        assertNotNull(result.changedFiles().getFirst().afterHash());

        // Then 受保护路径和目录覆盖被拒绝
        assertEquals(CallStatus.REJECTED, tool.execute("run_1", workspace(),
                "{\"path\":\".env\",\"content\":\"SECRET=1\"}").status());
        assertEquals(CallStatus.REJECTED, tool.execute("run_1", workspace(),
                "{\"path\":\".\",\"content\":\"x\"}").status());
    }

    @Test
    void shouldDeleteExistingTextFileAndRejectDirectory() throws Exception {
        // Given workspace 内已有文本文件
        Path file = workspaceRoot.resolve("docs/delete-me.md");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "delete me");
        DeleteFileTool tool = new DeleteFileTool(new WorkspacePort(new AgentRuntimeProperties()), new ProtectedPathPolicy());

        // When 删除文件 / Then 记录 DELETE 变更并保留 beforeContent
        var result = tool.execute("run_1", workspace(), "{\"path\":\"docs/delete-me.md\"}");
        assertEquals(CallStatus.SUCCESS, result.status());
        assertFalse(Files.exists(file));
        assertEquals("DELETE", result.changedFiles().getFirst().changeType());
        assertEquals("delete me", result.changedFiles().getFirst().beforeContent());

        // Then 删除目录被拒绝
        assertEquals(CallStatus.REJECTED, tool.execute("run_1", workspace(),
                "{\"path\":\"docs\"}").status());
    }

    private WorkspaceDescriptor workspace() {
        return new WorkspaceDescriptor("repo", workspaceRoot);
    }
}
