## 1. 框架适配决策与配置基线

- [x] 1.1 编写 `framework-adapter-decision.md`，对比 Spring AI Alibaba、LangChain4j、Embabel 和自研 OkHttp/JDBC 的适用性。
- [x] 1.2 明确框架不得接管 Agent Run 主循环，所有框架能力必须通过项目端口接入。
- [x] 1.3 扩展 `application.yml` 和配置类，新增 `PGVECTOR_*`、`EMBEDDING_*`、`MEMORY_*`、`CONTEXT_*`、`TOOL_APPROVAL_*`、`EVAL_*` 配置。
- [x] 1.4 更新 README 的 `.env` 配置模板。
- [x] 1.5 添加配置绑定测试。

## 2. 数据库与领域模型

- [x] 2.1 编写 MySQL v4 SQL，新增模型配置、上下文快照、记忆元数据、召回记录、审批请求、eval 表。
- [x] 2.2 编写 PostgreSQL/pgvector 初始化 SQL，启用 `vector` 扩展并创建 memory chunk 表和索引。
- [x] 2.3 编写数据库回滚 SQL。
- [x] 2.4 新增 ModelProvider、ContextBudget、ContextSnapshot、MemoryItem、MemoryChunk、MemoryRecall、ToolApprovalRequest、EvalBenchmark、EvalRun 等领域对象。
- [x] 2.5 定义模型、embedding、vector memory、上下文治理、token 估算、工具治理和 eval 端口。
- [x] 2.6 新增 MyBatis-Plus PO/DAO/Repository 和 pgvector Repository。
- [x] 2.7 使用 Given/When/Then 测试验证新增表映射、workspaceKey 隔离、脱敏字段和回滚影响范围。

## 3. 模型配置中心

- [x] 3.1 使用 TDD 覆盖模型配置创建、查询、修改、删除、启用、默认模型选择和删除保护。
- [x] 3.2 实现模型配置用例和 Repository，支持 API Key 安全存储与查询脱敏。
- [x] 3.3 新增 `GET/POST/PUT/DELETE /api/model-providers` 系列 API。
- [x] 3.4 支持 endpointType=`chat-completions|responses`、streamingEnabled、toolCallingEnabled 和模型级上下文预算。
- [x] 3.5 移除 v3 三模型静态白名单运行时路径，提供初始化 SQL 将旧 `.env` 模型配置转换为 `agent_model_provider` 记录。
- [x] 3.6 更新 Agent Run 创建逻辑，按模型 key 加载数据库模型配置，失败时返回 `MODEL_NOT_CONFIGURED`。

## 4. 流式模型网关

- [x] 4.1 定义统一 ModelStreamEvent、StreamingModelGateway 和流式回调接口。
- [x] 4.2 使用 TDD 覆盖 Chat Completions streaming 文本 delta、tool_calls delta、完成和异常解析。
- [x] 4.3 实现 OpenAI-compatible Chat Completions streaming 网关。
- [x] 4.4 使用 TDD 覆盖 Responses API streaming 文本 delta、function call delta、完成和异常解析。
- [x] 4.5 实现 OpenAI Responses streaming 网关，禁止使用 `previous_response_id`、`store` 或服务端 hosted state。
- [x] 4.6 改造 AgentRunExecutor，支持 assistant delta 累积、partial message 持久化、tool call 参数组装和取消时保存已输出内容。
- [x] 4.7 扩展 SSE 事件类型，新增 assistant message started/delta/completed/cancelled 和 model stream failure 事件。
- [x] 4.8 使用真实 OpenAI-compatible Chat Completions streaming 做冒烟测试；Responses API streaming 以可用配置做受控集成测试；验证 streaming 失败时不会回退非流式请求。

## 5. 上下文治理引擎

- [x] 5.1 定义 ContextCandidate、ContextLayer、ContextBudget、ContextAssemblyResult 和裁剪原因枚举。
- [x] 5.2 使用 TDD 覆盖上下文分层装配。
- [x] 5.3 实现 TokenEstimator，并预留 LangChain4j tokenizer 适配点。
- [x] 5.4 实现模型级预算优先、全局默认兜底的预算解析逻辑。
- [x] 5.5 实现上下文裁剪策略，确保 system、权限策略、当前任务和必要工具状态强制保留。
- [x] 5.6 实现工具输出压缩，完整输出仍保存到 `tool-output/*.txt`。
- [x] 5.7 每次模型调用前写入 `.coder/runs/{runId}/context-snapshot/*.json` 和 MySQL context snapshot 指标。
- [x] 5.8 扩展 Agent Run 详情和 `final-result.json`，输出 raw/final token 估算、compression ratio、memory hit、stale memory 和 selected snippet 指标。

