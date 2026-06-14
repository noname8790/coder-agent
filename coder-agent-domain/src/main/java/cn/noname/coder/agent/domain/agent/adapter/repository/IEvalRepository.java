package cn.noname.coder.agent.domain.agent.adapter.repository;

import cn.noname.coder.agent.domain.agent.model.entity.EvalBenchmark;
import cn.noname.coder.agent.domain.agent.model.entity.EvalCaseResult;
import cn.noname.coder.agent.domain.agent.model.entity.EvalRun;

import java.util.List;
import java.util.Optional;

public interface IEvalRepository {

    void saveBenchmark(EvalBenchmark benchmark);

    void saveRun(EvalRun evalRun);

    void saveCaseResult(EvalCaseResult result);

    List<EvalBenchmark> listActiveBenchmarks();

    Optional<EvalRun> findRun(String evalId);

    List<EvalCaseResult> listCaseResults(String evalId);
}
