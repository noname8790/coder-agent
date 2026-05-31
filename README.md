# coder-agent

`coder-agent` 是一个 Java 服务端本地代码 Agent Harness。它通过 REST API 接收任务，在服务端配置的本地 `workspaceKey` 内执行受限工具，调用 OpenAI-compatible 模型接口，并把运行记录写入 MySQL，把可回放工件写入 `{workspaceRoot}/.coder/runs/{runId}/`。

首版定位是开发可用闭环：读仓库、搜索、执行受限诊断命令、生成结论。首版不开放文件编辑、Git commit/push、分支创建或 PR 生成。

## 模块

- `coder-agent-types`：通用枚举、异常、响应、配置。
- `coder-agent-api`：REST DTO。
- `coder-agent-domain`：领域对象和值对象、端口接口。
- `coder-agent-case`：创建、查询、取消和后台执行编排。
- `coder-agent-infrastructure`：MySQL、OpenAI-compatible 网关、本地工具、工件落盘。
- `coder-agent-trigger`：HTTP Controller。
- `coder-agent-app`：Spring Boot 启动和配置。

## 数据库

初始化 SQL：

```text
docs/dev-ops/mysql/sql/coder_agent.sql
```

涉及表：

- `agent_run`
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

创建运行，使用默认模型：

```powershell
curl -X POST http://127.0.0.1:8080/api/agent-runs `
  -H "Content-Type: application/json" `
  -d "{\"workspaceKey\":\"coder-agent\",\"task\":\"请阅读仓库结构并说明这个项目的模块职责\"}"
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

取消运行：

```powershell
curl -X POST http://127.0.0.1:8080/api/agent-runs/{runId}/cancel
```

## 工具白名单

首版工具：

- `list_files`
- `read_file`
- `search_text`
- `run_shell`

默认允许命令前缀：

- `git status`
- `git diff`
- `git log`
- `mvn test`
- `mvn -q test`
- `mvn clean test`
- `mvn package`
- `mvn -pl`
- `java -version`

包含 `&&`、`|`、重定向、删除、移动、`git reset`、`git push`、`git commit` 等高风险 token 的命令会被拒绝。

## 工件

每次运行会写入：

```text
{workspaceRoot}/.coder/runs/{runId}/run-meta.json
{workspaceRoot}/.coder/runs/{runId}/trace.jsonl
{workspaceRoot}/.coder/runs/{runId}/context-snapshot/*.json
{workspaceRoot}/.coder/runs/{runId}/tool-output/*.txt
{workspaceRoot}/.coder/runs/{runId}/final-result.json
```

## 真实 API 冒烟验证

补齐 MySQL、OpenAI-compatible 和 workspace 配置后，可以创建一个本地仓库分析任务。验收点：

- `agent_run` 进入 `SUCCEEDED`，或模型不可用时进入 `FAILED`。
- `model_call` 至少有一条真实模型调用记录。
- `trace.jsonl` 可以逐行解析。
- `final-result.json` 包含 `status`、`model`、`actual_model`、`attempts`、`tool_steps`、`model_calls`、`tool_calls`、`duration`。

## 回滚方案

- 停止 `coder-agent-app` 服务。
- 删除多模型配置，只保留旧单模型 `coder-agent.model` 配置。
- 停止向 `POST /api/agent-runs` 传入 `model` 字段。
- 如需清理测试数据，删除 MySQL 中对应 `run_id` 的运行记录，并删除 workspace 下 `.coder/runs/{runId}/` 工件目录。
