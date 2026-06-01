package cn.noname.coder.agent.domain.agent.model.valobj;

/**
 * Agent Run 的运行模式。默认只读，显式 EDIT 才允许进入编辑工具权限判断。
 */
public enum AgentRunMode {
    READ_ONLY,
    EDIT
}
