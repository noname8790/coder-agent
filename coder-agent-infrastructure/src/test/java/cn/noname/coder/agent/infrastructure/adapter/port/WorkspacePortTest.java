package cn.noname.coder.agent.infrastructure.adapter.port;

import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import cn.noname.coder.agent.types.exception.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WorkspacePortTest {

    @TempDir
    Path workspaceRoot;

    @Test
    void shouldRejectPathEscapeGivenRelativeParentPath() {
        // Given 已配置 workspaceKey
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.getWorkspaces().put("repo", workspaceRoot.toString());
        WorkspacePort port = new WorkspacePort(properties);
        WorkspaceDescriptor workspace = port.resolve("repo").orElseThrow();

        // When / Then 访问父目录路径时必须拒绝
        AppException error = assertThrows(AppException.class, () -> port.resolveInside(workspace, "../secret.txt"));
        assertEquals("PATH_ESCAPE", error.getCode());
    }

    @Test
    void shouldResolveInsideWorkspaceGivenNormalPath() {
        // Given 已配置 workspaceKey
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.getWorkspaces().put("repo", workspaceRoot.toString());
        WorkspacePort port = new WorkspacePort(properties);

        // When 解析 workspace 内路径
        Path resolved = port.resolveInside(port.resolve("repo").orElseThrow(), "pom.xml");

        // Then 结果仍在 workspace 内
        assertTrue(resolved.startsWith(workspaceRoot.toAbsolutePath().normalize()));
    }

    @Test
    void shouldRejectWindowsDriveRelativeWorkspaceRootGivenUnescapedBackslashesInEnvFile() {
        // Given Windows .properties 会把 E:\IdeaProjects\coder-agent 解析成 E:IdeaProjectscoder-agent
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.getWorkspaces().put("repo", "E:IdeaProjectscoder-agent");
        WorkspacePort port = new WorkspacePort(properties);

        // When / Then 这种盘符相对路径必须拒绝，不能静默落到当前目录下面
        AppException error = assertThrows(AppException.class, () -> port.resolve("repo"));
        assertEquals("WORKSPACE_PATH_INVALID", error.getCode());
    }
}
