## ADDED Requirements

### Requirement: Chat 模型配置持久化
系统 SHALL 支持用户通过 API 创建、查询、修改、删除和启用 chat 模型配置。模型配置 MUST 包含模型 key、显示名称、provider、baseUrl、apiKey、modelName、endpointType、timeoutSeconds、temperature、streamingEnabled、toolCallingEnabled 和状态。涉及 API：`/api/model-providers`。涉及表：`agent_model_provider`。

#### Scenario: 创建模型配置
- **WHEN** 用户提交合法的 OpenAI-compatible 模型配置
- **THEN** 系统保存配置并返回模型 key、显示名称、协议类型和启用状态

#### Scenario: 删除正在使用的模型配置
- **WHEN** 用户删除当前默认模型或存在运行中任务引用的模型配置
- **THEN** 系统 MUST 拒绝删除并返回结构化错误原因

### Requirement: Agent Run 仅使用数据库模型配置
系统 MUST 使用 `agent_model_provider` 中启用的模型配置创建 Agent Run，不得在运行时回退到第三版 `application.yml/.env` 中固定的 `qwen3.6-plus`、`glm-5`、`deepseek-v4-flash` 或默认模型静态配置。涉及 API：`POST /api/agent-runs`、`/api/model-providers`。涉及表：`agent_model_provider`、`agent_run`。

#### Scenario: 请求未配置模型
- **WHEN** 用户创建 Agent Run 并传入数据库中不存在或未启用的 model key
- **THEN** 系统 MUST 返回 `MODEL_NOT_CONFIGURED`，且不得尝试读取静态三模型配置作为 fallback

#### Scenario: 迁移后的默认模型
- **WHEN** 系统已经通过初始化 SQL 或迁移脚本创建默认模型配置
- **THEN** Agent Run SHALL 从数据库读取默认模型配置，而不是从 `application.yml` 的固定模型字段读取

### Requirement: API Key 安全存储与脱敏
系统 MUST 对模型配置中的 apiKey 做安全存储，并在查询 API、日志、trace、context snapshot 和错误信息中脱敏展示。涉及 API：`/api/model-providers`。涉及表：`agent_model_provider`、`audit_event`。

#### Scenario: 查询模型配置列表
- **WHEN** 用户查询模型配置列表
- **THEN** 系统返回脱敏后的 apiKey，且不返回明文密钥

### Requirement: 模型级上下文预算
系统 SHALL 支持每个 chat 模型配置覆盖全局上下文预算，包括 maxContextTokens、maxOutputTokens、inputBudgetTokens、memoryBudgetTokens、fileSummaryBudgetTokens、rawFileBudgetTokens、toolResultBudgetTokens 和 recentMessageBudgetTokens。涉及 API：`/api/model-providers`。涉及表：`agent_model_provider`、`model_call`、`context_snapshot`。

#### Scenario: 使用模型级预算创建运行
- **WHEN** Agent Run 使用带自定义预算的模型配置
- **THEN** 每次模型调用的 context snapshot MUST 记录实际使用的模型预算来源

### Requirement: 固定全局 Embedding 配置
系统 SHALL 使用服务端 `.env` 中的全局 embedding 配置生成向量，不提供运行时切换 embedding 模型的客户端入口。涉及配置：`EMBEDDING_*`、`PGVECTOR_VECTOR_DIMENSIONS`。涉及表：`memory_chunk`。

#### Scenario: 启动时加载 embedding 配置
- **WHEN** 服务启动且 `MEMORY_ENABLED=true`
- **THEN** 系统 MUST 校验 embedding 配置和 pgvector 维度配置，并在配置缺失时禁用向量记忆或启动失败，具体行为由配置控制

## REMOVED Requirements

### Requirement: 静态三模型运行时白名单
**Reason**: v4 要开放用户自己的模型公共调用 URL 和 API Key，固定三模型运行时白名单会形成双轨模型来源，影响模型预算、协议能力和流式能力治理。

**Migration**: 使用初始化 SQL 或迁移脚本把旧 `.env` 中的模型配置写入 `agent_model_provider`。迁移后运行时只读取数据库模型配置。

#### Scenario: 旧静态配置不再兜底
- **WHEN** 数据库中没有对应模型配置，但 `.env` 中仍存在旧模型变量
- **THEN** 系统 MUST 拒绝创建 Agent Run，而不是使用旧静态配置继续运行
