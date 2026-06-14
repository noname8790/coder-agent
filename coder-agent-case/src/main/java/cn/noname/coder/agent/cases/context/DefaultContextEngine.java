package cn.noname.coder.agent.cases.context;

import cn.noname.coder.agent.domain.agent.adapter.port.IContextEngine;
import cn.noname.coder.agent.domain.agent.model.valobj.ContextAssemblyResult;
import cn.noname.coder.agent.domain.agent.model.valobj.ContextBudget;
import cn.noname.coder.agent.domain.agent.model.valobj.ContextCandidate;
import cn.noname.coder.agent.domain.agent.model.valobj.ContextCutReason;
import cn.noname.coder.agent.domain.agent.model.valobj.ContextLayer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DefaultContextEngine implements IContextEngine {

    @Override
    public ContextAssemblyResult assemble(List<ContextCandidate> candidates, ContextBudget budget) {
        List<ContextCandidate> safeCandidates = candidates == null ? List.of() : candidates;
        int inputBudget = inputBudget(budget);
        int rawTokens = safeCandidates.stream().mapToInt(ContextCandidate::estimatedTokens).sum();
        List<ContextCandidate> selected = new ArrayList<>();
        List<ContextCandidate> rejected = new ArrayList<>();
        Map<ContextLayer, Integer> selectedLayerTokens = new EnumMap<>(ContextLayer.class);
        int finalTokens = 0;

        for (ContextCandidate candidate : safeCandidates.stream().filter(ContextCandidate::required).toList()) {
            selected.add(candidate);
            finalTokens += candidate.estimatedTokens();
            selectedLayerTokens.merge(candidate.layer(), candidate.estimatedTokens(), Integer::sum);
        }

        List<ContextCandidate> optionalCandidates = safeCandidates.stream()
                .filter(candidate -> !candidate.required())
                .sorted(Comparator.comparingInt(ContextCandidate::priority).reversed())
                .toList();
        for (ContextCandidate candidate : optionalCandidates) {
            int layerTokens = selectedLayerTokens.getOrDefault(candidate.layer(), 0);
            int layerBudget = layerBudget(candidate.layer(), budget);
            if (layerBudget > 0 && layerTokens + candidate.estimatedTokens() > layerBudget) {
                rejected.add(candidate.withCutReason(ContextCutReason.LAYER_BUDGET_EXCEEDED));
                continue;
            }
            if (finalTokens + candidate.estimatedTokens() > inputBudget) {
                rejected.add(candidate.withCutReason(ContextCutReason.INPUT_BUDGET_EXCEEDED));
                continue;
            }
            selected.add(candidate);
            finalTokens += candidate.estimatedTokens();
            selectedLayerTokens.merge(candidate.layer(), candidate.estimatedTokens(), Integer::sum);
        }

        double compressionRatio = rawTokens == 0 ? 0.0 : (rawTokens - finalTokens) * 1.0 / rawTokens;
        return new ContextAssemblyResult(
                selected,
                rejected,
                selected.stream().map(this::toMessage).toList(),
                rawTokens,
                finalTokens,
                compressionRatio,
                countLayer(selected, ContextLayer.MEMORY_RECALL),
                countStaleMemory(selected),
                budget == null ? "global-default" : "resolved",
                null);
    }

    private int inputBudget(ContextBudget budget) {
        if (budget == null) {
            return 24000;
        }
        if (positive(budget.inputBudgetTokens())) {
            return budget.inputBudgetTokens();
        }
        if (positive(budget.maxContextTokens())) {
            return Math.max(1, budget.maxContextTokens() - valueOrDefault(budget.maxOutputTokens(), 4000));
        }
        return 24000;
    }

    private int layerBudget(ContextLayer layer, ContextBudget budget) {
        if (budget == null) {
            return 0;
        }
        return switch (layer) {
            case MEMORY_RECALL -> valueOrDefault(budget.memoryBudgetTokens(), 0);
            case FILE_SUMMARY -> valueOrDefault(budget.fileSummaryBudgetTokens(), 0);
            case RAW_SNIPPET -> valueOrDefault(budget.rawFileBudgetTokens(), 0);
            case TOOL_RESULT -> valueOrDefault(budget.toolResultBudgetTokens(), 0);
            case RECENT_MESSAGES -> valueOrDefault(budget.recentMessageBudgetTokens(), 0);
            default -> 0;
        };
    }

    private String toMessage(ContextCandidate candidate) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("layer", candidate.layer().name());
        header.put("title", candidate.title());
        header.put("sourceType", candidate.sourceType());
        header.put("sourceId", candidate.sourceId());
        return header + "\n" + candidate.content();
    }

    private int countLayer(List<ContextCandidate> candidates, ContextLayer layer) {
        return (int) candidates.stream().filter(candidate -> candidate.layer() == layer).count();
    }

    private int countStaleMemory(List<ContextCandidate> candidates) {
        return (int) candidates.stream()
                .filter(candidate -> candidate.layer() == ContextLayer.MEMORY_RECALL)
                .filter(candidate -> candidate.sourceId() != null && candidate.sourceId().contains("stale"))
                .count();
    }

    private boolean positive(Integer value) {
        return value != null && value > 0;
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        return positive(value) ? value : defaultValue;
    }
}
