package cn.noname.coder.agent.domain.agent.model.valobj;

/**
 * SSE 稳定事件类型。
 */
public enum AgentRunEventType {
    RUN_STARTED("run_started"),
    MODEL_CALL_STARTED("model_call_started"),
    MODEL_CALL_COMPLETED("model_call_completed"),
    ASSISTANT_MESSAGE_STARTED("assistant_message_started"),
    ASSISTANT_DELTA("assistant_delta"),
    ASSISTANT_MESSAGE_COMPLETED("assistant_message_completed"),
    ASSISTANT_MESSAGE_CANCELLED("assistant_message_cancelled"),
    MODEL_STREAM_FAILURE("model_stream_failure"),
    TOOL_CALL_STARTED("tool_call_started"),
    TOOL_CALL_COMPLETED("tool_call_completed"),
    AUDIT_EVENT("audit_event"),
    FILE_CHANGED("file_changed"),
    TEST_REPORTED("test_reported"),
    GIT_COMMITTED("git_committed"),
    PR_DRAFT_GENERATED("pr_draft_generated"),
    RUN_FINISHED("run_finished");

    private final String code;

    AgentRunEventType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
