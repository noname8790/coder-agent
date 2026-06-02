# coder-agent

`coder-agent` 是一个 Java 服务端本地代码 Agent Harness。它通过 REST API 接收任务，在服务端配置的本地 `workspaceKey` 内执行受限工具，调用 OpenAI-compatible 模型接口，并把运行记录写入 MySQL，把可回放工件写入 `{workspaceRoot}/.coder/runs/{runId}/`。

当前第三版后端定位是本地仓库 Agent 基础闭环：读仓库、搜索、按权限等级执行安全编辑或仓库写入、本地 Git 分支/提交、生成 PR 草稿、SSE 运行事件流、会话历史和回滚材料。第三版仍不开放 `git push`、`git clean`、`git reset --hard` 或远程 PR 创建。

## 模块

- `coder-agent-types`：通用枚举、异常、响应、配置。
- `coder-agent-api`：REST DTO。
- `coder-agent-domain`：领域对象和值对象、端口接口。
- `coder-agent-case`：会话、权限等级、创建、查询、取消、事件发布和后台执行编排。
- `coder-agent-infrastructure`：MySQL、OpenAI-compatible 网关、本地工具、工件落盘。
- `coder-agent-trigger`：HTTP Controller。
- `coder-agent-app`：Spring Boot 启动和配置。

## 数据库

初始化 SQL：

```text
docs/dev-ops/mysql/sql/coder_agent.sql
```

如果是从首版或第二版数据库升级，`CREATE TABLE IF NOT EXISTS` 不会修改既有 `agent_run` 表。第三版升级字段和回滚 SQL 已写在 `docs/dev-ops/mysql/sql/coder_agent.sql` 尾部，至少需要新增：

- `agent_conversation`
- `agent_message`
- `agent_permission_audit`
- `agent_run.conversation_id`
- `agent_run.permission_level`
- `agent_run.git_branch`
- `agent_run.commit_hash`

涉及表：

- `agent_run`
- `agent_workspace`
- `agent_conversation`
- `agent_message`
- `agent_permission_audit`
- `agent_step`
- `model_call`
- `tool_call`
- `audit_event`
- `run_artifact`

## 配置

`coder-agent-app/src/main/resources/application.yml` 内置本地开发默认值：

- MySQL：`127.0.0.1:13306/coder-agent`
- 默认 `workspaceKey`：`coder-agent`
- 默认模型 key：`glm-5`
- 可选模型 key：`qwen3.6-plus`、`glm-5`、`deepseek-v4-flash`

本地开发可以在项目根目录创建 `.env`，Spring Boot 会通过 `spring.config.import=optional:file:.env[.properties]` 自动读取。`.env` 使用普通 `KEY=value` 格式：

```dotenv
MYSQL_URL=jdbc:mysql://127.0.0.1:13306/coder-agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
MYSQL_USERNAME=root
MYSQL_PASSWORD=

CODER_AGENT_WORKSPACE_ROOT=
CODER_AGENT_DEFAULT_MODEL_KEY=glm-5

OPENAI_COMPATIBLE_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
OPENAI_COMPATIBLE_API_KEY=
OPENAI_COMPATIBLE_ENDPOINT_TYPE=chat-completions
OPENAI_COMPATIBLE_TIMEOUT_SECONDS=180

GLM_OPENAI_COMPATIBLE_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
GLM_OPENAI_COMPATIBLE_API_KEY=
GLM_OPENAI_COMPATIBLE_MODEL=glm-5
GLM_OPENAI_COMPATIBLE_ENDPOINT_TYPE=chat-completions
GLM_OPENAI_COMPATIBLE_TIMEOUT_SECONDS=180

QWEN_OPENAI_COMPATIBLE_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
QWEN_OPENAI_COMPATIBLE_API_KEY=
QWEN_OPENAI_COMPATIBLE_MODEL=qwen3.6-plus
QWEN_OPENAI_COMPATIBLE_ENDPOINT_TYPE=chat-completions
QWEN_OPENAI_COMPATIBLE_TIMEOUT_SECONDS=180

DEEPSEEK_OPENAI_COMPATIBLE_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
DEEPSEEK_OPENAI_COMPATIBLE_API_KEY=
DEEPSEEK_OPENAI_COMPATIBLE_MODEL=deepseek-v4-flash
DEEPSEEK_OPENAI_COMPATIBLE_ENDPOINT_TYPE=chat-completions
DEEPSEEK_OPENAI_COMPATIBLE_TIMEOUT_SECONDS=180
```

