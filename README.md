# coder-agent

`coder-agent` 是一个 Java 服务端本地代码 Agent Harness，配套 Tauri 桌面客户端使用。它面向用户选择的本地代码仓库工作，通过 REST API 和 SSE 接收任务，在 workspace 内读取仓库、搜索代码、修改文件、运行受控 PowerShell 命令、执行 Git/PR 草稿流程，并把会话、上下文、记忆、工具调用、审批和运行工件持久化到 MySQL、PostgreSQL/pgvector 与 `{workspaceRoot}/.coder/`。

当前版本：`4.5.0`。v4.5 的重点是重建 Agent Harness 的认知内核：128K 分层上下文治理、结构化记忆与 pgvector 召回、freshness/可信度裁决、工具治理证据链、评测闭环，以及更清晰的 DDD 领域边界。

## 核心能力

- **模型配置**：客户端保存 OpenAI-compatible 模型配置，支持模型 key、展示名、Base URL、API Key、模型名、协议类型、超时和模型级上下文预算。
- **流式交互**：后端通过 SSE 推送 Agent 运行状态和模型输出，客户端使用 Markdown 渲染 Agent 消息并支持复制。
- **workspace 与会话**：用户可注册任意本地 workspace；会话保存最后一次模型和权限选择；新会话首条任务会自动生成标题。
- **权限等级**：`只读`、`默认`、`完全控制`。默认权限允许常规仓库任务，高风险动作需要审批；完全控制在 workspace 边界内免审批执行高风险动作。
- **代码工具**：支持读文件、搜索、列目录、补丁修改、覆盖写入、删除文件、受控 shell、Git status/diff/add/commit/reset/rm/clean/restore/log、PR 草稿等能力。
- **工具治理**：统一参数校验、workspace 路径隔离、审批、重复调用复用/阻断、敏感信息脱敏、工具结果证据化。
- **上下文治理**：使用 `<system>`、`<agent_workflow>`、`<workspace>`、`<permission>`、`<memory>`、`<context>`、`<work_status>`、`<current_task>` 分层 prompt；按预算装配、去重、压缩、锚点保护并保存快照。
- **结构化记忆**：支持项目级、文件级、任务级、运行观察和工作记忆；项目级/文件级记忆在同一 workspace 下跨会话共享，任务级/运行级记忆限定在 conversation/run 作用域。
- **pgvector 召回**：记忆摘要写入 MySQL，同时把可检索 chunk 写入 pgvector；召回时结合向量相似、路径/符号精确命中、freshness、可信度和权限过滤。
- **freshness 裁决**：文件 hash、路径存在性、workspace fingerprint、checkpoint 作用域和 evidence 校验失败时，旧记忆直接删除，后续重新读取再生成。
- **内部摘要**：任务结束后生成不展示给用户的 RUN_SUMMARY；文件读取和编辑写入结构化摘要，不把完整源码或长回复直接塞入记忆。
- **撤销与 checkpoint**：支持任务级撤销/还原代码改动，支持在会话内还原到检查点并折叠后续历史，仅用于审计。
- **运行审计与评测**：记录 agent run、model call、tool call、context snapshot、memory recall、eval case/run/result；支持压缩率、记忆命中、重复读、工具步数等指标。

## 模块

- `coder-agent-types`：通用配置、异常、枚举和响应模型。
- `coder-agent-api`：HTTP DTO。
- `coder-agent-domain`：按业务边界拆分为 `agent`、`context`、`memory`、`tool`、`workspace`、`model`、`evaluation` 等领域对象和端口。
- `coder-agent-case`：用例编排，包括 Agent Run、上下文、记忆、工具治理、审批、workspace、conversation、model provider、evaluation。
- `coder-agent-infrastructure`：MySQL、pgvector、模型网关、本地工具、Git 策略、工件落盘。
- `coder-agent-trigger`：REST Controller、SSE、CORS。
- `coder-agent-app`：Spring Boot 启动模块。
- `coder-agent-client`：Tauri + React 客户端。

