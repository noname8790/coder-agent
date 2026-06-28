package cn.noname.coder.agent.infrastructure.adapter.repository;

import cn.noname.coder.agent.domain.evaluation.adapter.repository.IEvalRepository;
import cn.noname.coder.agent.domain.evaluation.model.entity.EvalBenchmark;
import cn.noname.coder.agent.domain.evaluation.model.entity.EvalCaseResult;
import cn.noname.coder.agent.domain.evaluation.model.entity.EvalRun;
import cn.noname.coder.agent.infrastructure.dao.IEvalBenchmarkDao;
import cn.noname.coder.agent.infrastructure.dao.IEvalCaseResultDao;
import cn.noname.coder.agent.infrastructure.dao.IEvalRunDao;
import cn.noname.coder.agent.infrastructure.dao.po.EvalBenchmarkPO;
import cn.noname.coder.agent.infrastructure.dao.po.EvalCaseResultPO;
import cn.noname.coder.agent.infrastructure.dao.po.EvalRunPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class EvalRepository implements IEvalRepository {

    private final IEvalBenchmarkDao benchmarkDao;
    private final IEvalRunDao runDao;
    private final IEvalCaseResultDao caseResultDao;

    @Override
    public void saveBenchmark(EvalBenchmark benchmark) {
        EvalBenchmarkPO po = new EvalBenchmarkPO();
        po.setBenchmarkId(benchmark.getBenchmarkId());
        po.setName(benchmark.getName());
        po.setWorkspaceKey(benchmark.getWorkspaceKey());
        po.setTask(benchmark.getTask());
        po.setPermissionLevel(benchmark.getPermissionLevel());
        po.setModelKey(benchmark.getModelKey());
        po.setExpectedOutcome(benchmark.getExpectedOutcome());
        po.setEvaluatorType(benchmark.getEvaluatorType());
        po.setTimeoutSeconds(benchmark.getTimeoutSeconds());
        po.setStatus(benchmark.getStatus());
        po.setCreatedAt(benchmark.getCreatedAt());
        po.setUpdatedAt(benchmark.getUpdatedAt());
        benchmarkDao.insert(po);
    }

    @Override
    public void saveRun(EvalRun evalRun) {
        EvalRunPO po = new EvalRunPO();
        po.setEvalId(evalRun.getEvalId());
        po.setName(evalRun.getName());
        po.setStatus(evalRun.getStatus());
        po.setModelKeys(evalRun.getModelKeys());
        po.setPassRate(evalRun.getPassRate());
        po.setReportPath(evalRun.getReportPath());
        po.setCreatedAt(evalRun.getCreatedAt());
        po.setStartedAt(evalRun.getStartedAt());
        po.setEndedAt(evalRun.getEndedAt());
        runDao.insert(po);
    }

    @Override
    public void saveCaseResult(EvalCaseResult result) {
        EvalCaseResultPO po = new EvalCaseResultPO();
        po.setEvalId(result.getEvalId());
        po.setBenchmarkId(result.getBenchmarkId());
        po.setRunId(result.getRunId());
        po.setModelKey(result.getModelKey());
        po.setStatus(result.getStatus());
        po.setPassed(result.getPassed());
        po.setAttempts(result.getAttempts());
        po.setModelCalls(result.getModelCalls());
        po.setToolCalls(result.getToolCalls());
        po.setToolSteps(result.getToolSteps());
        po.setDurationMs(result.getDurationMs());
        po.setFailureCategory(result.getFailureCategory());
        po.setContextCompressionRatio(result.getContextCompressionRatio());
        po.setMemoryHitCount(result.getMemoryHitCount());
        po.setRetainedAnchorRate(result.getRetainedAnchorRate());
        po.setMemoryRecallPrecision(result.getMemoryRecallPrecision());
        po.setMemoryRecallAtK(result.getMemoryRecallAtK());
        po.setStaleBlockRate(result.getStaleBlockRate());
        po.setRepeatedReadCount(result.getRepeatedReadCount());
        po.setTokenCost(result.getTokenCost());
        po.setResultPath(result.getResultPath());
        po.setCreatedAt(result.getCreatedAt());
        caseResultDao.insert(po);
    }

    @Override
    public List<EvalBenchmark> listActiveBenchmarks() {
        return benchmarkDao.selectList(new LambdaQueryWrapper<EvalBenchmarkPO>()
                        .eq(EvalBenchmarkPO::getStatus, "ACTIVE"))
                .stream()
                .map(this::toBenchmark)
                .toList();
    }

    @Override
    public Optional<EvalRun> findRun(String evalId) {
        return Optional.ofNullable(runDao.selectOne(new LambdaQueryWrapper<EvalRunPO>()
                .eq(EvalRunPO::getEvalId, evalId)))
                .map(po -> EvalRun.builder()
                        .id(po.getId())
                        .evalId(po.getEvalId())
                        .name(po.getName())
                        .status(po.getStatus())
                        .modelKeys(po.getModelKeys())
                        .passRate(po.getPassRate())
                        .reportPath(po.getReportPath())
                        .createdAt(po.getCreatedAt())
                        .startedAt(po.getStartedAt())
                        .endedAt(po.getEndedAt())
                        .build());
    }

    @Override
    public List<EvalCaseResult> listCaseResults(String evalId) {
        return caseResultDao.selectList(new LambdaQueryWrapper<EvalCaseResultPO>()
                        .eq(EvalCaseResultPO::getEvalId, evalId))
                .stream()
                .map(po -> EvalCaseResult.builder()
                        .id(po.getId())
                        .evalId(po.getEvalId())
                        .benchmarkId(po.getBenchmarkId())
                        .runId(po.getRunId())
                        .modelKey(po.getModelKey())
                        .status(po.getStatus())
                        .passed(po.getPassed())
                        .attempts(po.getAttempts())
                        .modelCalls(po.getModelCalls())
                        .toolCalls(po.getToolCalls())
                        .toolSteps(po.getToolSteps())
                        .durationMs(po.getDurationMs())
                        .failureCategory(po.getFailureCategory())
                        .contextCompressionRatio(po.getContextCompressionRatio())
                        .memoryHitCount(po.getMemoryHitCount())
                        .retainedAnchorRate(po.getRetainedAnchorRate())
                        .memoryRecallPrecision(po.getMemoryRecallPrecision())
                        .memoryRecallAtK(po.getMemoryRecallAtK())
                        .staleBlockRate(po.getStaleBlockRate())
                        .repeatedReadCount(po.getRepeatedReadCount())
                        .tokenCost(po.getTokenCost())
                        .resultPath(po.getResultPath())
                        .createdAt(po.getCreatedAt())
                        .build())
                .toList();
    }

    private EvalBenchmark toBenchmark(EvalBenchmarkPO po) {
        return EvalBenchmark.builder()
                .id(po.getId())
                .benchmarkId(po.getBenchmarkId())
                .name(po.getName())
                .workspaceKey(po.getWorkspaceKey())
                .task(po.getTask())
                .permissionLevel(po.getPermissionLevel())
                .modelKey(po.getModelKey())
                .expectedOutcome(po.getExpectedOutcome())
                .evaluatorType(po.getEvaluatorType())
                .timeoutSeconds(po.getTimeoutSeconds())
                .status(po.getStatus())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }
}
