package cn.noname.coder.agent.cases.eval;

import cn.noname.coder.agent.api.dto.EvalBenchmarkRequestDTO;
import cn.noname.coder.agent.api.dto.EvalBenchmarkResponseDTO;
import cn.noname.coder.agent.api.dto.EvalRunResponseDTO;
import cn.noname.coder.agent.api.dto.StartEvalRunRequestDTO;

public interface IEvalCase {

    EvalBenchmarkResponseDTO createBenchmark(EvalBenchmarkRequestDTO request);

    EvalRunResponseDTO startRun(StartEvalRunRequestDTO request);

    EvalRunResponseDTO queryRun(String evalId);
}
