package cn.noname.coder.agent.trigger.http;

import cn.noname.coder.agent.api.dto.*;
import cn.noname.coder.agent.cases.agent.ICancelAgentRunCase;
import cn.noname.coder.agent.cases.agent.ICreateAgentRunCase;
import cn.noname.coder.agent.cases.agent.IQueryAgentRunCase;
import cn.noname.coder.agent.cases.agent.IQueryAgentRunDraftCase;
import cn.noname.coder.agent.cases.agent.IQueryRunTraceCase;
import cn.noname.coder.agent.types.common.Response;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * AgentRun REST API 入口。Controller 只做路由，不承载业务编排。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent-runs")
public class AgentRunController {

    private final ICreateAgentRunCase createAgentRunCase;
    private final IQueryAgentRunCase queryAgentRunCase;
    private final IQueryAgentRunDraftCase queryAgentRunDraftCase;
    private final IQueryRunTraceCase queryRunTraceCase;
    private final ICancelAgentRunCase cancelAgentRunCase;

    @PostMapping
    public Response<CreateAgentRunResponseDTO> create(@RequestBody CreateAgentRunRequestDTO request) {
        return Response.ok(createAgentRunCase.create(request));
    }

    @GetMapping("/{runId}")
    public Response<AgentRunResponseDTO> query(@PathVariable("runId") String runId) {
        return Response.ok(queryAgentRunCase.query(runId));
    }

    @GetMapping("/{runId}/draft")
    public Response<AgentRunDraftResponseDTO> draft(@PathVariable("runId") String runId) {
        return Response.ok(queryAgentRunDraftCase.queryDraft(runId));
    }

    @GetMapping("/{runId}/trace")
    public Response<TraceQueryResponseDTO> trace(@PathVariable("runId") String runId) {
        return Response.ok(queryRunTraceCase.queryTrace(runId));
    }

    @PostMapping("/{runId}/cancel")
    public Response<CancelAgentRunResponseDTO> cancel(@PathVariable("runId") String runId) {
        return Response.ok(cancelAgentRunCase.cancel(runId));
    }
}
