## ADDED Requirements

### Requirement: 分层结构化记忆
系统 MUST 将记忆区分为 PROJECT_MEMORY、FILE_MEMORY、TASK_MEMORY、RUN_OBSERVATION 和 WORKING_MEMORY。项目级和文件级记忆 MUST 在同一 workspace 下跨会话共享，任务级和运行级记忆 MUST 限定在 conversation/run 作用域。

#### Scenario: 跨会话召回项目记忆
- **GIVEN** 会话 A 已基于工具证据写入一个 PROJECT_MEMORY
- **WHEN** 用户在同一 workspace 的会话 B 发起相关任务
- **THEN** 系统 MUST 在通过 workspace、freshness 和可信度校验后召回该 PROJECT_MEMORY
- **AND** 召回结果 MUST 标注 workspaceKey、sourceRef、trustScore 和 evidenceRefs

### Requirement: 记忆证据与可信度
系统 MUST 为稳定记忆保存证据引用和可信度。无来源模型结论 MUST 只能作为低可信提示，不能作为写文件、删除文件、Git reset/rm/clean 等高风险操作依据。

#### Scenario: 低可信记忆驱动高风险操作
- **GIVEN** 召回候选只有无工具证据的低可信记忆
- **WHEN** Agent 尝试基于该记忆执行删除文件
- **THEN** 工具治理层 MUST 拒绝直接执行
- **AND** 系统 MUST 要求 Agent 先读取当前文件或获取新的工具证据

### Requirement: freshness 失败删除记忆
系统 MUST 在编辑、删除、checkpoint 回滚或文件 hash 不匹配时删除相关 MySQL memory、memory evidence、memory recall 和 pgvector chunk。

#### Scenario: 文件修改后删除旧文件记忆
- **GIVEN** `Foo.java` 已存在 FILE_MEMORY 和 pgvector chunk
- **WHEN** Agent 修改或覆盖 `Foo.java`
- **THEN** 系统 MUST 删除该文件旧 FILE_MEMORY 和对应 pgvector chunk
- **AND** 后续只有重新读取当前文件后才能生成新的文件记忆
