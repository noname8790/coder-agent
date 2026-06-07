## 1. 框架适配决策与配置基线

- [ ] 1.1 编写 `framework-adapter-decision.md`，对比 Spring AI Alibaba、LangChain4j、Embabel 和自研 OkHttp 在 embedding、streaming、tool calling、vector store、agent orchestration 上的适用性。
- [ ] 1.2 明确框架不得接管 Agent Run 主循环，所有框架能力必须通过项目端口接入。
- [ ] 1.3 扩展 `application.yml` 和配置类，新增 `PGVECTOR_*`、`EMBEDDING_*`、`MEMORY_*`、`CONTEXT_*`、`TOOL_APPROVAL_*`、`EVAL_*` 配置；streaming 作为 v4 模型主流程能力，不提供全局关闭开关。
- [ ] 1.4 更新 README 的 `.env` 配置模板，说明 pgvector、embedding、上下文预算和回滚开关。
- [ ] 1.5 添加配置加载测试，覆盖 pgvector 开关、embedding 缺失、全局预算默认值和模型级预算覆盖。

## 2. 数据库与领域模型

- [ ] 2.1 编写 MySQL 迁移 SQL，新增模型配置、上下文快照索引、记忆元数据、召回记录、审批请求、eval benchmark/run/result 表。
- [ ] 2.2 编写 PostgreSQL/pgvector 初始化 SQL，启用 `vector` 扩展，创建 memory chunk 表和 HNSW/IVFFLAT 索引。
- [ ] 2.3 编写数据库回滚 SQL，覆盖 MySQL 新增表和 pgvector 表/索引。
- [ ] 2.4 在 Domain 层新增 ModelProvider、ContextBudget、ContextSnapshot、MemoryItem、MemoryChunk、MemoryRecall、ToolApprovalRequest、EvalBenchmark、EvalRun 等领域对象。
- [ ] 2.5 在 Domain 层定义模型、embedding、vector memory、上下文治理、token 估算、工具治理和 eval 端口。
- [ ] 2.6 在 Infrastructure 层新增 MyBatis-Plus PO/DAO/Repository 和 pgvector Repository。
- [ ] 2.7 使用 Given/When/Then 测试验证新增表映射、workspaceKey 隔离字段、脱敏字段和回滚影响范围。

## 3. 模型配置中心

- [ ] 3.1 使用 TDD 编写模型配置创建、查询、修改、删除、启用、默认模型选择和删除保护测试。
- [ ] 3.2 实现模型配置用例和 Repository，支持 apiKey 安全存储与查询脱敏。
- [ ] 3.3 新增 `GET/POST/PUT/DELETE /api/model-providers` 系列 API。
- [ ] 3.4 支持 endpointType=`chat-completions|responses`、streamingEnabled、toolCallingEnabled 和模型级上下文预算。
- [ ] 3.5 移除第三版三模型静态白名单运行时路径，提供初始化 SQL 或迁移脚本将旧 `.env` 模型配置转换为 `agent_model_provider` 记录。
- [ ] 3.6 更新 Agent Run 创建逻辑，按模型 key 加载数据库模型配置，失败时返回 `MODEL_NOT_CONFIGURED`。

## 4. 流式模型网关

- [ ] 4.1 定义统一 ModelStreamEvent、StreamingModelGateway 和流式回调接口。
- [ ] 4.2 使用 TDD 编写 Chat Completions streaming 文本 delta、tool_calls delta、完成和异常解析测试。
- [ ] 4.3 实现 OpenAI-compatible Chat Completions streaming 网关。
- [ ] 4.4 使用 TDD 编写 Responses API streaming 文本 delta、function call delta、完成和异常解析测试。
- [ ] 4.5 实现 OpenAI Responses streaming 网关，禁止使用 `previous_response_id`、`store` 或服务端 hosted state。
- [ ] 4.6 改造 AgentRunExecutor，支持 assistant delta 累积、partial message 持久化、tool call 完整参数组装和取消时保存已输出内容。
- [ ] 4.7 扩展 SSE 事件类型，新增 assistant message started/delta/completed/cancelled 和 model stream failure 事件。
- [ ] 4.8 使用真实 OpenAI-compatible Chat Completions streaming 做冒烟测试；Responses API streaming 以可用配置做真实或受控集成测试；验证 streaming 失败时不会回退到非流式请求。

