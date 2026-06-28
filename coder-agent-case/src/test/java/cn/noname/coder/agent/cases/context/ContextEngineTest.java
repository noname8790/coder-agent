package cn.noname.coder.agent.cases.context;

import cn.noname.coder.agent.domain.context.model.valobj.ContextBudget;
import cn.noname.coder.agent.domain.context.model.valobj.ContextCandidate;
import cn.noname.coder.agent.domain.context.model.valobj.ContextCutReason;
import cn.noname.coder.agent.domain.context.model.valobj.ContextLayer;
import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.model.model.valobj.ModelBackendConfig;
import cn.noname.coder.agent.domain.workspace.model.entity.AgentMessage;
import cn.noname.coder.agent.domain.workspace.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
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

        // Then 所有层都能入选并转换为一个分层 prompt
        assertEquals(ContextLayer.values().length, result.selected().size());
        assertEquals(1, result.messages().size());
        assertTrue(result.messages().getFirst().contains("<system>"));
        assertTrue(result.messages().getFirst().contains("<agent_workflow>"));
        assertTrue(result.messages().getFirst().contains("<current_task>"));
        assertTrue(result.rejected().isEmpty());
    }

    @Test
    void shouldRenderMemoryWithSeparatorsAndFieldBulletsGivenStructuredMemory() {
        // Given 记忆候选来自文件摘要和召回内容
        DefaultContextEngine engine = new DefaultContextEngine();
        List<ContextCandidate> candidates = List.of(
                required("system", ContextLayer.SYSTEM, 5),
                required("task", ContextLayer.CURRENT_TASK, 5),
                optional("memory-1", ContextLayer.MEMORY_RECALL, """
                        文件：src/main/java/Demo.java
                        语言：Java
                        用途：演示
                        主要符号：Demo
                        行为摘要：提供示例
                        风险：无
                        """, 20, 90),
                optional("memory-2", ContextLayer.FILE_SUMMARY, "文件：src/test/java/DemoTest.java\n用途：测试", 20, 80)
        );

        // When 组装 prompt
        String prompt = engine.assemble(candidates, new ContextBudget(500, 50, 450, 0, 200, 0, 0, 0))
                .messages()
                .getFirst();

        // Then 记忆之间有分隔符，结构字段以列表项呈现
        assertTrue(prompt.contains("<memory>"));
        assertTrue(prompt.contains("以下是本次任务召回的结构化记忆"));
        assertTrue(prompt.contains("---------------\n- 文件：src/main/java/Demo.java"));
        assertTrue(prompt.contains("- 语言：Java"));
        assertTrue(prompt.contains("- 用途：演示"));
        assertTrue(prompt.contains("- 主要符号：Demo"));
        assertTrue(prompt.contains("- 行为摘要：提供示例"));
        assertTrue(prompt.contains("- 风险：无"));
        assertTrue(prompt.contains("---------------\n- 文件：src/test/java/DemoTest.java"));
    }

    @Test
    void shouldRenderRecentMessagesAsDelimitedConversationTurns() {
        // Given 最近消息包含用户任务和 Agent 回复
        var assembler = new cn.noname.coder.agent.cases.agent.AgentContextAssembler();
        AgentRun run = AgentRun.builder()
                .runId("run_1")
                .conversationId("conv_1")
                .build();
        List<AgentMessage> messages = List.of(
                AgentMessage.builder().role("USER").content("写一个 gcdTest").build(),
                AgentMessage.builder().role("AGENT").content("## 任务完成总结").build()
        );

        // When 构造最近消息候选
        ContextCandidate candidate = assembler.recentMessagesCandidate(run, messages);

        // Then 每轮对话有明确分隔符和角色标签
        assertNotNull(candidate);
        assertTrue(candidate.content().contains("---------------\n-用户任务：写一个 gcdTest"));
        assertTrue(candidate.content().contains("-coder-agent：## 任务完成总结"));
        assertTrue(candidate.content().strip().endsWith("---------------"));
        assertFalse(candidate.content().contains("[用户]"));
        assertFalse(candidate.content().contains("[Agent]"));
    }

    @Test
    void shouldCompressOlderMessagesIntoStructuredSummaryInsteadOfTruncatedTranscript() {
        // Given 更早消息包含长篇原始回复、文件变更和验证结果
        var assembler = new cn.noname.coder.agent.cases.agent.AgentContextAssembler();
        AgentRun run = AgentRun.builder()
                .runId("run_1")
                .conversationId("conv_1")
                .build();
        String longRawOutput = "这是一段非常长的原始输出".repeat(80);
        List<AgentMessage> messages = List.of(
                AgentMessage.builder().role("USER").content("请创建 LogarithmCalculator.java 和对应测试类，支持 log2 与 log10").build(),
                AgentMessage.builder().role("AGENT").content("""
                        ## 任务完成总结
                        已创建 `src/main/java/cn/noname/demo/LogarithmCalculator.java`
                        已创建 `src/test/java/cn/noname/demo/LogarithmCalculatorTest.java`
                        验证结果：`mvn test` 通过。
                        %s
                        """.formatted(longRawOutput)).build(),
                AgentMessage.builder().role("USER").content("把 LogarithmCalculatorTest.java 删除，并注意不要影响 CalculatorTest.java").build(),
                AgentMessage.builder().role("AGENT").content("""
                        已删除文件：src/test/java/cn/noname/demo/LogarithmCalculatorTest.java
                        风险：测试覆盖减少，后续如继续修改对数逻辑需要重新补测试。
                        """).build()
        );

        // When 构造更早消息摘要候选
        ContextCandidate candidate = assembler.conversationSummaryCandidate(run, messages);

        // Then 摘要按长链路任务要点结构化，不保留逐条截断的原始全文
        assertNotNull(candidate);
        String content = candidate.content();
        assertTrue(content.contains("-历史消息数量：4"));
        assertTrue(content.contains("-用户目标："));
        assertTrue(content.contains("创建 LogarithmCalculator.java"));
        assertTrue(content.contains("-已完成事项："));
        assertTrue(content.contains("已创建"));
        assertTrue(content.contains("-涉及文件："));
        assertTrue(content.contains("src/main/java/cn/noname/demo/LogarithmCalculator.java"));
        assertTrue(content.contains("src/test/java/cn/noname/demo/LogarithmCalculatorTest.java"));
        assertTrue(content.contains("-验证结果："));
        assertTrue(content.contains("mvn test"));
        assertTrue(content.contains("-待注意事项："));
        assertTrue(content.contains("测试覆盖减少"));
        assertFalse(content.contains(longRawOutput.substring(0, 80)));
        assertTrue(content.length() < 1800);
    }
    
    @Test
    void shouldIncludeNestedWorkspaceTreeWithoutTypePrefixesGivenTopLevelFolders(@TempDir Path root) throws Exception {
        // Given workspace 包含顶层文件和 src 子目录，噪声目录应被忽略
        Files.createDirectories(root.resolve("src/main/java/cn/noname/demo"));
        Files.createDirectories(root.resolve(".coder/runs"));
        Files.createDirectories(root.resolve("target/classes"));
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        Files.writeString(root.resolve(".gitignore"), ".coder\n");
        Files.writeString(root.resolve("src/main/java/cn/noname/demo/App.java"), "class App {}");

        var assembler = new cn.noname.coder.agent.cases.agent.AgentContextAssembler();
        AgentRun run = AgentRun.builder().runId("run_1").task("分析项目").build();

        // When 构造 workspace 候选
        ContextCandidate workspace = assembler.initialCandidates(run, new WorkspaceDescriptor("demo", root))
                .stream()
                .filter(candidate -> candidate.layer() == ContextLayer.WORKSPACE_PROFILE)
                .findFirst()
                .orElseThrow();

        // Then 顶层条目不带 [F]/[D]，src 下有更深结构，噪声目录不出现
        assertTrue(workspace.content().contains("pom.xml"));
        assertTrue(workspace.content().contains(".gitignore"));
        assertTrue(workspace.content().contains("src"));
        assertTrue(workspace.content().contains("main"));
        assertTrue(workspace.content().contains("App.java"));
        assertFalse(workspace.content().contains("[F]"));
        assertFalse(workspace.content().contains("[D]"));
        assertFalse(workspace.content().contains(".coder"));
        assertFalse(workspace.content().contains("target"));
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
    void shouldResolveV45BudgetGiven128kGlobalConfiguration() {
        // Given v4.5 使用 128K 轻量模型作为默认上下文基线
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.getContext().setMaxContextTokens(131072);
        properties.getContext().setMaxInputTokens(106496);
        properties.getContext().setMaxOutputTokens(8192);
        properties.getContext().setSafetyReserveTokens(16384);
        properties.getContext().setPrefixBudgetTokens(8192);
        properties.getContext().setWorkingMemoryBudgetTokens(12288);
        properties.getContext().setMemoryBudgetTokens(12288);
        properties.getContext().setFileSummaryBudgetTokens(16384);
        properties.getContext().setRawSnippetBudgetTokens(32768);
        properties.getContext().setToolResultBudgetTokens(20480);
        properties.getContext().setRecentMessageBudgetTokens(16384);
        properties.getContext().setRunTraceBudgetTokens(8192);

        // When 解析全局上下文预算
        ContextBudget budget = new ContextBudgetResolver(properties).resolve(null);

        // Then 每个上下文层都有独立预算，当前请求仍不受裁剪
        assertEquals(131072, budget.maxContextTokens());
        assertEquals(106496, budget.inputBudgetTokens());
        assertEquals(8192, budget.maxOutputTokens());
        assertEquals(16384, budget.safetyReserveTokens());
        assertEquals(8192, budget.prefixBudgetTokens());
        assertEquals(12288, budget.workingMemoryBudgetTokens());
        assertEquals(12288, budget.memoryBudgetTokens());
        assertEquals(20480, budget.toolResultBudgetTokens());
        assertEquals(8192, budget.runTraceBudgetTokens());
    }

    @Test
    void shouldDeduplicateRepeatedToolResultsGivenContextAboveWatermark() {
        // Given 重复工具结果把上下文推到 75% 水位以上
        DefaultContextEngine engine = new DefaultContextEngine();
        String repeated = "x".repeat(800);
        List<ContextCandidate> candidates = List.of(
                required("task", ContextLayer.CURRENT_TASK, 20),
                new ContextCandidate("tool-1", ContextLayer.TOOL_RESULT, "tool", repeated,
                        repeated.length(), 100, "tool_call", "1"),
                new ContextCandidate("tool-2", ContextLayer.TOOL_RESULT, "tool", repeated,
                        repeated.length(), 90, "tool_call", "2")
        );

        // When 组装上下文
        var result = engine.assemble(candidates, new ContextBudget(1000, 100, 900, 0, 0, 0, 0, 1000));

        // Then 重复工具结果只保留一份，另一份被标记为去重裁剪
        assertEquals(2, result.selected().size());
        assertEquals(ContextCutReason.DEDUPLICATED, result.rejected().getFirst().cutReason());
    }

    @Test
    void shouldKeepCandidateEvidenceMetadataGivenV45Candidate() {
        // Given v4.5 候选携带来源、作用域、freshness、可信度和证据
        ContextCandidate candidate = new ContextCandidate(
                "mem-1",
                ContextLayer.MEMORY_RECALL,
                "文件记忆",
                "Calculator.java 负责计算",
                10,
                80,
                "memory",
                "mem_1",
                "workspace",
                "FRESH",
                0.91,
                true,
                List.of("tool_call:1"),
                false,
                ContextCutReason.NONE);

        // Then 元数据可被上下文治理和审计链路读取
        assertEquals("workspace", candidate.scope());
        assertEquals("FRESH", candidate.freshnessStatus());
        assertEquals(0.91, candidate.trustScore());
        assertEquals(List.of("tool_call:1"), candidate.evidenceRefs());
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

    private ContextCandidate optional(String id, ContextLayer layer, String content, int tokens, int priority) {
        return new ContextCandidate(id, layer, id, content, tokens, priority, "test", id);
    }
}
