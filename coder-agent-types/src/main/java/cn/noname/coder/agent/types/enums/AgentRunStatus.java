package cn.noname.coder.agent.types.enums;

/**
 * Agent 运行生命周期状态。
 */
public enum AgentRunStatus {
    CREATED,
    RUNNING,
    WAITING_APPROVAL,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    REJECTED;

    /**
     * 终态运行不会再被后台执行循环继续推进。
     */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED || this == REJECTED;
    }
}
