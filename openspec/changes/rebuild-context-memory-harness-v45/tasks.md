## 1. 迁移准备与配置

- [ ] 1.1 在实现前提醒用户把 v4.5 的 `CONTEXT_*`、`MEMORY_*`、`PGVECTOR_*`、`EMBEDDING_*` 默认配置写入 `.env`。
- [ ] 1.2 补充 v4.5 破坏性迁移说明，明确旧历史消息、旧 run、旧 context/memory/pgvector 数据不兼容时直接清理。
- [ ] 1.3 编写 MySQL 迁移前导出脚本，导出旧 conversation、message、run、step、model_call、tool_call、context、memory、eval 和 audit 相关数据。
- [ ] 1.4 编写 pgvector 迁移前导出脚本，导出旧 memory chunk 数据。
- [ ] 1.5 编写 v4.5 MySQL 清理与建表脚本，覆盖 context section/snapshot、memory item/evidence/recall、eval case/run/result、framework adapter metadata。
- [ ] 1.6 编写 v4.5 pgvector 清理与建表脚本，重建 memory chunk 表、向量列、workspace/source/freshness 索引。
- [ ] 1.7 增加运行时开关：`MEMORY_ENABLED`、`PGVECTOR_ENABLED`、`CONTEXT_COMPRESSION_ENABLED`、`EVAL_ENABLED`。
- [ ] 1.8 如本次提升项目版本号，同步更新根 POM、子模块 POM 和客户端 package 版本。

## 2. DDD 领域边界重构

- [ ] 2.1 在 `coder-agent-domain` 内拆分 `agent`、`context`、`memory`、`tool`、`workspace`、`model`、`evaluation` 业务包。
- [ ] 2.2 迁移 AgentRun、状态机、attempt/step 相关对象到 `domain.agent`。
- [ ] 2.3 迁移 context candidate、section、budget、snapshot、compression 对象与端口到 `domain.context`。
- [ ] 2.4 迁移 memory item、chunk、evidence、recall、freshness 对象与端口到 `domain.memory`。
- [ ] 2.5 迁移 tool descriptor、tool invocation、governance、approval、result evidence 对象与端口到 `domain.tool`。
- [ ] 2.6 迁移 workspace、checkpoint、change set、路径边界对象与端口到 `domain.workspace`。
- [ ] 2.7 迁移 model provider、capability、stream/embedding/compression gateway 端口到 `domain.model`。
- [ ] 2.8 新增 `domain.evaluation`，定义 benchmark、eval run、metric、report 对象与端口。
- [ ] 2.9 清理把 context/memory/tool/model/eval 继续放入 agent 包的旧引用。
- [ ] 2.10 调整 case/infrastructure/trigger 的 import 和依赖方向，确保 domain/case 不依赖 infrastructure 框架类型。

## 3. 上下文治理内核

- [ ] 3.1 定义 `ContextCandidate`、`ContextSection`、`ContextBudget`、`ContextSnapshot`、`ContextCompressionResult`。
- [ ] 3.2 实现分层候选收集：prefix、working memory、relevant memory、recent messages、file summaries、raw snippets、tool results、run trace、current request。
- [ ] 3.3 实现 128K 默认预算配置读取和 per-section budget 装配。
- [ ] 3.4 实现 75% 水位规则去重：重复工具结果、重复文件片段、长 shell 输出折叠。
- [ ] 3.5 实现 85% 水位模型压缩调用，使用当前任务模型输出结构化 JSON 摘要。
- [ ] 3.6 实现压缩调用独立预算和失败回退规则压缩。
- [ ] 3.7 实现 95% 水位强制锚点保护，保留任务目标、关键约束、当前文件、失败尝试、下一步行动。
- [ ] 3.8 实现 100% 水位拒绝继续拼接并恢复最近有效 checkpoint 的错误路径。
- [ ] 3.9 每次模型调用前保存 `context-snapshot/*.json` 和 MySQL snapshot 元数据。
- [ ] 3.10 编写 Given/When/Then 单测覆盖上下文超预算、压缩失败回退、current request 不裁剪、snapshot 指标落库。

## 4. 结构化记忆与 pgvector

- [ ] 4.1 定义 PROJECT_MEMORY、FILE_MEMORY、TASK_MEMORY、RUN_OBSERVATION、WORKING_MEMORY 类型与作用域规则。
- [ ] 4.2 实现 memory item、memory evidence、memory recall 的 MySQL repository。
- [ ] 4.3 实现 pgvector memory chunk repository，支持 workspaceKey、sourceType、sourceId、freshness、trustScore 元数据。
- [ ] 4.4 实现 embedding 批量调用和 hash 缓存，限制每 run embedding 调用次数。
- [ ] 4.5 实现文件读取、搜索命中、测试结果、依赖、模块结构的自动记忆写入。
- [ ] 4.6 实现项目级记忆晋升规则：必须有文件证据、工具结果或多次运行证据。
- [ ] 4.7 实现文件级记忆摘要，保存 path、language、symbols、summary、hash、workspaceKey、createdRunId、evidenceRefs。
- [ ] 4.8 实现任务级记忆和运行观察的 conversation/run 作用域隔离。
- [ ] 4.9 实现编辑、删除、checkpoint 回滚后直接删除相关 MySQL memory、memory evidence、memory recall 和 pgvector chunk。
- [ ] 4.10 编写 Given/When/Then 单测覆盖跨会话项目记忆、文件记忆 hash、删除后重新生成、低可信记忆不能驱动高风险操作。

## 5. 召回、freshness 与污染拦截

