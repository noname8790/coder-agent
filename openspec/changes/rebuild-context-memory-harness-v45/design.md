## Context

`coder-agent` v4.2 已经具备本地仓库读写、权限等级、审批、Git/PR 工具、流式输出、pgvector 记忆和 checkpoint 回滚等基础能力。但当前上下文与记忆实现仍不能支撑高质量长链路 Agent Harness：上下文候选缺少生命周期分层，压缩主要依赖简单预算截断，记忆召回主要依赖向量相似，freshness 未形成召回前裁决，工具证据、任务状态和工作记忆也没有稳定进入 prompt。

v4.5 的目标不是增加一个新功能点，而是重建 Harness 的认知内核。它需要同时解决三类问题：第一，prompt 膨胀和旧信息污染；第二，记忆缺乏来源、可信度和 freshness，导致重复读文件或基于过期事实推理；第三，当前 domain 包几乎都收敛在 agent 领域下，业务边界不清晰，后续继续扩展 context、memory、tool、evaluation 会越来越难维护。

本次变更允许破坏性迁移：旧历史消息以及旧 run/context/memory/pgvector 等不能可靠兼容新结构的数据直接清理。这样做牺牲旧历史回放，但避免把缺少来源、可信度、freshness 和作用域的旧数据迁入新系统后污染模型上下文。

## Goals / Non-Goals

**Goals:**
- 建立适配 128K 模型的分层上下文治理：prefix、working memory、relevant memory、recent messages、file summaries、raw snippets、tool results、run trace 分层预算。
- 建立预算水位机制：75% 规则去重，85% 触发结构化模型压缩，95% 强制压缩并保留锚点，100% 拒绝继续拼接并使用最近有效 checkpoint 恢复。
- 建立结构化记忆：项目级记忆、文件级记忆同 workspace 跨会话共享；任务级记忆、运行观察、工作记忆限定在当前 conversation/run 作用域。
- 建立 freshness 与可信度裁决：召回前校验 hash、workspace fingerprint、checkpoint 作用域和 evidence；低可信记忆不能直接驱动写文件或高风险操作。
- 建立压缩与记忆评测闭环：量化压缩率、锚点保留率、召回 precision/recall@k、stale block rate、重复读率、pass rate 和 token 成本。
- 整理 DDD 业务边界：在现有 Maven 模块内拆分 `agent`、`context`、`memory`、`tool`、`workspace`、`model`、`evaluation` 领域包。
- 将工具实现从大量独立类收敛为 descriptor + handler/strategy 的组合模型，降低 Git/File/Shell 工具扩展成本。
- 为 Spring AI Alibaba、AgentScope、LangChain4j、Embabel 保留局部适配点，但不让任何框架接管 Agent 主循环。

**Non-Goals:**
- 不做跨 workspace 知识库，不做用户全局记忆。
- 不做 workspace 注册时全量索引整个仓库。
- 不新增专用压缩模型配置，压缩使用当前任务模型。
- 不升级 Spring Boot 4，不把 Spring AI Alibaba 2.x 直接作为主框架接入。
- 不引入 AgentScope RC 版本接管 ReAct/Harness 主循环。
- 不拆 Maven 物理模块，不进行包名或 artifactId 大迁移。
- 不保证旧历史消息、旧 run、旧记忆可迁移；不兼容的数据直接删除。

## Decisions

### 1. 上下文由生命周期分层，而不是按来源简单拼接

ContextEngine 将输入统一建模为 `ContextCandidate`，每个候选必须包含 `sectionType`、`scope`、`sourceRef`、`priority`、`tokenEstimate`、`freshnessStatus`、`trustScore`、`compressible`、`required` 和 `evidenceRefs`。装配顺序固定为：prefix -> working memory -> relevant memory -> recent messages -> file summaries -> raw snippets -> tool results -> run trace -> current request。

