package cn.noname.coder.agent.cases.context;

import cn.noname.coder.agent.domain.context.adapter.port.IContextEngine;
import cn.noname.coder.agent.domain.context.model.valobj.ContextAssemblyResult;
import cn.noname.coder.agent.domain.context.model.valobj.ContextBudget;
import cn.noname.coder.agent.domain.context.model.valobj.ContextCandidate;
import cn.noname.coder.agent.domain.context.model.valobj.ContextCutReason;
import cn.noname.coder.agent.domain.context.model.valobj.ContextLayer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DefaultContextEngine implements IContextEngine {

    @Override
    public ContextAssemblyResult assemble(List<ContextCandidate> candidates, ContextBudget budget) {
        List<ContextCandidate> safeCandidates = candidates == null ? List.of() : candidates;
        int inputBudget = inputBudget(budget);
        int rawTokens = safeCandidates.stream().mapToInt(ContextCandidate::estimatedTokens).sum();
        List<ContextCandidate> normalizedCandidates = applyWatermarkDedupe(safeCandidates, inputBudget, rawTokens);
        List<ContextCandidate> selected = new ArrayList<>();
        List<ContextCandidate> rejected = new ArrayList<>();
        Map<ContextLayer, Integer> selectedLayerTokens = new EnumMap<>(ContextLayer.class);
        int finalTokens = 0;

        for (ContextCandidate candidate : normalizedCandidates.stream().filter(ContextCandidate::required).toList()) {
            selected.add(candidate);
            finalTokens += candidate.estimatedTokens();
            selectedLayerTokens.merge(candidate.layer(), candidate.estimatedTokens(), Integer::sum);
        }

        List<ContextCandidate> optionalCandidates = normalizedCandidates.stream()
                .filter(candidate -> !candidate.required())
                .sorted(Comparator.comparingInt(ContextCandidate::priority).reversed())
                .toList();
        for (ContextCandidate candidate : optionalCandidates) {
            if (candidate.cutReason() != ContextCutReason.NONE) {
                rejected.add(candidate);
                continue;
            }
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
                List.of(renderPrompt(selected)),
                rawTokens,
                finalTokens,
                compressionRatio,
                countLayer(selected, ContextLayer.MEMORY_RECALL),
                countStaleMemory(selected, rejected),
                budget == null ? "global-default" : "resolved",
                null);
    }

    private List<ContextCandidate> applyWatermarkDedupe(List<ContextCandidate> candidates, int inputBudget, int rawTokens) {
        if (inputBudget <= 0 || rawTokens < inputBudget * 0.75) {
            return candidates;
        }
        Set<String> seen = new HashSet<>();
        List<ContextCandidate> normalized = new ArrayList<>();
        for (ContextCandidate candidate : candidates) {
            if (candidate.required() || !dedupeEligible(candidate)) {
                normalized.add(candidate);
                continue;
            }
            String key = candidate.layer() + ":" + candidate.sourceType() + ":" + normalize(candidate.content());
            if (!seen.add(key)) {
                normalized.add(candidate.withCutReason(ContextCutReason.DEDUPLICATED));
            } else {
                normalized.add(candidate);
            }
        }
        return normalized;
    }

    private boolean dedupeEligible(ContextCandidate candidate) {
        return candidate.layer() == ContextLayer.TOOL_RESULT
                || candidate.layer() == ContextLayer.RAW_SNIPPET
                || candidate.layer() == ContextLayer.FILE_SUMMARY
                || candidate.layer() == ContextLayer.RUN_TRACE_SUMMARY;
    }

    private String normalize(String content) {
        if (content == null) {
            return "";
        }
        return content.replaceAll("\\s+", " ").trim();
    }

    private int inputBudget(ContextBudget budget) {
        if (budget == null) {
            return 24000;
        }
        int windowBudget = 0;
        if (positive(budget.maxContextTokens())) {
            windowBudget = Math.max(1,
                    budget.maxContextTokens()
                            - valueOrDefault(budget.maxOutputTokens(), 4000)
                            - valueOrDefault(budget.safetyReserveTokens(), 0));
        }
        if (positive(budget.inputBudgetTokens())) {
            return windowBudget > 0 ? Math.min(budget.inputBudgetTokens(), windowBudget) : budget.inputBudgetTokens();
        }
        if (windowBudget > 0) {
            return windowBudget;
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
            case RUN_TRACE_SUMMARY -> valueOrDefault(budget.runTraceBudgetTokens(), 0);
            case SYSTEM, WORKSPACE_PROFILE, PERMISSION_POLICY -> valueOrDefault(budget.prefixBudgetTokens(), 0);
            case CONVERSATION_SUMMARY -> valueOrDefault(budget.workingMemoryBudgetTokens(), 0);
            default -> 0;
        };
    }

    private String renderPrompt(List<ContextCandidate> selected) {
        return String.join("\n\n",
                tag("system", section(selected, ContextLayer.SYSTEM)),
                tag("agent_workflow", agentWorkflow()),
                tag("workspace", section(selected, ContextLayer.WORKSPACE_PROFILE)),
                tag("permission", section(selected, ContextLayer.PERMISSION_POLICY)),
                tag("memory", memorySection(selected)),
                tag("context", section(selected, ContextLayer.RECENT_MESSAGES, ContextLayer.CONVERSATION_SUMMARY)),
                tag("work_status", section(selected, ContextLayer.RUN_TRACE_SUMMARY, ContextLayer.TOOL_RESULT)),
                tag("current_task", section(selected, ContextLayer.CURRENT_TASK)));
    }

    private String tag(String name, String content) {
        return "<" + name + ">\n" + (content == null || content.isBlank() ? "null" : content.strip()) + "\n</" + name + ">";
    }

    private String section(List<ContextCandidate> selected, ContextLayer... layers) {
        Set<ContextLayer> layerSet = Set.of(layers);
        String content = selected.stream()
                .filter(candidate -> layerSet.contains(candidate.layer()))
                .map(ContextCandidate::content)
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .collect(java.util.stream.Collectors.joining("\n\n"));
        return content.isBlank() ? "null" : content;
    }

    private String memorySection(List<ContextCandidate> selected) {
        List<String> memories = selected.stream()
                .filter(candidate -> candidate.layer() == ContextLayer.MEMORY_RECALL
                        || candidate.layer() == ContextLayer.FILE_SUMMARY
                        || candidate.layer() == ContextLayer.RAW_SNIPPET)
                .map(ContextCandidate::content)
                .filter(value -> value != null && !value.isBlank())
                .map(this::normalizeMemoryContent)
                .toList();
        if (memories.isEmpty()) {
            return "null";
        }
        return """
                以下是本次任务召回的结构化记忆，包含项目级、任务级和文件级记忆。不同记忆之间已用分割线隔离。
                这些内容用于减少重复读取、理解历史事实来源和定位相关文件；如果记忆与当前工作区事实或当前任务冲突，必须以当前工具结果和当前任务为准。

                ---------------------------------------------------------------
                %s
                """.formatted(String.join("\n---------------------------------------------------------------\n", memories));
    }

    private String normalizeMemoryContent(String content) {
        StringBuilder normalized = new StringBuilder();
        for (String rawLine : content.strip().replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String line = rawLine.strip();
            if (line.isBlank()) {
                normalized.append('\n');
                continue;
            }
            if (isStructuredMemoryField(line) && !line.startsWith("- ")) {
                normalized.append("- ");
            }
            normalized.append(line).append('\n');
        }
        return normalized.toString().stripTrailing();
    }

    private boolean isStructuredMemoryField(String line) {
        return line.startsWith("文件：")
                || line.startsWith("语言：")
                || line.startsWith("用途：")
                || line.startsWith("主要符号：")
                || line.startsWith("行为摘要：")
                || line.startsWith("风险：");
    }

    private String agentWorkflow() {
        return """
                - 先判断任务类型：只读分析、定位问题、修改代码、运行验证、Git/PR 操作。
                - 默认循环：收集上下文 -> 判断方案 -> 执行必要操作 -> 验证结果 -> 总结。
                - 修改前理解相关代码；不要凭猜测编辑。
                - 修改后优先运行相关、低成本验证；无法验证时说明原因。
                - 工具返回失败、拒绝或空结果时，把它当作事实处理，换路径或明确阻塞原因，不要机械重复同一调用。
                """;
    }

    private int countLayer(List<ContextCandidate> candidates, ContextLayer layer) {
        return (int) candidates.stream().filter(candidate -> candidate.layer() == layer).count();
    }

    private int countStaleMemory(List<ContextCandidate> selected, List<ContextCandidate> rejected) {
        return (int) java.util.stream.Stream.concat(selected.stream(), rejected.stream())
                .filter(candidate -> candidate.layer() == ContextLayer.MEMORY_RECALL)
                .filter(candidate -> "STALE".equals(candidate.freshnessStatus()) || candidate.cutReason() == ContextCutReason.STALE)
                .count();
    }

    private boolean positive(Integer value) {
        return value != null && value > 0;
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        return positive(value) ? value : defaultValue;
    }
}
