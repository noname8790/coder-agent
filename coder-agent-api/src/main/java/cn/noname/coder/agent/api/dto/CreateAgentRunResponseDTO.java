package cn.noname.coder.agent.api.dto;

import java.time.LocalDateTime;

/**
 * 创建运行立即返回，后台执行不阻塞 HTTP 请求。
 */
public record CreateAgentRunResponseDTO(String runId, String status, LocalDateTime createdAt) {
}
