package cn.noname.coder.agent.api.dto;

import java.util.List;

/**
 * trace 查询响应。
 */
public record TraceQueryResponseDTO(String runId, List<TraceEventDTO> events) {
}
