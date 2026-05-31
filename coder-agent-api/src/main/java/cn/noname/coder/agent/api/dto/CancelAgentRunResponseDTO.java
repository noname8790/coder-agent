package cn.noname.coder.agent.api.dto;

/**
 * 取消运行响应。
 */
public record CancelAgentRunResponseDTO(String runId, String status) {
}
