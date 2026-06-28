package cn.noname.coder.agent.cases.context;

import cn.noname.coder.agent.domain.context.model.valobj.ContextBudget;
import cn.noname.coder.agent.domain.model.model.valobj.ModelBackendConfig;
import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import org.springframework.stereotype.Component;

@Component
public class ContextBudgetResolver {

    private final AgentRuntimeProperties properties;

    public ContextBudgetResolver(AgentRuntimeProperties properties) {
        this.properties = properties;
    }

    public ContextBudget resolve(ModelBackendConfig modelConfig) {
        ContextBudget modelBudget = modelConfig == null ? null : modelConfig.budget();
        AgentRuntimeProperties.Context global = properties.getContext();
        return new ContextBudget(
                first(modelBudget == null ? null : modelBudget.maxContextTokens(), global.getMaxContextTokens()),
                first(modelBudget == null ? null : modelBudget.maxOutputTokens(), global.getMaxOutputTokens()),
                first(modelBudget == null ? null : modelBudget.inputBudgetTokens(), global.getMaxInputTokens()),
                first(modelBudget == null ? null : modelBudget.safetyReserveTokens(), global.getSafetyReserveTokens()),
                first(modelBudget == null ? null : modelBudget.prefixBudgetTokens(), global.getPrefixBudgetTokens()),
                first(modelBudget == null ? null : modelBudget.workingMemoryBudgetTokens(), global.getWorkingMemoryBudgetTokens()),
                first(modelBudget == null ? null : modelBudget.memoryBudgetTokens(), global.getMemoryBudgetTokens()),
                first(modelBudget == null ? null : modelBudget.fileSummaryBudgetTokens(), global.getFileSummaryBudgetTokens()),
                first(modelBudget == null ? null : modelBudget.rawFileBudgetTokens(), global.getRawSnippetBudgetTokens()),
                first(modelBudget == null ? null : modelBudget.toolResultBudgetTokens(), global.getToolResultBudgetTokens()),
                first(modelBudget == null ? null : modelBudget.recentMessageBudgetTokens(), global.getRecentMessageBudgetTokens()),
                first(modelBudget == null ? null : modelBudget.runTraceBudgetTokens(), global.getRunTraceBudgetTokens()));
    }

    public String budgetSource(ModelBackendConfig modelConfig) {
        return modelConfig != null && modelConfig.budget() != null ? "model:" + modelConfig.modelKey() : "global-default";
    }

    private Integer first(Integer value, Integer fallback) {
        return value != null && value > 0 ? value : fallback;
    }
}
