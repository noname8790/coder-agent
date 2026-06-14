package cn.noname.coder.agent.domain.agent.adapter.port;

import cn.noname.coder.agent.domain.agent.model.entity.EvalBenchmark;
import cn.noname.coder.agent.domain.agent.model.entity.EvalRun;

import java.util.List;

public interface IEvalPort {

    EvalRun start(EvalRun evalRun, List<EvalBenchmark> benchmarks);
}
