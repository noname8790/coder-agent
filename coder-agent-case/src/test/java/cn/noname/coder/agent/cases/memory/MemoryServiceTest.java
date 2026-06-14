package cn.noname.coder.agent.cases.memory;

import cn.noname.coder.agent.domain.agent.adapter.port.IEmbeddingGateway;
import cn.noname.coder.agent.domain.agent.adapter.port.IVectorMemoryPort;
import cn.noname.coder.agent.domain.agent.adapter.repository.IMemoryRepository;
import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.agent.model.entity.MemoryChunk;
import cn.noname.coder.agent.domain.agent.model.entity.MemoryItem;
import cn.noname.coder.agent.domain.agent.model.entity.MemoryRecall;
import cn.noname.coder.agent.domain.agent.model.valobj.EmbeddingRequest;
import cn.noname.coder.agent.domain.agent.model.valobj.EmbeddingResponse;
import cn.noname.coder.agent.domain.agent.model.valobj.ChangedFile;
import cn.noname.coder.agent.domain.agent.model.valobj.MemorySearchHit;
import cn.noname.coder.agent.domain.agent.model.valobj.MemorySearchRequest;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolInvocation;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolResult;
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
    void shouldMarkOldFileMemoryStaleGivenFileHashChanged() {
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

        // Then 旧摘要和旧向量 chunk 标记 stale，新摘要保持 fresh
        assertEquals("STALE", memoryRepository.memories.getFirst().getFreshnessStatus());
        assertEquals("FRESH", memoryRepository.memories.get(1).getFreshnessStatus());
        assertEquals("STALE", vectorPort.chunks.getFirst().getFreshnessStatus());
        assertEquals("FRESH", vectorPort.chunks.get(1).getFreshnessStatus());
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
                return new EmbeddingResponse(request.model(), List.of(List.of(0.1, 0.2, 0.3)), 1, 1);
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
            memories.stream()
                    .filter(memory -> workspaceKey.equals(memory.getWorkspaceKey()))
                    .filter(memory -> filePath.equals(memory.getFilePath()))
                    .filter(memory -> !currentContentHash.equals(memory.getContentHash()))
                    .forEach(memory -> memory.setFreshnessStatus("STALE"));
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
