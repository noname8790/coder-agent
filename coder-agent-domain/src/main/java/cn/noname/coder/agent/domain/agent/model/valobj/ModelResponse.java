package cn.noname.coder.agent.domain.agent.model.valobj;

import java.util.List;

/**
 * 统一模型响应，finalAnswer 与 toolInvocations 二选一为主。
 */
public record ModelResponse(String responseId, String finalAnswer, List<ToolInvocation> toolInvocations, String rawSummary) {

    public boolean hasToolCalls() {
        return toolInvocations != null && !toolInvocations.isEmpty();
    }
}
