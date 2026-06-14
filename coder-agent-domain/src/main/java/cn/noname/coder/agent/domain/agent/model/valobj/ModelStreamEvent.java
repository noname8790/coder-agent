package cn.noname.coder.agent.domain.agent.model.valobj;

public record ModelStreamEvent(
        ModelStreamEventType type,
        String contentDelta,
        String toolCallId,
        String toolName,
        String argumentsDelta,
        String errorMessage
) {
}
