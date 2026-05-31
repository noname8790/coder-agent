package cn.noname.coder.agent;

import cn.noname.coder.agent.domain.agent.adapter.port.IModelGateway;
import cn.noname.coder.agent.domain.agent.model.valobj.ModelResponse;
import cn.noname.coder.agent.types.enums.AgentRunStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_TESTS", matches = "true")
class AgentRunE2EMySqlTest {

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private IModelGateway modelGateway;

    private final List<String> runIds = new ArrayList<>();

    @AfterEach
    void clean() {
        for (String runId : runIds) {
            delete("run_artifact", runId);
            delete("audit_event", runId);
            delete("tool_call", runId);
            delete("model_call", runId);
            delete("agent_step", runId);
            delete("agent_run", runId);
        }
    }

    @Test
    void shouldCreateRunAndGenerateTraceAndArtifactsGivenModelFinalAnswer() throws Exception {
        // Given 模型返回最终结论
        when(modelGateway.call(any())).thenReturn(new ModelResponse("resp_1", "仓库分析完成", List.of(), "final"));

        // When 通过 REST 创建运行
        String runId = createRun();
        JsonNode run = waitTerminal(runId);

        // Then 运行成功，trace 可查询，final-result 已落盘
        assertEquals(AgentRunStatus.SUCCEEDED.name(), run.at("/data/status").asText());
        ResponseEntity<String> traceResponse = restTemplate.getForEntity("/api/agent-runs/" + runId + "/trace", String.class);
        assertEquals(HttpStatus.OK, traceResponse.getStatusCode());
        JsonNode trace = objectMapper.readTree(traceResponse.getBody());
        assertTrue(trace.at("/data/events").size() > 0);
        Path finalResult = workspaceRoot().resolve(".coder/runs").resolve(runId).resolve("final-result.json");
        assertTrue(Files.exists(finalResult));
        JsonNode finalJson = objectMapper.readTree(Files.readString(finalResult));
        assertEquals(AgentRunStatus.SUCCEEDED.name(), finalJson.get("status").asText());
    }

    @Test
    void shouldFailRunAndKeepServiceAliveGivenModelUnavailable() throws Exception {
        // Given 模型调用失败
        when(modelGateway.call(any())).thenThrow(new IllegalStateException("model unavailable"));

        // When 通过 REST 创建运行
        String runId = createRun();
        JsonNode run = waitTerminal(runId);

        // Then 当前运行失败且服务仍能响应查询，final-result 记录失败状态
        assertEquals(AgentRunStatus.FAILED.name(), run.at("/data/status").asText());
        ResponseEntity<String> queryAgain = restTemplate.getForEntity("/api/agent-runs/" + runId, String.class);
        assertEquals(HttpStatus.OK, queryAgain.getStatusCode());
        Path finalResult = workspaceRoot().resolve(".coder/runs").resolve(runId).resolve("final-result.json");
        assertTrue(Files.exists(finalResult));
        JsonNode finalJson = objectMapper.readTree(Files.readString(finalResult));
        assertEquals(AgentRunStatus.FAILED.name(), finalJson.get("status").asText());
    }

    private String createRun() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.postForEntity("/api/agent-runs",
                new HttpEntity<>("{\"workspaceKey\":\"coder-agent\",\"task\":\"请分析仓库结构\"}", headers), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode json = objectMapper.readTree(response.getBody());
        String runId = json.at("/data/runId").asText();
        assertFalse(runId.isBlank());
        runIds.add(runId);
        return runId;
    }

    private JsonNode waitTerminal(String runId) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        JsonNode last = null;
        while (System.nanoTime() < deadline) {
            ResponseEntity<String> response = restTemplate.getForEntity("/api/agent-runs/" + runId, String.class);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            last = objectMapper.readTree(response.getBody());
            String status = last.at("/data/status").asText();
            if (AgentRunStatus.SUCCEEDED.name().equals(status) || AgentRunStatus.FAILED.name().equals(status)) {
                return last;
            }
            Thread.sleep(200);
        }
        fail("AgentRun did not reach terminal status: " + last);
        return last;
    }

    private Path workspaceRoot() {
        String root = System.getenv("CODER_AGENT_WORKSPACE_ROOT");
        assertNotNull(root, "CODER_AGENT_WORKSPACE_ROOT is required for E2E test");
        return Path.of(root).toAbsolutePath().normalize();
    }

    private void delete(String table, String runId) {
        jdbcTemplate.update("delete from " + table + " where run_id = ?", runId);
    }
}