Windows 路径建议使用正斜杠，例如 `D:/Projects/coder-agent`。如果使用反斜杠，需要写成双反斜杠，例如 `D:\\Projects\\coder-agent`，否则 `.env[.properties]` 会把反斜杠当作转义字符，导致 workspaceRoot 被解析到错误目录。

`POST /api/agent-runs` 中的 `model` 字段是服务端配置的模型 key，只能传 `qwen3.6-plus`、`glm-5`、`deepseek-v4-flash` 这类已配置 key。请求体不能传 `baseUrl` 或 `apiKey`，模型后端地址和密钥只允许来自服务端配置。

## 启动

```powershell
mvn -pl coder-agent-app -am clean package
java -jar coder-agent-app\target\coder-agent.jar
```

首版 Shell 工具只支持 Windows PowerShell。

## API 示例

注册 workspace：

```powershell
curl -X POST http://127.0.0.1:8080/api/workspaces `
  -H "Content-Type: application/json" `
  -d "{\"workspaceKey\":\"demo\",\"rootPath\":\"E:/Projects/demo\"}"
```

查询 workspace：

```powershell
curl http://127.0.0.1:8080/api/workspaces
curl http://127.0.0.1:8080/api/workspaces/demo
```

停用 workspace：

```powershell
curl -X DELETE http://127.0.0.1:8080/api/workspaces/demo
```

创建运行，使用默认模型：

```powershell
curl -X POST http://127.0.0.1:8080/api/agent-runs `
  -H "Content-Type: application/json" `
  -d "{\"workspaceKey\":\"coder-agent\",\"task\":\"请阅读仓库结构并说明这个项目的模块职责\"}"
```

查询权限等级：

```powershell
curl http://127.0.0.1:8080/api/permission-levels
```

创建会话：

```powershell
curl -X POST http://127.0.0.1:8080/api/conversations `
  -H "Content-Type: application/json" `
  -d "{\"workspaceKey\":\"demo\",\"title\":\"修复计算器\",\"defaultModel\":\"glm-5\",\"defaultPermissionLevel\":\"L2_SAFE_EDIT\"}"
```

查询会话与消息：

```powershell
curl http://127.0.0.1:8080/api/conversations?workspaceKey=demo
curl http://127.0.0.1:8080/api/conversations/{conversationId}
curl http://127.0.0.1:8080/api/conversations/{conversationId}/messages
```

创建 L2 安全编辑运行：

```powershell
curl -X POST http://127.0.0.1:8080/api/agent-runs `
  -H "Content-Type: application/json" `
  -d "{\"workspaceKey\":\"demo\",\"conversationId\":\"{conversationId}\",\"model\":\"glm-5\",\"permissionLevel\":\"L2_SAFE_EDIT\",\"task\":\"请新增一个 docs/agent-note.md 并运行 mvn test\"}"
```

创建 L3 仓库写入运行，允许覆盖/删除文件、本地分支和本地 commit：

```powershell
curl -X POST http://127.0.0.1:8080/api/agent-runs `
  -H "Content-Type: application/json" `
  -d "{\"workspaceKey\":\"demo\",\"conversationId\":\"{conversationId}\",\"model\":\"glm-5\",\"permissionLevel\":\"L3_REPO_WRITE\",\"task\":\"请修复 Calculator 测试，必要时覆盖或删除无用文件，完成后创建本地 commit 并生成 PR 草稿\"}"
```

创建运行，指定模型 key：

```powershell
curl -X POST http://127.0.0.1:8080/api/agent-runs `
  -H "Content-Type: application/json" `
  -d "{\"workspaceKey\":\"coder-agent\",\"model\":\"deepseek-v4-flash\",\"task\":\"请阅读仓库结构并说明这个项目的模块职责\"}"
```

查询运行：

```powershell
curl http://127.0.0.1:8080/api/agent-runs/{runId}
```

查询 trace：

```powershell
curl http://127.0.0.1:8080/api/agent-runs/{runId}/trace
```

订阅 SSE 运行事件：

```powershell
curl -N http://127.0.0.1:8080/api/agent-runs/{runId}/events
```

