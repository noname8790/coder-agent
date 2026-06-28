package cn.noname.coder.agent.domain.model.model.valobj;

public record ModelStreamEvent(
        ModelStreamEventType type,
        String contentDelta,
        String toolCallId,
        String toolName,
        String argumentsDelta,
        String errorMessage
) {
}