## 5. 上下文治理引擎

- [ ] 5.1 定义 ContextCandidate、ContextLayer、ContextBudget、ContextAssemblyResult 和裁剪原因枚举。
- [ ] 5.2 使用 TDD 编写上下文分层装配测试，覆盖 system、任务、权限、消息、摘要、记忆、文件片段、工具结果和 trace 摘要。
- [ ] 5.3 实现 TokenEstimator，先提供简单估算 fallback，并预留 LangChain4j tokenizer 适配点。
- [ ] 5.4 实现模型级预算优先、全局默认兜底的预算解析逻辑。
- [ ] 5.5 实现上下文裁剪策略，确保 system、权限策略、当前任务和必要工具状态强制保留。
- [ ] 5.6 实现工具输出压缩，长输出进入 prompt 前截断或摘要化，完整输出仍保存到 `tool-output/*.txt`。
- [ ] 5.7 每次模型调用前写入 `.coder/runs/{runId}/context-snapshot/*.json` 和 MySQL context snapshot 指标。
- [ ] 5.8 扩展 Agent Run 详情和 `final-result.json`，输出 raw/final token 估算、compression ratio、memory hit、stale memory 和 selected snippet 指标。

## 6. 结构化记忆与 pgvector

- [ ] 6.1 使用 TDD 编写单 workspace 记忆隔离测试，验证不同 workspace 不能互相召回。
- [ ] 6.2 实现 MemoryService，支持文件摘要、会话摘要、任务摘要、运行摘要的 MySQL 元数据持久化。
- [ ] 6.3 实现 PgVectorMemoryPort，支持写入 embedding、按 workspace topK 相似度召回、minScore 过滤和元数据查询。
- [ ] 6.4 实现 EmbeddingGateway，使用固定全局 OpenAI-compatible embeddings 配置生成向量。
- [ ] 6.5 实现 freshness 校验，记录 path、contentHash、mtime、summaryVersion，文件变化后标记 stale。
- [ ] 6.6 实现记忆召回流程，将当前任务和最近用户输入向量化后召回相关 memory chunk。
- [ ] 6.7 将召回结果交给 ContextEngine 作为候选上下文，并记录 memory recall 事件和指标。
- [ ] 6.8 在 `PGVECTOR_ENABLED=false` 或 pgvector 不可用时降级运行，并记录降级原因。

## 7. 自动摘要生成

- [ ] 7.1 使用 TDD 编写自动文件摘要触发测试，覆盖 read_file、search_text、stale 文件和预算耗尽场景。
- [ ] 7.2 实现摘要生成策略，跳过 `.env`、`.git/`、`.coder/`、`target/`、密钥和疑似敏感文件。
- [ ] 7.3 实现每 run 的摘要预算控制：文件数、embedding 调用数、摘要模型调用数、输入字节数和耗时。
- [ ] 7.4 实现文件摘要模型提示与结构化输出解析，保存 language、purpose、symbols、dependencies、behavior、risks。
- [ ] 7.5 实现会话摘要、任务摘要和运行摘要生成，运行结束后写入记忆并向量化。
- [ ] 7.6 对摘要输入、摘要结果、embedding 输入、trace 和 snapshot 执行敏感信息脱敏。
- [ ] 7.7 将摘要生成过程写入 trace、audit_event 和 `.coder/runs/{runId}` 工件。

## 8. 工具治理与人工审批

