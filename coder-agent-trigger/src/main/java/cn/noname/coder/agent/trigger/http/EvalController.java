package cn.noname.coder.agent.trigger.http;

import cn.noname.coder.agent.api.dto.EvalBenchmarkRequestDTO;
import cn.noname.coder.agent.api.dto.EvalBenchmarkResponseDTO;
import cn.noname.coder.agent.api.dto.EvalRunResponseDTO;
import cn.noname.coder.agent.api.dto.StartEvalRunRequestDTO;
import cn.noname.coder.agent.cases.eval.IEvalCase;
import cn.noname.coder.agent.types.common.Response;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/evals")
@RequiredArgsConstructor
public class EvalController {

    private final IEvalCase evalCase;

    @PostMapping("/benchmarks")
    public Response<EvalBenchmarkResponseDTO> createBenchmark(@RequestBody EvalBenchmarkRequestDTO request) {
        return Response.ok(evalCase.createBenchmark(request));
    }

    @PostMapping("/runs")
    public Response<EvalRunResponseDTO> startRun(@RequestBody(required = false) StartEvalRunRequestDTO request) {
        return Response.ok(evalCase.startRun(request));
    }

    @GetMapping("/runs/{evalId}")
    public Response<EvalRunResponseDTO> queryRun(@PathVariable("evalId") String evalId) {
        return Response.ok(evalCase.queryRun(evalId));
    }
}