`current request` 与工具协议永远不裁剪；任务目标、关键约束、当前变更文件、审批状态、失败尝试等进入 working memory；可替代的历史细节、重复工具输出和长日志优先压缩或外置到 run artifacts。

备选方案是继续按现有候选优先级排序并截断。该方案实现成本低，但不能区分稳定前缀、工作状态、可召回记忆和当前请求的生命周期，长任务中容易丢任务目标或把旧事实压过新事实，因此不采用。

### 2. 压缩是状态治理，不是尾部截断

ContextEngine 在每次模型调用前保存 `context-snapshot/*.json`，记录原始候选、入选候选、被折叠候选、压缩前后 token、触发水位、压缩摘要和保留锚点。压缩流程分三层：

1. 规则压缩：去重相同工具结果、折叠长 shell 输出、保留文件路径/命令/exitCode/关键错误。
2. 模型压缩：达到 85% 水位时调用当前任务模型生成结构化摘要，输出必须按固定 JSON schema 包含 task_state、key_decisions、changed_files、failed_attempts、open_questions、evidence_refs。
3. 强制保护：达到 95% 水位时仅保留锚点、最近关键消息、当前文件片段和下一步行动。

压缩调用使用独立预算，例如 `CONTEXT_COMPRESSION_MAX_MODEL_CALLS_PER_RUN` 和 `CONTEXT_COMPRESSION_MAX_TOKENS_PER_RUN`，避免压缩本身耗尽任务预算。压缩失败时回退规则压缩，并在 snapshot 中记录 failure reason。

### 3. 记忆分层：项目/文件跨会话，任务/运行不跨会话

记忆类型分为：

- `PROJECT_MEMORY`：项目结构、常用测试命令、代码风格、关键模块、架构约束。跨同 workspace 会话共享，但必须有文件、工具或多次运行 evidence。
- `FILE_MEMORY`：文件职责、关键符号、最近摘要、hash、路径、语言、模块。跨同 workspace 会话共享，但每次召回前必须 freshness 校验。
- `TASK_MEMORY`：当前任务目标、已尝试方案、失败路径、用户明确偏好。只在当前 conversation 有效。
- `RUN_OBSERVATION`：工具结果、测试输出、审批结果、异常分类。只作为当前 run 或同 conversation 的短期证据。
- `WORKING_MEMORY`：当前 prompt 的工作台状态，不直接进 pgvector，随 run/context snapshot 持久化。

项目级和文件级记忆可以跨会话共享，是因为它们描述 workspace 稳定事实；任务级和运行级记忆不跨会话，是为了避免把某个任务的临时目标污染其他任务。

### 4. 记忆准入采用 evidence + trust score，不把模型结论当事实

每条稳定记忆必须保存 evidence：文件路径/hash、tool_call_id、run_id、model_call_id、checkpoint_id、created_reason 和 confidence。自动晋升规则：

- 文件读取、搜索命中、测试结果、依赖和模块结构等可验证事实可自动写入。
- 项目惯例、架构约束、常用命令必须有文件证据、工具结果或多次运行证据才能晋升项目级记忆。
- 无来源模型结论只能作为低可信提示，不能直接用于写文件、删除文件、Git reset/clean/rm 等高风险操作。

召回后需要二次裁决：低于 `MEMORY_MIN_TRUST_SCORE` 的候选只允许进入“可能相关提示”，不能进入“事实依据”。涉及代码修改时，模型必须优先回读当前文件或使用新鲜 raw snippet。

### 5. freshness 失败直接删除相关记忆

召回前必须校验 `workspaceKey`、文件 hash、路径存在性、checkpoint 作用域和 schema version。发生编辑、删除、checkpoint 回滚后，与该 run/change set/file 相关的 MySQL memory、memory evidence、memory recall 和 pgvector chunk 直接删除。文件仍存在时，后续通过真实读取重新生成摘要和 embedding。

