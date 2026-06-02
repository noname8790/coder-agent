package cn.noname.coder.agent.api.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * SSE 运行事件。
 */
public record AgentRunEventDTO(
        String eventId,
        String runId,
        String type,
        LocalDateTime time,
        Map<String, Object> payload
) {
}
