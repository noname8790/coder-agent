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
    void shouldDefineMysqlTablesGivenV4Sql() throws IOException {
        String sql = read("docs/dev-ops/mysql/sql/coder-agent.sql");

        assertAll(
                () -> assertTrue(sql.contains("CREATE TABLE `agent_model_provider`")),
                () -> assertTrue(sql.contains("CREATE TABLE `context_snapshot`")),
                () -> assertTrue(sql.contains("CREATE TABLE `agent_memory_item`")),
                () -> assertTrue(sql.contains("CREATE TABLE `memory_recall`")),
                () -> assertTrue(sql.contains("CREATE TABLE `tool_approval_request`")),
                () -> assertTrue(sql.contains("CREATE TABLE `eval_benchmark`")),
                () -> assertTrue(sql.contains("CREATE TABLE `eval_run`")),
                () -> assertTrue(sql.contains("CREATE TABLE `eval_case_result`"))
        );
    }

    @Test
    void shouldKeepWorkspaceIsolationFieldsGivenMemoryAndContextTables() throws IOException {
        String sql = read("docs/dev-ops/mysql/sql/coder-agent.sql");

        assertAll(
                () -> assertTrue(tableSection(sql, "context_snapshot").contains("`workspace_key` varchar(128)")),
                () -> assertTrue(tableSection(sql, "agent_memory_item").contains("`workspace_key` varchar(128)")),
                () -> assertTrue(tableSection(sql, "memory_recall").contains("`workspace_key` varchar(128)")),
                () -> assertTrue(tableSection(sql, "tool_approval_request").contains("`workspace_key` varchar(128)")),
                () -> assertTrue(tableSection(sql, "eval_benchmark").contains("`workspace_key` varchar(128)")),
                () -> assertTrue(sql.contains("idx_context_snapshot_workspace")),
                () -> assertTrue(sql.contains("idx_memory_workspace")),
                () -> assertTrue(sql.contains("idx_memory_recall_workspace"))
        );
    }

    @Test
    void shouldStoreOnlyEncryptedApiKeyGivenModelProviderTable() throws IOException {
        String modelProviderSql = tableSection(read("docs/dev-ops/mysql/sql/coder-agent.sql"), "agent_model_provider");

        assertAll(
                () -> assertTrue(modelProviderSql.contains("`api_key_cipher` text")),
                () -> assertFalse(modelProviderSql.contains(" `api_key` varchar")),
                () -> assertFalse(modelProviderSql.contains(" `api_key` text"))
        );
    }

    @Test
    void shouldDefinePgVectorTableAndRollbackGivenVectorSql() throws IOException {
        String sql = read("docs/dev-ops/postgresql/sql/coder-agent.sql");

        assertAll(
                () -> assertTrue(sql.contains("CREATE TYPE \"public\".\"vector\"")),
                () -> assertTrue(sql.contains("CREATE TABLE \"public\".\"coder_agent_memory_chunk\"")),
                () -> assertTrue(sql.contains("\"workspace_key\" varchar")),
                () -> assertTrue(sql.contains("\"embedding\" \"public\".\"vector\" NOT NULL")),
                () -> assertTrue(sql.contains("idx_memory_chunk_embedding_hnsw")),
                () -> assertTrue(sql.contains("DROP TABLE IF EXISTS \"public\".\"coder_agent_memory_chunk\""))
        );
    }

    @Test
    void shouldMapPoToExpectedTablesGivenMybatisPlusAnnotations() {
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

        expectedTables.forEach((type, tableName) -> assertEquals(tableName, type.getAnnotation(TableName.class).value()));
    }

    private String tableSection(String sql, String tableName) {
        int start = sql.indexOf("CREATE TABLE `" + tableName + "`");
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
