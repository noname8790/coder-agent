# coder-agent

`coder-agent` 是一个 Java 服务端本地代码 Agent Harness。它通过 REST API 和 Tauri 客户端接收任务，在用户注册的本地 workspace 中读取仓库、搜索、修改文件、执行受限 PowerShell 命令、生成本地 commit/PR 草稿，并把运行过程持久化到 MySQL、pgvector 和 `{workspaceRoot}/.coder/`。

当前 v4 目标是把项目从“能操作仓库的 coding agent”推进到“可治理、可记忆、可审计、可评测的 Agent Harness”，v4 版经过功能测试已实现Coding Agent基本功能。

## 模块

- `coder-agent-types`：通用配置、枚举、异常和响应。
- `coder-agent-api`：REST DTO。
- `coder-agent-domain`：领域对象、值对象和端口接口。
- `coder-agent-case`：Agent Run、workspace、conversation、model provider、memory、context、approval、eval 用例编排。
- `coder-agent-infrastructure`：MySQL、pgvector、OpenAI-compatible 网关、本地工具、工件落盘。
- `coder-agent-trigger`：HTTP Controller 和 SSE。
- `coder-agent-app`：Spring Boot 启动模块。
- `coder-agent-client`：Tauri + React 客户端。

## v4 能力

- 模型配置中心：用户通过 API/客户端保存 OpenAI-compatible Base URL、API Key、模型名、Endpoint Type、流式能力和上下文预算。v4 不再使用 v3 的固定三模型运行时白名单。
- 流式输出：Agent 正文通过 `assistant_delta` 实时推送，不再保留 v3 非流式一次性返回主路径。
- 长上下文治理：按 system、任务、权限、消息、记忆、文件摘要、原始片段、工具结果分层装配 prompt，并写入 context snapshot。
- 结构化记忆：MySQL 保存记忆元数据，PostgreSQL + pgvector 保存向量 chunk，每个 workspace 独立召回。
- 自动摘要：读取文件、搜索命中和运行结束时按预算生成文件/运行摘要并向量化。
- 工具治理：工具 schema 校验、敏感路径拒绝、重复调用拦截、敏感信息脱敏。
- 高风险审批：`overwrite_file`、`delete_file`、`git commit` 进入 `WAITING_APPROVAL`；批准后继续，拒绝后把结构化拒绝结果返回 Agent。
- Eval MVP：支持 benchmark、按模型批量执行、汇总 pass_rate/model_calls/tool_calls/failure_category/context compression/memory hit，并写 `.coder/evals/{evalId}/` 报告。

## 数据库

MySQL 初始化：

```text
docs/dev-ops/mysql/sql/coder-agent.sql
```

PostgreSQL/pgvector 初始化：

```text
docs/dev-ops/postgresql/sql/coder-agent.sql
```

MYSQL 表包含会话记录、模型配置、上下文快照、Agent 运行记录、工作区配置、记忆元数据、记忆召回、工具审批、运行工件记录、模型调用记录、eval benchmark/run/result等。

pgvector 保存向量 chunk 和检索元数据。

## .env 配置

本地 `.env` 不提交。推荐至少配置：