- [ ] 8.1 定义工具参数 schema 校验接口和 ToolGovernanceService。
- [ ] 8.2 使用 TDD 编写工具参数缺失、类型错误、路径逃逸、权限不足和敏感路径拒绝测试。
- [ ] 8.3 实现重复工具调用识别，覆盖重复读取、重复搜索、重复 shell 和重复写入拦截。
- [ ] 8.4 实现工具参数、输出、trace、SSE 和 final-result 的敏感信息脱敏。
- [ ] 8.5 使用 TDD 编写高风险审批测试，覆盖 overwrite_file、delete_file、git commit 的等待、批准、拒绝、取消和超时。
- [ ] 8.6 实现 `WAITING_APPROVAL` run 状态和审批请求持久化。
- [ ] 8.7 新增审批 API：查询待审批、批准、拒绝。
- [ ] 8.8 改造 AgentRunExecutor，使审批批准后继续执行，拒绝后将结构化拒绝结果返回 Agent。

## 9. Eval Benchmark MVP

- [ ] 9.1 定义 benchmark、eval run、case result 和 failure category 领域模型。
- [ ] 9.2 使用 TDD 编写 benchmark 创建、eval run 启动、结果查询和基线对比测试。
- [ ] 9.3 新增 `/api/evals/benchmarks` 和 `/api/evals/runs` 系列 API。
- [ ] 9.4 实现固定 benchmark 执行器，支持按模型配置批量运行任务。
- [ ] 9.5 汇总 pass_rate、attempts、model_calls、tool_calls、tool_steps、duration、failure_category、context compression 和 memory hit 指标。
- [ ] 9.6 生成 `.coder/evals/{evalId}/summary.json`、`report.md`、`cases/*.json` 和模型对比报告。
- [ ] 9.7 准备 6-10 个实验 benchmark，用于验证上下文治理、记忆召回、工具审批和模型对比。

## 10. 客户端 v4 适配

- [ ] 10.1 根据 `design.md` 更新 `openspec/changes/enhance-agent-harness-v4/design-ui/`，记录模型配置、流式消息、审批弹窗和指标展示设计。
- [ ] 10.2 实现模型配置管理页面，支持新增、编辑、删除、启用、测试连接和预算配置。
- [ ] 10.3 客户端启动并连接后端后读取已启用模型列表，模型选择器只使用后端返回的数据库模型配置。
- [ ] 10.4 实现无模型配置空态：模型选择器展示“请配置模型”，点击跳转模型配置页，发送任务按钮禁用。
- [ ] 10.5 保存并启用模型配置后刷新模型候选项，并在对话输入区展示新模型。
- [ ] 10.6 改造 SSE 消费逻辑，支持 assistant_delta 实时追加到当前 Agent 消息。
- [ ] 10.7 保持工具事件只作为临时进度状态，不作为对话正文长期展示。
- [ ] 10.8 实现高风险工具审批弹窗，展示工具名、参数、文件路径、diff 摘要、风险说明和批准/拒绝操作。
- [ ] 10.9 在运行详情中展示 context tokens、compression ratio、memory hit、stale memory、embedding calls 和 snapshot 路径。
- [ ] 10.10 增加客户端单元测试，覆盖模型列表加载、无模型空态跳转、发送禁用、流式消息累积、取消保留 partial message、审批弹窗状态和模型配置表单校验。

## 11. 文档、验证与回滚

- [ ] 11.1 更新 README，说明 v4 Harness 能力、pgvector 部署、embedding 配置、模型配置中心、流式输出、审批和 eval。
- [ ] 11.2 更新 SQL 文档，标注 MySQL/pgvector 新增表、字段、索引和回滚 SQL。
- [ ] 11.3 更新 OpenSpec 规格引用，确保 proposal、design、specs、tasks 一致。
- [ ] 11.4 运行后端单元测试和集成测试，至少覆盖模型配置、上下文治理、记忆、pgvector、审批和 eval。
- [ ] 11.5 运行客户端单元测试和构建。
- [ ] 11.6 运行 `openspec validate enhance-agent-harness-v4`。
- [ ] 11.7 使用实验 workspace 完成真实 API 冒烟测试：Chat Completions streaming、pgvector 记忆召回、自动摘要、高风险审批和 eval report。
- [ ] 11.8 验证回滚边界：memory、pgvector、approval、eval 可通过开关停用；模型静态配置和非流式输出只能通过代码回滚恢复，v4 主线不保留兼容分支。
