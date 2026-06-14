## ADDED Requirements

### Requirement: 单 Workspace 记忆隔离
系统 MUST 按 workspaceKey 隔离所有记忆元数据、向量 chunk、召回记录和上下文候选。

#### Scenario: 召回当前 workspace 记忆
- **WHEN** Agent Run 在 workspace A 中执行记忆召回
- **THEN** 系统 MUST 只返回 workspace A 的记忆，不得返回 workspace B 的记忆

### Requirement: pgvector 向量记忆持久化
系统 SHALL 使用 PostgreSQL + pgvector 保存 memory chunk embedding，并支持 topK、minScore 和 workspaceKey 过滤。

#### Scenario: 写入并检索向量记忆
- **WHEN** 系统生成文件摘要或运行摘要
- **THEN** 系统 SHALL 写入 MySQL 记忆元数据和 pgvector 向量 chunk

### Requirement: Freshness 校验
系统 MUST 为文件摘要记录 path、contentHash、mtime 和 summaryVersion，并在文件变化后标记 stale。

#### Scenario: 文件内容变化
- **WHEN** 文件摘要对应的 contentHash 与当前文件不一致
- **THEN** 系统 MUST 标记该记忆为 stale，并避免把它作为可信上下文直接注入 prompt

### Requirement: pgvector 降级运行
系统 SHALL 在 `PGVECTOR_ENABLED=false` 或 pgvector 不可用时继续执行 Agent Run，并记录降级原因。

#### Scenario: pgvector 不可用
- **WHEN** 向量记忆端口不可用
- **THEN** 系统 MUST 跳过向量召回并继续执行非向量上下文装配

### Requirement: 删除会话时清理记忆
系统 MUST 在删除会话或关联运行数据时，同步清理 MySQL 记忆元数据、记忆召回记录和 pgvector memory chunk。

#### Scenario: 删除会话级联清理记忆
- **WHEN** 用户删除一个会话
- **THEN** 系统 MUST 删除该会话所有 runId 对应的 MySQL memory 和 pgvector chunk
- **AND** 不得保留可被后续同 workspace 任务召回的孤立记忆