## 6. 结构化记忆与 pgvector

- [x] 6.1 使用 TDD 覆盖单 workspace 记忆隔离。
- [x] 6.2 实现 MemoryService，支持文件摘要、会话摘要、任务摘要、运行摘要的 MySQL 元数据持久化。
- [x] 6.3 实现 PgVectorMemoryPort，支持写入 embedding、按 workspace topK 相似度召回、minScore 过滤和元数据查询。
- [x] 6.4 实现 EmbeddingGateway，使用固定全局 OpenAI-compatible embeddings 配置生成向量。
- [x] 6.5 实现 freshness 校验，记录 path、contentHash、mtime、summaryVersion，文件变化后标记 stale。
- [x] 6.6 实现记忆召回流程，将当前任务和最近用户输入向量化后召回相关 memory chunk。
- [x] 6.7 将召回结果交给 ContextEngine 作为候选上下文，并记录 memory recall 事件和指标。
- [x] 6.8 在 `PGVECTOR_ENABLED=false` 或 pgvector 不可用时降级运行，并记录降级原因。

## 7. 自动摘要生成

- [x] 7.1 使用 TDD 覆盖自动文件摘要触发。
- [x] 7.2 实现摘要生成策略，跳过 `.env`、`.git/`、`.coder/`、`target/`、密钥和疑似敏感文件。
- [x] 7.3 实现每 run 的摘要预算控制。
- [x] 7.4 实现文件摘要模型提示与结构化输出解析。
- [x] 7.5 实现会话摘要、任务摘要和运行摘要生成，运行结束后写入记忆并向量化。
- [x] 7.6 对摘要输入、摘要结果、embedding 输入、trace 和 snapshot 执行敏感信息脱敏。
- [x] 7.7 将摘要生成过程写入 trace、audit_event 和 `.coder/runs/{runId}` 工件。

## 8. 工具治理与人工审批

- [x] 8.1 定义工具参数 schema 校验接口和 ToolGovernanceService。
- [x] 8.2 使用 TDD 覆盖工具参数缺失、类型错误、路径逃逸、权限不足和敏感路径拒绝。
- [x] 8.3 实现重复工具调用识别。
- [x] 8.4 实现工具参数、输出、trace、SSE 和 final-result 的敏感信息脱敏。
- [x] 8.5 使用 TDD 覆盖高风险审批。
- [x] 8.6 实现 `WAITING_APPROVAL` run 状态和审批请求持久化。
- [x] 8.7 新增审批 API：查询待审批、批准、拒绝。
- [x] 8.8 改造 AgentRunExecutor，审批批准后继续执行，拒绝后将结构化拒绝结果返回 Agent。

## 9. Eval Benchmark MVP

- [x] 9.1 定义 benchmark、eval run、case result 和 failure category 领域模型。
- [x] 9.2 使用 TDD 覆盖 benchmark 创建、eval run 启动、结果查询和基线对比。
- [x] 9.3 新增 `/api/evals/benchmarks` 和 `/api/evals/runs` 系列 API。
- [x] 9.4 实现固定 benchmark 执行器，支持按模型配置批量运行任务。
- [x] 9.5 汇总 pass_rate、attempts、model_calls、tool_calls、tool_steps、duration、failure_category、context compression 和 memory hit 指标。
- [x] 9.6 生成 `.coder/evals/{evalId}/summary.json`、`report.md`、`cases/*.json` 和模型对比报告。
- [x] 9.7 准备 6 个实验 benchmark。

## 10. 客户端 v4 适配

- [x] 10.1 根据 `design.md` 更新 `design-ui/`，记录模型配置、流式消息、审批弹窗和指标展示设计。
- [x] 10.2 实现模型配置管理页面。
- [x] 10.3 客户端启动并连接后端后读取已启用模型列表。
- [x] 10.4 实现无模型配置空态。
- [x] 10.5 保存并启用模型配置后刷新模型候选项。
- [x] 10.6 改造 SSE 消费逻辑，支持 assistant_delta 实时追加到当前 Agent 消息。
- [x] 10.7 保持工具事件只作为临时进度状态。
- [x] 10.8 实现高风险工具审批弹窗。
- [x] 10.9 在运行详情中展示 context tokens、compression ratio、memory hit、stale memory、embedding calls 和 snapshot 路径。
- [x] 10.10 增加客户端单元测试。

## 11. 文档、验证与回滚

