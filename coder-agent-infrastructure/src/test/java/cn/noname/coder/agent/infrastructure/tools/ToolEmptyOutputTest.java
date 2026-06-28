package cn.noname.coder.agent.infrastructure.tools;

import cn.noname.coder.agent.domain.workspace.adapter.port.IWorkspacePort;
import cn.noname.coder.agent.domain.tool.model.valobj.ToolResult;
import cn.noname.coder.agent.domain.workspace.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import cn.noname.coder.agent.types.enums.CallStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolEmptyOutputTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReturnExplicitNoMatchesGivenSearchFindsNothing() {
        IWorkspacePort workspacePort = mock(IWorkspacePort.class);
        WorkspaceDescriptor workspace = new WorkspaceDescriptor("demo", tempDir);
        when(workspacePort.resolveInside(eq(workspace), any())).thenReturn(tempDir);
        SearchTextTool tool = new SearchTextTool(workspacePort, new AgentRuntimeProperties());

        ToolResult result = tool.execute("run_1", workspace, "{\"query\":\"not-present-token\"}");

        assertEquals(CallStatus.SUCCESS, result.status());
        assertTrue(result.summary().contains("未找到匹配内容"));
        assertTrue(result.summary().contains("not-present-token"));
    }

    @Test
    void shouldReturnExplicitEmptyDirectoryGivenDirectoryHasNoEntries() {
        IWorkspacePort workspacePort = mock(IWorkspacePort.class);
        WorkspaceDescriptor workspace = new WorkspaceDescriptor("demo", tempDir);
        when(workspacePort.resolveInside(eq(workspace), any())).thenReturn(tempDir);
        ListFilesTool tool = new ListFilesTool(workspacePort, new AgentRuntimeProperties());

        ToolResult result = tool.execute("run_1", workspace, "{\"path\":\".\"}");

        assertEquals(CallStatus.SUCCESS, result.status());
        assertTrue(result.summary().contains("目录为空"));
    }
}
