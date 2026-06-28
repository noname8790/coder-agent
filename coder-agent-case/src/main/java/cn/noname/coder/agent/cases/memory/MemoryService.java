package cn.noname.coder.agent.cases.memory;

import cn.noname.coder.agent.domain.model.adapter.port.IEmbeddingGateway;
import cn.noname.coder.agent.domain.memory.adapter.port.IVectorMemoryPort;
import cn.noname.coder.agent.domain.memory.adapter.repository.IMemoryRepository;
import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.memory.model.entity.MemoryChunk;
import cn.noname.coder.agent.domain.memory.model.entity.MemoryItem;
import cn.noname.coder.agent.domain.memory.model.entity.MemoryRecall;
import cn.noname.coder.agent.domain.workspace.model.valobj.ChangedFile;
import cn.noname.coder.agent.domain.context.model.valobj.ContextCandidate;
import cn.noname.coder.agent.domain.context.model.valobj.ContextLayer;
import cn.noname.coder.agent.domain.model.model.valobj.EmbeddingRequest;
import cn.noname.coder.agent.domain.model.model.valobj.EmbeddingResponse;
import cn.noname.coder.agent.domain.memory.model.valobj.MemorySearchHit;
import cn.noname.coder.agent.domain.memory.model.valobj.MemorySearchRequest;
import cn.noname.coder.agent.domain.tool.model.valobj.ToolInvocation;
import cn.noname.coder.agent.domain.tool.model.valobj.ToolResult;
import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import cn.noname.coder.agent.types.enums.CallStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class MemoryService {

    private static final String SUMMARY_VERSION = "v4.0";

    private final IMemoryRepository memoryRepository;
    private final IVectorMemoryPort vectorMemoryPort;
    private final IEmbeddingGateway embeddingGateway;
    private final AgentRuntimeProperties properties;
    private final MemorySummaryService memorySummaryService;
    private final ConcurrentMap<String, AtomicInteger> embeddingCallsByRun = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> autoSummaryFilesByRun = new ConcurrentHashMap<>();

    public MemoryService(IMemoryRepository memoryRepository,
                         IVectorMemoryPort vectorMemoryPort,
                         IEmbeddingGateway embeddingGateway,
                         AgentRuntimeProperties properties) {
        this(memoryRepository, vectorMemoryPort, embeddingGateway, properties, new MemorySummaryService(null));
    }

    @Autowired
    public MemoryService(IMemoryRepository memoryRepository,
                         IVectorMemoryPort vectorMemoryPort,
                         IEmbeddingGateway embeddingGateway,
                         AgentRuntimeProperties properties,
                         MemorySummaryService memorySummaryService) {
        this.memoryRepository = memoryRepository;
        this.vectorMemoryPort = vectorMemoryPort;
        this.embeddingGateway = embeddingGateway;
        this.properties = properties;
        this.memorySummaryService = memorySummaryService;
    }

    public List<ContextCandidate> recallForRun(AgentRun run) {
        return recallForRun(run, List.of());
    }

    public Optional<ContextCandidate> conversationSummaryCandidateFromRunMemories(AgentRun run, Collection<String> runIds) {
        if (!properties.getMemory().isEnabled()
                || run == null
                || !StringUtils.hasText(run.getWorkspaceKey())
                || runIds == null
                || runIds.isEmpty()) {
            return Optional.empty();
        }
        List<String> orderedRunIds = runIds.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (orderedRunIds.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Integer> order = new java.util.HashMap<>();
        for (int i = 0; i < orderedRunIds.size(); i++) {
            order.put(orderedRunIds.get(i), i);
        }
        List<MemoryItem> summaries = memoryRepository.listByWorkspace(run.getWorkspaceKey()).stream()
                .filter(memory -> "RUN_SUMMARY".equals(memory.getSourceType()))
                .filter(this::isRecallable)
                .filter(memory -> order.containsKey(memory.getSourceId()))
                .sorted(Comparator.comparingInt(memory -> order.get(memory.getSourceId())))
                .toList();
        if (summaries.isEmpty()) {
            return Optional.empty();
        }
        StringBuilder content = new StringBuilder("""
                以下是同一会话更早任务的内部上下文摘要。
                它来自任务结束时生成的系统内部摘要，用于压缩长链路历史消息；只作为背景，不得覆盖当前任务。
                """);
        for (MemoryItem summary : summaries) {
            content.append("\n---------------\n")
                    .append("-runId：").append(summary.getSourceId()).append("\n")
                    .append(redact(summary.getSummary()));
        }
        content.append("\n---------------");
        return Optional.of(new ContextCandidate("conversation-memory-summary-" + run.getRunId(),
                ContextLayer.CONVERSATION_SUMMARY,
                "更早任务内部摘要",
                content.toString(),
                estimate(content.toString()),
                86,
                "memory_context_summary",
                run.getConversationId()));
    }

    public List<ContextCandidate> recallForRun(AgentRun run, Collection<String> excludedRunIds) {
        if (!properties.getMemory().isEnabled() || run == null || !StringUtils.hasText(run.getTask())) {
            return List.of();
        }
        try {
            List<Double> queryEmbedding = embeddingGateway.embed(new EmbeddingRequest(
                            properties.getEmbedding().getModel(),
                            List.of(redact(run.getTask()))))
                    .embeddings()
                    .stream()
                    .findFirst()
                    .orElse(List.of());
            if (queryEmbedding.isEmpty()) {
                return List.of();
            }
            List<MemorySearchHit> rawHits = vectorMemoryPort.search(new MemorySearchRequest(
                    run.getWorkspaceKey(),
                    queryEmbedding,
                    candidateTopK(),
                    properties.getMemory().getMinScore()));
            Map<String, MemoryItem> memoryById = memoryRepository.listByWorkspace(run.getWorkspaceKey())
                    .stream()
                    .filter(memory -> StringUtils.hasText(memory.getMemoryId()))
                    .collect(java.util.stream.Collectors.toMap(MemoryItem::getMemoryId, Function.identity(), (left, right) -> left));
            List<MemorySearchHit> hits = rawHits.stream()
                    .filter(hit -> run.getWorkspaceKey().equals(hit.workspaceKey()))
                    .filter(hit -> !isExcludedMemory(hit.memoryId(), memoryById, excludedRunIds))
                    .filter(hit -> isRecallable(memoryById.get(hit.memoryId())))
                    .sorted(Comparator.comparingDouble((MemorySearchHit hit) -> trustScore(memoryById.get(hit.memoryId())))
                            .reversed()
                            .thenComparing(Comparator.comparingDouble(MemorySearchHit::score).reversed()))
                    .limit(selectedTopK())
                    .toList();
            List<ContextCandidate> staleCandidates = staleCandidates(run, rawHits, memoryById, excludedRunIds);
            saveRecall(run, rawHits.size(), hits);
            if (hits.isEmpty()) {
                List<ContextCandidate> fallback = new ArrayList<>(staleCandidates);
                fallback.addAll(recentRunSummaryCandidates(run, memoryById, excludedRunIds));
                return fallback;
            }
            List<ContextCandidate> candidates = new ArrayList<>(hits.stream()
                    .map(hit -> toContextCandidate(hit, memoryById.get(hit.memoryId())))
                    .toList());
            candidates.addAll(staleCandidates);
            return candidates;
        } catch (Exception e) {
            log.warn("记忆召回降级 runId={} workspaceKey={} reason={}", run.getRunId(), run.getWorkspaceKey(), e.getMessage());
            return List.of();
        }
    }

    private List<ContextCandidate> recentRunSummaryCandidates(AgentRun run,
                                                              Map<String, MemoryItem> memoryById,
                                                              Collection<String> excludedRunIds) {
        if (memoryById == null || memoryById.isEmpty()) {
            return List.of();
        }
        return memoryById.values().stream()
                .filter(memory -> "RUN_SUMMARY".equals(memory.getSourceType()))
                .filter(this::isRecallable)
                .filter(memory -> !isExcludedMemory(memory.getMemoryId(), memoryById, excludedRunIds))
                .sorted(Comparator.comparing(MemoryItem::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(Math.max(1, Math.min(3, selectedTopK())))
                .map(this::toRecentRunSummaryCandidate)
                .toList();
    }

    private List<ContextCandidate> staleCandidates(AgentRun run,
                                                   List<MemorySearchHit> rawHits,
                                                   Map<String, MemoryItem> memoryById,
                                                   Collection<String> excludedRunIds) {
        return rawHits.stream()
                .filter(hit -> run.getWorkspaceKey().equals(hit.workspaceKey()))
                .filter(hit -> !isExcludedMemory(hit.memoryId(), memoryById, excludedRunIds))
                .filter(hit -> isStale(memoryById.get(hit.memoryId())))
                .map(hit -> toStaleContextCandidate(hit, memoryById.get(hit.memoryId())))
                .toList();
    }

    private ContextCandidate toRecentRunSummaryCandidate(MemoryItem memory) {
        String content = """
                WORKSPACE_RECENT_RUN_MEMORY
                这是同一 workspace 的近期任务记忆，可用于跨会话延续和避免重复操作。
                sourceId=%s
                summary=%s
                """.formatted(memory.getSourceId(), redact(memory.getSummary()));
        return new ContextCandidate(
                "memory-recent-run-" + memory.getMemoryId(),
                ContextLayer.MEMORY_RECALL,
                "近期任务记忆 " + memory.getSourceId(),
                content,
                estimate(content),
                86,
                memory == null ? "memory" : memory.getSourceType(),
                memory.getMemoryId(),
                scope(memory),
                memory.getFreshnessStatus(),
                trustScore(memory),
                true,
                evidenceRefs(memory),
                false,
                null);
    }

    private boolean isExcludedMemory(String memoryId, Map<String, MemoryItem> memoryById, Collection<String> excludedRunIds) {
        if (excludedRunIds == null || excludedRunIds.isEmpty() || !StringUtils.hasText(memoryId)) {
            return false;
        }
        Set<String> excluded = new HashSet<>(excludedRunIds);
        MemoryItem memory = memoryById.get(memoryId);
        if (memory == null || !StringUtils.hasText(memory.getSourceId())) {
            return false;
        }
        String sourceId = memory.getSourceId();
        return excluded.stream().anyMatch(runId -> sourceId.equals(runId) || sourceId.startsWith(runId + ":"));
    }

    private boolean isRecallable(MemoryItem memory) {
        return memory != null
                && "FRESH".equals(memory.getFreshnessStatus())
                && trustScore(memory) >= properties.getMemory().getMinTrustScore();
    }

    private boolean isStale(MemoryItem memory) {
        return memory != null && "STALE".equals(memory.getFreshnessStatus());
    }

    private ContextCandidate toContextCandidate(MemorySearchHit hit, MemoryItem memory) {
        return new ContextCandidate(
                "memory-" + hit.chunkId(),
                ContextLayer.MEMORY_RECALL,
                "记忆召回 " + hit.memoryId(),
                redact(hit.content()),
                estimate(hit.content()),
                (int) Math.round(hit.score() * 100),
                memory == null ? "memory" : memory.getSourceType(),
                hit.memoryId(),
                scope(memory),
                memory == null ? "UNKNOWN" : memory.getFreshnessStatus(),
                trustScore(memory),
                true,
                evidenceRefs(memory),
                false,
                null);
    }

    private ContextCandidate toStaleContextCandidate(MemorySearchHit hit, MemoryItem memory) {
        return new ContextCandidate(
                "memory-stale-" + hit.chunkId(),
                ContextLayer.MEMORY_RECALL,
                "过期记忆 " + hit.memoryId(),
                redact(hit.content()),
                estimate(hit.content()),
                0,
                memory == null ? "memory" : memory.getSourceType(),
                hit.memoryId(),
                scope(memory),
                "STALE",
                trustScore(memory),
                true,
                evidenceRefs(memory),
                false,
                cn.noname.coder.agent.domain.context.model.valobj.ContextCutReason.STALE);
    }

    private List<String> evidenceRefs(MemoryItem memory) {
        if (memory == null || !StringUtils.hasText(memory.getEvidenceJson())) {
            return memory == null || !StringUtils.hasText(memory.getSourceId()) ? List.of() : List.of(memory.getSourceId());
        }
        return List.of(memory.getEvidenceJson());
    }

    private String scope(MemoryItem memory) {
        return memory == null || !StringUtils.hasText(memory.getScope()) ? "workspace" : memory.getScope();
    }

    private double trustScore(MemoryItem memory) {
        return memory == null || memory.getTrustScore() == null ? 0.8 : memory.getTrustScore();
    }

    private int candidateTopK() {
        int configured = properties.getMemory().getCandidateTopK();
        return configured > 0 ? configured : properties.getMemory().getTopK();
    }

    private int selectedTopK() {
        int configured = properties.getMemory().getSelectedTopK();
        int selected = configured > 0 ? configured : properties.getMemory().getMaxChunksPerRun();
        return Math.max(1, Math.min(selected, properties.getMemory().getMaxChunksPerRun()));
    }
    public void rememberRunSummary(AgentRun run) {
        if (!properties.getMemory().isEnabled() || run == null) {
            return;
        }
        String summary = memorySummaryService.summarizeRun(run);
        if (StringUtils.hasText(summary)) {
            saveMemory(run.getWorkspaceKey(), "RUN_SUMMARY", run.getRunId(), null, summary);
        }
    }

    public void rememberToolResult(AgentRun run, ToolInvocation invocation, ToolResult result) {
        if (!properties.getMemory().isEnabled()
                || run == null
                || invocation == null
                || result == null
                || result.status() != CallStatus.SUCCESS) {
            return;
        }
        if ("read_file".equals(invocation.name())) {
            rememberReadFile(run, invocation, result);
        } else if ("search_text".equals(invocation.name())) {
            rememberSearchResult(run, invocation, result);
        }
        rememberChangedFiles(run, invocation, result);
    }

    public void deleteRunMemories(String workspaceKey, Collection<String> runIds) {
        if (!properties.getMemory().isEnabled() || !StringUtils.hasText(workspaceKey) || runIds == null || runIds.isEmpty()) {
            return;
        }
        memoryRepository.deleteByRunIds(workspaceKey, runIds);
        try {
            vectorMemoryPort.deleteByRunIds(workspaceKey, runIds);
        } catch (Exception e) {
            log.warn("pgvector 记忆清理失败，已降级为仅清理 MySQL 记忆 workspaceKey={} runCount={} reason={}",
                    workspaceKey, runIds.size(), e.getMessage());
        }
        log.info("记忆已按运行清理 workspaceKey={} runCount={}", workspaceKey, runIds.size());
    }

    public void saveMemory(String workspaceKey, String sourceType, String sourceId, String filePath, String summary) {
        saveMemory(workspaceKey, sourceType, sourceId, filePath, null, null, summary);
    }

    public void saveFileMemory(String workspaceKey, String sourceId, String filePath, String contentHash, LocalDateTime fileMtime, String summary) {
        saveMemory(workspaceKey, "FILE_SUMMARY", sourceId, filePath, contentHash, fileMtime, summary);
    }

    public boolean refreshFreshness(String workspaceKey, String filePath, String currentContentHash) {
        if (!properties.getMemory().isEnabled()
                || !StringUtils.hasText(workspaceKey)
                || !StringUtils.hasText(filePath)
                || !StringUtils.hasText(currentContentHash)) {
            return false;
        }
        List<String> staleMemoryIds = memoryRepository.listByWorkspace(workspaceKey)
                .stream()
                .filter(memory -> filePath.equals(memory.getFilePath()))
                .filter(memory -> "FRESH".equals(memory.getFreshnessStatus()))
                .filter(memory -> !currentContentHash.equals(memory.getContentHash()))
                .map(MemoryItem::getMemoryId)
                .toList();
        if (staleMemoryIds.isEmpty()) {
            return false;
        }
        memoryRepository.markFileMemoriesStale(workspaceKey, filePath, currentContentHash);
        vectorMemoryPort.deleteByMemoryIds(workspaceKey, staleMemoryIds);
        log.info("文件旧记忆已删除 workspaceKey={} filePath={} staleCount={}", workspaceKey, filePath, staleMemoryIds.size());
        return true;
    }

    private void saveMemory(String workspaceKey, String sourceType, String sourceId, String filePath,
                            String contentHash, LocalDateTime fileMtime, String summary) {
        if (!properties.getMemory().isEnabled() || !StringUtils.hasText(summary)) {
            return;
        }
        try {
            String memoryId = "mem_" + UUID.randomUUID().toString().replace("-", "");
            String safeSummary = redact(summary);
            String hash = StringUtils.hasText(contentHash) ? contentHash : sha256(safeSummary);
            MemoryItem item = MemoryItem.builder()
                    .memoryId(memoryId)
                    .workspaceKey(workspaceKey)
                    .sourceType(sourceType)
                    .sourceId(sourceId)
                    .filePath(filePath)
                    .contentHash(hash)
                    .fileMtime(fileMtime)
                    .summaryVersion(SUMMARY_VERSION)
                    .memoryType(memoryType(sourceType))
                    .scope(memoryScope(sourceType))
                    .title(sourceType + ":" + sourceId)
                    .summary(safeSummary)
                    .metadataJson("{\"summaryVersion\":\"" + SUMMARY_VERSION
                            + "\",\"memoryType\":\"" + memoryType(sourceType)
                            + "\",\"trustScore\":" + trustScoreFor(sourceType) + "}")
                    .trustScore(trustScoreFor(sourceType))
                    .evidenceJson(evidenceJson(sourceType, sourceId, filePath, hash))
                    .freshnessStatus("FRESH")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            memoryRepository.saveMemory(item);
            List<String> chunks = splitIntoChunks(safeSummary);
            List<List<Double>> embeddings = embedForRun(sourceId, chunks).embeddings();
            for (int i = 0; i < chunks.size() && i < embeddings.size(); i++) {
                List<Double> embedding = embeddings.get(i);
                if (embedding == null || embedding.isEmpty()) {
                    continue;
                }
                vectorMemoryPort.saveChunk(MemoryChunk.builder()
                        .chunkId("chk_" + UUID.randomUUID().toString().replace("-", ""))
                        .workspaceKey(workspaceKey)
                        .memoryId(memoryId)
                        .sourceType(sourceType)
                        .sourceId(sourceId)
                        .filePath(filePath)
                        .contentHash(hash)
                        .trustScore(item.getTrustScore())
                        .freshnessStatus("FRESH")
                        .content(chunks.get(i))
                        .metadataJson(item.getMetadataJson())
                        .embedding(embedding)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build());
            }
            log.info("记忆已写入 workspaceKey={} memoryId={} sourceType={} sourceId={}", workspaceKey, memoryId, sourceType, sourceId);
        } catch (Exception e) {
            log.warn("记忆写入降级 workspaceKey={} sourceType={} sourceId={} reason={}", workspaceKey, sourceType, sourceId, e.getMessage());
        }
    }

    private String memoryType(String sourceType) {
        if ("FILE_SUMMARY".equals(sourceType)) {
            return "FILE_MEMORY";
        }
        if ("RUN_SUMMARY".equals(sourceType)) {
            return "RUN_OBSERVATION";
        }
        if ("TOOL_EDIT".equals(sourceType) || "TASK_SUMMARY".equals(sourceType)) {
            return "TASK_MEMORY";
        }
        return "PROJECT_MEMORY";
    }

    private String memoryScope(String sourceType) {
        if ("FILE_SUMMARY".equals(sourceType) || "PROJECT_MEMORY".equals(sourceType)) {
            return "workspace";
        }
        return "run";
    }

    private double trustScoreFor(String sourceType) {
        if ("FILE_SUMMARY".equals(sourceType) || "TOOL_EDIT".equals(sourceType)) {
            return 0.95;
        }
        if ("RUN_SUMMARY".equals(sourceType)) {
            return 0.85;
        }
        return 0.8;
    }

    private String evidenceJson(String sourceType, String sourceId, String filePath, String contentHash) {
        return "{\"sourceType\":\"" + escape(sourceType)
                + "\",\"sourceId\":\"" + escape(sourceId)
                + "\",\"filePath\":\"" + escape(filePath)
                + "\",\"contentHash\":\"" + escape(contentHash) + "\"}";
    }
    private void rememberReadFile(AgentRun run, ToolInvocation invocation, ToolResult result) {
        String filePath = parseJsonString(invocation.argumentsJson(), "path");
        String content = result.fullOutput();
        if (!StringUtils.hasText(filePath) || !StringUtils.hasText(content) || shouldSkipFile(filePath, content)) {
            return;
        }
        if (!consumeAutoSummaryBudget(run.getRunId())) {
            log.info("跳过文件摘要 runId={} filePath={} reason=AUTO_SUMMARY_BUDGET_EXHAUSTED", run.getRunId(), filePath);
            return;
        }
        String contentHash = sha256(content);
        if (memoryRepository.findFreshFileMemory(run.getWorkspaceKey(), filePath, contentHash).isPresent()) {
            return;
        }
        refreshFreshness(run.getWorkspaceKey(), filePath, contentHash);
        saveFileMemory(run.getWorkspaceKey(), filePath, filePath, contentHash, null,
                memorySummaryService.summarizeFile(run, filePath, content));
    }

    private void rememberSearchResult(AgentRun run, ToolInvocation invocation, ToolResult result) {
        if (!StringUtils.hasText(result.summary()) || !consumeAutoSummaryBudget(run.getRunId())) {
            return;
        }
        String query = parseJsonString(invocation.argumentsJson(), "query");
        String summary = "搜索词：" + query + "\n搜索结果摘要：\n" + abbreviate(redact(result.summary()), 2000);
        saveMemory(run.getWorkspaceKey(), "TASK_SUMMARY", run.getRunId() + ":search:" + run.getToolCallCount(), null, summary);
    }

    private void rememberChangedFiles(AgentRun run, ToolInvocation invocation, ToolResult result) {
        if (result.changedFiles() == null || result.changedFiles().isEmpty()) {
            return;
        }
        for (ChangedFile changedFile : result.changedFiles()) {
            if (changedFile == null || !StringUtils.hasText(changedFile.relativePath())) {
                continue;
            }
            String content = StringUtils.hasText(changedFile.afterContent()) ? changedFile.afterContent() : changedFile.beforeContent();
            if (StringUtils.hasText(content) && shouldSkipFile(changedFile.relativePath(), content)) {
                continue;
            }
            if (!consumeAutoSummaryBudget(run.getRunId())) {
                log.info("跳过文件变更记忆 runId={} filePath={} reason=AUTO_SUMMARY_BUDGET_EXHAUSTED",
                        run.getRunId(), changedFile.relativePath());
                return;
            }
            String changeHash = StringUtils.hasText(changedFile.afterHash())
                    ? changedFile.afterHash()
                    : sha256((changedFile.changeType() == null ? "" : changedFile.changeType()) + ":" + changedFile.relativePath());
            refreshFreshness(run.getWorkspaceKey(), changedFile.relativePath(), changeHash);
            saveMemory(run.getWorkspaceKey(),
                    "TOOL_EDIT",
                    run.getRunId() + ":tool:" + run.getToolCallCount() + ":" + changedFile.relativePath(),
                    changedFile.relativePath(),
                    changeHash,
                    null,
                    memorySummaryService.summarizeEdit(run, invocation, changedFile, content));
        }
    }

    private EmbeddingResponse embedForRun(String runKey, String text) {
        return embedForRun(runKey, List.of(text));
    }

    private EmbeddingResponse embedForRun(String runKey, List<String> texts) {
        String key = runKey == null ? "global" : runKey;
        int used = embeddingCallsByRun.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
        if (used > properties.getMemory().getMaxEmbeddingCallsPerRun()) {
            throw new IllegalStateException("EMBEDDING_BUDGET_EXHAUSTED");
        }
        return embeddingGateway.embed(new EmbeddingRequest(properties.getEmbedding().getModel(), texts));
    }

    private List<String> splitIntoChunks(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        int maxChars = Math.max(1, properties.getMemory().getChunkMaxTokens()) * 4;
        int overlapChars = Math.min(maxChars - 1, Math.max(0, properties.getMemory().getChunkOverlapTokens()) * 4);
        int maxChunks = Math.max(1, properties.getMemory().getMaxChunksPerRun());
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length() && chunks.size() < maxChunks) {
            int end = Math.min(text.length(), start + maxChars);
            chunks.add(text.substring(start, end));
            if (end >= text.length()) {
                break;
            }
            start = Math.max(start + 1, end - overlapChars);
        }
        return chunks;
    }

    private boolean consumeAutoSummaryBudget(String runId) {
        int used = autoSummaryFilesByRun.computeIfAbsent(runId, ignored -> new AtomicInteger()).incrementAndGet();
        return used <= properties.getMemory().getMaxAutoSummaryFilesPerRun();
    }

    private boolean shouldSkipFile(String filePath, String content) {
        String normalized = filePath.replace("\\", "/").toLowerCase();
        if (normalized.contains("/.git/") || normalized.startsWith(".git/")
                || normalized.contains("/.coder/") || normalized.startsWith(".coder/")
                || normalized.contains("/target/") || normalized.startsWith("target/")
                || normalized.endsWith(".env") || normalized.contains("/.env")) {
            return true;
        }
        if (content.getBytes(StandardCharsets.UTF_8).length > properties.getMemory().getMaxFileBytesForSummary()) {
            return true;
        }
        return Pattern.compile("(?i)(api[_-]?key|token|password|credential)\\s*[:=]").matcher(content).find()
                || content.contains("-----BEGIN ") && content.contains("PRIVATE KEY-----");
    }

    private String parseJsonString(String json, String field) {
        if (json == null) {
            return "";
        }
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"").matcher(json);
        return matcher.find() ? matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\") : "";
    }

    private void saveRecall(AgentRun run, int candidateCount, List<MemorySearchHit> hits) {
        try {
            memoryRepository.saveRecall(MemoryRecall.builder()
                    .recallId("recall_" + UUID.randomUUID().toString().replace("-", ""))
                    .runId(run.getRunId())
                    .workspaceKey(run.getWorkspaceKey())
                    .queryText(redact(run.getTask()))
                    .topK(properties.getMemory().getTopK())
                    .minScore(properties.getMemory().getMinScore())
                    .hitCount(hits.size())
                    .selectedCount(hits.size())
                    .candidateCount(candidateCount)
                    .filteredCount(Math.max(0, candidateCount - hits.size()))
                    .detailJson(toDetailJson(hits))
                    .createdAt(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("记忆召回记录写入失败 runId={} reason={}", run.getRunId(), e.getMessage());
        }
    }

    private String toDetailJson(List<MemorySearchHit> hits) {
        String body = hits.stream()
                .map(hit -> "{\"chunkId\":\"" + escape(hit.chunkId()) + "\",\"memoryId\":\"" + escape(hit.memoryId())
                        + "\",\"workspaceKey\":\"" + escape(hit.workspaceKey()) + "\",\"score\":" + hit.score() + "}")
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return "[" + body + "]";
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String redact(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replaceAll("(?i)(api[_-]?key|token|password|credential)\\s*[:=]\\s*[^\\s,;]+", "$1=[REDACTED]")
                .replaceAll("-----BEGIN [^-]+PRIVATE KEY-----[\\s\\S]*?-----END [^-]+PRIVATE KEY-----", "[REDACTED_PRIVATE_KEY]");
    }

    private String abbreviate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text == null ? "" : text;
        }
        return text.substring(0, Math.max(0, maxChars)) + "\n[已截断]";
    }

    private int estimate(String text) {
        return Math.max(1, text == null ? 0 : text.length() / 4);
    }

    private String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(content.hashCode());
        }
    }
}
