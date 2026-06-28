package cn.noname.coder.agent.domain.model.model.valobj;

import cn.noname.coder.agent.domain.tool.model.valobj.ToolInvocation;

import java.util.List;

/**
 * 统一模型响应，finalAnswer 与 toolInvocations 二选一为主。
 */
public record ModelResponse(String responseId, String finalAnswer, List<ToolInvocation> toolInvocations, String rawSummary) {

    public boolean hasToolCalls() {
        return toolInvocations != null && !toolInvocations.isEmpty();
    }
}
