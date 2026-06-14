package cn.noname.coder.agent.api.dto;

public record ContextBudgetDTO(
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
