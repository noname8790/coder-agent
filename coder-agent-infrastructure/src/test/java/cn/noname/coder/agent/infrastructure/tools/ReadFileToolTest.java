package cn.noname.coder.agent.infrastructure.tools;

import cn.noname.coder.agent.domain.workspace.adapter.port.IWorkspacePort;
import cn.noname.coder.agent.domain.tool.model.valobj.ToolResult;
import cn.noname.coder.agent.domain.workspace.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import cn.noname.coder.agent.types.enums.CallStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReadFileToolTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReturnExplicitEmptyFileSummaryGivenFileHasNoContent() throws Exception {
        Path file = tempDir.resolve("src/test/java/cn/noname/SimpleTest.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "");
        IWorkspacePort workspacePort = mock(IWorkspacePort.class);
        WorkspaceDescriptor workspace = new WorkspaceDescriptor("demo", tempDir);
        when(workspacePort.resolveInside(eq(workspace), any())).thenReturn(file);
        ReadFileTool tool = new ReadFileTool(workspacePort, new AgentRuntimeProperties());

        ToolResult result = tool.execute("run_1", workspace,
                "{\"path\":\"src/test/java/cn/noname/SimpleTest.java\"}");

        assertEquals(CallStatus.SUCCESS, result.status());
        assertTrue(result.summary().contains("文件为空"));
        assertTrue(result.summary().contains("src/test/java/cn/noname/SimpleTest.java"));
    }
}
