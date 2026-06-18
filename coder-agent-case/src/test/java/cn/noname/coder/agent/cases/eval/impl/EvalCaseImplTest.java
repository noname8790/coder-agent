package cn.noname.coder.agent.cases.eval.impl;

import cn.noname.coder.agent.api.dto.CreateAgentRunRequestDTO;
import cn.noname.coder.agent.api.dto.CreateAgentRunResponseDTO;
import cn.noname.coder.agent.api.dto.EvalBenchmarkRequestDTO;
import cn.noname.coder.agent.api.dto.StartEvalRunRequestDTO;
import cn.noname.coder.agent.cases.agent.ICreateAgentRunCase;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRunRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IContextSnapshotRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IEvalRepository;
import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.agent.model.entity.ContextSnapshot;
import cn.noname.coder.agent.domain.agent.model.entity.EvalBenchmark;
import cn.noname.coder.agent.domain.agent.model.entity.EvalCaseResult;
import cn.noname.coder.agent.domain.agent.model.entity.EvalRun;
import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import cn.noname.coder.agent.types.enums.AgentRunStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvalCaseImplTest {

    @Test
    void shouldExecuteBenchmarkCasesGivenTwoModelKeys() {
        // Given 一个启用 benchmark 和两个模型 key
        InMemoryEvalRepository evalRepository = new InMemoryEvalRepository();
        InMemoryRunRepository runRepository = new InMemoryRunRepository();
        InMemoryContextSnapshotRepository snapshotRepository = new InMemoryContextSnapshotRepository();
        ICreateAgentRunCase createRunCase = request -> {
            String runId = "run_" + request.model();
            AgentRun run = AgentRun.builder()
                    .runId(runId)
                    .workspaceKey(request.workspaceKey())
                    .task(request.task())
                    .model(request.model())
                    .status(AgentRunStatus.SUCCEEDED)
                    .finalAnswer("分析完成")
                    .modelCallCount(2)
                    .toolCallCount(3)
                    .stepCount(4)
                    .durationMs(1200L)
                    .createdAt(LocalDateTime.now())
                    .startedAt(LocalDateTime.now())
                    .endedAt(LocalDateTime.now())
                    .build();
            runRepository.save(run);
            snapshotRepository.snapshots.put(runId, List.of(ContextSnapshot.builder()
                    .runId(runId)
                    .compressionRatio(0.22)
                    .memoryHitCount(2)
                    .build()));
            return new CreateAgentRunResponseDTO(runId, "SUCCEEDED", LocalDateTime.now());
        };
        EvalCaseImpl evalCase = new EvalCaseImpl(evalRepository, createRunCase, runRepository, snapshotRepository, new AgentRuntimeProperties());
        evalCase.createBenchmark(new EvalBenchmarkRequestDTO("读取仓库", "repo", "请分析仓库", "READ_ONLY", null, "完成", "RULE", 30));

        // When 启动 eval run
        var response = evalCase.startRun(new StartEvalRunRequestDTO("模型对比", List.of("glm-5", "qwen")));

        // Then 每个模型都生成 case result，并汇总通过率和指标
        assertEquals("COMPLETED", response.status());
        assertEquals(1.0, response.passRate());
        assertEquals(2, response.cases().size());
        assertTrue(response.cases().stream().allMatch(result -> Boolean.TRUE.equals(result.passed())));
        assertTrue(response.cases().stream().allMatch(result -> result.modelCalls() == 2 && result.toolCalls() == 3));
        assertTrue(response.cases().stream().allMatch(result -> result.contextCompressionRatio() == 0.22 && result.memoryHitCount() == 2));
    }

    static class InMemoryEvalRepository implements IEvalRepository {
        private final Map<String, EvalBenchmark> benchmarks = new LinkedHashMap<>();
        private final Map<String, EvalRun> runs = new LinkedHashMap<>();
        private final Map<String, EvalCaseResult> results = new LinkedHashMap<>();

        @Override
        public void saveBenchmark(EvalBenchmark benchmark) {
            benchmarks.put(benchmark.getBenchmarkId(), benchmark);
        }

        @Override
        public void saveRun(EvalRun evalRun) {
            runs.put(evalRun.getEvalId(), evalRun);
        }

        @Override
        public void saveCaseResult(EvalCaseResult result) {
            results.put(result.getEvalId() + ":" + result.getBenchmarkId() + ":" + result.getModelKey(), result);
        }

        @Override
        public List<EvalBenchmark> listActiveBenchmarks() {
            return benchmarks.values().stream().filter(benchmark -> "ACTIVE".equals(benchmark.getStatus())).toList();
        }

        @Override
        public Optional<EvalRun> findRun(String evalId) {
            return Optional.ofNullable(runs.get(evalId));
        }

        @Override
        public List<EvalCaseResult> listCaseResults(String evalId) {
            return results.values().stream().filter(result -> evalId.equals(result.getEvalId())).toList();
        }
    }

    static class InMemoryRunRepository implements IAgentRunRepository {
        private final Map<String, AgentRun> runs = new LinkedHashMap<>();

        @Override
        public void save(AgentRun run) {
            runs.put(run.getRunId(), run);
        }

        @Override
        public void update(AgentRun run) {
            runs.put(run.getRunId(), run);
        }

        @Override
        public Optional<AgentRun> findByRunId(String runId) {
            return Optional.ofNullable(runs.get(runId));
        }

        @Override
        public long countByStatuses(Collection<AgentRunStatus> statuses) {
            return runs.values().stream().filter(run -> statuses.contains(run.getStatus())).count();
        }
    }

    static class InMemoryContextSnapshotRepository implements IContextSnapshotRepository {
        private final Map<String, List<ContextSnapshot>> snapshots = new LinkedHashMap<>();

        @Override
        public void save(ContextSnapshot snapshot) {
        }

        @Override
        public List<ContextSnapshot> listByRunId(String runId) {
            return snapshots.getOrDefault(runId, List.of());
        }
    }
}
