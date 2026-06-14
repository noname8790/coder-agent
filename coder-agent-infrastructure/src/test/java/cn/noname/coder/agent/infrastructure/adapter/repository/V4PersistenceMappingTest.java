package cn.noname.coder.agent.infrastructure.adapter.repository;

import cn.noname.coder.agent.infrastructure.dao.po.ContextSnapshotPO;
import cn.noname.coder.agent.infrastructure.dao.po.EvalBenchmarkPO;
import cn.noname.coder.agent.infrastructure.dao.po.EvalCaseResultPO;
import cn.noname.coder.agent.infrastructure.dao.po.EvalRunPO;
import cn.noname.coder.agent.infrastructure.dao.po.MemoryItemPO;
import cn.noname.coder.agent.infrastructure.dao.po.MemoryRecallPO;
import cn.noname.coder.agent.infrastructure.dao.po.ModelProviderPO;
import cn.noname.coder.agent.infrastructure.dao.po.ToolApprovalRequestPO;
import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V4PersistenceMappingTest {

    @Test
    void shouldDefineMysqlTablesGivenV4MigrationSql() throws IOException {
        // Given v4 MySQL migration SQL
        String sql = read("docs/dev-ops/mysql/sql/coder_agent_v4.sql");

        // When checking required business tables
        // Then model, context, memory, approval and eval tables are defined
        assertAll(
                () -> assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS agent_model_provider")),
                () -> assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS context_snapshot")),
                () -> assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS agent_memory_item")),
                () -> assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS memory_recall")),
                () -> assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS tool_approval_request")),
                () -> assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS eval_benchmark")),
                () -> assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS eval_run")),
                () -> assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS eval_case_result"))
        );
    }

    @Test
    void shouldKeepWorkspaceIsolationFieldsGivenMemoryAndContextTables() throws IOException {
        // Given v4 MySQL migration SQL
        String sql = read("docs/dev-ops/mysql/sql/coder_agent_v4.sql");

        // When checking workspace-scoped records
        // Then memory, recall, approval, benchmark and context snapshot all carry workspace_key
        assertAll(
                () -> assertTrue(tableSection(sql, "context_snapshot").contains("workspace_key VARCHAR(128) NOT NULL")),
                () -> assertTrue(tableSection(sql, "agent_memory_item").contains("workspace_key VARCHAR(128) NOT NULL")),
                () -> assertTrue(tableSection(sql, "memory_recall").contains("workspace_key VARCHAR(128) NOT NULL")),
                () -> assertTrue(tableSection(sql, "tool_approval_request").contains("workspace_key VARCHAR(128) NOT NULL")),
                () -> assertTrue(tableSection(sql, "eval_benchmark").contains("workspace_key VARCHAR(128) NOT NULL")),
                () -> assertTrue(sql.contains("idx_context_snapshot_workspace")),
                () -> assertTrue(sql.contains("idx_memory_workspace")),
                () -> assertTrue(sql.contains("idx_memory_recall_workspace"))
        );
    }

    @Test
    void shouldStoreOnlyEncryptedApiKeyGivenModelProviderTable() throws IOException {
        // Given model provider table
        String modelProviderSql = tableSection(read("docs/dev-ops/mysql/sql/coder_agent_v4.sql"), "agent_model_provider");

        // When checking sensitive fields
        // Then API Key is stored as cipher text and no plain api_key column is present
        assertAll(
                () -> assertTrue(modelProviderSql.contains("api_key_cipher TEXT NULL")),
                () -> assertFalse(modelProviderSql.contains(" api_key VARCHAR")),
                () -> assertFalse(modelProviderSql.contains(" api_key TEXT"))
        );
    }

    @Test
    void shouldDefinePgVectorTableAndRollbackGivenVectorSql() throws IOException {
        // Given pgvector SQL and rollback SQL
        String sql = read("docs/dev-ops/postgresql/sql/coder_agent_memory.sql");
        String rollback = read("docs/dev-ops/postgresql/sql/coder_agent_memory_rollback.sql");

        // When checking vector storage boundary
        // Then vector extension, workspace index, HNSW index and rollback are defined
        assertAll(
                () -> assertTrue(sql.contains("CREATE EXTENSION IF NOT EXISTS vector")),
                () -> assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS coder_agent_memory_chunk")),
                () -> assertTrue(sql.contains("workspace_key VARCHAR(128) NOT NULL")),
                () -> assertTrue(sql.contains("embedding vector(1024) NOT NULL")),
                () -> assertTrue(sql.contains("USING hnsw (embedding vector_cosine_ops)")),
                () -> assertTrue(rollback.contains("DROP TABLE IF EXISTS coder_agent_memory_chunk"))
        );
    }

    @Test
    void shouldMapPoToExpectedTablesGivenMybatisPlusAnnotations() {
        // Given v4 PO classes
        Map<Class<?>, String> expectedTables = Map.of(
                ModelProviderPO.class, "agent_model_provider",
                ContextSnapshotPO.class, "context_snapshot",
                MemoryItemPO.class, "agent_memory_item",
                MemoryRecallPO.class, "memory_recall",
                ToolApprovalRequestPO.class, "tool_approval_request",
                EvalBenchmarkPO.class, "eval_benchmark",
                EvalRunPO.class, "eval_run",
                EvalCaseResultPO.class, "eval_case_result"
        );

        // When reading MyBatis-Plus table names
        // Then PO mappings match the migration tables
        expectedTables.forEach((type, tableName) -> assertEquals(tableName, type.getAnnotation(TableName.class).value()));
    }

    private String tableSection(String sql, String tableName) {
        int start = sql.indexOf("CREATE TABLE IF NOT EXISTS " + tableName);
        assertTrue(start >= 0, "missing table: " + tableName);
        int end = sql.indexOf(";\n", start);
        if (end < 0) {
            end = sql.length();
        }
        return sql.substring(start, end);
    }

    private String read(String relativePath) throws IOException {
        Path path = Path.of(relativePath);
        if (!Files.exists(path)) {
            path = Path.of("..").resolve(relativePath);
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
