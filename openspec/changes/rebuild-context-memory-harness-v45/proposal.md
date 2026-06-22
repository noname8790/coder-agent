## Why

v4 已经具备模型配置、流式输出、工具治理和基础 pgvector 记忆，但当前上下文与记忆仍偏“功能骨架”：缺少稳定的工作记忆、分层压缩、freshness 裁决、可信度治理和可量化评测，导致长链路任务中仍容易出现 prompt 膨胀、过期记忆污染、重复读文件和任务目标漂移。

v4.5 将把项目从“可执行代码任务的 Agent 应用”推进到“可治理、可恢复、可评测的 Agent Harness 认知内核”，同时整理 DDD 领域边界，降低后续继续扩展模型、工具、上下文和评测能力的维护成本。

## What Changes

- **BREAKING** 重建上下文与记忆数据结构：旧历史消息、旧 run、旧 context snapshot、旧 MySQL memory、旧 pgvector chunk 等无法可靠兼容新认知结构的数据在迁移时直接清理，不做强行兼容。
- **BREAKING** 替换当前简单 ContextEngine：引入分层上下文候选、预算水位、规则去重、模型压缩、锚点保护、压缩快照和恢复路径。
- **BREAKING** 重建结构化记忆：按 workspace 隔离，并拆分项目级记忆、文件级记忆、任务级记忆、运行观察与工作记忆；项目级和文件级记忆在同一 workspace 下跨会话共享。
- 增强 freshness 机制：召回前校验文件 hash / workspace fingerprint / checkpoint 作用域；发生编辑、删除、checkpoint 回滚后，相关记忆和 pgvector chunk 直接删除，后续重新读取再生成。
- 增强记忆召回：从单纯向量相似扩展为“向量候选 + 路径/符号/模块/测试名精确命中 + 来源可信度 + freshness + 时间衰减 + 重排裁决”。
- 引入上下文压缩调用：当 prompt 达到水位时，使用当前任务模型执行结构化压缩；压缩调用受独立预算控制，失败时回退规则压缩，不新增专用压缩模型配置。
- 提升 128K 上下文预算默认值，并把预算拆分到 prefix、working memory、relevant memory、recent messages、file summary、raw snippet、tool result、run trace 等层。
- 建立上下文/记忆评测闭环：增加压缩率、锚点保留率、记忆召回 precision/recall@k、stale block rate、重复读文件次数、pass rate、token 成本等指标。
- 整理 DDD 边界：不拆 Maven 物理模块，在现有模块内按业务领域拆分 `agent`、`context`、`memory`、`tool`、`workspace`、`model`、`evaluation` 包。
- 重构工具实现模式：保留对外 toolName/API 兼容，内部使用 ToolDescriptor + ToolHandler/Strategy 收敛文件工具、Shell/Git 工具和治理策略，减少重复实现。
- 引入框架局部适配边界：不让 Spring AI Alibaba、AgentScope、LangChain4j 或 Embabel 接管主循环；仅在模型客户端、工具 schema、观测、PlanNotebook/Hook/Sandbox 等适合位置做可替换适配。
- 更新配置和 README：提醒用户在实施前补充 v4.5 的 `CONTEXT_*`、`MEMORY_*`、`PGVECTOR_*`、`EMBEDDING_*` 配置。

## Capabilities

### New Capabilities

- `context-compression-governance`: 定义分层上下文、预算水位、压缩、裁剪、锚点保护、快照和恢复要求。
- `structured-memory-governance`: 定义 workspace 隔离的项目级、文件级、任务级和运行级记忆结构、写入、召回、freshness、可信度与污染拦截要求。
- `agent-harness-evaluation`: 定义上下文和记忆改进的 benchmark、指标汇总、回归对比和报告工件要求。
- `domain-boundary-refactoring`: 定义 DDD 领域边界拆分、端口迁移、工具策略化和框架局部适配约束。

### Modified Capabilities

- `context-governance`: 由 v4 的基础分层装配升级为 v4.5 的预算水位、模型压缩、候选裁决和可恢复上下文链路。
- `workspace-memory`: 由 v4 的基础 pgvector 召回升级为分层结构化记忆、跨会话项目/文件记忆、freshness 校验和污染拦截。
- `memory-summarization`: 由运行结束/文件读取摘要升级为项目级、文件级、任务级、运行级摘要的准入、晋升、降级和删除规则。
- `agent-evaluation`: 由基础运行评测扩展为上下文/记忆专项评测与量化指标。
- `tool-governance-approval`: 补充工具结果作为上下文证据、受限工具拦截与低可信记忆不得驱动高风险操作的规则。

## Impact

- 影响模块：
  - `coder-agent-domain`：按业务领域拆分包，迁移上下文、记忆、工具、模型、workspace、evaluation 的实体、值对象和端口。
  - `coder-agent-case`：重构 AgentRun 上下文装配、记忆写入/召回、压缩、freshness 裁决、评测用例和工具调度流程。
  - `coder-agent-infrastructure`：调整 MySQL/pgvector repository、embedding 批处理、hash 缓存、工具策略、框架适配和审计落库。
  - `coder-agent-trigger`：补充上下文/记忆/eval 指标接口，保持既有客户端 API 稳定。
  - `coder-agent-app`：新增配置项、迁移脚本装配和运行时开关。
  - `coder-agent-client`：本轮不做大 UI 改造，仅在需要时展示新增指标和评测结果。
- 影响数据库：
  - MySQL：新增或重建 context section、context snapshot、memory item、memory evidence、memory recall、evaluation case/run/result 等表；旧上下文/记忆/历史运行链路数据允许清理。
  - PostgreSQL/pgvector：重建 memory chunk 表结构和索引；旧 chunk 直接清空后按新结构重新生成。
- 影响配置：
  - 新增/调整 `CONTEXT_MAX_CONTEXT_TOKENS`、`CONTEXT_MAX_INPUT_TOKENS`、`CONTEXT_*_BUDGET_TOKENS`、`MEMORY_CANDIDATE_TOP_K`、`MEMORY_SELECTED_TOP_K`、`MEMORY_MIN_TRUST_SCORE`、`MEMORY_CHUNK_*` 等配置。
  - 实施前需要提醒用户把 v4.5 默认配置写入 `.env`。
- 回滚方案：
  - 代码层可回滚到 v4.2 分支/提交。
  - 数据层提供破坏性迁移前导出脚本；迁移后如需回滚，恢复导出数据并回退 MySQL/pgvector schema。
  - 运行时开关保留：`MEMORY_ENABLED=false`、`PGVECTOR_ENABLED=false`、`CONTEXT_COMPRESSION_ENABLED=false`、`EVAL_ENABLED=false` 可临时关闭对应能力。
