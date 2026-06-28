package cn.noname.coder.agent.cases.memory;

import cn.noname.coder.agent.domain.model.adapter.port.IEmbeddingGateway;
import cn.noname.coder.agent.domain.memory.adapter.port.IVectorMemoryPort;
import cn.noname.coder.agent.domain.memory.adapter.repository.IMemoryRepository;
import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.memory.model.entity.MemoryChunk;
import cn.noname.coder.agent.domain.memory.model.entity.MemoryItem;
import cn.noname.coder.agent.domain.memory.model.entity.MemoryRecall;
import cn.noname.coder.agent.domain.context.model.valobj.ContextLayer;
import cn.noname.coder.agent.domain.model.model.valobj.EmbeddingRequest;
import cn.noname.coder.agent.domain.model.model.valobj.EmbeddingResponse;
import cn.noname.coder.agent.domain.workspace.model.valobj.ChangedFile;
import cn.noname.coder.agent.domain.memory.model.valobj.MemorySearchHit;
import cn.noname.coder.agent.domain.memory.model.valobj.MemorySearchRequest;
import cn.noname.coder.agent.domain.tool.model.valobj.ToolInvocation;
import cn.noname.coder.agent.domain.tool.model.valobj.ToolResult;
import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import cn.noname.coder.agent.types.enums.AgentRunStatus;
import cn.noname.coder.agent.types.enums.CallStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MemoryServiceTest {

    @Test
    void shouldRecallOnlyCurrentWorkspaceGivenCrossWorkspaceHits() {
        // Given pgvector 返回当前 workspace 和其它 workspace 的命中
        AgentRuntimeProperties properties = enabledProperties();
        InMemoryMemoryRepository memoryRepository = new InMemoryMemoryRepository();
        memoryRepository.memories.add(MemoryItem.builder()
                .memoryId("mem_1")
                .workspaceKey("repo-a")
                .sourceType("PROJECT_MEMORY")
                .sourceId("run_1")
                .summary("A 璁板繂")
                .freshnessStatus("FRESH")
                .trustScore(0.9)
                .build());
        memoryRepository.memories.add(MemoryItem.builder()
                .memoryId("mem_2")
                .workspaceKey("repo-b")
                .sourceType("PROJECT_MEMORY")
                .sourceId("run_2")
                .summary("B 璁板繂")
                .freshnessStatus("FRESH")
                .trustScore(0.9)
                .build());
        InMemoryVectorMemoryPort vectorPort = new InMemoryVectorMemoryPort();
        vectorPort.hits = List.of(
                new MemorySearchHit("chk_1", "mem_1", "repo-a", "A 记忆", 0.91, "{}"),
                new MemorySearchHit("chk_2", "mem_2", "repo-b", "B 记忆", 0.99, "{}")
        );
        MemoryService service = new MemoryService(memoryRepository, vectorPort, embeddingGateway(), properties);

        // When repo-a 运行触发召回
        var candidates = service.recallForRun(run("run_1", "repo-a"));

        // Then 只返回 repo-a 的记忆并保存召回记录
        assertEquals(1, candidates.size());
        assertEquals("mem_1", candidates.getFirst().sourceId());
        assertEquals("repo-a", vectorPort.lastRequest.workspaceKey());
        assertEquals(1, memoryRepository.recalls.size());
        assertEquals("repo-a", memoryRepository.recalls.getFirst().getWorkspaceKey());
    }

    @Test
    void shouldSaveMemoryMetadataAndVectorChunkGivenRunSummary() {
        // Given 运行成功且 memory 开启
        AgentRuntimeProperties properties = enabledProperties();
        InMemoryMemoryRepository memoryRepository = new InMemoryMemoryRepository();
        InMemoryVectorMemoryPort vectorPort = new InMemoryVectorMemoryPort();
        MemoryService service = new MemoryService(memoryRepository, vectorPort, embeddingGateway(), properties);
        AgentRun run = run("run_1", "repo-a");
        run.setStatus(AgentRunStatus.SUCCEEDED);
        run.setFinalAnswer("完成分析");

        // When 写入运行摘要
        service.rememberRunSummary(run);

        // Then MySQL 元数据和向量 chunk 都写入同一 workspace
        assertEquals(1, memoryRepository.memories.size());
        assertEquals(1, vectorPort.chunks.size());
        assertEquals("repo-a", memoryRepository.memories.getFirst().getWorkspaceKey());
        assertEquals("repo-a", vectorPort.chunks.getFirst().getWorkspaceKey());
        assertEquals("RUN_SUMMARY", vectorPort.chunks.getFirst().getSourceType());
    }

    @Test
    void shouldPersistInternalRunSummaryInsteadOfVisibleFinalAnswerGivenCompletedRun() {
        // Given Agent 最终回复中包含大段可见回答原文
        AgentRuntimeProperties properties = enabledProperties();
        InMemoryMemoryRepository memoryRepository = new InMemoryMemoryRepository();
        InMemoryVectorMemoryPort vectorPort = new InMemoryVectorMemoryPort();
        MemoryService service = new MemoryService(memoryRepository, vectorPort, embeddingGateway(), properties);
        AgentRun run = run("run_1", "repo-a");
        run.setStatus(AgentRunStatus.SUCCEEDED);
        run.setFinalAnswer("""
                RAW_VISIBLE_SENTENCE_SHOULD_NOT_BE_STORED
                ## 任务完成
                已新增 src/main/java/cn/noname/demo/VersionInfo.java，并提供 appName 和 version 两个静态方法。
                验证命令 mvn test 已通过。
                """);

        // When 写入运行摘要
        service.rememberRunSummary(run);

        // Then 记忆保存的是内部结构化摘要，不直接保存可见回答原文
        String summary = memoryRepository.memories.getFirst().getSummary();
        assertTrue(summary.contains("用户目标"));
        assertTrue(summary.contains("完成事项"));
        assertFalse(summary.contains("RAW_VISIBLE_SENTENCE_SHOULD_NOT_BE_STORED"));
    }

    @Test
    void shouldSaveVectorMemoryGivenToolChangedFile() {
        // Given 工具成功新增文件
        AgentRuntimeProperties properties = enabledProperties();
        InMemoryMemoryRepository memoryRepository = new InMemoryMemoryRepository();
        InMemoryVectorMemoryPort vectorPort = new InMemoryVectorMemoryPort();
        MemoryService service = new MemoryService(memoryRepository, vectorPort, embeddingGateway(), properties);
        AgentRun run = run("run_1", "repo-a");
        run.setToolCallCount(1);

        // When 记录工具结果
        service.rememberToolResult(run,
                new ToolInvocation("tool_1", "write_file", "{\"path\":\"src/NewFeature.java\"}"),
                new ToolResult(CallStatus.SUCCESS, "新增文件", "ok", 0, null,
                        List.of(new ChangedFile("src/NewFeature.java", "ADDED", null, "hash-new", 1,
                                null, "class NewFeature {}")), null));

        // Then MySQL 记忆和 pgvector chunk 都保存了文件变更摘要
        assertEquals(1, memoryRepository.memories.size());
        assertEquals(1, vectorPort.chunks.size());
        assertEquals("TOOL_EDIT", memoryRepository.memories.getFirst().getSourceType());
        assertTrue(memoryRepository.memories.getFirst().getSummary().contains("新增文件"));
        assertTrue(vectorPort.chunks.getFirst().getContent().contains("src/NewFeature.java"));
    }

    @Test
    void shouldPersistStructuredEditSummaryInsteadOfChangedFileContentGivenToolChangedFile() {
        // Given 工具成功修改文件，afterContent 包含不应直接进入记忆的大段源码
        AgentRuntimeProperties properties = enabledProperties();
        InMemoryMemoryRepository memoryRepository = new InMemoryMemoryRepository();
        InMemoryVectorMemoryPort vectorPort = new InMemoryVectorMemoryPort();
        MemoryService service = new MemoryService(memoryRepository, vectorPort, embeddingGateway(), properties);
        AgentRun run = run("run_1", "repo-a");
        run.setToolCallCount(1);

        // When 记录工具变更
        service.rememberToolResult(run,
                new ToolInvocation("tool_1", "overwrite_file", "{\"path\":\"src/NewFeature.java\"}"),
                new ToolResult(CallStatus.SUCCESS, "覆盖文件", "ok", 0, null,
                        List.of(new ChangedFile("src/NewFeature.java", "MODIFIED", "hash-old", "hash-new", 1,
                                "class NewFeature {}",
                                "class NewFeature { String marker() { return \"RAW_METHOD_BODY_SHOULD_NOT_BE_STORED\"; } }")),
                        null));

        // Then 只保存结构化变更摘要，不保存变更后源码原文
        String summary = memoryRepository.memories.getFirst().getSummary();
        assertTrue(summary.contains("变更类型"));
        assertTrue(summary.contains("src/NewFeature.java"));
        assertFalse(summary.contains("RAW_METHOD_BODY_SHOULD_NOT_BE_STORED"));
    }

    @Test
    void shouldDeleteMysqlAndVectorMemoryGivenRunIds() {
        // Given 已保存运行记忆
        AgentRuntimeProperties properties = enabledProperties();
        InMemoryMemoryRepository memoryRepository = new InMemoryMemoryRepository();
        InMemoryVectorMemoryPort vectorPort = new InMemoryVectorMemoryPort();
        MemoryService service = new MemoryService(memoryRepository, vectorPort, embeddingGateway(), properties);
        AgentRun run = run("run_1", "repo-a");
        run.setStatus(AgentRunStatus.SUCCEEDED);
        run.setFinalAnswer("完成分析");
        service.rememberRunSummary(run);

        // When 清理该运行的记忆
        service.deleteRunMemories("repo-a", List.of("run_1"));

        // Then MySQL 记忆、召回和向量 chunk 均被清理
        assertTrue(memoryRepository.memories.isEmpty());
        assertTrue(vectorPort.chunks.isEmpty());
    }

    @Test
    void shouldKeepDeleteSuccessfulGivenVectorCleanupUnavailable() {
        // Given pgvector 清理不可用但 MySQL 记忆元数据存在
        AgentRuntimeProperties properties = enabledProperties();
        InMemoryMemoryRepository memoryRepository = new InMemoryMemoryRepository();
        InMemoryVectorMemoryPort vectorPort = new InMemoryVectorMemoryPort();
        vectorPort.deleteFailure = true;
        MemoryService service = new MemoryService(memoryRepository, vectorPort, embeddingGateway(), properties);
        AgentRun run = run("run_1", "repo-a");
        run.setStatus(AgentRunStatus.SUCCEEDED);
        run.setFinalAnswer("完成分析");
        service.rememberRunSummary(run);

        // When 清理运行记忆
        assertDoesNotThrow(() -> service.deleteRunMemories("repo-a", List.of("run_1")));

        // Then 主库记忆已经删除，pgvector 异常只降级记录
        assertTrue(memoryRepository.memories.isEmpty());
    }

    @Test
    void shouldSkipMemoryGivenMemoryDisabled() {
        // Given memory 未开启
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        InMemoryMemoryRepository memoryRepository = new InMemoryMemoryRepository();
        InMemoryVectorMemoryPort vectorPort = new InMemoryVectorMemoryPort();
        MemoryService service = new MemoryService(memoryRepository, vectorPort, embeddingGateway(), properties);

        // When 召回和写入记忆
        assertTrue(service.recallForRun(run("run_1", "repo-a")).isEmpty());
        service.rememberRunSummary(run("run_1", "repo-a"));

        // Then 不触发持久化
        assertTrue(memoryRepository.memories.isEmpty());
        assertTrue(vectorPort.chunks.isEmpty());
    }

    @Test
    void shouldRedactSensitiveTextGivenSummaryContainsApiKey() {
        // Given 摘要含敏感字段
        AgentRuntimeProperties properties = enabledProperties();
        InMemoryMemoryRepository memoryRepository = new InMemoryMemoryRepository();
        InMemoryVectorMemoryPort vectorPort = new InMemoryVectorMemoryPort();
        MemoryService service = new MemoryService(memoryRepository, vectorPort, embeddingGateway(), properties);

        // When 保存记忆
        service.saveMemory("repo-a", "FILE_SUMMARY", "src/App.java", "src/App.java",
                "api_key=secret-value password:123456 正常说明");

        // Then 元数据和 chunk 内容均已脱敏
        assertTrue(memoryRepository.memories.getFirst().getSummary().contains("[REDACTED]"));
        assertTrue(vectorPort.chunks.getFirst().getContent().contains("[REDACTED]"));
    }

    @Test
    void shouldDeleteOldFileMemoryGivenFileHashChanged() {
        // Given 同一文件已有旧摘要
        AgentRuntimeProperties properties = enabledProperties();
        InMemoryMemoryRepository memoryRepository = new InMemoryMemoryRepository();
        InMemoryVectorMemoryPort vectorPort = new InMemoryVectorMemoryPort();
        MemoryService service = new MemoryService(memoryRepository, vectorPort, embeddingGateway(), properties);
        service.saveFileMemory("repo-a", "src/App.java", "src/App.java", "old-hash", null, "旧摘要");

        // When 读取同一文件的新内容
        service.rememberToolResult(run("run_1", "repo-a"),
                new ToolInvocation("tool_1", "read_file", "{\"path\":\"src/App.java\"}"),
                new ToolResult(CallStatus.SUCCESS, "读取成功", "class App { void next() {} }", 0, null));

        // Then v4.5 直接删除旧文件记忆和向量 chunk，只保留重新读取后生成的新记忆
        assertEquals(1, memoryRepository.memories.size());
        assertEquals("FRESH", memoryRepository.memories.getFirst().getFreshnessStatus());
        assertEquals(1, vectorPort.chunks.size());
        assertEquals("FRESH", vectorPort.chunks.getFirst().getFreshnessStatus());
    }

    @Test
    void shouldFilterLowTrustMemoryAndLimitSelectedHitsGivenRecall() {
        // Given pgvector 返回高可信与低可信记忆
        AgentRuntimeProperties properties = enabledProperties();
        properties.getMemory().setCandidateTopK(24);
        properties.getMemory().setSelectedTopK(1);
        properties.getMemory().setMinTrustScore(0.65);
        InMemoryMemoryRepository memoryRepository = new InMemoryMemoryRepository();
        memoryRepository.memories.add(MemoryItem.builder()
                .memoryId("mem_high")
                .workspaceKey("repo-a")
                .sourceType("PROJECT_MEMORY")
                .sourceId("run_1")
                .summary("高可信项目记忆")
                .freshnessStatus("FRESH")
                .trustScore(0.9)
                .build());
        memoryRepository.memories.add(MemoryItem.builder()
                .memoryId("mem_low")
                .workspaceKey("repo-a")
                .sourceType("PROJECT_MEMORY")
                .sourceId("run_2")
                .summary("低可信项目记忆")
                .freshnessStatus("FRESH")
                .trustScore(0.2)
                .build());
        InMemoryVectorMemoryPort vectorPort = new InMemoryVectorMemoryPort();
        vectorPort.hits = List.of(
                new MemorySearchHit("chk_low", "mem_low", "repo-a", "低可信项目记忆", 0.99, "{}"),
                new MemorySearchHit("chk_high", "mem_high", "repo-a", "高可信项目记忆", 0.80, "{}")
        );
        MemoryService service = new MemoryService(memoryRepository, vectorPort, embeddingGateway(), properties);

        // When 执行记忆召回
        var candidates = service.recallForRun(run("run_3", "repo-a"));

        // Then 低可信候选被过滤，只选中 selectedTopK 条可信记忆
        assertEquals(1, candidates.size());
        assertEquals("mem_high", candidates.getFirst().sourceId());
        assertEquals(24, vectorPort.lastRequest.topK());
        assertEquals(0.9, candidates.getFirst().trustScore());
    }

    @Test
    void shouldSkipAutoSummaryGivenBudgetExhausted() {
        // Given 每 run 只允许自动摘要 1 个文件
        AgentRuntimeProperties properties = enabledProperties();
        properties.getMemory().setMaxAutoSummaryFilesPerRun(1);
        InMemoryMemoryRepository memoryRepository = new InMemoryMemoryRepository();
        InMemoryVectorMemoryPort vectorPort = new InMemoryVectorMemoryPort();
        MemoryService service = new MemoryService(memoryRepository, vectorPort, embeddingGateway(), properties);
        AgentRun run = run("run_1", "repo-a");

        // When 连续读取两个文件
        service.rememberToolResult(run, new ToolInvocation("tool_1", "read_file", "{\"path\":\"src/A.java\"}"),
                new ToolResult(CallStatus.SUCCESS, "ok", "class A {}", 0, null));
        service.rememberToolResult(run, new ToolInvocation("tool_2", "read_file", "{\"path\":\"src/B.java\"}"),
                new ToolResult(CallStatus.SUCCESS, "ok", "class B {}", 0, null));

        // Then 只写入第一个文件摘要
        assertEquals(1, memoryRepository.memories.size());
        assertEquals("src/A.java", memoryRepository.memories.getFirst().getFilePath());
    }

    @Test
    void shouldDegradeRecallGivenVectorPortUnavailable() {
        // Given pgvector 检索不可用
        AgentRuntimeProperties properties = enabledProperties();
        InMemoryMemoryRepository memoryRepository = new InMemoryMemoryRepository();
        InMemoryVectorMemoryPort vectorPort = new InMemoryVectorMemoryPort();
        vectorPort.searchFailure = true;
        MemoryService service = new MemoryService(memoryRepository, vectorPort, embeddingGateway(), properties);

        // When 触发召回 / Then 降级为空且不抛异常
        assertTrue(service.recallForRun(run("run_1", "repo-a")).isEmpty());
        assertTrue(memoryRepository.recalls.isEmpty());
    }

    @Test
    void shouldBuildConversationSummaryCandidateFromInternalRunMemoriesGivenOlderRuns() {
        // Given 同一 workspace 已保存更早任务的内部摘要
        AgentRuntimeProperties properties = enabledProperties();
        InMemoryMemoryRepository memoryRepository = new InMemoryMemoryRepository();
        InMemoryVectorMemoryPort vectorPort = new InMemoryVectorMemoryPort();
        MemoryService service = new MemoryService(memoryRepository, vectorPort, embeddingGateway(), properties);
        service.saveMemory("repo-a", "RUN_SUMMARY", "run_old_1", null,
                "用户目标：新增 VersionInfo\n完成事项：\n- 新增 VersionInfo.java\n验证结果：\n- mvn test 通过");
        service.saveMemory("repo-a", "RUN_SUMMARY", "run_old_2", null,
                "用户目标：删除 LegacyService\n完成事项：\n- 删除 LegacyService.java");

        // When 为当前 run 构造更早任务上下文摘要
        var candidate = service.conversationSummaryCandidateFromRunMemories(
                run("run_current", "repo-a"), List.of("run_old_1", "run_old_2"));

        // Then 内部摘要进入 context 层，而不是依赖历史消息全文
        assertTrue(candidate.isPresent());
        assertEquals(ContextLayer.CONVERSATION_SUMMARY, candidate.get().layer());
        assertTrue(candidate.get().content().contains("更早任务的内部上下文摘要"));
        assertTrue(candidate.get().content().contains("新增 VersionInfo"));
        assertTrue(candidate.get().content().contains("删除 LegacyService"));
    }

    private AgentRun run(String runId, String workspaceKey) {
        return AgentRun.builder()
                .runId(runId)
                .workspaceKey(workspaceKey)
                .task("查找配置读取逻辑")
                .model("glm")
                .status(AgentRunStatus.RUNNING)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private AgentRuntimeProperties enabledProperties() {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.getMemory().setEnabled(true);
        properties.getMemory().setTopK(8);
        properties.getMemory().setMinScore(0.35);
        properties.getMemory().setMaxChunksPerRun(20);
        properties.getEmbedding().setModel("text-embedding-v4");
        return properties;
    }

    private IEmbeddingGateway embeddingGateway() {
        return new IEmbeddingGateway() {
            @Override
            public EmbeddingResponse embed(EmbeddingRequest request) {
                return new EmbeddingResponse(request.model(),
                        request.inputs().stream().map(ignored -> List.of(0.1, 0.2, 0.3)).toList(), 1, 1);
            }
        };
    }

    static class InMemoryMemoryRepository implements IMemoryRepository {
        private final List<MemoryItem> memories = new ArrayList<>();
        private final List<MemoryRecall> recalls = new ArrayList<>();

        @Override
        public void saveMemory(MemoryItem memory) {
            memories.add(memory);
        }

        @Override
        public void saveRecall(MemoryRecall recall) {
            recalls.add(recall);
        }

        @Override
        public Optional<MemoryItem> findFreshFileMemory(String workspaceKey, String filePath, String contentHash) {
            return memories.stream()
                    .filter(memory -> workspaceKey.equals(memory.getWorkspaceKey()))
                    .filter(memory -> filePath.equals(memory.getFilePath()))
                    .filter(memory -> contentHash.equals(memory.getContentHash()))
                    .filter(memory -> "FRESH".equals(memory.getFreshnessStatus()))
                    .findFirst();
        }

        @Override
        public List<MemoryItem> listByWorkspace(String workspaceKey) {
            return memories.stream().filter(memory -> workspaceKey.equals(memory.getWorkspaceKey())).toList();
        }

        @Override
        public void markFileMemoriesStale(String workspaceKey, String filePath, String currentContentHash) {
            memories.removeIf(memory -> workspaceKey.equals(memory.getWorkspaceKey())
                    && filePath.equals(memory.getFilePath())
                    && !currentContentHash.equals(memory.getContentHash()));
        }

        @Override
        public void deleteByRunIds(String workspaceKey, Collection<String> runIds) {
            memories.removeIf(memory -> workspaceKey.equals(memory.getWorkspaceKey())
                    && runIds.stream().anyMatch(runId -> memory.getSourceId().equals(runId)
                    || memory.getSourceId().startsWith(runId + ":")));
            recalls.removeIf(recall -> runIds.contains(recall.getRunId()));
        }
    }

    static class InMemoryVectorMemoryPort implements IVectorMemoryPort {
        private final List<MemoryChunk> chunks = new ArrayList<>();
        private List<MemorySearchHit> hits = List.of();
        private MemorySearchRequest lastRequest;
        private boolean searchFailure;
        private boolean deleteFailure;

        @Override
        public void saveChunk(MemoryChunk chunk) {
            chunks.add(chunk);
        }

        @Override
        public List<MemorySearchHit> search(MemorySearchRequest request) {
            if (searchFailure) {
                throw new IllegalStateException("pgvector unavailable");
            }
            lastRequest = request;
            return hits;
        }

        @Override
        public void markStale(String workspaceKey, String memoryId) {
            chunks.stream()
                    .filter(chunk -> workspaceKey.equals(chunk.getWorkspaceKey()))
                    .filter(chunk -> memoryId.equals(chunk.getMemoryId()))
                    .forEach(chunk -> chunk.setFreshnessStatus("STALE"));
        }

        @Override
        public void deleteByMemoryIds(String workspaceKey, Collection<String> memoryIds) {
            chunks.removeIf(chunk -> workspaceKey.equals(chunk.getWorkspaceKey())
                    && memoryIds.contains(chunk.getMemoryId()));
        }

        @Override
        public void deleteByRunIds(String workspaceKey, Collection<String> runIds) {
            if (deleteFailure) {
                throw new IllegalStateException("pgvector delete unavailable");
            }
            chunks.removeIf(chunk -> workspaceKey.equals(chunk.getWorkspaceKey())
                    && runIds.stream().anyMatch(runId -> chunk.getSourceId().equals(runId)
                    || chunk.getSourceId().startsWith(runId + ":")));
        }
    }
}
