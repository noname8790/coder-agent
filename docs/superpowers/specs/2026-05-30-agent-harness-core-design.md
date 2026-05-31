# Agent Harness 核心闭环设计

## 背景

本项目从 0 到 1 构建一个面向本地代码仓库长链路任务的 Java 服务端 Agent Harness。首版目标不是完整实现项目的全部指标体系，也不做本地 CLI 客户端，而是先打通可真实执行服务端配置 workspace 的核心闭环。

已确认首版采用方案 B：Agent Harness 核心闭环。该方案在第一版建立可执行、可回放、可审计、可扩展的骨架，但不提前引入结构化记忆、PGVector、完整评测报表和文件编辑能力。

## 已确认需求

- 入口：REST API。
- 执行模式：异步执行。
- 运行形态：Java 服务端 Agent Harness，可以运行在开发者本机，默认建议监听 `127.0.0.1`，不要求额外云服务器。
- 模型后端：OpenAI-compatible Responses API 优先，后续扩展 DashScope 专用 API。
- 模型验收：首版必须跑通真实 OpenAI-compatible API，不以 Mock 模型作为最终验收。
- 工具边界：开发可用版，只读工具 + 受限 Shell，暂不开放文件编辑。
- Shell 环境：Windows PowerShell，不兼容其他系统。
- 工作区输入：`workspaceKey`，服务端配置真实路径。
- 持久化：MySQL，后续向量数据再接 PostgreSQL/PGVector。
- 架构：Maven 多模块 DDD，使用 `ddd-skills` 脚手架规范。
- Maven 坐标：`cn.noname:coder-agent`。
- Java 包名：`cn.noname.coder.agent`。
- API 鉴权：首版不加鉴权，仅按本地开发服务处理。
- 运行并发：首版最大并发运行数为 2。
- 交付文档：生成 README、curl 示例和一条可跑通的本地仓库任务示例。
- 断网行为：服务进程不崩溃；依赖模型的 AgentRun 进入 `FAILED`，并记录 trace 与 `final-result.json`。

## 首版能力

REST API：

- `POST /api/agent-runs`：创建并启动 Agent 运行。
- `GET /api/agent-runs/{runId}`：查询运行状态和最终结果摘要。
- `GET /api/agent-runs/{runId}/trace`：查询可回放执行链路。
- `POST /api/agent-runs/{runId}/cancel`：取消运行。

运行状态：

- `CREATED`
- `RUNNING`
- `SUCCEEDED`
- `FAILED`
- `CANCELLED`
- `REJECTED`

默认预算：

```yaml
max_steps: 25
max_model_calls: 25
max_tool_calls: 50
timeout_seconds: 300
max_concurrent_runs: 2
```

首版工具：

- `list_files`
- `read_file`
- `search_text`
- `run_shell`

Shell 安全：

- 命令前缀白名单。
- 危险 token 拒绝。
- 禁止删除、移动、重定向、管道、链式执行、Git reset/push/commit 等高风险操作。
- 默认允许命令：`git status`、`git diff`、`git log`、`mvn test`、`mvn -q test`、`mvn clean test`、`mvn package`、`mvn -pl`、`java -version`。
- `search_text` 默认使用 Java 内置扫描。

运行工件：

默认目录：

```text
{workspaceRoot}/.coder/runs/{runId}/
```

文件：

- `run-meta.json`
- `trace.jsonl`
- `context-snapshot/*.json`
- `tool-output/*.txt`
- `final-result.json`

## DDD 模块设计

- `coder-agent-types`：通用枚举、异常、响应结构。
- `coder-agent-api`：REST DTO、API 契约、错误码。
- `coder-agent-domain`：AgentRun、AgentStep、ModelCall、ToolCall、AuditEvent、RunArtifact 等领域模型与端口接口。
- `coder-agent-case`：创建运行、后台执行、取消运行、查询状态和查询 trace 的用例编排。
- `coder-agent-infrastructure`：MySQL DAO/PO、OpenAI-compatible Gateway、工具执行适配器、工件落盘适配器。
- `coder-agent-trigger`：HTTP Controller。
- `coder-agent-app`：Spring Boot 启动类、配置、MyBatis Mapper、Dockerfile。

架构约束：

- Domain 层不依赖 MyBatis、HTTP Client、Redis、Spring 具体实现。
- Infrastructure 通过 `adapter/repository` 和 `adapter/port` 实现 Domain 接口。
- DAO 放在 `infrastructure/dao`。
- PO 放在 `infrastructure/dao/po`。
- 禁止创建 `persistent` 包。
- Controller 只做路由和 DTO 转换，不放业务逻辑。

## 数据库范围

涉及表：

- `agent_run`
- `agent_step`
- `model_call`
- `tool_call`
- `audit_event`
- `run_artifact`

MySQL 保存结构化记录和工件索引，本地文件保存长 trace、上下文快照和工具输出。

## 上下文管理

首版采用基础上下文管理 + 简单预算保护：

- 系统提示词。
- 用户任务。
- workspace 基本信息。
- 最近工具结果摘要。
- 当前预算与已用次数。
- 最近模型结论。
- 最大上下文字符数限制。
- 长工具输出落盘，仅摘要进入上下文。
- 每次模型调用前保存摘要版 `context-snapshot/*.json`。

首版不做：

- 结构化记忆。
- 文件摘要缓存。
- freshness 校验。
- 向量召回。
- 历史对话复杂压缩。

## 非目标

- 不开放文件编辑工具。
- 不自动提交代码。
- 不生成 PR。
- 不实现结构化记忆。
- 不接 PostgreSQL/PGVector。
- 不接 Elasticsearch。
- 不实现完整 benchmark 报表。
- 不实现 DashScope 专用 API。
- 不实现多 Agent 协作。
- 不实现 API 鉴权。
- 不兼容 Linux/macOS Shell。

## 回滚方案

- 停止 `coder-agent-app` 服务。
- 删除或停用新增 REST API 路由。
- 回滚新增 MySQL 表，或保留空表但停止写入。
- 删除 workspace 内 `.coder/runs/{runId}/` 运行工件。
- 移除 OpenAI-compatible 模型配置和 workspace 配置。
- 若已容器化部署，回退到变更前镜像。

## OpenSpec 文档

本设计对应 OpenSpec change：

- `openspec/changes/build-agent-harness-core/proposal.md`
- `openspec/changes/build-agent-harness-core/design.md`
- `openspec/changes/build-agent-harness-core/tasks.md`
- `openspec/changes/build-agent-harness-core/specs/agent-run-lifecycle/spec.md`
- `openspec/changes/build-agent-harness-core/specs/agent-tool-calling/spec.md`
- `openspec/changes/build-agent-harness-core/specs/workspace-governance/spec.md`
- `openspec/changes/build-agent-harness-core/specs/run-audit-artifacts/spec.md`
