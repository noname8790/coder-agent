### 本轮运行治理修正

删除 workspace 仍暂时保留 `INACTIVE` 状态字段以兼容现有接口，但业务语义改为清理工作区关联数据：删除该 workspace 下的会话、消息、run 主表、运行步骤、模型调用、工具调用、审计事件、工件索引、审批请求、上下文快照、MySQL memory 与 pgvector chunk。下一版可在移除 workspace 状态字段时进一步收敛为真正的删除语义。

高风险工具审批以 `runId + toolName + 规范化 argumentsJson` 作为幂等键。模型请求高风险工具时，系统必须先写入 `tool_call`，状态为 `WAITING_APPROVAL`，再切换 run 到 `WAITING_APPROVAL`。审批通过后，已批准记录作为高优先级上下文回灌给模型，要求模型使用同一工具和同一参数继续执行，避免重复创建审批请求。

重复工具调用治理分两层：治理层可以复用已有成功结果并回灌给模型；执行器层保留硬停止保护，同一 run 内同一工具和同一参数第三次出现时直接失败结束，并把“重复工具调用无法推进”写入 run failure reason、agent 消息和 final-result。这样避免模型在空文件、超时命令、无命中搜索等场景中一直重复同一调用。

当前任务上下文必须始终高于最近消息和记忆。最近消息、结构化记忆、工具观测只能用于指代消解和连续工作；若历史内容与当前用户任务冲突，模型必须忽略历史内容并执行当前任务。

## Context

`coder-agent` 第三版已经具备本地仓库基础闭环：Tauri 客户端、workspace 管理、会话历史、权限等级、读写文件、本地 Git commit、PR 草稿、SSE 运行状态和审计工件。第四版聚焦强 Harness 内核：长上下文治理、单 workspace 结构化记忆、pgvector 向量召回、动态模型配置、真实流式输出、工具治理/审批和评测闭环。

v4 是一次主流程替换：chat 模型从静态三模型配置切换为数据库模型配置；模型输出从非流式一次性结果切换为 streaming delta。OpenAI-compatible Chat Completions 和 Responses API 都只作为模型调用协议，不依赖远端 hosted state。

## Goals

- 建立模型配置中心，支持用户保存 chat 模型配置、协议类型、流式能力、tool calling 能力和模型级上下文预算。
- 移除 v3 运行时固定三模型白名单和默认模型静态配置主流程。
- 建立固定全局 embedding 配置，使用 PostgreSQL + pgvector 保存单 workspace 向量记忆。
- 建立上下文治理引擎，按分层上下文和模型预算裁剪 prompt，并生成 context snapshot。
- 建立结构化记忆系统，覆盖文件摘要、任务摘要、会话摘要、运行摘要、freshness 校验和向量召回。
- 支持文件读取、搜索、运行结束时自动生成摘要，并受预算、权限和敏感路径保护。
- 支持 Chat Completions streaming 与 Responses API streaming，并统一转换为内部模型事件。
- 让客户端展示模型文本流式 delta，而不是等待运行结束后一次性刷新结果。
- 增强工具治理，支持参数校验、重复调用拦截、敏感信息脱敏和高风险工具人工审批。
- 建立后端 eval MVP，输出固定 benchmark 的 pass_rate、attempts、model_calls、tool_calls、failure_category 和报告工件。

## Non-Goals

- 不做通用知识库上传、文档库管理或跨项目知识共享。
- 不做跨 workspace 记忆复用。
- 不做 workspace 注册时全量 embedding。
- 不支持多个 embedding 模型运行时切换，也不做 embedding 维度自动迁移。
- 不使用 OpenAI Responses API 的 `previous_response_id`、`store` 或服务端 hosted state。
- 不开放 `git push`、`git clean`、`git reset --hard`，不接 GitHub/GitLab API。
- 不让 Spring AI Alibaba、LangChain4j、Embabel 接管 Agent Run 主循环。
- 客户端不做完整评测看板，只展示当前 run 的上下文/记忆指标。

## Decisions

### Decision 1: 单 workspace 记忆隔离

所有记忆、摘要、向量 chunk、召回结果和上下文快照都必须携带 `workspaceKey`。prompt 装配时只允许召回当前 workspace 的数据。每个 workspace 都是独立 prompt 基础，不允许跨 workspace 复用。

### Decision 2: MySQL 管业务，pgvector 管向量记忆

MySQL 保存运行、消息、模型配置、工具调用、审批、上下文指标和 eval 记录。PostgreSQL + pgvector 保存向量 chunk、embedding 和相似度检索元数据。pgvector 不保存 API Key。

### Decision 3: 固定全局 embedding 配置

embedding 模型使用服务端全局配置，不在客户端提供运行时切换。原因是 embedding 模型决定向量维度和历史向量兼容性，随意切换会导致 pgvector 表和历史数据失效。

### Decision 4: Chat 模型配置替换静态三模型配置

v3 的 `qwen3.6-plus`、`glm-5`、`deepseek-v4-flash` 和默认模型 key 不再作为运行时模型白名单。Agent Run 只能使用 `agent_model_provider` 中启用的模型配置。可提供初始化 SQL 帮助迁移旧配置，但运行时不再读取旧三模型作为 fallback。

### Decision 5: 上下文治理作为独立引擎

新增 `ContextEngine`，负责从当前任务、会话、记忆、文件摘要、工具结果和 run trace 中选择上下文，并按模型预算装配。模型调用前必须保存 `context-snapshot/*.json`，记录候选、入选、裁剪、预算和 token 估算。

### Decision 6: 自动摘要按真实任务渐进积累

系统不在 workspace 注册时全量扫描并生成摘要。摘要由真实任务触发：文件读取、搜索命中、运行结束、会话累计超过阈值时生成或刷新摘要。

