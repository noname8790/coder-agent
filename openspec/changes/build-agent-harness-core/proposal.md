## Why

当前项目需要从 0 到 1 构建一个面向本地代码仓库长链路任务的 Java 服务端 Agent Harness，使模型能够通过受控工具真实读取、搜索和执行仓库任务，并留下可回放、可审计的执行链路。首版不做本地 CLI 客户端，服务可以运行在开发者本机并通过 REST API 接收任务。

先建设核心闭环，可以在不开放文件编辑和高风险操作的前提下验证模型接入、工具调用、上下文管理、运行审计和工件落盘的主路径，为后续长上下文治理、结构化记忆、评测闭环和 DashScope 专用 API 适配提供稳定基础。

## What Changes

- 新增 Maven 多模块 DDD 项目骨架，模块包括 `coder-agent-types`、`coder-agent-api`、`coder-agent-domain`、`coder-agent-case`、`coder-agent-infrastructure`、`coder-agent-trigger`、`coder-agent-app`。
- 新增 REST API 异步运行入口，支持创建 Agent 运行、查询状态、查询 trace、取消运行。
- 新增 OpenAI API 兼容模式模型后端，首版通过统一 `ModelGateway` 接口优先调用 Responses API + Tool Calling。
- 新增开发可用版工具调用能力，首版工具包括 `list_files`、`read_file`、`search_text`、`run_shell`。
- 新增真实模型验收要求，首版必须跑通真实 OpenAI-compatible API。
- 新增 Windows PowerShell-only 运行约束，首版不兼容 Linux/macOS Shell。
- 新增 `workspaceKey` 工作区配置和路径边界校验，禁止 API 直接传任意本地绝对路径执行。
- 新增工具安全策略，`run_shell` 采用命令前缀白名单 + 危险 token 拒绝机制。
- 新增基础上下文管理与简单预算保护，限制模型调用、工具调用、执行步数和总超时时间。
- 新增最大并发运行限制，首版 `max_concurrent_runs=2`。
- 新增 MySQL 持久化运行记录、步骤、模型调用、工具调用、审计事件和运行工件索引。
- 新增 workspace 内 `.coder` 运行工件落盘目录，保存 `run-meta.json`、`trace.jsonl`、`context-snapshot/*.json`、`tool-output/*.txt`、`final-result.json`。
- 首版不开放文件编辑、自动提交、PR 生成、结构化记忆、PGVector、Elasticsearch、完整 benchmark 报表、DashScope 专用 API、API 鉴权和多 Agent 协作。

## Capabilities

### New Capabilities

- `agent-run-lifecycle`: 定义 Agent 异步运行、状态流转、预算限制、取消和结果查询能力。
- `agent-tool-calling`: 定义 OpenAI-compatible Tool Calling、工具注册、工具执行和工具结果回传能力。
- `workspace-governance`: 定义 `workspaceKey`、工作区路径隔离、受限 Shell 白名单和安全拒绝能力。
- `run-audit-artifacts`: 定义运行审计事件、trace 事件流、上下文快照、工具输出和最终结果工件能力。

### Modified Capabilities

- 无。当前 `openspec/specs/` 为空，本次只新增能力规格。

## Impact

- 影响模块范围：
  - `coder-agent-types`: 通用枚举、异常、响应结构。
  - `coder-agent-api`: REST 请求/响应 DTO 与错误码。
  - `coder-agent-domain`: AgentRun、AgentStep、ModelCall、ToolCall、AuditEvent、RunArtifact 等领域模型与端口接口。
  - `coder-agent-case`: 创建运行、后台执行、取消运行、查询状态和 trace 的用例编排。
  - `coder-agent-infrastructure`: MySQL DAO/PO、OpenAI-compatible Gateway、工具执行适配器、工件落盘适配器。
  - `coder-agent-trigger`: HTTP Controller。
  - `coder-agent-app`: Spring Boot 启动类、配置、MyBatis Mapper、Dockerfile 和本地配置。
- 影响 API：
  - `POST /api/agent-runs`
  - `GET /api/agent-runs/{runId}`
  - `GET /api/agent-runs/{runId}/trace`
  - `POST /api/agent-runs/{runId}/cancel`
- 影响数据库表：
  - `agent_run`
  - `agent_step`
  - `model_call`
  - `tool_call`
  - `audit_event`
  - `run_artifact`
- 新增外部依赖：
  - Spring Boot 3.x
  - MyBatis-Plus
  - MySQL Connector/J
  - OkHttp 或 WebClient HTTP 客户端
  - Jackson
  - Lombok
  - JUnit 5 / Mockito
- 回滚方案：
  - 删除或停用本次新增 Maven 模块和 REST API 路由。
  - 回滚 MySQL 初始化 SQL，删除新增表或保留空表不再写入。
  - 移除 OpenAI-compatible 模型配置和工作区配置。
  - 删除 workspace 内 `.coder/runs/{runId}/` 中的新运行数据。
  - 若已部署服务，回退到变更前镜像或停止 `coder-agent-app` 服务。