备选方案是标记 stale 并保留旧摘要。该方案有审计价值，但对本地 coding agent 更容易污染当前任务；用户已明确 v4.5 以正确上下文优先，因此采用直接删除。

### 6. 召回由多路候选和重排组成

召回流程：

1. 构造 query：当前任务、当前文件路径、最近用户消息、已触达符号、工具失败摘要。
2. 候选召回：pgvector topK、路径精确命中、符号/类名/测试名命中、最近文件摘要、checkpoint 前有效 task memory。
3. 过滤：workspace、conversation cutoff、freshness、trust score、权限等级、敏感路径。
4. 重排：路径/符号强命中优先于泛语义相似；近期证据、文件摘要、测试失败相关记忆加权；低可信候选降权。
5. 装配：最多注入 `MEMORY_SELECTED_TOP_K` 条，每条必须带来源和可信度说明。

这比纯向量搜索复杂，但可以减少“语义相似但任务污染”的问题。

### 7. 领域边界按业务拆分

本次不拆 Maven module，只重整包和端口：

- `domain.agent`：AgentRun、状态机、run lifecycle、attempt/step。
- `domain.context`：context candidate、section、budget、snapshot、compression。
- `domain.memory`：memory item、chunk、evidence、recall、freshness。
- `domain.tool`：tool descriptor、tool invocation、governance、approval、result evidence。
- `domain.workspace`：workspace、checkpoint、change set、路径边界。
- `domain.model`：model provider、model capability、stream/embedding/compression gateway port。
- `domain.evaluation`：benchmark、eval run、metric、report。

通用基础值对象如 id、clock、json、error code 仍可放在现有 types/common 中，保留 DDD 边界。

### 8. 工具实现使用 Descriptor + Strategy

对外保持 toolName 不变，例如 `read_file`、`write_file`、`git_status`、`git_commit`。内部改为：

- `ToolDescriptor` 描述 name、riskLevel、requiredPermission、schema、timeout、approvalPolicy、resultEvidencePolicy。
- `ToolHandler` 执行具体工具。
- `ToolGovernancePolicy` 做参数校验、重复调用复用/阻断、敏感信息脱敏、审批判断。
- Git 工具使用 `GitOperationStrategy` 管理 status/diff/add/commit/reset/rm/clean/restore 等命令。

这样既能减少大量 LocalTool 重复类，也便于未来接入框架 tool schema。

### 9. 框架只做局部适配

Spring AI Alibaba 2.x 依赖 Spring Boot 4，当前项目仍是 Spring Boot 3，因此不直接升级接管。AgentScope Java 2.x 仍偏 RC，可借鉴 PlanNotebook、Hook、Sandbox Runtime 和 OpenTelemetry 思路，但不接管主循环。LangChain4j/Embabel 可作为后续模型客户端或工具 schema 适配候选。

v4.5 增加 `framework-adapter` 边界：框架对象只能存在于 infrastructure adapter 内，domain/case 只依赖项目自定义端口。这保证未来可替换，不把 Harness 状态机绑死在单一框架上。

### 10. 评测先服务上下文和记忆，不做泛化榜单

v4.5 eval 重点不是模型排行榜，而是验证认知系统是否变好。benchmark 分四类：

- 长上下文压缩：长历史、重复工具、失败尝试、checkpoint 回滚后的上下文保真。
- 记忆召回：跨会话项目记忆、文件摘要、外部修改 freshness、低可信污染拦截。
- 工具治理：重复读文件复用、受限工具拦截、高风险操作前回读证据。
- 回归任务：读仓库、改文件、跑测试、Git/PR、撤销/还原、checkpoint。

每次 eval 输出 `.coder/evals/{evalId}/report.json` 和 markdown summary，并可写入 MySQL eval 表。

## Default Configuration

v4.5 以 128K 上下文模型为默认基线，建议实施前写入 `.env`：

