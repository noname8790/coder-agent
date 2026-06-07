## Context

`coder-agent` 第三版已经具备本地仓库基础闭环：Tauri 客户端、workspace 管理、会话历史、权限等级、读写文件、本地 Git commit、PR 草稿、SSE 运行状态和审计工件。当前短板不在“能否操作仓库”，而在 Harness 内核：长任务 prompt 会膨胀，文件和会话上下文缺少结构化复用，模型配置仍偏静态，工具治理缺少运行中审批，模型回复不是真正逐 token/逐 delta 流式反馈，评测也没有形成固定闭环。

第四版聚焦“强 Harness”能力。它以单 workspace 记忆隔离为核心，引入 PostgreSQL + pgvector 做向量记忆持久化，继续用 MySQL 保存业务事务、审计、模型配置和指标。OpenAI-compatible Chat Completions 和 OpenAI Responses API 都只作为模型调用协议，所有状态仍由本地 MySQL、pgvector 和 `.coder` 工件管理。第四版同时是一次行为替换：模型配置从第三版静态三模型白名单切换为数据库持久化配置；模型回复从非流式一次性结果切换为流式 delta 驱动。

约束：
- 后端继续使用 Spring Boot 3.x + JDK 21 + Maven 多模块 DDD。
- 命令执行仍只面向 Windows PowerShell。
- 不引入多用户登录、远程 GitHub/GitLab PR、跨 workspace 记忆或通用知识库。
- 框架可以局部适配，但不得接管 Agent Run 主循环。

## Goals / Non-Goals

**Goals:**

- 建立模型配置中心，支持用户保存 chat 模型配置、协议类型、流式能力、tool calling 能力和模型级上下文预算。
- 移除第三版运行时固定三模型白名单和默认模型静态配置主流程，Agent Run 只能引用数据库中启用的模型配置。
- 建立固定全局 embedding 配置，使用 PostgreSQL + pgvector 保存单 workspace 向量记忆。
- 建立上下文治理引擎，按分层上下文和模型预算裁剪 prompt，并生成可审计 context snapshot。
- 建立结构化记忆系统，覆盖文件摘要、任务摘要、会话摘要、运行摘要、freshness 校验和向量召回。
- 支持文件读取/搜索/运行结束时自动生成摘要，并受预算、权限和敏感路径保护。
- 支持 Chat Completions streaming 与 Responses API streaming，并统一转换为内部模型事件。
- 让客户端展示模型文本流式 delta，而不是等运行结束后一次性刷新结果。
- 移除第三版非流式模型调用和运行结束后一次性写入 Agent 消息的主流程。
- 增强工具治理，支持参数校验、重复调用拦截、敏感信息脱敏和高风险工具人工审批。
- 建立后端 eval MVP，输出固定 benchmark 的 pass_rate、attempts、model_calls、tool_calls、failure_category 和报告工件。

**Non-Goals:**

- 不做通用知识库上传、文档库管理或跨项目知识共享。
- 不做跨 workspace 记忆复用。
- 不做全仓库首次注册时的全量 embedding。
- 不支持多个 embedding 模型运行时切换，也不做 embedding 维度自动迁移。
- 不使用 OpenAI Responses API 的 `previous_response_id`、`store` 或服务端 hosted state。
- 不开放 `git push`、`git clean`、`git reset --hard`，不接 GitHub/GitLab API。
- 不让 Spring AI Alibaba、LangChain4j、Embabel 接管 Agent Run 主循环。
- 客户端不做完整评测看板，只展示当前 run 的上下文/记忆指标。

## Decisions

### Decision 1: 单 workspace 记忆隔离

所有记忆、摘要、向量 chunk、召回结果和上下文快照都必须带 `workspaceKey`，prompt 装配时只允许召回当前 workspace 的数据。每个 workspace 都是独立 prompt 域，不允许跨 workspace 复用。