- [ ] 5.1 实现召回 query 构造：当前任务、路径、符号、最近用户消息、工具失败摘要。
- [ ] 5.2 实现多路召回：pgvector topK、路径精确命中、符号/类名/测试名命中、最近文件摘要、checkpoint 前有效 task memory。
- [ ] 5.3 实现召回过滤：workspace、conversation cutoff、freshness、trustScore、权限等级、敏感路径。
- [ ] 5.4 实现重排：路径/符号强命中优先，近期证据和测试失败相关记忆加权，低可信候选降权。
- [ ] 5.5 实现召回结果装配，最多注入 `MEMORY_SELECTED_TOP_K` 条并带来源与可信度说明。
- [ ] 5.6 实现 freshness 前置校验：文件 hash、路径存在性、workspace fingerprint、schema version、checkpoint 作用域。
- [ ] 5.7 实现 stale/不可信记忆污染拦截，要求模型先回读当前文件或获取新工具证据。
- [ ] 5.8 编写集成测试覆盖外部修改文件后旧摘要不进入 prompt、路径命中优先、低可信候选降级。

## 6. 工具策略化与证据链

- [ ] 6.1 定义 ToolDescriptor，覆盖 name、riskLevel、requiredPermission、schema、timeout、approvalPolicy、resultEvidencePolicy。
- [ ] 6.2 定义 ToolHandler 和 ToolGovernancePolicy，统一参数校验、重复调用复用/阻断、脱敏、审批判断。
- [ ] 6.3 将文件读写工具迁移到 descriptor + handler 模式，保留对外 toolName 不变。
- [ ] 6.4 将 Shell 工具迁移到 descriptor + handler 模式，保留命令白名单、危险 token、超时和输出截断策略。
- [ ] 6.5 将 Git 工具收敛为 GitOperationStrategy，覆盖 status、diff、add、commit、reset、rm、clean、restore、PR draft。
- [ ] 6.6 将成功、失败、拒绝、超时和审批结果统一转换为 ToolObservation。
- [ ] 6.7 将 ToolObservation 写入上下文候选和 memory evidence。
- [ ] 6.8 编写 Given/When/Then 测试覆盖重复工具调用复用、失败工具结果回灌、Git 策略审批和 result evidence 写入。

## 7. 框架局部适配

- [ ] 7.1 新增 framework adapter 边界，明确框架类型不得进入 domain/case。
- [ ] 7.2 评估 Spring AI Alibaba 2.x 与当前 Spring Boot 3 的兼容性，若必须升级 Boot 4 则记录为后续版本，不在 v4.5 接入。
- [ ] 7.3 评估 AgentScope Java 的 PlanNotebook、Hook、Sandbox Runtime、OpenTelemetry 思路，选择可无侵入适配的局部点。
- [ ] 7.4 为模型客户端、工具 schema、观测 hook 预留 adapter 端口和配置，不改变 AgentRun 主循环。
- [ ] 7.5 编写架构测试或静态检查，确保 domain/case 不引用 Spring AI Alibaba、AgentScope、LangChain4j、Embabel 类型。

## 8. 评测闭环

- [ ] 8.1 定义 benchmark case schema：workspace fixture、初始文件、任务输入、期望结果、允许工具、评价器。
- [ ] 8.2 创建长上下文压缩 benchmark，覆盖长历史、重复工具、失败尝试、checkpoint 回滚。
- [ ] 8.3 创建记忆召回 benchmark，覆盖跨会话项目记忆、文件摘要、外部修改 freshness、低可信污染拦截。
- [ ] 8.4 创建工具治理 benchmark，覆盖重复读文件复用、受限工具拦截、高风险操作前回读证据。
- [ ] 8.5 创建回归 benchmark，覆盖读仓库、改文件、跑测试、Git/PR、撤销/还原、checkpoint。
- [ ] 8.6 实现 eval runner，输出 pass_rate、compression_ratio、retained_anchor_rate、memory_recall_precision/recall@k、stale_block_rate、repeated_read_count、tool_steps、token cost。
- [ ] 8.7 生成 `.coder/evals/{evalId}/report.json` 和 markdown summary。
- [ ] 8.8 编写 eval 结果 MySQL repository 和查询接口。

## 9. API、客户端与文档

- [ ] 9.1 扩展 run/detail API 返回上下文指标、记忆命中、压缩率和 eval 相关摘要。
- [ ] 9.2 保持现有客户端核心交互不破坏，仅在侧栏或详情区展示新增上下文/记忆指标。
- [ ] 9.3 更新 README，说明 v4.5 破坏性迁移、`.env` 配置、pgvector 重建和回滚方法。
- [ ] 9.4 更新 SQL 文档，标注影响表、中文 COMMENT 和回滚脚本路径。
- [ ] 9.5 补充 docs/superpowers 设计摘要，方便后续复盘和面试项目描述使用。

## 10. 验证与交付

- [ ] 10.1 运行 Maven 单元测试，确保 domain/case/infrastructure/trigger 编译通过。
- [ ] 10.2 运行 MySQL 迁移脚本冒烟测试，确认旧数据清理、新表创建和回滚导出可用。
- [ ] 10.3 运行 pgvector 冒烟测试，确认 chunk 写入、召回、删除和索引查询正常。
- [ ] 10.4 使用真实模型运行上下文压缩冒烟测试，确认结构化压缩和失败回退可用。
- [ ] 10.5 使用真实模型运行记忆召回冒烟测试，确认跨会话项目/文件记忆命中且 stale 记忆被阻断。
- [ ] 10.6 运行 v4.5 benchmark，记录压缩率、召回率、重复读率和 pass_rate。
- [ ] 10.7 检查 OpenSpec proposal/design/spec/tasks 一致性。
- [ ] 10.8 完成后提醒用户提交 v4.5 文档或实现代码。
