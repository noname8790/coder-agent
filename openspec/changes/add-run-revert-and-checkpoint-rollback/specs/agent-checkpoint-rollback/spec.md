## ADDED Requirements

### Requirement: 会话检查点
系统 SHALL 在可作为回滚边界的 Agent 消息下方提供检查点能力，并在后端记录 checkpointId、conversationId、messageId、runId、messageSeq 和 workspaceKey。涉及 API：`POST /api/conversations/{conversationId}/checkpoints`、conversation/message 查询。涉及表：`agent_checkpoint`、`agent_message`。

#### Scenario: Agent 消息生成检查点入口
- **WHEN** Agent 消息对应的 run 已结束
- **THEN** 客户端 MUST 在该 Agent 消息下方展示“还原到此检查点”入口

#### Scenario: 保存检查点记录
- **WHEN** 用户点击“还原到此检查点”并确认
- **THEN** 系统 MUST 创建或复用该消息对应的 checkpoint 记录

### Requirement: 检查点回滚确认
客户端 MUST 在执行 checkpoint 回滚前展示确认提示，明确说明该操作会修改当前 workspace 文件，并使检查点之后的消息仅保留为历史审计。涉及页面：对话消息列表。

#### Scenario: 展示风险确认
- **WHEN** 用户点击“还原到此检查点”
- **THEN** 客户端 MUST 展示确认弹窗，说明当前工作区文件会被回滚

#### Scenario: 用户取消确认
- **WHEN** 用户在确认弹窗中取消
- **THEN** 客户端 MUST 不调用回滚接口，workspace 文件和消息状态不得变化

### Requirement: 原会话内回滚到检查点
系统 SHALL 在原 conversation 内回滚到指定 checkpoint，不新增会话、不创建 git worktree。回滚时 MUST 按时间倒序撤销该 checkpoint 之后有效 run 的代码改动。涉及 API：`POST /api/conversations/{conversationId}/checkpoints/{checkpointId}/rollback`。涉及表：`agent_checkpoint`、`agent_run_change_set`、`agent_run_file_change`、`agent_message`、`audit_event`。

#### Scenario: 成功回滚到检查点
- **WHEN** 用户确认回滚到某个 checkpoint 且该 checkpoint 之后所有 run 都可撤销
- **THEN** 系统 MUST 将 workspace 文件恢复到该 checkpoint 对应状态
- **AND** 系统 MUST 将 checkpoint 之后消息标记为 `ROLLED_BACK`

#### Scenario: 回滚遇到不可逆文件
- **WHEN** checkpoint 之后存在不可逆文件改动
- **THEN** 系统 MUST 拒绝整体回滚，返回不可逆文件列表，并不得执行部分文件回滚

#### Scenario: 回滚遇到 hash 冲突
- **WHEN** checkpoint 之后某个文件已被用户手动修改导致 hash 不匹配
- **THEN** 系统 MUST 拒绝整体回滚，返回冲突文件列表，并不得覆盖用户手动修改

### Requirement: 回滚后消息折叠审计
客户端 MUST 将 checkpoint 之后被标记为 `ROLLED_BACK` 的消息折叠显示，并明确标出折叠边界。用户点击后 SHALL 可展开查看这些消息；折叠消息仅用于历史审计，不参与新任务输入状态。涉及 API：conversation messages 查询。涉及表：`agent_message`。

#### Scenario: 折叠已回滚消息
- **WHEN** 会话中存在 `ROLLED_BACK` 消息
- **THEN** 客户端 MUST 显示清晰的折叠边界，例如“已回滚的历史消息，仅用于审计”

#### Scenario: 展开审计消息
- **WHEN** 用户点击折叠边界
- **THEN** 客户端 SHALL 展开显示已回滚消息内容，但不得将其作为当前输入上下文状态

### Requirement: 回滚边界进入后续上下文
系统 MUST 在 checkpoint 回滚后，把“已还原到哪个检查点、哪些后续 run 已失效”写入后续 Agent Run 的上下文摘要。涉及模块：context governance、AgentContextAssembler。涉及表：`context_snapshot`、`agent_checkpoint`。

#### Scenario: 后续任务知道回滚状态
- **WHEN** 用户在 checkpoint 回滚后发起新任务
- **THEN** 系统 MUST 在 prompt 中说明当前会话已回滚到该 checkpoint

#### Scenario: 回滚后新任务不继承后续历史
- **WHEN** 用户在 checkpoint 回滚后发起新任务
- **THEN** 系统 MUST 排除 checkpoint 之后且 rollback 之前的消息、工具结果和运行摘要
