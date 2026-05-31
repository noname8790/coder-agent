package cn.noname.coder.agent.domain.agent.model.valobj;

import cn.noname.coder.agent.types.enums.CallStatus;

/**
 * 工具执行结果，summary 进入模型上下文，fullOutput 可落盘。
 */
public record ToolResult(CallStatus status, String summary, String fullOutput, Integer exitCode, String errorMessage) {

    public boolean success() {
        return status == CallStatus.SUCCESS;
    }
}
