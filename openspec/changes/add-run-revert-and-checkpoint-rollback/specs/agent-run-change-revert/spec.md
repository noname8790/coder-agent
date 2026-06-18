## ADDED Requirements

### Requirement: Run 级可逆变更集
系统 SHALL 为每个产生文件改动的 Agent Run 保存 run 级变更集，并记录每个文件的变更类型、before/after hash、before/after 快照路径、是否可逆和不可逆原因。涉及 API：`GET /api/agent-runs/{runId}`、`GET /api/conversations/{conversationId}/messages`。涉及表：`agent_run_change_set`、`agent_run_file_change`、`agent_run`、`agent_message`。

#### Scenario: 保存文件修改变更集
- **WHEN** Agent Run 修改、覆盖、新增或删除文件
- **THEN** 系统 MUST 保存该 run 的 change set 和每个文件的可逆信息

#### Scenario: 无文件变更不生成变更集
- **WHEN** Agent Run 只读分析且没有文件改动
- **THEN** 系统 MUST 不生成可撤销 change set，查询响应中 MUST 表示无可撤销改动

### Requirement: 撤销当前 Run 改动
系统 SHALL 提供撤销 run 改动接口。撤销前 MUST 校验当前文件 hash 与 change set 的 after hash 一致；一致时恢复 before 快照或删除该 run 新增文件；不一致时拒绝撤销并返回冲突文件列表。涉及 API：`POST /api/agent-runs/{runId}/revert`。涉及表：`agent_run_change_set`、`agent_run_file_change`、`audit_event`。

#### Scenario: 成功撤销文件改动
- **WHEN** 用户点击某个已应用 run 的“撤销”且当前文件 hash 与 after hash 匹配
- **THEN** 系统 MUST 恢复该 run 执行前的文件状态，并将 change set 状态更新为 `REVERTED`

#### Scenario: 撤销遇到手动修改冲突
- **WHEN** 用户点击“撤销”但任一文件当前 hash 与 after hash 不匹配
- **THEN** 系统 MUST 拒绝撤销，返回冲突文件路径，并不得覆盖用户手动修改

### Requirement: 还原已撤销改动
系统 SHALL 提供还原 run 改动接口。还原前 MUST 校验当前文件 hash 与 change set 的 before hash 一致；一致时恢复 after 快照或重新写入新增文件；不一致时拒绝还原并返回冲突文件列表。涉及 API：`POST /api/agent-runs/{runId}/restore`。涉及表：`agent_run_change_set`、`agent_run_file_change`、`audit_event`。

#### Scenario: 成功还原已撤销改动
- **WHEN** 用户点击已撤销 run 的“还原更改”且当前文件 hash 与 before hash 匹配
- **THEN** 系统 MUST 恢复该 run 执行后的文件状态，并将 change set 状态更新为 `APPLIED`

#### Scenario: 还原遇到冲突
- **WHEN** 用户点击“还原更改”但任一文件当前 hash 与 before hash 不匹配
- **THEN** 系统 MUST 拒绝还原，返回冲突文件路径，并保持 change set 状态不变

### Requirement: 不可逆文件标识
系统 MUST 标记无法自动撤销的文件，并在 Diff 摘要中返回不可逆原因。不可逆文件包括但不限于二进制文件、超过可逆大小上限的文件、缺少 before/after 快照的文件和 hash 无法计算的文件。涉及 API：run/message 详情。涉及表：`agent_run_file_change`。

#### Scenario: Diff 显示不可逆文件
- **WHEN** Agent Run 改动的文件无法自动撤销
- **THEN** 系统 MUST 在该文件的 Diff 数据中返回 `reversible=false` 和不可逆原因

#### Scenario: 不可逆文件阻止撤销
- **WHEN** 用户尝试撤销包含不可逆文件的 run
- **THEN** 系统 MUST 拒绝自动撤销，并返回所有不可逆文件及原因

### Requirement: 撤销状态进入后续上下文
系统 MUST 将 run 撤销/还原状态写入后续 Agent Run 的上下文摘要，使模型知道当前 workspace 是否包含某次任务改动。涉及模块：context governance、AgentContextAssembler。涉及表：`context_snapshot`、`agent_run_change_set`。

#### Scenario: 已撤销 run 不误导后续任务
- **WHEN** 某个 run 已被撤销后用户发起新任务
- **THEN** 系统 MUST 在 prompt 中说明该 run 的改动已撤销，不得把其文件改动当作当前代码状态

#### Scenario: 已还原 run 注入当前状态
- **WHEN** 某个 run 被还原后用户发起新任务
- **THEN** 系统 MUST 在 prompt 中说明该 run 的改动当前已应用
