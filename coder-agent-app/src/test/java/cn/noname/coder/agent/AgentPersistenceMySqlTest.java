package cn.noname.coder.agent;

import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRecordRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRunRepository;
import cn.noname.coder.agent.domain.agent.model.entity.*;
import cn.noname.coder.agent.domain.tool.model.entity.ToolCall;
import cn.noname.coder.agent.types.enums.AgentRunStatus;
import cn.noname.coder.agent.types.enums.ArtifactType;
import cn.noname.coder.agent.types.enums.AuditEventType;
import cn.noname.coder.agent.types.enums.CallStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_TESTS", matches = "true")
class AgentPersistenceMySqlTest {

    private final String runId = "test_persistence_" + System.currentTimeMillis();

    @Autowired
    private IAgentRunRepository runRepository;
    @Autowired
    private IAgentRecordRepository recordRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clean() {
        delete("run_artifact");
        delete("audit_event");
        delete("tool_call");
        delete("model_call");
        delete("agent_step");
        delete("agent_run");
    }

    @Test
    void shouldPersistRunStatusCallsAndArtifactIndexGivenRealMySql() {
        // Given 一个待运行 AgentRun
        AgentRun run = AgentRun.builder()
                .runId(runId)
                .workspaceKey("coder-agent")
                .task("分析仓库")
                .model("qwen")
                .status(AgentRunStatus.CREATED)
                .maxSteps(25)
                .maxModelCalls(25)
                .maxToolCalls(50)
                .timeoutSeconds(300)
                .stepCount(0)
                .modelCallCount(0)
                .toolCallCount(0)
                .createdAt(LocalDateTime.now())
                .build();

        // When 写入运行、更新状态并写入调用记录和工件索引
        runRepository.save(run);
        run.setStatus(AgentRunStatus.SUCCEEDED);
        run.setFinalAnswer("完成");
        run.setStartedAt(LocalDateTime.now());
        run.setEndedAt(LocalDateTime.now());
        run.setDurationMs(12L);
        run.setModelCallCount(1);
        runRepository.update(run);
        recordRepository.saveStep(AgentStep.builder()
                .runId(runId).stepNo(1).stepType("run_started").summary("开始").createdAt(LocalDateTime.now()).build());
        recordRepository.saveModelCall(ModelCall.builder()
                .runId(runId).callNo(1).provider("openai-compatible").model("qwen")
                .requestSummary("request").responseSummary("response").status(CallStatus.SUCCESS)
                .latencyMs(10L).createdAt(LocalDateTime.now()).build());
        recordRepository.saveToolCall(ToolCall.builder()
                .runId(runId).callNo(1).toolName("list_files").argumentsSummary("{}").resultSummary("ok")
                .exitCode(0).status(CallStatus.SUCCESS).latencyMs(5L).createdAt(LocalDateTime.now()).build());
        recordRepository.saveAuditEvent(AuditEvent.builder()
                .runId(runId).eventType(AuditEventType.TOOL_REJECTED).message("拒绝").detail("detail").createdAt(LocalDateTime.now()).build());
        recordRepository.saveArtifact(RunArtifact.builder()
                .runId(runId).artifactType(ArtifactType.FINAL_RESULT)
                .relativePath(".coder/runs/" + runId + "/final-result.json")
                .fileSize(12L).createdAt(LocalDateTime.now()).build());

        // Then 可以从 MySQL 读取运行状态、审计事件和工件索引
        AgentRun saved = runRepository.findByRunId(runId).orElseThrow();
        assertEquals(AgentRunStatus.SUCCEEDED, saved.getStatus());
        assertEquals("完成", saved.getFinalAnswer());
        assertTrue(runRepository.countByStatuses(List.of(AgentRunStatus.SUCCEEDED)) >= 1L);
        assertEquals(1, recordRepository.listAuditEvents(runId).size());
        assertEquals(1, recordRepository.listArtifacts(runId).size());
        assertEquals(1, count("model_call"));
        assertEquals(1, count("tool_call"));
    }

    private int count(String table) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from " + table + " where run_id = ?", Integer.class, runId);
        return count == null ? 0 : count;
    }

    private void delete(String table) {
        jdbcTemplate.update("delete from " + table + " where run_id = ?", runId);
    }
}