```dotenv
MYSQL_URL=jdbc:mysql://127.0.0.1:13306<替换为你的MYSQL端口>/coder-agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
MYSQL_USERNAME=<你的MYSQL账户>
MYSQL_PASSWORD=<你的MYSQL密码>

PGVECTOR_ENABLED=true
PGVECTOR_URL=jdbc:postgresql://127.0.0.1:15432<替换为你的postgreSQL端口>/coder-agent
PGVECTOR_USERNAME=<你的postgreSQL账户>
PGVECTOR_PASSWORD=<你的postgreSQL密码>
PGVECTOR_SCHEMA=public
PGVECTOR_TABLE_PREFIX=coder_agent
PGVECTOR_VECTOR_DIMENSIONS=1024
PGVECTOR_INDEX_TYPE=hnsw
PGVECTOR_SIMILARITY=cosine

EMBEDDING_PROVIDER=openai-compatible
EMBEDDING_BASE_URL=<Base URL>
EMBEDDING_API_KEY=<你的API Key>
EMBEDDING_MODEL=<模型 key>
EMBEDDING_ENDPOINT_TYPE=embeddings
EMBEDDING_TIMEOUT_SECONDS=120

MEMORY_ENABLED=true
MEMORY_TOP_K=8
MEMORY_MIN_SCORE=0.35
MEMORY_MAX_CHUNKS_PER_RUN=20
MEMORY_MAX_EMBEDDING_CALLS_PER_RUN=8
MEMORY_MAX_AUTO_SUMMARY_FILES_PER_RUN=6
MEMORY_MAX_FILE_BYTES_FOR_SUMMARY=65536

CONTEXT_MAX_INPUT_TOKENS=24000
CONTEXT_MAX_OUTPUT_TOKENS=4096
CONTEXT_TOOL_RESULT_BUDGET_TOKENS=4000

TOOL_APPROVAL_ENABLED=true
EVAL_ENABLED=true
```

Chat 模型通过客户端模型配置中心保存到数据库；Embedding 模型仍使用`.env` 全局固定配置[需在使用前进行手动配置]。

## 启动

后端：

```powershell
$env:JAVA_HOME='E:\Java\jdk-21.0.11'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -pl coder-agent-app -am spring-boot:run
```

客户端[自动启动后端]：

```powershell
cd coder-agent-client
npm install
npm run tauri dev
```

## 常用 API [curl测试]

模型配置：

```powershell
curl -X POST http://localhost:8080/api/model-providers `
  -H "Content-Type: application/json" `
  -d '{ "modelKey":"glm-5", "displayName":"GLM-5", "baseUrl":"https://dashscope.aliyuncs.com/compatible-mode/v1", "apiKey":"<api-key>", "modelName":"glm-5", "endpointType":"chat-completions", "streamingEnabled":true, "toolCallingEnabled":true, "enabled":true, "defaultModel":true }'

curl http://localhost:8080/api/model-providers?enabledOnly=true
```

创建运行：

```powershell
curl -X POST http://localhost:8080/api/agent-runs `
  -H "Content-Type: application/json" `
  -d '{ "workspaceKey":"agent-test-demo", "task":"阅读当前仓库并简要说明模块结构。", "model":"glm-5", "permissionLevel":"L1" }'
```

SSE：

```powershell
curl http://localhost:8080/api/agent-runs/{runId}/events
```

审批：

```powershell
curl http://localhost:8080/api/tool-approvals?workspaceKey=agent-test-demo

curl -X POST http://localhost:8080/api/tool-approvals/{approvalId}/approve `
  -H "Content-Type: application/json" `
  -d '{ "reason":"确认允许本次高风险操作" }'

curl -X POST http://localhost:8080/api/tool-approvals/{approvalId}/reject `
  -H "Content-Type: application/json" `
  -d '{ "reason":"不允许修改该文件" }'
```

Eval：

```powershell
curl -X POST http://localhost:8080/api/evals/runs `
  -H "Content-Type: application/json" `
  -d '{ "workspaceKey":"agent-test-demo", "modelKeys":["glm-5"] }'
```

## 验证

```powershell
mvn -pl coder-agent-app -am test

cd coder-agent-client
npm test
npm run build

cd ..
openspec validate enhance-agent-harness-v4
```

## 回滚边界

- `MEMORY_ENABLED=false` 可停用结构化记忆流程。
- `PGVECTOR_ENABLED=false` 可停用 pgvector 向量召回，系统降级为无向量记忆。
- `TOOL_APPROVAL_ENABLED=false` 可停用人工审批，仍保留基础工具治理。
- `EVAL_ENABLED=false` 可停用 eval API 与报告生成。
- v4 不保留静态三模型白名单和非流式一次性结果输出的兼容分支；如需恢复，只能通过代码回滚。
