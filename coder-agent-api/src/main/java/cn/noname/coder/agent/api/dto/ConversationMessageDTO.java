package cn.noname.coder.agent.api.dto;

import java.time.LocalDateTime;

/**
 * 会话消息响应。
 */
public record ConversationMessageDTO(
        String messageId,
        String conversationId,
        String runId,
        String role,
        String content,
        String status,
        String failureReason,
        LocalDateTime createdAt,
        DiffSummaryDTO diffSummary,
        Long sequenceNo,
        String visibilityStatus,
        String rolledBackByCheckpointId,
        String modelDisplayName
) {
    public ConversationMessageDTO(String messageId,
                                  String conversationId,
                                  String runId,
                                  String role,
                                  String content,
                                  String status,
                                  String failureReason,
                                  LocalDateTime createdAt,
                                  DiffSummaryDTO diffSummary) {
        this(messageId, conversationId, runId, role, content, status, failureReason, createdAt, diffSummary, null, "VISIBLE", null, null);
    }
}
