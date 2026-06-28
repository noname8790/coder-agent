package cn.noname.coder.agent.infrastructure.adapter.repository;

import cn.noname.coder.agent.domain.memory.adapter.port.IVectorMemoryPort;
import cn.noname.coder.agent.domain.memory.model.entity.MemoryChunk;
import cn.noname.coder.agent.domain.memory.model.valobj.MemorySearchHit;
import cn.noname.coder.agent.domain.memory.model.valobj.MemorySearchRequest;
import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Repository
@ConditionalOnProperty(prefix = "coder-agent.pgvector", name = "enabled", havingValue = "true")
public class PgVectorMemoryRepository implements IVectorMemoryPort {

    private final JdbcTemplate jdbcTemplate;
    private final AgentRuntimeProperties properties;

    public PgVectorMemoryRepository(@Qualifier("pgVectorJdbcTemplate") JdbcTemplate jdbcTemplate,
                                    AgentRuntimeProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Override
    public void saveChunk(MemoryChunk chunk) {
        jdbcTemplate.update("""
                        INSERT INTO %s
                        (chunk_id, workspace_key, memory_id, source_type, source_id, file_path,
                         content_hash, trust_score, freshness_status, content, metadata, embedding)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::vector)
                        """.formatted(tableName()),
                chunk.getChunkId(),
                chunk.getWorkspaceKey(),
                chunk.getMemoryId(),
                chunk.getSourceType(),
                chunk.getSourceId(),
                chunk.getFilePath(),
                chunk.getContentHash(),
                chunk.getTrustScore() == null ? 0.8 : chunk.getTrustScore(),
                chunk.getFreshnessStatus(),
                chunk.getContent(),
                chunk.getMetadataJson(),
                toVectorLiteral(chunk.getEmbedding()));
    }

    @Override
    public List<MemorySearchHit> search(MemorySearchRequest request) {
        String vectorLiteral = toVectorLiteral(request.queryEmbedding());
        return jdbcTemplate.query("""
                        SELECT chunk_id, memory_id, workspace_key, content,
                               1 - (embedding <=> ?::vector) AS score,
                               COALESCE(metadata::text, '{}') AS metadata_json
                        FROM %s
                        WHERE workspace_key = ?
                          AND freshness_status = 'FRESH'
                          AND 1 - (embedding <=> ?::vector) >= ?
                        ORDER BY embedding <=> ?::vector
                        LIMIT ?
                        """.formatted(tableName()),
                (rs, rowNum) -> new MemorySearchHit(
                        rs.getString("chunk_id"),
                        rs.getString("memory_id"),
                        rs.getString("workspace_key"),
                        rs.getString("content"),
                        rs.getDouble("score"),
                        rs.getString("metadata_json")),
                vectorLiteral,
                request.workspaceKey(),
                vectorLiteral,
                request.minScore(),
                vectorLiteral,
                request.topK());
    }

    @Override
    public void markStale(String workspaceKey, String memoryId) {
        jdbcTemplate.update("UPDATE " + tableName() + " SET freshness_status = 'STALE' WHERE workspace_key = ? AND memory_id = ?",
                workspaceKey, memoryId);
    }

    @Override
    public void deleteByMemoryIds(String workspaceKey, Collection<String> memoryIds) {
        if (workspaceKey == null || workspaceKey.isBlank() || memoryIds == null || memoryIds.isEmpty()) {
            return;
        }
        for (String memoryId : memoryIds) {
            if (memoryId == null || memoryId.isBlank()) {
                continue;
            }
            jdbcTemplate.update("DELETE FROM " + tableName() + " WHERE workspace_key = ? AND memory_id = ?",
                    workspaceKey, memoryId);
        }
    }

    @Override
    public void deleteByRunIds(String workspaceKey, Collection<String> runIds) {
        if (workspaceKey == null || workspaceKey.isBlank() || runIds == null || runIds.isEmpty()) {
            return;
        }
        for (String runId : runIds) {
            if (runId == null || runId.isBlank()) {
                continue;
            }
            String prefix = runId + ":";
            jdbcTemplate.update("DELETE FROM " + tableName() + " WHERE workspace_key = ? AND (source_id = ? OR LEFT(source_id, ?) = ?)",
                    workspaceKey, runId, prefix.length(), prefix);
        }
    }

    @Override
    public void deleteByWorkspaceKey(String workspaceKey) {
        if (workspaceKey == null || workspaceKey.isBlank()) {
            return;
        }
        jdbcTemplate.update("DELETE FROM " + tableName() + " WHERE workspace_key = ?", workspaceKey);
    }

    private String tableName() {
        AgentRuntimeProperties.Pgvector pgvector = properties.getPgvector();
        String schema = normalizeIdentifier(pgvector.getSchema(), "public");
        String prefix = normalizeIdentifier(pgvector.getTablePrefix(), "coder_agent");
        return quoteIdentifier(schema) + "." + quoteIdentifier(prefix + "_memory_chunk");
    }

    private String normalizeIdentifier(String value, String defaultValue) {
        String raw = value == null || value.isBlank() ? defaultValue : value.trim();
        String normalized = raw.replaceAll("[^A-Za-z0-9_]", "_");
        if (normalized.isBlank()) {
            return defaultValue;
        }
        if (Character.isDigit(normalized.charAt(0))) {
            return "_" + normalized;
        }
        return normalized;
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private String toVectorLiteral(List<Double> embedding) {
        if (embedding == null || embedding.isEmpty()) {
            return "[]";
        }
        return embedding.stream()
                .map(value -> Double.toString(value == null ? 0.0 : value))
                .collect(Collectors.joining(",", "[", "]"));
    }
}