## 环境要求

- Windows + PowerShell
- JDK 21
- Maven 3.9+
- Node.js 20+ / npm
- Rust + Tauri 所需 Windows C++ Build Tools
- MySQL 8+
- PostgreSQL 16+ + pgvector

## 数据库脚本

MySQL：

```text
docs/dev-ops/mysql/sql/coder-agent.sql
```

PostgreSQL/pgvector：

```text
docs/dev-ops/postgresql/sql/coder-agent.sql
```

## .env 配置

本地 `.env` 不提交。Embedding 模型需使用 `.env` 配置。

```dotenv
SERVER_ADDRESS=127.0.0.1
SERVER_PORT=8080

MYSQL_URL=jdbc:mysql://127.0.0.1:<你的 MYSQL 端口>/coder-agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
MYSQL_USERNAME=<你的 MySQL 账号>
MYSQL_PASSWORD=<你的 MySQL 密码>

PGVECTOR_ENABLED=true
PGVECTOR_URL=jdbc:postgresql://127.0.0.1:<你的 PostgreSQL 端口>/coder-agent
PGVECTOR_USERNAME=<你的 PostgreSQL 账号>
PGVECTOR_PASSWORD=<你的 PostgreSQL 密码>
PGVECTOR_SCHEMA=public
PGVECTOR_TABLE_PREFIX=coder_agent
PGVECTOR_VECTOR_DIMENSIONS=1024
PGVECTOR_INDEX_TYPE=hnsw
PGVECTOR_SIMILARITY=cosine

EMBEDDING_PROVIDER=openai-compatible
EMBEDDING_BASE_URL=<Embedding Base URL>
EMBEDDING_API_KEY=<你的 Embedding API Key>
EMBEDDING_MODEL=<Embedding 模型名>
EMBEDDING_ENDPOINT_TYPE=embeddings
EMBEDDING_TIMEOUT_SECONDS=120

MEMORY_ENABLED=true
CONTEXT_COMPRESSION_ENABLED=true
EVAL_ENABLED=true

CONTEXT_MAX_CONTEXT_TOKENS=131072
CONTEXT_MAX_INPUT_TOKENS=106496
CONTEXT_MAX_OUTPUT_TOKENS=8192
CONTEXT_SAFETY_RESERVE_TOKENS=16384
CONTEXT_SYSTEM_RESERVE_TOKENS=2000
CONTEXT_PREFIX_BUDGET_TOKENS=8192
CONTEXT_WORKING_MEMORY_BUDGET_TOKENS=12288
CONTEXT_MEMORY_BUDGET_TOKENS=12288
CONTEXT_RECENT_MESSAGE_BUDGET_TOKENS=16384
CONTEXT_FILE_SUMMARY_BUDGET_TOKENS=16384
CONTEXT_RAW_SNIPPET_BUDGET_TOKENS=32768
CONTEXT_TOOL_RESULT_BUDGET_TOKENS=20480
CONTEXT_RUN_TRACE_BUDGET_TOKENS=8192
CONTEXT_COMPRESSION_MAX_MODEL_CALLS_PER_RUN=2
CONTEXT_COMPRESSION_MAX_TOKENS_PER_RUN=8192

MEMORY_CANDIDATE_TOP_K=24
MEMORY_SELECTED_TOP_K=8
MEMORY_MIN_SCORE=0.35
MEMORY_MIN_TRUST_SCORE=0.65
MEMORY_MAX_CHUNKS_PER_RUN=12
MEMORY_MAX_EMBEDDING_CALLS_PER_RUN=8
MEMORY_MAX_AUTO_SUMMARY_FILES_PER_RUN=12
MEMORY_MAX_FILE_BYTES_FOR_SUMMARY=262144
MEMORY_CHUNK_MAX_TOKENS=1200
MEMORY_CHUNK_OVERLAP_TOKENS=120

TOOL_APPROVAL_ENABLED=true

AGENT_MAX_STEPS=50
AGENT_MAX_MODEL_CALLS=50
AGENT_MAX_TOOL_CALLS=100
AGENT_TIMEOUT_SECONDS=300
AGENT_MAX_CONCURRENT_RUNS=2

CODER_AGENT_WORKSPACE_ROOT=<服务默认工作目录，可留空使用 user.dir>
```