理由：本项目服务用户本地任意项目，跨 workspace 记忆会造成上下文污染和隐私边界问题。先把单 workspace 记忆做好，才能稳定解决 prompt 膨胀和重复读取。

替代方案：跨 workspace 复用通用知识。该方案看似提升召回覆盖，但会引入权限、隐私和相关性污染，不进入 v4。

### Decision 2: MySQL 管业务，pgvector 管向量记忆

MySQL 继续保存运行、消息、模型配置、工具调用、审计、审批、上下文指标和 eval 记录；PostgreSQL + pgvector 保存向量 chunk、embedding 和用于相似度检索的元数据。pgvector 不保存 API Key，embedding API Key 来自全局 `.env`。

建议 `.env` 配置：

```dotenv
PGVECTOR_ENABLED=true
PGVECTOR_URL=jdbc:postgresql://127.0.0.1:5432/coder_agent_memory
PGVECTOR_USERNAME=postgres
PGVECTOR_PASSWORD=
PGVECTOR_SCHEMA=public
PGVECTOR_TABLE_PREFIX=coder_agent
PGVECTOR_VECTOR_DIMENSIONS=1536
PGVECTOR_INDEX_TYPE=hnsw
PGVECTOR_SIMILARITY=cosine

EMBEDDING_PROVIDER=openai-compatible
EMBEDDING_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
EMBEDDING_API_KEY=
EMBEDDING_MODEL=text-embedding-v4
EMBEDDING_ENDPOINT_TYPE=embeddings
EMBEDDING_TIMEOUT_SECONDS=120

MEMORY_ENABLED=true
MEMORY_TOP_K=8
MEMORY_MIN_SCORE=0.35
MEMORY_MAX_CHUNKS_PER_RUN=20
MEMORY_MAX_EMBEDDING_CALLS_PER_RUN=8
MEMORY_MAX_AUTO_SUMMARY_FILES_PER_RUN=6
MEMORY_MAX_FILE_BYTES_FOR_SUMMARY=65536
```

理由：业务事务和审计天然适合 MySQL；向量召回适合 pgvector。两者拆开能避免把业务表强行迁移到 PostgreSQL。

替代方案：全部放 MySQL 或全部迁移 PostgreSQL。前者向量检索弱，后者迁移成本和风险过高。

### Decision 3: 固定全局 embedding 配置

v4 的 embedding 模型使用服务端全局配置，不在客户端提供运行时切换。chat 模型可以动态配置，embedding 模型不能随便切换。

理由：embedding 模型决定向量维度和历史向量兼容性。随意切换会导致 pgvector 表和历史数据失效。后续如果切换 embedding 模型，应通过重建索引流程处理。

### Decision 3.5: Chat 模型配置替换静态三模型配置

第三版 `application.yml/.env` 中的 `qwen3.6-plus`、`glm-5`、`deepseek-v4-flash` 和默认模型 key 不再作为运行时模型白名单。第四版启动后，Agent Run 只能使用 `agent_model_provider` 中启用的模型配置。部署迁移可以提供初始化 SQL 或启动导入脚本，把用户已有 `.env` 配置转成数据库记录；迁移完成后运行时不再读取旧三模型配置作为 fallback。

理由：v4 要开放用户自己的公共调用 URL 和 API Key，如果继续保留固定三模型兼容分支，会让模型来源、预算、流式能力和协议能力出现双轨逻辑，后续治理成本很高。

替代方案：数据库配置优先、静态配置兜底。该方案短期平滑，但会保留两套模型来源，不符合 v4 “最终补齐配置”的方向。

### Decision 4: 上下文治理作为独立引擎

新增 `ContextEngine`，负责从当前任务、会话、记忆、文件摘要、工具结果和 run trace 中选取上下文，并按模型预算装配。模型调用前必须保存 `context-snapshot/*.json`，记录候选、入选、裁剪、预算和估算 token。

