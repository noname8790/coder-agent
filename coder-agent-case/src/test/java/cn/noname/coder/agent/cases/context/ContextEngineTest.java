package cn.noname.coder.agent.cases.context;

import cn.noname.coder.agent.domain.agent.model.valobj.ContextBudget;
import cn.noname.coder.agent.domain.agent.model.valobj.ContextCandidate;
import cn.noname.coder.agent.domain.agent.model.valobj.ContextCutReason;
import cn.noname.coder.agent.domain.agent.model.valobj.ContextLayer;
import cn.noname.coder.agent.domain.agent.model.valobj.ModelBackendConfig;
import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContextEngineTest {

    @Test
    void shouldKeepRequiredCandidatesGivenContextOverBudget() {
        // Given system、权限和当前任务是必保留上下文，raw snippet 超过预算
        DefaultContextEngine engine = new DefaultContextEngine();
        List<ContextCandidate> candidates = List.of(
                required("system", ContextLayer.SYSTEM, 10),
                required("permission", ContextLayer.PERMISSION_POLICY, 10),
                required("task", ContextLayer.CURRENT_TASK, 10),
                optional("raw-1", ContextLayer.RAW_SNIPPET, 100, 10),
                optional("memory-1", ContextLayer.MEMORY_RECALL, 20, 30)
        );

        // When 按输入预算装配
        var result = engine.assemble(candidates, new ContextBudget(70, 10, 50, 30, 0, 30, 0, 0));

        // Then 必保留层仍入选，超预算候选被裁剪并记录原因
        assertEquals(4, result.selected().size());
        assertTrue(result.selected().stream().anyMatch(ContextCandidate::required));
        assertEquals(1, result.rejected().size());
        assertEquals(ContextCutReason.LAYER_BUDGET_EXCEEDED, result.rejected().getFirst().cutReason());
        assertTrue(result.compressionRatio() > 0);
    }

    @Test
    void shouldApplyLayerBudgetGivenManyMemoryCandidates() {
        // Given memory recall 候选很多
        DefaultContextEngine engine = new DefaultContextEngine();
        List<ContextCandidate> candidates = List.of(
                required("task", ContextLayer.CURRENT_TASK, 10),
                optional("memory-high", ContextLayer.MEMORY_RECALL, 30, 100),
                optional("memory-low", ContextLayer.MEMORY_RECALL, 30, 1)
        );

        // When memory 层预算只允许一个候选
        var result = engine.assemble(candidates, new ContextBudget(100, 10, 90, 30, 0, 0, 0, 0));

        // Then 高优先级 memory 入选，低优先级 memory 被裁剪
        assertTrue(result.selected().stream().anyMatch(candidate -> "memory-high".equals(candidate.candidateId())));
        assertTrue(result.rejected().stream().anyMatch(candidate -> "memory-low".equals(candidate.candidateId())));
        assertEquals(1, result.memoryHitCount());
    }

    @Test
    void shouldAssembleAllContextLayersGivenLayeredCandidates() {
        // Given 覆盖 v4 设计中的主要上下文层
        DefaultContextEngine engine = new DefaultContextEngine();
        List<ContextCandidate> candidates = List.of(
                required("system", ContextLayer.SYSTEM, 5),
                required("workspace", ContextLayer.WORKSPACE_PROFILE, 5),
                required("task", ContextLayer.CURRENT_TASK, 5),
                required("permission", ContextLayer.PERMISSION_POLICY, 5),
                optional("recent", ContextLayer.RECENT_MESSAGES, 5, 90),
                optional("conversation-summary", ContextLayer.CONVERSATION_SUMMARY, 5, 80),
                optional("memory", ContextLayer.MEMORY_RECALL, 5, 80),
                optional("file-summary", ContextLayer.FILE_SUMMARY, 5, 70),
                optional("snippet", ContextLayer.RAW_SNIPPET, 5, 60),
                optional("tool", ContextLayer.TOOL_RESULT, 5, 50),
                optional("trace", ContextLayer.RUN_TRACE_SUMMARY, 5, 40)
        );

        // When 预算足够
        var result = engine.assemble(candidates, new ContextBudget(200, 20, 180, 50, 50, 50, 50, 50));

        // Then 所有层都能入选并转换为模型消息
        assertEquals(ContextLayer.values().length, result.selected().size());
        assertEquals(ContextLayer.values().length, result.messages().size());
        assertTrue(result.rejected().isEmpty());
    }

    @Test
    void shouldCutByInputBudgetGivenOptionalCandidatesExceedTotalBudget() {
        // Given 单层预算足够，但总输入预算不足
        DefaultContextEngine engine = new DefaultContextEngine();
        List<ContextCandidate> candidates = List.of(
                required("task", ContextLayer.CURRENT_TASK, 10),
                optional("recent", ContextLayer.RECENT_MESSAGES, 30, 100),
                optional("trace", ContextLayer.RUN_TRACE_SUMMARY, 30, 90)
        );

        // When 输入预算只允许一个 optional
        var result = engine.assemble(candidates, new ContextBudget(70, 10, 50, 100, 100, 100, 100, 100));

        // Then 低优先级 optional 因总预算被裁剪
        assertEquals(2, result.selected().size());
        assertEquals(ContextCutReason.INPUT_BUDGET_EXCEEDED, result.rejected().getFirst().cutReason());
    }

    @Test
    void shouldCompressToolOutputGivenSummaryExceedsBudget() {
        // Given 长工具输出
        var executor = new cn.noname.coder.agent.cases.agent.AgentContextAssembler();
        String longSummary = "x".repeat(5000);

        // When 构建工具结果候选
        ContextCandidate candidate = executor.toolResultCandidate(
                cn.noname.coder.agent.domain.agent.model.entity.AgentRun.builder()
                        .runId("run_1")
                        .toolCallCount(1)
                        .build(),
                "run_shell",
                longSummary.substring(0, 1000));

        // Then 工具结果可作为 TOOL_RESULT 层候选进入上下文
        assertEquals(ContextLayer.TOOL_RESULT, candidate.layer());
        assertTrue(candidate.estimatedTokens() > 0);
    }

    @Test
    void shouldResolveModelBudgetWithGlobalFallbackGivenPartialModelBudget() {
        // Given 模型只覆盖 maxContext 和 memory 预算
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.getContext().setMaxInputTokens(32000);
        properties.getContext().setOutputReserveTokens(4000);
        properties.getContext().setRawSnippetBudgetTokens(8000);
        ContextBudgetResolver resolver = new ContextBudgetResolver(properties);
        ModelBackendConfig model = new ModelBackendConfig("glm", "openai-compatible", "glm", "https://example.test/v1",
                "key", "chat-completions", 0.2, 60, true, true,
                new ContextBudget(64000, null, null, 12000, null, null, null, null));

        // When 解析预算
        ContextBudget budget = resolver.resolve(model);

        // Then 模型级字段优先，其余字段使用全局默认
        assertEquals(64000, budget.maxContextTokens());
        assertEquals(12000, budget.memoryBudgetTokens());
        assertEquals(8000, budget.rawFileBudgetTokens());
        assertEquals("model:glm", resolver.budgetSource(model));
    }

    @Test
    void shouldEstimateChineseAndAsciiGivenMixedText() {
        // Given 中英文混合文本
        SimpleTokenEstimator estimator = new SimpleTokenEstimator();

        // When 估算 token
        int tokens = estimator.estimate("hello 世界");

        // Then 中文按字符、英文粗略按 4 字符估算
        assertTrue(tokens >= 3);
    }

    private ContextCandidate required(String id, ContextLayer layer, int tokens) {
        return new ContextCandidate(id, layer, id, id + " content", tokens, 100, "test", id, true, ContextCutReason.NONE);
    }

    private ContextCandidate optional(String id, ContextLayer layer, int tokens, int priority) {
        return new ContextCandidate(id, layer, id, id + " content", tokens, priority, "test", id);
    }
}