### Decision 7: 流式输出替换非流式输出

v4 的 Agent 正文必须由 streaming delta 驱动。Chat Completions streaming 和 Responses API streaming 都统一转为 `ModelStreamEvent`，并写入 SSE、消息和 trace。streaming 失败时不回退到非流式请求。

### Decision 8: 高风险工具进入人工审批

`overwrite_file`、`delete_file`、`git commit` 等高风险工具在启用审批时将 run 切到 `WAITING_APPROVAL`，持久化审批请求。批准后继续执行，拒绝后把结构化拒绝结果回灌给 Agent。

### Decision 9: 框架只做局部适配

Spring AI Alibaba、LangChain4j、Embabel 可以作为 Infrastructure 层局部适配候选，但不得接管 Agent Run 主循环。v4 优先自研 OkHttp/JDBC 适配 streaming、embedding 和 pgvector。

## Frontend Architecture

客户端继续使用 Tauri + React。v4 新增模型配置页面、模型选择空态、流式消息渲染、审批弹窗和运行指标侧栏。模型选择器启动后只读取后端启用模型；无模型时显示“请配置模型”，点击跳转模型配置界面且发送按钮禁用。

## UI Design Tokens

视觉延续 v3 浅色三栏布局。模型配置表单参考用户提供截图：分组标题加粗，说明文字在输入框上方，左侧细色条提示配置块。按钮、输入框、选择器保持 8px 以内圆角，避免卡片套卡片。

## Rollback

- `MEMORY_ENABLED=false` 停用结构化记忆流程。
- `PGVECTOR_ENABLED=false` 停用 pgvector 向量召回。
- `TOOL_APPROVAL_ENABLED=false` 停用人工审批。
- `EVAL_ENABLED=false` 停用 eval API 与报告。
- 静态三模型白名单和非流式输出只通过代码回滚恢复，v4 主线不保留兼容分支。

## V4 缺陷修正补充

运行中的可见回复 delta 先写入服务端内存草稿缓存，并通过 SSE 推送给客户端。刷新、切换会话或 SSE 重连时，客户端通过 `/api/agent-runs/{runId}/draft` 恢复已输出草稿；运行成功、失败或取消后，系统才将最终回复或取消前草稿一次性保存为正式 Agent 消息并清理草稿。模型返回的 `<think>...</think>` 或 reasoning-only 内容不得作为用户可见正文输出。

删除会话时必须按 runId 清理完整运行链路，包括 run 主表、step、model call、tool call、audit event、artifact 索引、approval、context snapshot、MySQL 记忆和 pgvector chunk，避免消息删除后留下孤立运行数据。

### 多轮工具调用草稿边界

模型在请求工具前可能会输出计划性文本。该文本用于运行中可见反馈，必须贯穿整个 run 的运行中草稿缓存，刷新、切换会话或 SSE 重连时都不能丢失。重复推进不通过清理草稿解决，而由工具治理层控制：同一 run 内相同工具和相同参数若已有成功结果，系统复用上次工具结果并回灌给模型；只有尚无成功结果的重复调用才返回重复调用拦截。这样既保留运行中可见性，又避免“重复调用被拒绝”导致模型反复请求同一工具。
当某一轮模型同时输出可见文本和工具调用时，该可见文本仍然只视为运行草稿，而不是最终回答。Harness 必须等待后续无工具调用的最终回答，或者在取消/失败终态时回收当时最后一版可见草稿作为消息内容。

工具结果进入下一轮模型前必须统一转成 `TOOL_OBSERVATION`，至少包含 `tool`、`arguments`、`status`、`exitCode`、`error`、`summary` 和明确的下一步指令。`read_file`、`search_text`、`list_files` 等工具即使执行成功但没有可见内容，也必须返回“文件为空 / 未找到匹配内容 / 目录为空”等明确语义。同一 run 内同工具同参数的观测只加入上下文一次，后续重复调用只记录工具调用和复用结果，不再污染 prompt。

### 重跑与审批一致性

用户修改历史消息并重跑时，旧消息关联的 runId 必须按删除语义清理完整链路，避免旧上下文、记忆和审批污染新 run。高风险工具审批使用 runId、toolName 和规范化后的 argumentsJson 作为幂等键；如果同一请求已经处于 PENDING，则复用原审批记录，不再创建重复审批。
## 2026-06-13 晚间缺陷修正

- 审批恢复：高风险工具审批通过后，Harness 直接执行原审批记录中的 `toolName + argumentsJson`，不再要求模型再次生成同一 tool call。执行后写入普通 `tool_call`、trace、审计、记忆和上下文候选，并将审批状态从 `APPROVED` 标记为 `APPROVED_EXECUTED`。
- 审批幂等：`findApproved` 和审批上下文同时识别 `APPROVED` 与 `APPROVED_EXECUTED`，避免同一操作重复要求审批。
- 工具阻塞终止：`run_shell` 返回 `exitCode=124`、`TIMEOUT` 或“命令超时”摘要时，run 立即失败并写入 `TOOL_TIMEOUT` 审计事件。
- 拒绝循环终止：连续两次工具调用被拒绝时，run 立即失败，避免模型在白名单外命令、无效参数或受保护路径之间反复尝试。
- Shell 白名单补充：`run_shell` 白名单显式允许 `cd` 作为工作目录切换前缀，减少多模块仓库和子目录验证任务的误拦截。
- Workspace 删除：删除 workspace 时同步清理 conversation、message、agent_run、step、model_call、tool_call、artifact、audit、context snapshot、MySQL memory、pgvector chunk 和审批记录。本版本仍保留 workspace `status/deleted_at` 以兼容现有表结构。
