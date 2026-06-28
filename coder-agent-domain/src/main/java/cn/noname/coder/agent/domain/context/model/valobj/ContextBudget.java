package cn.noname.coder.agent.domain.context.model.valobj;

public record ContextBudget(
        Integer maxContextTokens,
        Integer maxOutputTokens,
        Integer inputBudgetTokens,
        Integer safetyReserveTokens,
        Integer prefixBudgetTokens,
        Integer workingMemoryBudgetTokens,
        Integer memoryBudgetTokens,
        Integer fileSummaryBudgetTokens,
        Integer rawFileBudgetTokens,
        Integer toolResultBudgetTokens,
        Integer recentMessageBudgetTokens,
        Integer runTraceBudgetTokens
) {
    public ContextBudget(Integer maxContextTokens,
                         Integer maxOutputTokens,
                         Integer inputBudgetTokens,
                         Integer memoryBudgetTokens,
                         Integer fileSummaryBudgetTokens,
                         Integer rawFileBudgetTokens,
                         Integer toolResultBudgetTokens,
                         Integer recentMessageBudgetTokens) {
        this(maxContextTokens, maxOutputTokens, inputBudgetTokens, null, null, null,
                memoryBudgetTokens, fileSummaryBudgetTokens, rawFileBudgetTokens,
                toolResultBudgetTokens, recentMessageBudgetTokens, null);
    }
}
