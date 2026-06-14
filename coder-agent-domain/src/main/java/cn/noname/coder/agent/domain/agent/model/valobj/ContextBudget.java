package cn.noname.coder.agent.domain.agent.model.valobj;

public record ContextBudget(
        Integer maxContextTokens,
        Integer maxOutputTokens,
        Integer inputBudgetTokens,
        Integer memoryBudgetTokens,
        Integer fileSummaryBudgetTokens,
        Integer rawFileBudgetTokens,
        Integer toolResultBudgetTokens,
        Integer recentMessageBudgetTokens
) {
}
