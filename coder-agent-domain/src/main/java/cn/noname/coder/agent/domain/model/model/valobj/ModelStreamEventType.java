package cn.noname.coder.agent.domain.model.model.valobj;

public enum ModelStreamEventType {
    ASSISTANT_MESSAGE_STARTED,
    ASSISTANT_DELTA,
    TOOL_CALL_STARTED,
    TOOL_CALL_ARGUMENTS_DELTA,
    TOOL_CALL_COMPLETED,
    MODEL_COMPLETED,
    MODEL_FAILED
}
