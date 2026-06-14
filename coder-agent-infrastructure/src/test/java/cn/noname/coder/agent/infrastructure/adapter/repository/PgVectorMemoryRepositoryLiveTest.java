package cn.noname.coder.agent.infrastructure.adapter.repository;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PgVectorMemoryRepositoryLiveTest {

    @Test
    void shouldInsertAndDeleteMemoryChunkGivenLocalPgVector() throws Exception {
        // Given 显式开启 live test 且 .env 中存在 pgvector 配置
        Assumptions.assumeTrue(Boolean.getBoolean("RUN_PGVECTOR_LIVE_TEST"), "live pgvector test disabled");
        Map<String, String> env = readDotEnv();
        Assumptions.assumeTrue("true".equalsIgnoreCase(env.getOrDefault("PGVECTOR_ENABLED", "false")), "pgvector disabled");

        String jdbcUrl = env.get("PGVECTOR_URL");
        String username = env.get("PGVECTOR_USERNAME");
        String password = env.getOrDefault("PGVECTOR_PASSWORD", "");
        String schema = normalizeIdentifier(env.getOrDefault("PGVECTOR_SCHEMA", "public"), "public");
        String prefix = normalizeIdentifier(env.getOrDefault("PGVECTOR_TABLE_PREFIX", "coder_agent"), "coder_agent");
        String tableName = quoteIdentifier(schema) + "." + quoteIdentifier(prefix + "_memory_chunk");
        assertNotNull(jdbcUrl);
        assertNotNull(username);

        String exactRunId = "__live_run_exact__";
        String prefixedRunId = "__live_run_prefix__";
        String vectorLiteral = IntStream.range(0, 1024)
                .mapToObj(ignored -> "0.001")
                .collect(Collectors.joining(",", "[", "]"));

        try (var connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("SELECT to_regclass('" + schema + "." + prefix + "_memory_chunk')");
                statement.executeUpdate("DELETE FROM " + tableName + " WHERE workspace_key = '__live_ws__'");
            }

            try (var insert = connection.prepareStatement("""
                    INSERT INTO %s
                    (chunk_id, workspace_key, memory_id, source_type, source_id, content, metadata, embedding)
                    VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::vector)
                    """.formatted(tableName))) {
                insert.setString(1, "__live_chunk_exact__");
                insert.setString(2, "__live_ws__");
                insert.setString(3, "__live_mem_exact__");
                insert.setString(4, "RUN_SUMMARY");
                insert.setString(5, exactRunId);
                insert.setString(6, "live exact chunk");
                insert.setString(7, "{}");
                insert.setString(8, vectorLiteral);
                insert.executeUpdate();

                insert.setString(1, "__live_chunk_prefix__");
                insert.setString(3, "__live_mem_prefix__");
                insert.setString(5, prefixedRunId + ":summary");
                insert.setString(6, "live prefix chunk");
                insert.executeUpdate();
            }

            int exactDeleted;
            try (var delete = connection.prepareStatement("DELETE FROM " + tableName + " WHERE workspace_key = ? AND source_id = ?")) {
                delete.setString(1, "__live_ws__");
                delete.setString(2, exactRunId);
                exactDeleted = delete.executeUpdate();
            }

            int prefixDeleted;
            try (var delete = connection.prepareStatement("DELETE FROM " + tableName + " WHERE workspace_key = ? AND source_id LIKE ? ESCAPE '\\'")) {
                delete.setString(1, "__live_ws__");
                delete.setString(2, escapeLike(prefixedRunId + ":") + "%");
                prefixDeleted = delete.executeUpdate();
            }

            connection.rollback();

            // Then 精确 runId 和 runId 前缀记忆都可以被 PostgreSQL 真实删除
            assertEquals(1, exactDeleted);
            assertEquals(1, prefixDeleted);
        }
    }

    private Map<String, String> readDotEnv() throws Exception {
        Path path = Path.of(".env");
        if (!Files.exists(path)) {
            path = Path.of("..", ".env");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            String trimmed = line.trim();
            if (trimmed.isBlank() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                continue;
            }
            int split = trimmed.indexOf('=');
            values.put(trimmed.substring(0, split).trim(), trimmed.substring(split + 1).trim());
        }
        return values;
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

    private String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