上下文层级：
- system instruction
- workspace profile
- current task
- permission policy
- recent messages
- conversation summary
- task/run memories
- file summaries
- raw file snippets
- recent tool results
- current run trace summary

理由：上下文治理是 v4 核心，必须可审计、可回放、可量化，不能散落在 prompt 拼接代码里。

### Decision 5: 自动摘要按真实任务渐进积累

系统不会在 workspace 注册时全量扫描并生成摘要。摘要生成由真实任务触发：文件读取、搜索命中、运行结束、会话累积超过阈值时生成或刷新摘要。

理由：避免首次注册大量烧 token，也避免生成大量无关摘要。真实任务驱动的摘要更贴近用户需求。

替代方案：全仓库预索引。适合后续版本做主动索引任务，不进入 v4 主线。

### Decision 6: 双协议 streaming 统一为内部模型事件

模型网关新增统一流式接口。Chat Completions streaming 和 Responses API streaming 都转成内部事件，并取代第三版非流式完整响应主流程：
- `assistant_message_started`
- `assistant_delta`
- `tool_call_started`
- `tool_call_arguments_delta`
- `tool_call_completed`
- `model_completed`
- `model_failed`

业务层、SSE 和客户端只消费内部事件，不直接感知底层协议。Responses API 只作为调用协议，不使用服务端状态。

理由：v4 必须解决模型内容流式反馈问题，同时为 OpenAI 官方 Responses API 和 DashScope/OpenAI-compatible Chat Completions 保持兼容。

旧的非流式“一次性返回 Agent 结果”不作为 v4 运行时 fallback 保留。如果模型配置不支持 streaming，应在模型配置校验或运行创建阶段拒绝，并提示用户更换支持 streaming 的模型配置。

### Decision 7: 框架只做局部适配

核心 Harness 编排自研，框架不得接管 run lifecycle。框架评估原则：
- Spring AI Alibaba：优先评估 embedding、DashScope/OpenAI-compatible 模型和 vector store 适配。
- LangChain4j：评估 tokenizer、embedding/vector 抽象，必要时局部引入。
- Embabel：作为 Spring/JVM Agent 编排参考，v4 不进入主链路。
- OkHttp 自研网关保留为稳定 fallback。

理由：项目已有 DDD、工具审计、权限等级、工件和 trace。完整 Agent 框架接管主循环会模糊责任边界，降低可回放和可治理能力。

### Decision 8: 高风险工具使用运行中审批

`overwrite_file`、`delete_file`、`git commit` 等高风险动作进入 `WAITING_APPROVAL`，客户端展示工具名、参数、风险说明和相关 diff/文件路径。用户批准后继续，拒绝后工具返回拒绝结果，Agent 可继续规划或结束。

理由：权限等级解决“是否允许”，审批解决“这一次是否确认执行”。这更接近真实 coding agent 的安全体验。

### Decision 9: eval MVP 先做后端报告

新增后端 eval API 和 `.coder/evals/{evalId}/` 报告工件，客户端暂不做完整评测看板。报告包含 pass_rate、attempts、model_calls、tool_calls、failure_category、trace 和模型对比结果。

理由：评测闭环是 Harness 必需能力，但可视化看板会拉大 v4 范围。先保证后端评测数据可信。

### § Frontend Architecture

客户端继续使用 Tauri + React + TypeScript + Vite。v4 前端只做四项增量：
- 模型配置管理页面：新增/编辑/删除/启用 chat 模型，配置 endpoint type、baseUrl、apiKey、model、timeout、预算。
- 流式消息展示：消费 `assistant_delta`，实时追加 Agent 消息；工具事件只作为状态，不作为正文。
- 审批弹窗：展示高风险工具参数、文件路径、diff 摘要、风险说明和批准/拒绝操作。
- 当前 run 指标展示：展示 context tokens、compression ratio、memory hit、stale memory、embedding calls 等指标。