```env
CONTEXT_MAX_CONTEXT_TOKENS=131072
CONTEXT_MAX_INPUT_TOKENS=106496
CONTEXT_MAX_OUTPUT_TOKENS=8192
CONTEXT_SAFETY_RESERVE_TOKENS=16384
CONTEXT_PREFIX_BUDGET_TOKENS=8192
CONTEXT_WORKING_MEMORY_BUDGET_TOKENS=12288
CONTEXT_MEMORY_BUDGET_TOKENS=12288
CONTEXT_RECENT_MESSAGE_BUDGET_TOKENS=16384
CONTEXT_FILE_SUMMARY_BUDGET_TOKENS=16384
CONTEXT_RAW_SNIPPET_BUDGET_TOKENS=32768
CONTEXT_TOOL_RESULT_BUDGET_TOKENS=20480
CONTEXT_RUN_TRACE_BUDGET_TOKENS=8192
MEMORY_CANDIDATE_TOP_K=24
MEMORY_SELECTED_TOP_K=8
MEMORY_MIN_SCORE=0.35
MEMORY_MIN_TRUST_SCORE=0.65
MEMORY_MAX_CHUNKS_PER_RUN=12
MEMORY_MAX_EMBEDDING_CALLS_PER_RUN=8
MEMORY_MAX_AUTO_SUMMARY_FILES_PER_RUN=12
MEMORY_MAX_FILE_BYTES_FOR_SUMMARY=262144
MEMORY_CHUNK_MAX_TOKENS=1200
MEMORY_CHUNK_OVERLAP_TOKENS=120
```

## Risks / Trade-offs

- [Risk] 破坏性迁移会丢失旧历史消息和旧运行审计。-> 迁移前提供导出脚本，并在 README 明确 v4.5 是认知数据重建。
- [Risk] 模型压缩可能生成错误摘要。-> 压缩摘要必须带 evidence_refs，关键写操作仍要求回读当前文件或工具证据。
- [Risk] 召回和 freshness 校验增加延迟。-> 使用 hash 缓存、embedding 批处理、候选数量上限和分层预算。
- [Risk] DDD 拆包影响大量 import。-> 不拆 Maven module，按端口逐步迁移，保留 API 层 DTO 稳定。
- [Risk] 框架适配引入维护成本。-> 只在 infrastructure adapter 引入，domain/case 不依赖框架类型。
- [Risk] 评测用例不足会误判效果。-> 首版覆盖固定 benchmark，后续每次缺陷复盘都加入 regression case。

## Migration Plan

1. 增加迁移前导出脚本，导出旧 conversation/message/run/tool/model/context/memory/pgvector 数据。
2. 执行破坏性清理：删除旧历史消息、旧 run 链路、旧 context snapshot、旧 MySQL memory、旧 pgvector chunk。
3. 创建新 MySQL 表/字段：context section/snapshot、memory item/evidence/recall、eval case/run/result、framework adapter metadata。
4. 重建 pgvector memory chunk 表结构和索引。
5. 接入新 ContextEngine 和 MemoryService，先以功能开关方式上线。
6. 重构领域包和工具策略，保持 HTTP API 和 toolName 兼容。
7. 补充 README 和 `.env` 配置说明，提醒用户实施前配置新预算参数。
8. 运行单元测试、集成测试和 v4.5 benchmark。

回滚策略：停止服务，恢复迁移前导出的 MySQL/pgvector 数据，回退代码到 v4.2 提交；若仅需临时止血，可关闭 `MEMORY_ENABLED`、`PGVECTOR_ENABLED`、`CONTEXT_COMPRESSION_ENABLED`、`EVAL_ENABLED`。

## Open Questions

当前需求边界已确认：v4.5 不保留旧认知数据兼容、不新增专用压缩模型、不让外部框架接管主循环。实现阶段如发现某框架适配会强迫升级 Spring Boot 4 或侵入 domain/case，应推迟到后续版本。
