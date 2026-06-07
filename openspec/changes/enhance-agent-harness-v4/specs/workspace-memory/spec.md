## ADDED Requirements

### Requirement: 单 Workspace 记忆隔离
系统 MUST 将所有结构化记忆、向量 chunk、召回记录和 freshness 状态绑定到 workspaceKey，并禁止跨 workspace 召回。涉及 API：`POST /api/agent-runs`、`GET /api/workspaces/{workspaceKey}/memory`。涉及表：`agent_memory_item`、`memory_chunk`。

#### Scenario: 跨 Workspace 召回隔离
- **WHEN** workspace A 的任务触发记忆召回
- **THEN** 系统 MUST 只查询 workspace A 的记忆和向量 chunk

### Requirement: pgvector 向量记忆持久化
系统 SHALL 使用 PostgreSQL + pgvector 保存 memory chunk、embedding、sourceType、sourceId、workspaceKey、metadata、contentHash、freshnessStatus 和 createdAt。涉及配置：`PGVECTOR_*`。涉及表：`memory_chunk`。

#### Scenario: 写入文件摘要向量
- **WHEN** 文件摘要生成成功且 embedding 配置可用
- **THEN** 系统 MUST 将摘要文本向量化并写入 pgvector

### Requirement: 记忆向量召回
系统 SHALL 根据当前任务、会话摘要和最近用户输入生成查询向量，从当前 workspace 的 memory chunk 中召回 topK 相关记忆，并应用 minScore 过滤。涉及 API：`POST /api/agent-runs`。涉及表：`memory_recall`、`memory_chunk`。

#### Scenario: 召回相关文件摘要
- **WHEN** 用户任务与历史文件摘要相关
- **THEN** 系统 SHALL 将命中摘要作为候选上下文交给上下文治理引擎

### Requirement: Freshness 校验
系统 MUST 对文件摘要记忆记录 path、contentHash、mtime 和 summaryVersion。文件发生变化后，旧摘要 MUST 标记为 stale，不得作为可信上下文直接使用。涉及表：`agent_memory_item`、`memory_chunk`。

#### Scenario: 文件修改后重新运行
- **WHEN** 文件 hash 与摘要记录中的 contentHash 不一致
- **THEN** 系统 MUST 标记该文件摘要 stale，并触发刷新或跳过该记忆

