package cn.noname.coder.agent.cases.agent;

import cn.noname.coder.agent.api.dto.TraceQueryResponseDTO;

/**
 * 查询可回放 trace 事件流。
 */
public interface IQueryRunTraceCase {

    TraceQueryResponseDTO queryTrace(String runId);
}