### § UI Design Tokens

沿用第三版客户端的浅色、低干扰、三栏布局，不新增大型视觉风格。新增 UI 应遵循：
- 模型配置表单使用紧凑表单和明确信息分组。
- 审批弹窗使用高风险强调色，但不阻断客户端其它只读查看操作。
- 指标展示使用小型统计块和可折叠详情，避免占据对话主区域。
- 流式消息正文保持对话体验优先，事件状态最多显示一行灰色辅助文字。

## Risks / Trade-offs

- [Risk] pgvector 与 MySQL 双存储增加部署复杂度 → 提供 `PGVECTOR_ENABLED=false` 回退路径，禁用向量召回后仍可运行第三版能力。
- [Risk] embedding 模型维度变更导致历史向量不可用 → v4 固定全局 embedding 配置，不做运行时切换；后续通过重建索引迁移。
- [Risk] 自动摘要产生额外模型成本 → 每个 run 限制摘要文件数、embedding 调用数、输入字节数和耗时，超限只跳过并记录原因。
- [Risk] streaming tool call 解析容易因供应商兼容差异失败 → Chat Completions 和 Responses 各自独立适配，统一内部事件；第三方 Responses 兼容不承诺完全支持。
- [Risk] 审批等待导致 run 卡住 → run 进入明确 `WAITING_APPROVAL` 状态，支持超时、取消和拒绝继续。
- [Risk] 上下文裁剪影响正确性 → snapshot 记录裁剪原因和候选明细，eval 对比压缩前后任务效果。
- [Risk] 引入框架导致边界混乱 → 所有框架能力必须通过项目端口接入，不允许绕过审计、权限和工件落盘。

## Migration Plan

1. 新增 MySQL 表和字段：模型配置、上下文指标、记忆元数据、审批请求、eval 任务/运行/结果。
2. 新增 PostgreSQL/pgvector 初始化 SQL：启用 `vector` 扩展，创建 workspace memory chunk 表和向量索引。
3. 新增 `.env` 配置项：`PGVECTOR_*`、`EMBEDDING_*`、`MEMORY_*`、`CONTEXT_*`。
4. 提供一次性迁移脚本或初始化 SQL，将第三版 `.env` 静态模型配置转换为 `agent_model_provider` 记录；迁移后运行时不再保留静态模型 fallback。
5. 在 `PGVECTOR_ENABLED=false` 或连接失败时，记忆召回降级为 MySQL 摘要/规则召回或完全关闭，不阻断普通 Agent Run。
6. 增量实现 streaming 适配：先支持 Chat Completions streaming，再接 Responses streaming；v4 Agent Run 主流程不保留非流式回退分支。
7. 增量开启审批：先对 `overwrite_file`、`delete_file`、`git commit` 生效。
8. 新增 eval 默认样例任务，输出 `.coder/evals/` 报告。

回滚策略：
- 关闭 `MEMORY_ENABLED`、`PGVECTOR_ENABLED`、`TOOL_APPROVAL_ENABLED` 和 `EVAL_ENABLED`。
- 如需恢复第三版运行链路，应整体代码回滚到第三版实现；v4 客户端和后端不内置第三版静态模型/非流式链路开关。
- 停止写入新增表；保留历史数据以便排查，必要时执行回滚 SQL。
- 如需回退模型静态配置或非流式完整响应，必须通过代码回滚恢复第三版实现；v4 主线不内置这两条兼容分支。

## Open Questions

- 具体 embedding 默认模型和 `PGVECTOR_VECTOR_DIMENSIONS` 需要在实现前按用户 `.env` 最终配置确认。
- 是否在 v4 同步提供 pgvector Docker compose 示例，还是仅提供 SQL 和 `.env` 格式。
- Spring AI Alibaba、LangChain4j、Embabel 的最终局部适配结论需要在实现初期通过 `framework-adapter-decision.md` 固化。
