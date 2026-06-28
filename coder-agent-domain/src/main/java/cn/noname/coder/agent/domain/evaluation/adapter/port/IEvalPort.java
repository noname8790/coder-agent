package cn.noname.coder.agent.domain.evaluation.adapter.port;

import cn.noname.coder.agent.domain.evaluation.model.entity.EvalBenchmark;
import cn.noname.coder.agent.domain.evaluation.model.entity.EvalRun;

import java.util.List;

public interface IEvalPort {

    EvalRun start(EvalRun evalRun, List<EvalBenchmark> benchmarks);
}
