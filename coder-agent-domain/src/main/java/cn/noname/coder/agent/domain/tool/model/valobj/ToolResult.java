package cn.noname.coder.agent.domain.tool.model.valobj;

import cn.noname.coder.agent.domain.workspace.model.valobj.ChangedFile;
import cn.noname.coder.agent.types.enums.CallStatus;

import java.util.List;

/**
 * 工具执行结果，summary 进入模型上下文，fullOutput 可落盘。
 */
public record ToolResult(
        CallStatus status,
        String summary,
        String fullOutput,
        Integer exitCode,
        String errorMessage,
        List<ChangedFile> changedFiles,
        TestCommandReport testReport
) {

    public ToolResult(CallStatus status, String summary, String fullOutput, Integer exitCode, String errorMessage) {
        this(status, summary, fullOutput, exitCode, errorMessage, List.of(), null);
    }

    public boolean success() {
        return status == CallStatus.SUCCESS;
    }
}