取消运行：

```powershell
curl -X POST http://127.0.0.1:8080/api/agent-runs/{runId}/cancel
```

## 工具白名单

工具：

- `list_files`
- `read_file`
- `search_text`
- `run_shell`
- `apply_patch`：`L2_SAFE_EDIT` 及以上可见，只能修改已有文本文件。
- `write_file`：`L2_SAFE_EDIT` 及以上可见，只能新建文本文件，不覆盖已有文件。
- `overwrite_file`：仅 `L3_REPO_WRITE` 可见，覆盖已有文本文件。
- `delete_file`：仅 `L3_REPO_WRITE` 可见，删除普通文本文件。

权限等级：

- `L1_READ_ONLY`：读取、搜索、列目录、Git 只读命令。
- `L2_SAFE_EDIT`：L1 + 新增文件、patch 修改、测试/构建。
- `L3_REPO_WRITE`：L2 + 覆盖文件、删除文件、本地分支、`git add`、`git commit`、PR 草稿和回滚材料。

默认允许命令前缀：

- `git status`
- `git diff`
- `git log`
- `git checkout -b`
- `git add`
- `git commit`
- `mvn test`
- `mvn -q test`
- `mvn clean test`
- `mvn package`
- `mvn clean package`
- `mvn -pl`
- `java -version`

包含 `&&`、`|`、重定向、删除、移动、`git reset`、`git push`、`git clean` 等高风险 token 的命令会被拒绝。即使处于 `L3_REPO_WRITE`，`git push`、`git clean`、`git reset --hard` 仍会被拒绝。

编辑受保护路径：

- `.env`、`.env.*`
- `.git/`
- `.coder/`
- `target/`
- 文件名包含 `secret`、`token`、`password`、`credential` 或常见私钥文件名

## 工件

每次运行会写入：

```text
{workspaceRoot}/.coder/runs/{runId}/run-meta.json
{workspaceRoot}/.coder/runs/{runId}/trace.jsonl
{workspaceRoot}/.coder/runs/{runId}/context-snapshot/*.json
{workspaceRoot}/.coder/runs/{runId}/tool-output/*.txt
{workspaceRoot}/.coder/runs/{runId}/final-result.json
```

发生编辑或测试/构建后还会写入：

```text
{workspaceRoot}/.coder/runs/{runId}/patch.diff
{workspaceRoot}/.coder/runs/{runId}/changed-files.json
{workspaceRoot}/.coder/runs/{runId}/test-report.json
{workspaceRoot}/.coder/runs/{runId}/review-summary.md
{workspaceRoot}/.coder/runs/{runId}/rollback.patch
{workspaceRoot}/.coder/runs/{runId}/file-backup/*
{workspaceRoot}/.coder/runs/{runId}/pull-request.md
```

`final-result.json` 会包含 `permissionLevel`、`conversationId`、`changed`、`changedFileCount`、`testStatus`、`gitBranch`、`commitHash`、`prDraftPath`、`rollbackArtifacts` 和新增审查工件路径。

## 真实 API 冒烟验证

补齐 MySQL、OpenAI-compatible 和 workspace 配置后，可以创建一个本地仓库分析任务。验收点：

- `agent_run` 进入 `SUCCEEDED`，或模型不可用时进入 `FAILED`。
- `model_call` 至少有一条真实模型调用记录。
- `trace.jsonl` 可以逐行解析。
- `final-result.json` 包含 `status`、`model`、`actual_model`、`permissionLevel`、`attempts`、`tool_steps`、`model_calls`、`tool_calls`、`duration`。

## 回滚方案

- 停止 `coder-agent-app` 服务。
- 停止使用 `/api/conversations`、`/api/permission-levels`、`/api/agent-runs/{runId}/events` 和 `permissionLevel=L2/L3`。
- 将新任务默认限制为 `L1_READ_ONLY`。
- 从工具注册中移除 `overwrite_file`、`delete_file` 和 L3 Git 写入能力，必要时仅保留只读工具。
- 保留第三版新增表/字段但不再写入；必要时执行 SQL 中的第三版回滚语句删除新增表/列。
- 如需清理测试数据，删除 MySQL 中对应 `run_id` 的运行记录，并删除 workspace 下 `.coder/runs/{runId}/` 工件目录。
