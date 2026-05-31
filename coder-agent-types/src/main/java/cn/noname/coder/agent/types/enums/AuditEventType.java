package cn.noname.coder.agent.types.enums;

/**
 * 审计事件类型，重点记录安全治理和运行失败原因。
 */
public enum AuditEventType {
    STATUS_CHANGED,
    MODEL_CALL_FAILED,
    TOOL_REJECTED,
    PATH_ESCAPE,
    INVALID_ARGUMENT,
    DANGEROUS_COMMAND,
    COMMAND_NOT_ALLOWED,
    BUDGET_EXHAUSTED,
    WORKSPACE_REJECTED
}
