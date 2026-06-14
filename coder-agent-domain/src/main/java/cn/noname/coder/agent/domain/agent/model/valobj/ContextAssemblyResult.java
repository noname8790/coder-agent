package cn.noname.coder.agent.domain.agent.model.valobj;

import java.util.List;

public record ContextAssemblyResult(
        List<ContextCandidate> selected,
        List<ContextCandidate> rejected,
        List<String> messages,
        int rawEstimatedTokens,
        int finalEstimatedTokens,
        double compressionRatio,
        int memoryHitCount,
        int staleMemoryCount,
        String budgetSource,
        String snapshotPath
) {
}
