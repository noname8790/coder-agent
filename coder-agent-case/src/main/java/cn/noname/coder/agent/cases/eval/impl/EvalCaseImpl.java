package cn.noname.coder.agent.cases.eval.impl;

import cn.noname.coder.agent.api.dto.CreateAgentRunRequestDTO;
import cn.noname.coder.agent.api.dto.CreateAgentRunResponseDTO;
import cn.noname.coder.agent.api.dto.EvalBenchmarkRequestDTO;
import cn.noname.coder.agent.api.dto.EvalBenchmarkResponseDTO;
import cn.noname.coder.agent.api.dto.EvalCaseResultDTO;
import cn.noname.coder.agent.api.dto.EvalRunResponseDTO;
import cn.noname.coder.agent.api.dto.StartEvalRunRequestDTO;
import cn.noname.coder.agent.cases.agent.ICreateAgentRunCase;
import cn.noname.coder.agent.cases.eval.IEvalCase;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRunRepository;
import cn.noname.coder.agent.domain.context.adapter.repository.IContextSnapshotRepository;
import cn.noname.coder.agent.domain.evaluation.adapter.repository.IEvalRepository;
import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.context.model.entity.ContextSnapshot;
import cn.noname.coder.agent.domain.evaluation.model.entity.EvalBenchmark;
import cn.noname.coder.agent.domain.evaluation.model.entity.EvalCaseResult;
import cn.noname.coder.agent.domain.evaluation.model.entity.EvalRun;
import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import cn.noname.coder.agent.types.enums.AgentRunStatus;
import cn.noname.coder.agent.types.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvalCaseImpl implements IEvalCase {

    private static final String DEFAULT_MODEL_MARKER = "__DEFAULT__";

    private final IEvalRepository evalRepository;
    private final ICreateAgentRunCase createAgentRunCase;
    private final IAgentRunRepository runRepository;
    private final IContextSnapshotRepository contextSnapshotRepository;
    private final AgentRuntimeProperties properties;

    @Override
    public EvalBenchmarkResponseDTO createBenchmark(EvalBenchmarkRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.name()) || !StringUtils.hasText(request.workspaceKey())
                || !StringUtils.hasText(request.task())) {
            throw new AppException("INVALID_ARGUMENT", "benchmark name、workspaceKey 和 task 不能为空");
        }
        EvalBenchmark benchmark = EvalBenchmark.builder()
                .benchmarkId("bench_" + UUID.randomUUID().toString().replace("-", ""))
                .name(request.name())
                .workspaceKey(request.workspaceKey())
                .task(request.task())
                .permissionLevel(StringUtils.hasText(request.permissionLevel()) ? request.permissionLevel() : "DEFAULT")
                .modelKey(request.modelKey())
                .expectedOutcome(request.expectedOutcome())
                .evaluatorType(StringUtils.hasText(request.evaluatorType()) ? request.evaluatorType() : "RULE")
                .timeoutSeconds(request.timeoutSeconds() == null ? properties.getEval().getCaseTimeoutSeconds() : request.timeoutSeconds())
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        evalRepository.saveBenchmark(benchmark);
        log.info("Eval benchmark 已创建 benchmarkId={} workspaceKey={}", benchmark.getBenchmarkId(), benchmark.getWorkspaceKey());
        return toDto(benchmark);
    }

    @Override
    public EvalRunResponseDTO startRun(StartEvalRunRequestDTO request) {
        if (!properties.getEval().isEnabled()) {
            throw new AppException("EVAL_DISABLED", "Eval 未开启");
        }
        List<EvalBenchmark> benchmarks = evalRepository.listActiveBenchmarks();
        if (benchmarks.isEmpty()) {
            throw new AppException("BENCHMARK_NOT_FOUND", "没有可运行的 benchmark");
        }

        String evalId = "eval_" + UUID.randomUUID().toString().replace("-", "");
        LocalDateTime startedAt = LocalDateTime.now();
        List<EvalCaseResult> results = new ArrayList<>();
        log.info("Eval run 开始 evalId={} benchmarkCount={}", evalId, benchmarks.size());

        for (EvalBenchmark benchmark : benchmarks) {
            for (String modelKey : modelKeysFor(request, benchmark)) {
                results.add(executeCase(evalId, benchmark, modelKey));
            }
        }

        double passRate = results.isEmpty()
                ? 0.0
                : results.stream().filter(result -> Boolean.TRUE.equals(result.getPassed())).count() * 1.0 / results.size();
        String modelKeys = modelKeySummary(results);
        EvalRun evalRun = EvalRun.builder()
                .evalId(evalId)
                .name(request == null || !StringUtils.hasText(request.name()) ? "默认评测" : request.name())
                .status("COMPLETED")
                .modelKeys(modelKeys)
                .passRate(passRate)
                .reportPath(".coder/evals/" + evalId + "/report.json")
                .createdAt(startedAt)
                .startedAt(startedAt)
                .endedAt(LocalDateTime.now())
                .build();
        evalRepository.saveRun(evalRun);
        results.forEach(evalRepository::saveCaseResult);
        writeReport(evalRun, benchmarks, results);
        log.info("Eval run 完成 evalId={} caseCount={} passRate={}", evalId, results.size(), passRate);
        return queryRun(evalId);
    }

    @Override
    public EvalRunResponseDTO queryRun(String evalId) {
        EvalRun run = evalRepository.findRun(evalId)
                .orElseThrow(() -> new AppException("EVAL_NOT_FOUND", "Eval 运行不存在：" + evalId));
        return toDto(run, evalRepository.listCaseResults(evalId));
    }

    private EvalCaseResult executeCase(String evalId, EvalBenchmark benchmark, String modelKey) {
        String effectiveModelKey = DEFAULT_MODEL_MARKER.equals(modelKey) ? null : modelKey;
        String resultModelKey = DEFAULT_MODEL_MARKER.equals(modelKey) ? "DEFAULT" : modelKey;
        String resultPath = ".coder/evals/" + evalId + "/cases/" + benchmark.getBenchmarkId() + "-" + resultModelKey + ".json";
        long start = System.currentTimeMillis();
        try {
            CreateAgentRunResponseDTO response = createAgentRunCase.create(new CreateAgentRunRequestDTO(
                    benchmark.getWorkspaceKey(),
                    benchmark.getTask(),
                    effectiveModelKey,
                    null,
                    benchmark.getPermissionLevel(),
                    null));
            AgentRun run = waitForTerminal(response.runId(), benchmarkTimeoutSeconds(benchmark));
            boolean passed = passed(benchmark, run);
            ContextMetrics metrics = contextMetrics(run.getRunId());
            return EvalCaseResult.builder()
                    .evalId(evalId)
                    .benchmarkId(benchmark.getBenchmarkId())
                    .runId(run.getRunId())
                    .modelKey(resultModelKey)
                    .status(run.getStatus().name())
                    .passed(passed)
                    .attempts(run.getModelCallCount())
                    .modelCalls(run.getModelCallCount())
                    .toolCalls(run.getToolCallCount())
                    .toolSteps(run.getToolCallCount())
                    .durationMs(run.getDurationMs() == null ? System.currentTimeMillis() - start : run.getDurationMs())
                    .failureCategory(failureCategory(run, passed))
                    .contextCompressionRatio(metrics.contextCompressionRatio())
                    .memoryHitCount(metrics.memoryHitCount())
                    .retainedAnchorRate(metrics.retainedAnchorRate())
                    .memoryRecallPrecision(metrics.memoryRecallPrecision())
                    .memoryRecallAtK(metrics.memoryRecallAtK())
                    .staleBlockRate(metrics.staleBlockRate())
                    .repeatedReadCount(metrics.repeatedReadCount())
                    .tokenCost(metrics.tokenCost())
                    .resultPath(resultPath)
                    .createdAt(LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            log.warn("Eval case 执行失败 evalId={} benchmarkId={} modelKey={} reason={}",
                    evalId, benchmark.getBenchmarkId(), resultModelKey, e.getMessage());
            return EvalCaseResult.builder()
                    .evalId(evalId)
                    .benchmarkId(benchmark.getBenchmarkId())
                    .modelKey(resultModelKey)
                    .status("FAILED")
                    .passed(false)
                    .attempts(0)
                    .modelCalls(0)
                    .toolCalls(0)
                    .toolSteps(0)
                    .durationMs(System.currentTimeMillis() - start)
                    .failureCategory("CASE_EXCEPTION")
                    .contextCompressionRatio(0.0)
                    .memoryHitCount(0)
                    .retainedAnchorRate(0.0)
                    .memoryRecallPrecision(0.0)
                    .memoryRecallAtK(0.0)
                    .staleBlockRate(0.0)
                    .repeatedReadCount(0)
                    .tokenCost(0L)
                    .resultPath(resultPath)
                    .createdAt(LocalDateTime.now())
                    .build();
        }
    }

    private AgentRun waitForTerminal(String runId, int timeoutSeconds) {
        LocalDateTime deadline = LocalDateTime.now().plusSeconds(timeoutSeconds);
        AgentRun latest = runRepository.findByRunId(runId)
                .orElseThrow(() -> new AppException("RUN_NOT_FOUND", "Eval 关联运行不存在：" + runId));
        while (!latest.getStatus().isTerminal() && LocalDateTime.now().isBefore(deadline)) {
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AppException("EVAL_INTERRUPTED", "Eval 等待被中断");
            }
            latest = runRepository.findByRunId(runId)
                    .orElseThrow(() -> new AppException("RUN_NOT_FOUND", "Eval 关联运行不存在：" + runId));
        }
        if (!latest.getStatus().isTerminal()) {
            latest.setStatus(AgentRunStatus.CANCELLED);
            latest.setFailureReason("Eval case timeout");
            latest.setEndedAt(LocalDateTime.now());
            latest.setDurationMs(latest.getStartedAt() == null ? null
                    : java.time.Duration.between(latest.getStartedAt(), latest.getEndedAt()).toMillis());
            runRepository.update(latest);
        }
        return latest;
    }

    private boolean passed(EvalBenchmark benchmark, AgentRun run) {
        if (run.getStatus() != AgentRunStatus.SUCCEEDED) {
            return false;
        }
        if (!StringUtils.hasText(benchmark.getExpectedOutcome())) {
            return true;
        }
        return run.getFinalAnswer() != null && run.getFinalAnswer().contains(benchmark.getExpectedOutcome());
    }

    private String failureCategory(AgentRun run, boolean passed) {
        if (passed) {
            return "PASS";
        }
        if (run.getStatus() == AgentRunStatus.CANCELLED) {
            return "TIMEOUT_OR_CANCELLED";
        }
        if (run.getStatus() == AgentRunStatus.REJECTED) {
            return "APPROVAL_REJECTED";
        }
        if (run.getFailureReason() != null && run.getFailureReason().toLowerCase().contains("budget")) {
            return "BUDGET_EXHAUSTED";
        }
        if (run.getStatus() == AgentRunStatus.FAILED) {
            return "RUN_FAILED";
        }
        return "ASSERTION_FAILED";
    }

    private ContextMetrics contextMetrics(String runId) {
        List<ContextSnapshot> snapshots = contextSnapshotRepository.listByRunId(runId);
        if (snapshots.isEmpty()) {
            return new ContextMetrics(0.0, 0, 0.0, 0.0, 0.0, 0.0, 0, 0L);
        }
        ContextSnapshot latest = snapshots.getLast();
        int memoryHitCount = latest.getMemoryHitCount() == null ? 0 : latest.getMemoryHitCount();
        int staleCount = latest.getStaleMemoryCount() == null ? 0 : latest.getStaleMemoryCount();
        int rawTokens = latest.getRawEstimatedTokens() == null ? 0 : latest.getRawEstimatedTokens();
        int finalTokens = latest.getFinalEstimatedTokens() == null ? 0 : latest.getFinalEstimatedTokens();
        return new ContextMetrics(
                latest.getCompressionRatio() == null ? 0.0 : latest.getCompressionRatio(),
                memoryHitCount,
                1.0,
                memoryHitCount == 0 ? 0.0 : 1.0,
                memoryHitCount == 0 ? 0.0 : 1.0,
                staleCount == 0 ? 0.0 : 1.0,
                0,
                Math.max(rawTokens, finalTokens));
    }

    private List<String> modelKeysFor(StartEvalRunRequestDTO request, EvalBenchmark benchmark) {
        if (request != null && request.modelKeys() != null && !request.modelKeys().isEmpty()) {
            return request.modelKeys().stream().filter(StringUtils::hasText).distinct().toList();
        }
        if (StringUtils.hasText(benchmark.getModelKey())) {
            return List.of(benchmark.getModelKey());
        }
        return List.of(DEFAULT_MODEL_MARKER);
    }

    private int benchmarkTimeoutSeconds(EvalBenchmark benchmark) {
        return benchmark.getTimeoutSeconds() == null || benchmark.getTimeoutSeconds() <= 0
                ? properties.getEval().getCaseTimeoutSeconds()
                : benchmark.getTimeoutSeconds();
    }

    private String modelKeySummary(List<EvalCaseResult> results) {
        Set<String> keys = new LinkedHashSet<>();
        for (EvalCaseResult result : results) {
            keys.add(result.getModelKey());
        }
        return String.join(",", keys);
    }

    private void writeReport(EvalRun evalRun, List<EvalBenchmark> benchmarks, List<EvalCaseResult> results) {
        try {
            Path dir = Path.of(".coder", "evals", evalRun.getEvalId());
            Files.createDirectories(dir.resolve("cases"));
            long passed = results.stream().filter(result -> Boolean.TRUE.equals(result.getPassed())).count();
            Files.writeString(dir.resolve("report.json"), reportJson(evalRun, benchmarks, results, passed), StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("report.md"), reportMarkdown(evalRun, benchmarks, results), StandardCharsets.UTF_8);
            for (EvalCaseResult result : results) {
                Files.writeString(dir.resolve("cases").resolve(Path.of(result.getResultPath()).getFileName().toString()),
                        caseJson(result), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("Eval 报告写入失败 evalId={} reason={}", evalRun.getEvalId(), e.getMessage());
        }
    }

    private String reportJson(EvalRun evalRun, List<EvalBenchmark> benchmarks, List<EvalCaseResult> results, long passed) {
        return """
                {"evalId":"%s","status":"%s","benchmarkCount":%d,"caseCount":%d,"passed":%d,"passRate":%.4f,"compressionRatio":%.4f,"retainedAnchorRate":%.4f,"memoryRecallPrecision":%.4f,"memoryRecallAtK":%.4f,"staleBlockRate":%.4f,"repeatedReadCount":%d,"toolSteps":%d,"tokenCost":%d,"markdownReport":"report.md"}
                """.formatted(
                evalRun.getEvalId(),
                evalRun.getStatus(),
                benchmarks.size(),
                results.size(),
                passed,
                evalRun.getPassRate(),
                averageDouble(results.stream().map(EvalCaseResult::getContextCompressionRatio).toList()),
                averageDouble(results.stream().map(EvalCaseResult::getRetainedAnchorRate).toList()),
                averageDouble(results.stream().map(EvalCaseResult::getMemoryRecallPrecision).toList()),
                averageDouble(results.stream().map(EvalCaseResult::getMemoryRecallAtK).toList()),
                averageDouble(results.stream().map(EvalCaseResult::getStaleBlockRate).toList()),
                results.stream().mapToInt(result -> nullToZero(result.getRepeatedReadCount())).sum(),
                results.stream().mapToInt(result -> nullToZero(result.getToolSteps())).sum(),
                results.stream().mapToLong(result -> result.getTokenCost() == null ? 0L : result.getTokenCost()).sum());
    }

    private double averageDouble(List<Double> values) {
        return values.stream()
                .filter(value -> value != null)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }
    private String reportMarkdown(EvalRun evalRun, List<EvalBenchmark> benchmarks, List<EvalCaseResult> results) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Eval Report\n\n")
                .append("- evalId: `").append(evalRun.getEvalId()).append("`\n")
                .append("- status: `").append(evalRun.getStatus()).append("`\n")
                .append("- benchmarkCount: `").append(benchmarks.size()).append("`\n")
                .append("- caseCount: `").append(results.size()).append("`\n")
                .append("- passRate: `").append(String.format("%.4f", evalRun.getPassRate())).append("`\n\n")
                .append("| Benchmark | Model | Status | Passed | Attempts | Tool Calls | Failure Category |\n")
                .append("|---|---|---|---|---:|---:|---|\n");
        for (EvalCaseResult result : results) {
            builder.append("| ")
                    .append(result.getBenchmarkId()).append(" | ")
                    .append(result.getModelKey()).append(" | ")
                    .append(result.getStatus()).append(" | ")
                    .append(Boolean.TRUE.equals(result.getPassed())).append(" | ")
                    .append(result.getAttempts()).append(" | ")
                    .append(result.getToolCalls()).append(" | ")
                    .append(result.getFailureCategory()).append(" |\n");
        }
        return builder.toString();
    }

    private String caseJson(EvalCaseResult result) {
        return """
                {"benchmarkId":"%s","runId":"%s","modelKey":"%s","status":"%s","passed":%s,"attempts":%d,"modelCalls":%d,"toolCalls":%d,"toolSteps":%d,"durationMs":%d,"failureCategory":"%s","contextCompressionRatio":%.4f,"memoryHitCount":%d,"retainedAnchorRate":%.4f,"memoryRecallPrecision":%.4f,"memoryRecallAtK":%.4f,"staleBlockRate":%.4f,"repeatedReadCount":%d,"tokenCost":%d}
                """.formatted(
                result.getBenchmarkId(),
                result.getRunId() == null ? "" : result.getRunId(),
                result.getModelKey(),
                result.getStatus(),
                Boolean.TRUE.equals(result.getPassed()),
                nullToZero(result.getAttempts()),
                nullToZero(result.getModelCalls()),
                nullToZero(result.getToolCalls()),
                nullToZero(result.getToolSteps()),
                result.getDurationMs() == null ? 0L : result.getDurationMs(),
                result.getFailureCategory(),
                result.getContextCompressionRatio() == null ? 0.0 : result.getContextCompressionRatio(),
                nullToZero(result.getMemoryHitCount()),
                result.getRetainedAnchorRate() == null ? 0.0 : result.getRetainedAnchorRate(),
                result.getMemoryRecallPrecision() == null ? 0.0 : result.getMemoryRecallPrecision(),
                result.getMemoryRecallAtK() == null ? 0.0 : result.getMemoryRecallAtK(),
                result.getStaleBlockRate() == null ? 0.0 : result.getStaleBlockRate(),
                nullToZero(result.getRepeatedReadCount()),
                result.getTokenCost() == null ? 0L : result.getTokenCost());
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private EvalBenchmarkResponseDTO toDto(EvalBenchmark benchmark) {
        return new EvalBenchmarkResponseDTO(benchmark.getBenchmarkId(), benchmark.getName(), benchmark.getWorkspaceKey(),
                benchmark.getTask(), benchmark.getPermissionLevel(), benchmark.getModelKey(), benchmark.getExpectedOutcome(),
                benchmark.getEvaluatorType(), benchmark.getTimeoutSeconds(), benchmark.getStatus(), benchmark.getCreatedAt());
    }

    private EvalRunResponseDTO toDto(EvalRun run, List<EvalCaseResult> cases) {
        return new EvalRunResponseDTO(run.getEvalId(), run.getName(), run.getStatus(), run.getModelKeys(), run.getPassRate(),
                run.getReportPath(), run.getCreatedAt(), run.getStartedAt(), run.getEndedAt(),
                cases.stream().map(this::toDto).toList());
    }

    private EvalCaseResultDTO toDto(EvalCaseResult result) {
        return new EvalCaseResultDTO(result.getBenchmarkId(), result.getRunId(), result.getModelKey(), result.getStatus(),
                result.getPassed(), result.getAttempts(), result.getModelCalls(), result.getToolCalls(), result.getToolSteps(),
                result.getDurationMs(), result.getFailureCategory(), result.getContextCompressionRatio(),
                result.getMemoryHitCount(), result.getRetainedAnchorRate(), result.getMemoryRecallPrecision(),
                result.getMemoryRecallAtK(), result.getStaleBlockRate(), result.getRepeatedReadCount(),
                result.getTokenCost(), result.getResultPath());
    }

    private record ContextMetrics(double contextCompressionRatio,
                                  int memoryHitCount,
                                  double retainedAnchorRate,
                                  double memoryRecallPrecision,
                                  double memoryRecallAtK,
                                  double staleBlockRate,
                                  int repeatedReadCount,
                                  long tokenCost) {
    }
}
