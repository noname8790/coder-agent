## ADDED Requirements

### Requirement: 回滚作用域过滤
系统 MUST 在上下文治理阶段排除已回滚 checkpoint 之后且 rollback 之前产生的消息、run trace、工具结果、文件摘要和记忆召回结果。涉及模块：AgentContextAssembler、context snapshot。涉及表：`agent_checkpoint`、`agent_message`、`context_snapshot`、`agent_memory_item`、`memory_recall`。

#### Scenario: 上下文排除已回滚消息
- **WHEN** conversation 已还原到某个 checkpoint
- **THEN** 后续模型调用上下文 MUST 不包含该 checkpoint 之后且 rollback 之前的消息正文

#### Scenario: 上下文保留回滚后的新消息
- **WHEN** checkpoint 回滚后用户继续发起新任务
- **THEN** 后续上下文 MUST 包含 rollback 之后新产生的有效消息

### Requirement: 变更状态上下文摘要
系统 MUST 在上下文中注入 run 变更状态摘要，包括哪些 run 已应用、哪些 run 已撤销、当前会话是否还原到 checkpoint。涉及表：`agent_run_change_set`、`agent_checkpoint`、`context_snapshot`。

#### Scenario: 注入撤销摘要
- **WHEN** 某个历史 run 的 change set 状态为 `REVERTED`
- **THEN** 后续模型调用上下文 MUST 明确说明该 run 的代码改动当前不在 workspace 中

#### Scenario: 注入检查点摘要
- **WHEN** 当前 conversation 已回滚到 checkpoint
- **THEN** 后续模型调用上下文 MUST 明确说明当前会话有效历史截止点

### Requirement: Context Snapshot 记录过滤原因
系统 MUST 在 context snapshot 中记录被 checkpoint cutoff、run revert 或消息 visibility 过滤掉的候选数量和原因。涉及表：`context_snapshot` 和 context snapshot 工件。

#### Scenario: 查询过滤指标
- **WHEN** 用户查询 run 详情
- **THEN** 系统 SHALL 返回本次上下文过滤摘要，包括回滚过滤数量和撤销状态摘要路径
