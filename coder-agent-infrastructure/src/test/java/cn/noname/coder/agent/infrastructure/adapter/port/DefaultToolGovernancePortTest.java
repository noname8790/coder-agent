package cn.noname.coder.agent.infrastructure.adapter.port;

import cn.noname.coder.agent.domain.tool.model.valobj.ToolInvocation;
import cn.noname.coder.agent.domain.tool.model.valobj.ToolResult;
import cn.noname.coder.agent.types.enums.CallStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultToolGovernancePortTest {

    @Test
    void shouldRejectMissingRequiredArgumentGivenReadFileWithoutPath() {
        DefaultToolGovernancePort port = new DefaultToolGovernancePort();

        ToolResult result = port.validateBeforeExecution("run_1", "repo", new ToolInvocation("1", "read_file", "{}"));

        assertEquals(CallStatus.REJECTED, result.status());
        assertEquals("INVALID_ARGUMENT", result.errorMessage());
    }

    @Test
    void shouldRejectSensitivePathGivenEnvFile() {
        DefaultToolGovernancePort port = new DefaultToolGovernancePort();

        ToolResult result = port.validateBeforeExecution("run_1", "repo",
                new ToolInvocation("1", "read_file", "{\"path\":\".env\"}"));

        assertEquals(CallStatus.REJECTED, result.status());
        assertEquals("SENSITIVE_PATH_REJECTED", result.errorMessage());
    }

    @Test
    void shouldLeaveDuplicateSearchTextInvocationToExecutor() {
        DefaultToolGovernancePort port = new DefaultToolGovernancePort();
        ToolInvocation invocation = new ToolInvocation("1", "search_text", "{\"query\":\"Agent\"}");

        assertNull(port.validateBeforeExecution("run_1", "repo", invocation));
        assertNull(port.validateBeforeExecution("run_1", "repo", invocation));
    }

    @Test
    void shouldNotReusePreviousReadFileResultGivenDuplicateInvocationAfterSuccessfulExecution() {
        DefaultToolGovernancePort port = new DefaultToolGovernancePort();
        ToolInvocation invocation = new ToolInvocation("1", "read_file", "{\"path\":\"src/App.java\"}");

        assertNull(port.validateBeforeExecution("run_1", "repo", invocation));
        port.sanitizeAfterExecution("run_1", "repo", invocation,
                new ToolResult(CallStatus.SUCCESS, "file content", "file content", 0, null));
        assertNull(port.validateBeforeExecution("run_1", "repo", invocation));
    }

    @Test
    void shouldRedactSensitiveOutputGivenToolResultContainsToken() {
        DefaultToolGovernancePort port = new DefaultToolGovernancePort();

        ToolResult result = port.sanitizeAfterExecution("run_1", "repo",
                new ToolInvocation("1", "read_file", "{\"path\":\"src/App.java\"}"),
                new ToolResult(CallStatus.SUCCESS, "api_key=secret", "password:123456", 0, null));

        assertTrue(result.summary().contains("[REDACTED]"));
        assertTrue(result.fullOutput().contains("[REDACTED]"));
    }
}
