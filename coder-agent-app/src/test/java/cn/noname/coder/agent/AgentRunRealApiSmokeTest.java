package cn.noname.coder.agent;

import cn.noname.coder.agent.domain.agent.adapter.port.IModelConfigPort;
import cn.noname.coder.agent.domain.agent.model.valobj.ModelBackendConfig;
import cn.noname.coder.agent.types.enums.AgentRunStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "RUN_REAL_API_SMOKE", matches = "true")
class AgentRunRealApiSmokeTest {

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private IModelConfigPort modelConfigPort;

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
    void shouldCompleteLocalRepositoryTaskGivenRealOpenAiCompatibleApi() throws Exception {
        // Given 真实 OpenAI-compatible API 配置已通过 Spring 配置提供，通常来自 .env
        String modelKey = "glm-5";
        ModelBackendConfig modelConfig = modelConfigPort.resolve(modelKey).orElseThrow();
        assertFalse(modelConfig.baseUrl().isBlank(), "model baseUrl is required");
        assertFalse(modelConfig.apiKey().isBlank(), "model apiKey is required");
        assertFalse(modelConfig.actualModel().isBlank(), "actual model is required");

        // When 创建一个指定模型 key 的真实本地仓库分析任务
        String runId = createRun(modelKey);
        JsonNode run = waitTerminal(runId);

        // Then 运行成功，且至少记录一次真实模型调用
        assertEquals(AgentRunStatus.SUCCEEDED.name(), run.at("/data/status").asText(), run.toPrettyString());
        Integer modelCalls = jdbcTemplate.queryForObject("select count(*) from model_call where run_id = ?", Integer.class, runId);
        assertNotNull(modelCalls);
        assertTrue(modelCalls > 0);
        assertFalse(run.at("/data/finalAnswer").asText("").isBlank());

        String runModel = jdbcTemplate.queryForObject("select model from agent_run where run_id = ?", String.class, runId);
        assertEquals(modelKey, runModel);
    }

    private String createRun(String modelKey) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {"workspaceKey":"coder-agent","model":"%s","task":"请用一句话说明这个仓库的技术栈。优先直接回答，不需要调用工具。"}
                """.formatted(modelKey);
        ResponseEntity<String> response = restTemplate.postForEntity("/api/agent-runs", new HttpEntity<>(body, headers), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode json = objectMapper.readTree(response.getBody());
        String runId = json.at("/data/runId").asText();
        assertFalse(runId.isBlank());
        runIds.add(runId);
        return runId;
    }

    private JsonNode waitTerminal(String runId) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
        JsonNode last = null;
        while (System.nanoTime() < deadline) {
            ResponseEntity<String> response = restTemplate.getForEntity("/api/agent-runs/" + runId, String.class);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            last = objectMapper.readTree(response.getBody());
            String status = last.at("/data/status").asText();
            if (AgentRunStatus.SUCCEEDED.name().equals(status) || AgentRunStatus.FAILED.name().equals(status)) {
                return last;
            }
            Thread.sleep(500);
        }
        fail("AgentRun did not reach terminal status: " + last);
        return last;
    }

    private void delete(String table, String runId) {
        jdbcTemplate.update("delete from " + table + " where run_id = ?", runId);
    }
}