## 启动

[可选]构建后端 jar：

```powershell
mvn -pl coder-agent-app -am package -DskipTests
```

[可选]直接启动后端：

```powershell
java -jar coder-agent-app/target/coder-agent.jar
```

启动客户端：

```powershell
cd coder-agent-client
npm install
npm run tauri dev
```

[Tips] 客户端默认会构建并启动 `../coder-agent-app/target/coder-agent.jar`。

## 常用 API

创建 workspace：

```powershell
curl -X POST http://127.0.0.1:8080/api/workspaces `
  -H "Content-Type: application/json" `
  -d '{ "workspaceKey":"agent-test-demo", "rootPath":"E:/IdeaProjects/agent-test-demo", "displayName":"测试工作区" }'
```

保存模型配置：

```powershell
curl -X POST http://127.0.0.1:8080/api/model-providers `
  -H "Content-Type: application/json" `
  -d '{ "modelKey":"glm-5", "displayName":"glm-4.7", "baseUrl":"https://dashscope.aliyuncs.com/compatible-mode/v1", "apiKey":"<api-key>", "modelName":"glm-4.7", "endpointType":"chat-completions", "streamingEnabled":true, "toolCallingEnabled":true, "enabled":true }'
```

创建 Agent Run：

```powershell
curl -X POST http://127.0.0.1:8080/api/agent-runs `
  -H "Content-Type: application/json" `
  -d '{ "workspaceKey":"agent-test-demo", "task":"阅读当前仓库并说明模块结构。", "model":"glm-5", "permissionLevel":"DEFAULT" }'
```

订阅 SSE：

```powershell
curl http://127.0.0.1:8080/api/agent-runs/{runId}/events
```

审批高风险工具：

```powershell
curl http://127.0.0.1:8080/api/tool-approvals?workspaceKey=agent-test-demo

curl -X POST http://127.0.0.1:8080/api/tool-approvals/{approvalId}/approve `
  -H "Content-Type: application/json" `
  -d '{ "reason":"允许本次操作" }'

curl -X POST http://127.0.0.1:8080/api/tool-approvals/{approvalId}/reject `
  -H "Content-Type: application/json" `
  -d '{ "reason":"拒绝本次操作" }'
```

## 运行工件

每次任务会写入：

```text
{workspaceRoot}/.coder/runs/{runId}/
```

主要文件：

- `run-meta.json`：任务基本信息。
- `trace.jsonl`：一行一个运行事件，适合长任务回放。
- `context-snapshot/*.json`：每次模型调用前的上下文快照、预算、压缩和记忆指标。
- `tool-output/*.txt`：长工具输出。
- `changed-files.json`：文件变更摘要。
- `pull-request.md`：本地 PR 草稿。
- `final-result.json`：最终结论和指标。

## 验证

后端：

```powershell
mvn test
```

客户端：

```powershell
cd coder-agent-client
npm run test
npm run build
```

OpenSpec：

```powershell
openspec validate rebuild-context-memory-harness-v45 --strict
```

## 注意事项

- `.env`、`.coder/`、`coder-agent-client/src-tauri/target/` 都不应提交。
- v4.5 的记忆机制依赖 Embedding 配置和 pgvector；如果 `PGVECTOR_ENABLED=false` 或 `MEMORY_ENABLED=false`，Agent 仍可运行，但不会获得完整记忆召回能力。