- [x] 11.1 更新 README。
- [x] 11.2 更新 SQL 文档。
- [x] 11.3 更新 OpenSpec 规格引用，确保 proposal、design、specs、tasks 一致。
- [x] 11.4 运行后端单元测试和集成测试。
- [x] 11.5 运行客户端单元测试和构建。
- [x] 11.6 运行 `openspec validate enhance-agent-harness-v4`。
- [x] 11.7 使用实验 workspace 完成真实 API 冒烟测试：Chat Completions streaming、pgvector 记忆召回、自动摘要、高风险审批和 eval report。
- [x] 11.8 验证回滚边界：memory、pgvector、approval、eval 可通过开关停用；模型静态配置和非流式输出只能通过代码回滚恢复，v4 主线不保留兼容分支。

## 12. 缺陷修正验收

- [x] 12.1 新增运行中 Agent 回复草稿缓存与 `/api/agent-runs/{runId}/draft` 查询接口，刷新或切换会话后可恢复已输出 chunk。
- [x] 12.2 Agent 运行中不再把 partial message 高频写入 `agent_message`；带工具调用的规划轮次只保留为运行草稿，终态时仅保存无工具调用的最终回复或取消前最后一版可见草稿。
- [x] 12.3 Chat Completions streaming 过滤 `<think>...</think>` 推理片段，只向 SSE 和客户端发送用户可见回复 delta。
- [x] 12.4 删除会话时级联清理 run 主表、step、model_call、tool_call、audit_event、artifact、approval、context snapshot、MySQL memory 和 pgvector memory chunk。
- [x] 12.5 前端切换会话、刷新运行状态或重连 SSE 时，通过 draft 接口恢复运行中消息，避免已输出 chunk 被 `思考中...` 覆盖。
- [x] 12.6 增加草稿缓存、think 过滤、级联清理和 draft REST API 的回归测试。
- [x] 12.7 修正 tool-call 多轮模型调用的重复推进问题：运行中可见草稿贯穿整个 run，刷新/切换会话不丢失；重复工具调用在已有成功结果时复用上次结果回灌给模型，不再用拒绝结果诱发反复重试。
- [x] 12.8 修改用户消息并重跑时，按旧 runId 级联清理旧 run、step、model_call、tool_call、audit_event、artifact、approval、context snapshot、MySQL memory 和 pgvector chunk。
- [x] 12.9 高风险工具审批按 runId/tool/规范化参数复用待审批记录，避免同一删除/覆盖请求反复生成审批。
- [x] 12.10 工具结果进入下一轮模型前统一转换为 `TOOL_OBSERVATION`，包含 tool、arguments、status、exitCode、error、summary 和下一步指令；同一 run 内同工具同参数的观测只加入上下文一次。
- [x] 12.11 修正 `read_file`、`search_text`、`list_files` 的空成功结果语义，空文件、无搜索命中、空目录都必须返回明确中文说明，避免模型误判为未拿到结果。
### 12.x 本轮缺陷修正补充

- [x] 12.12 删除 workspace 时级联清理该 workspace 下的会话、消息、run 主表、step、model_call、tool_call、audit_event、artifact、approval、context snapshot、MySQL memory 和 pgvector chunk，避免工作区停用后仍污染上下文。
- [x] 12.13 高风险工具进入审批等待时同步保存 `tool_call` 记录，状态为 `WAITING_APPROVAL`；审批通过记录会作为高优先级上下文回灌，提示模型按同一工具和同一参数继续执行，避免重复请求审批。
- [x] 12.14 同一 run 内同一工具和同一参数最多允许两次；第三次判定为无法推进的重复工具调用，直接失败结束并保存原因，防止 `read_file`、`run_shell` 等工具循环重试导致超时。
- [x] 12.15 强化当前任务上下文优先级：最近消息和记忆只用于消歧，不得覆盖当前任务；同时补充 Maven compile/test-compile/指定测试命令与 `cd` 白名单，减少验证任务被无效拒绝。
## 13. 运行治理缺陷补充

- [x] 13.1 高风险工具审批通过后，执行器直接消费已批准审批记录并执行原始工具调用，执行后标记 `APPROVED_EXECUTED`。
- [x] 13.2 审批等待时写入 `tool_call WAITING_APPROVAL`，审批执行后写入真实工具调用记录。
- [x] 13.3 `run_shell` 命令超时时立即失败 run，写入 `TOOL_TIMEOUT` 审计事件和 final-result。
- [x] 13.4 连续两次工具拒绝时立即失败 run，避免模型在无效命令或参数之间循环。
- [x] 13.5 删除 workspace 时级联清理会话、消息、运行记录、上下文、记忆、pgvector chunk 和审批记录。
- [x] 13.6 使用 GLM-4.5-air 真实 API 验证审批删除、workspace 删除清理和工具阻塞终止路径。
