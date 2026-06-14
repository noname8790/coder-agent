package cn.noname.coder.agent.cases.memory;

import cn.noname.coder.agent.domain.agent.adapter.port.IEmbeddingGateway;
import cn.noname.coder.agent.domain.agent.adapter.port.IVectorMemoryPort;
import cn.noname.coder.agent.domain.agent.adapter.repository.IMemoryRepository;
import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.agent.model.entity.MemoryChunk;
import cn.noname.coder.agent.domain.agent.model.entity.MemoryItem;
import cn.noname.coder.agent.domain.agent.model.entity.MemoryRecall;
import cn.noname.coder.agent.domain.agent.model.valobj.ChangedFile;
import cn.noname.coder.agent.domain.agent.model.valobj.ContextCandidate;
import cn.noname.coder.agent.domain.agent.model.valobj.ContextLayer;
import cn.noname.coder.agent.domain.agent.model.valobj.EmbeddingRequest;
import cn.noname.coder.agent.domain.agent.model.valobj.EmbeddingResponse;
import cn.noname.coder.agent.domain.agent.model.valobj.MemorySearchHit;
import cn.noname.coder.agent.domain.agent.model.valobj.MemorySearchRequest;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolInvocation;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolResult;
import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import cn.noname.coder.agent.types.enums.CallStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
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
    private final ConcurrentMap<String, AtomicInteger> embeddingCallsByRun = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> autoSummaryFilesByRun = new ConcurrentHashMap<>();

    public MemoryService(IMemoryRepository memoryRepository,
                         IVectorMemoryPort vectorMemoryPort,
                         IEmbeddingGateway embeddingGateway,
                         AgentRuntimeProperties properties) {
        this.memoryRepository = memoryRepository;
        this.vectorMemoryPort = vectorMemoryPort;
        this.embeddingGateway = embeddingGateway;
        this.properties = properties;
    }

    public List<ContextCandidate> recallForRun(AgentRun run) {
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
            List<MemorySearchHit> hits = vectorMemoryPort.search(new MemorySearchRequest(
                            run.getWorkspaceKey(),
                            queryEmbedding,
                            properties.getMemory().getTopK(),
                            properties.getMemory().getMinScore()))
                    .stream()
                    .filter(hit -> run.getWorkspaceKey().equals(hit.workspaceKey()))
                    .limit(properties.getMemory().getMaxChunksPerRun())
                    .toList();
            saveRecall(run, hits);
            return hits.stream()
                    .map(hit -> new ContextCandidate(
                            "memory-" + hit.chunkId(),
                            ContextLayer.MEMORY_RECALL,
                            "记忆召回 " + hit.memoryId(),
                            redact(hit.content()),
                            estimate(hit.content()),
                            (int) Math.round(hit.score() * 100),
                            "memory",
                            hit.memoryId()))
                    .toList();
        } catch (Exception e) {
            log.warn("记忆召回降级 runId={} workspaceKey={} reason={}", run.getRunId(), run.getWorkspaceKey(), e.getMessage());
            return List.of();
        }
    }

    public void rememberRunSummary(AgentRun run) {
        if (!properties.getMemory().isEnabled() || run == null) {
            return;
        }
        String summary = runSummary(run);
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
        staleMemoryIds.forEach(memoryId -> vectorMemoryPort.markStale(workspaceKey, memoryId));
        log.info("文件摘要已标记 stale workspaceKey={} filePath={} staleCount={}", workspaceKey, filePath, staleMemoryIds.size());
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
                    .title(sourceType + ":" + sourceId)
                    .summary(safeSummary)
                    .metadataJson("{\"summaryVersion\":\"" + SUMMARY_VERSION + "\"}")
                    .freshnessStatus("FRESH")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            memoryRepository.saveMemory(item);
            List<Double> embedding = embedForRun(sourceId, safeSummary)
                    .embeddings()
                    .stream()
                    .findFirst()
                    .orElse(List.of());
            if (!embedding.isEmpty()) {
                vectorMemoryPort.saveChunk(MemoryChunk.builder()
                        .chunkId("chk_" + UUID.randomUUID().toString().replace("-", ""))
                        .workspaceKey(workspaceKey)
                        .memoryId(memoryId)
                        .sourceType(sourceType)
                        .sourceId(sourceId)
                        .filePath(filePath)
                        .contentHash(hash)
                        .freshnessStatus("FRESH")
                        .content(safeSummary)
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
        saveFileMemory(run.getWorkspaceKey(), filePath, filePath, contentHash, null, summarizeFile(filePath, content));
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
                    summarizeChangedFile(run, invocation, changedFile, content));
        }
    }

    private String summarizeChangedFile(AgentRun run, ToolInvocation invocation, ChangedFile changedFile, String content) {
        String changeType = StringUtils.hasText(changedFile.changeType()) ? changedFile.changeType() : "UNKNOWN";
        String action = switch (changeType) {
            case "ADDED", "CREATE", "CREATED" -> "新增文件";
            case "MODIFIED", "UPDATE", "UPDATED" -> "修改文件";
            case "DELETED", "DELETE", "REMOVED" -> "删除文件";
            default -> "文件变更";
        };
        String preview = StringUtils.hasText(content) ? abbreviate(redact(content).strip(), 1200) : "无文件内容快照";
        return """
                任务：%s
                工具：%s
                变更：%s
                文件：%s
                beforeHash=%s
                afterHash=%s
                内容摘要：
                %s
                """.formatted(
                run.getTask(),
                invocation == null ? "" : invocation.name(),
                action,
                changedFile.relativePath(),
                changedFile.beforeHash(),
                changedFile.afterHash(),
                preview);
    }

    private EmbeddingResponse embedForRun(String runKey, String text) {
        String key = runKey == null ? "global" : runKey;
        int used = embeddingCallsByRun.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
        if (used > properties.getMemory().getMaxEmbeddingCallsPerRun()) {
            throw new IllegalStateException("EMBEDDING_BUDGET_EXHAUSTED");
        }
        return embeddingGateway.embed(new EmbeddingRequest(properties.getEmbedding().getModel(), List.of(text)));
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

    private String summarizeFile(String filePath, String content) {
        String preview = abbreviate(redact(content).strip(), 3000);
        String language = detectLanguage(filePath);
        String symbols = extractLikelySymbols(content);
        return """
                文件：%s
                语言：%s
                用途：根据读取内容自动生成的文件摘要。
                主要符号：%s
                行为摘要：
                %s
                风险：自动摘要，后续如文件 hash 变化会标记 stale。
                """.formatted(filePath, language, symbols, preview);
    }

    private String detectLanguage(String filePath) {
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".java")) return "Java";
        if (lower.endsWith(".ts") || lower.endsWith(".tsx")) return "TypeScript";
        if (lower.endsWith(".js") || lower.endsWith(".jsx")) return "JavaScript";
        if (lower.endsWith(".md")) return "Markdown";
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) return "YAML";
        if (lower.endsWith(".sql")) return "SQL";
        return "Text";
    }

    private String extractLikelySymbols(String content) {
        Matcher matcher = Pattern.compile("\\b(class|interface|record|enum|function|const|let|def)\\s+([A-Za-z_][A-Za-z0-9_]*)")
                .matcher(content == null ? "" : content);
        StringBuilder symbols = new StringBuilder();
        while (matcher.find() && symbols.length() < 300) {
            if (!symbols.isEmpty()) {
                symbols.append(", ");
            }
            symbols.append(matcher.group(2));
        }
        return symbols.isEmpty() ? "未识别" : symbols.toString();
    }

    private String parseJsonString(String json, String field) {
        if (json == null) {
            return "";
        }
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"").matcher(json);
        return matcher.find() ? matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\") : "";
    }

    private void saveRecall(AgentRun run, List<MemorySearchHit> hits) {
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
                    .detailJson(toDetailJson(hits))
                    .createdAt(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("记忆召回记录写入失败 runId={} reason={}", run.getRunId(), e.getMessage());
        }
    }

    private String runSummary(AgentRun run) {
        String visibleResult = StringUtils.hasText(run.getFinalAnswer()) ? run.getFinalAnswer() : run.getFailureReason();
        if (!StringUtils.hasText(visibleResult)) {
            return "";
        }
        return "任务：" + run.getTask()
                + "\n状态：" + run.getStatus()
                + "\n模型：" + run.getModel()
                + "\n结论：" + visibleResult;
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
