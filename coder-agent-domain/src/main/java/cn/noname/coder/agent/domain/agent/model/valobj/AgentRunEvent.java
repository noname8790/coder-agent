package cn.noname.coder.agent.domain.agent.model.valobj;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Agent 运行事件，供 SSE 和历史回放使用。
 */
public record AgentRunEvent(
        String eventId,
        String runId,
        String type,
        LocalDateTime time,
        Map<String, Object> payload
) {
}
