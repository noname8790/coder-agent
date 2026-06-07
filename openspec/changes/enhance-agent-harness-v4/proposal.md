## Why

第三版已经具备本地仓库读写、权限等级、会话历史、SSE 事件和客户端闭环，但仍主要是“可操作仓库的 Agent 应用”，还没有形成强 Harness 所需的上下文治理、结构化记忆、向量召回、模型配置治理、工具审批和评测闭环。

第四版需要把核心能力从“能执行任务”推进到“能长期、可审计、可评估、可治理地执行代码仓库任务”，重点解决长链路任务中的 prompt 膨胀、重复读取、流式反馈不足和模型/工具治理边界不清问题。

## What Changes

- 改造模型配置能力，使用数据库持久化模型配置取代第三版 `application.yml/.env` 中三个固定模型和默认模型的静态白名单主流程。
- 新增固定全局 embedding 配置，使用 PostgreSQL + pgvector 持久化单 workspace 隔离的向量记忆。
- 新增上下文治理引擎，按 system、任务、权限、会话摘要、任务记忆、文件摘要、原始片段、工具结果等分层装配 prompt，并按模型预算裁剪。
- 新增结构化记忆系统，保存文件摘要、会话摘要、任务摘要、运行摘要和向量 chunk，并通过 freshness 校验避免过期记忆污染上下文。
- 新增自动摘要生成机制，在文件读取、搜索命中或运行结束时按预算生成摘要、embedding 并入库。
- 改造模型网关，使用 Chat Completions streaming 和 Responses API streaming 取代第三版非流式模型调用与运行结束后一次性写入结果的主流程。
- 改造客户端对话体验，显示模型文本 delta，剔除“等待运行结束后一次性展示 Agent 结果”的旧交互。
- 新增工具治理与人工审批机制，覆盖参数校验、重复调用拦截、敏感信息脱敏、高风险工具暂停审批和结构化拒绝原因。
- 新增后端评测闭环 MVP，支持固定 benchmark、模型对比、运行指标汇总和 `.coder/evals/` 报告工件。
- 保留本地 Harness 状态管理，不依赖 OpenAI Responses API 的 hosted state、`previous_response_id` 或远端 `store`。

## Capabilities

### New Capabilities

- `context-governance`: 系统按单 workspace、模型预算和上下文分层策略装配、裁剪、审计每次模型调用的 prompt。
- `workspace-memory`: 系统使用 MySQL + pgvector 保存单 workspace 结构化记忆、向量 chunk、freshness 状态和记忆召回结果。
- `memory-summarization`: 系统按预算自动生成文件、会话、任务和运行摘要，并对摘要生成过程做审计。
- `tool-governance-approval`: 系统增强工具参数校验、重复调用拦截、敏感信息脱敏和高风险工具人工审批。
- `agent-evaluation`: 系统提供后端 benchmark/eval MVP，支持固定任务、模型对比、指标汇总和报告工件。
- `agent-client-v4`: 客户端支持模型配置、模型文本流式展示、高风险审批弹窗和当前 run 的上下文/记忆指标展示。

### Modified Capabilities

- `model-provider-management`: 取代第三版固定 `qwen3.6-plus`、`glm-5`、`deepseek-v4-flash` 和默认模型 key 的静态配置主流程，模型必须来自数据库中的用户配置；旧静态三模型白名单不再作为运行时兼容路径保留。
- `streaming-model-gateway`: 取代第三版非流式模型调用和运行结束后一次性写入 Agent 消息的主流程；v4 的对话结果必须通过流式 delta 驱动，旧非流式“一键返回结果”模式不再作为运行时兼容路径保留。

## Impact

- 影响模块：
  - `coder-agent-api`：新增模型配置、上下文指标、记忆、审批、评测相关 DTO。
  - `coder-agent-domain`：新增模型配置、上下文预算、记忆、向量 chunk、审批、评测领域对象和端口。
  - `coder-agent-case`：改造 Agent 执行循环，接入上下文引擎、记忆召回、流式输出、审批等待和评测用例。
  - `coder-agent-infrastructure`：新增 PostgreSQL/pgvector、embedding、模型 streaming 网关、敏感脱敏、token 估算和框架适配实现。
  - `coder-agent-trigger`：新增模型配置、审批、记忆指标、评测 API；扩展 SSE 事件。
  - `coder-agent-app`：新增配置项、数据源配置、Bean 装配和迁移说明。
  - `coder-agent-client`：新增模型配置页面、流式消息、审批弹窗和上下文/记忆指标展示。
- 影响数据表：
  - MySQL 新增或扩展：模型配置、上下文快照索引、记忆元数据、审批请求、评测任务、评测运行、评测结果等表。
  - PostgreSQL/pgvector 新增：workspace 记忆 chunk 表和向量索引。
- 影响配置：
  - 新增 `PGVECTOR_*`、`EMBEDDING_*`、`MEMORY_*`、`CONTEXT_*` 配置。
  - chat 模型支持数据库持久化配置，embedding 模型仍使用全局 `.env` 固定配置。
- 影响依赖：
  - 新增 PostgreSQL JDBC / pgvector 相关依赖。
  - 评估 Spring AI Alibaba、LangChain4j、Embabel 的局部适配价值；框架不得接管 Agent Run 主循环。
- 回滚方案：
  - 关闭 `MEMORY_ENABLED` 和 `PGVECTOR_ENABLED`，回退到第三版无向量记忆的上下文装配。
  - 使用数据库回滚脚本恢复第三版静态模型配置代码和 `application.yml/.env` 固定模型配置；v4 代码内不保留静态模型兼容分支。
  - 使用代码回滚恢复第三版非流式模型调用；v4 代码内不保留非流式一键结果输出分支。
  - 关闭高风险审批，保留第三版权限等级和安全拒绝策略。
  - 停用 eval API 和 `.coder/evals/` 报告生成。
  - 保留新增表但停止写入；必要时执行回滚 SQL 删除新增表和 pgvector 索引。
