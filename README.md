# coder-agent

`coder-agent` 是一个 Java 服务端本地代码 Agent Harness。它通过 REST API 和 Tauri 客户端接收任务，在用户注册的本地 workspace 中读取仓库、搜索、修改文件、执行受限 PowerShell 命令、生成本地 commit/PR 草稿，并把运行过程持久化到 MySQL、pgvector 和 `{workspaceRoot}/.coder/`。

当前 v4.1 是在 v4 Harness 能力之上的产品化优化版本，重点包括权限等级重命名、审批治理收敛、Git/PR 专用工具、Markdown 消息展示、复制按钮和 Diff 摘要卡片。

v4.2 在 v4.1 的 Diff、Git/PR、Markdown 消息基础上补齐“可回退工作流”，并进行部分git治理：
- Agent 消息的模型展示名跟随消息框右下角展示；checkpoint 入口居中于整个对话页，只出现在非最新、非回滚的终态 Agent 消息下。
- 注册或重新激活 workspace 时会确保 `.gitignore` 包含 `.coder/`，避免 `.coder/runs` 等运行工件被 `git add .` 带入提交。
- `git reset`、`git rm`、`git clean`、`git restore` 属于本地 Git 高风险操作：默认权限下需要用户审批，完全控制权限下直接放行；`git push` 仍不在当前版本开放范围内。

## 模块

- `coder-agent-types`：通用配置、枚举、异常和响应。
- `coder-agent-api`：REST DTO。
- `coder-agent-domain`：领域对象、值对象和端口接口。
- `coder-agent-case`：Agent Run、workspace、conversation、model provider、memory、context、approval、eval 用例编排。
- `coder-agent-infrastructure`：MySQL、pgvector、OpenAI-compatible 网关、本地工具、工件落盘。
- `coder-agent-trigger`：HTTP Controller 和 SSE。
- `coder-agent-app`：Spring Boot 启动模块。
- `coder-agent-client`：Tauri + React 客户端。

## v4.1 能力

### 权限等级

对外权限等级改为：

- `READ_ONLY` / 只读：读取仓库、搜索、查看 Git 状态和生成分析结论，不修改本地文件。
- `DEFAULT` / 默认：允许常规仓库任务；删除、覆盖、Git 写入、PR 草稿和本地 commit 等高风险动作需要审批。
- `FULL_ACCESS` / 完全控制：解锁 workspace 内全部本地仓库操作，高风险动作不再请求审批，但仍保留 workspace 边界、受保护路径、危险命令拒绝、脱敏和审计。

首次打开 conversation 默认使用 `DEFAULT`。之后系统按 conversation 持久化 `lastPermissionLevel`，再次打开该会话时恢复上次选择。

### Git / PR 工具

v4.1 增加专用 Git/PR 工具，避免由 `run_shell` 拼接 Git 命令导致超时或解析不稳定：

- `git_status`
- `git_diff`
- `git_log`
- `git_add`
- `git_commit`
- `generate_pr_draft`

`generate_pr_draft` 只生成本地 `.coder/runs/{runId}/pull-request.md`，不会执行远程 push，也不会调用 GitHub/GitLab API。

### Diff 摘要

Agent 修改文件后会生成 `changed-files.json`，包含文件路径、变更类型和增删行统计。run/message 详情 API 会返回 Diff 摘要，客户端在 Agent 消息下方展示 Diff 卡片，默认显示前 3 个文件，可展开全部文件。

### 客户端体验

- 权限选择器为三档选项，包含图标、标题、描述、选中标记；`FULL_ACCESS` 使用黄色风险提示。
- Agent 消息按 Markdown 安全渲染，不执行 raw HTML。
- 用户消息和 Agent 消息下方提供复制图标，复制原始文本。
- 有文件变更的 Agent 消息展示 Diff 摘要卡片。

## 数据库

MySQL 初始化脚本：

```text
docs/dev-ops/mysql/sql/coder-agent.sql
```

PostgreSQL/pgvector 初始化脚本：

```text
docs/dev-ops/postgresql/sql/coder-agent.sql
```

MYSQL 表包含会话记录、模型配置、上下文快照、Agent 运行记录、工作区配置、记忆元数据、记忆召回、工具审批、运行工件记录、模型调用记录、eval benchmark/run/result等。

pgvector 保存向量 chunk 和检索元数据。

## .env 配置

本地 `.env` 不提交。推荐至少配置：

```dotenv
MYSQL_URL=jdbc:mysql://127.0.0.1:13306/coder-agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
MYSQL_USERNAME=<你的 MySQL 账号>
MYSQL_PASSWORD=<你的 MySQL 密码>

PGVECTOR_ENABLED=true
PGVECTOR_URL=jdbc:postgresql://127.0.0.1:15432/coder-agent
PGVECTOR_USERNAME=<你的 PostgreSQL 账号>
PGVECTOR_PASSWORD=<你的 PostgreSQL 密码>
PGVECTOR_SCHEMA=public
PGVECTOR_TABLE_PREFIX=coder_agent
PGVECTOR_VECTOR_DIMENSIONS=1024
PGVECTOR_INDEX_TYPE=hnsw
PGVECTOR_SIMILARITY=cosine

EMBEDDING_PROVIDER=openai-compatible
EMBEDDING_BASE_URL=<Base URL>
EMBEDDING_API_KEY=<你的 API Key>
EMBEDDING_MODEL=<Embedding 模型key>
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
  -d '{ "workspaceKey":"agent-test-demo", "task":"阅读当前仓库并简要说明模块结构。", "model":"glm-5", "permissionLevel":"DEFAULT" }'
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

## 验证

```powershell
mvn test

cd coder-agent-client
npm run test
npm run build

cd ..
openspec validate optimize-v4-agent-ux-and-git-ops --strict
```

## 回滚边界

- 可执行 `docs/dev-ops/mysql/sql/coder_agent_v4_1.sql` 中的回滚 SQL，将权限字段和值恢复到旧语义。
- 如需关闭结构化记忆：`MEMORY_ENABLED=false`。
- 如需关闭 pgvector 向量召回：`PGVECTOR_ENABLED=false`。
- 如需关闭人工审批：`TOOL_APPROVAL_ENABLED=false`，但仍保留基础工具治理。
- 如需关闭 eval：`EVAL_ENABLED=false`。