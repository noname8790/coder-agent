package cn.noname.coder.agent.api.dto;

import java.time.LocalDateTime;

/**
 * Agent 运行中的可见回复草稿，用于客户端刷新或切换会话后恢复流式输出。
 */
public record AgentRunDraftResponseDTO(
        String runId,
        String content,
        String status,
        String failureReason,
        LocalDateTime updatedAt
) {
}
