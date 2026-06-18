## ADDED Requirements

### Requirement: Diff 撤销与还原入口
客户端 MUST 在包含可逆 change set 的 Agent 消息 Diff 摘要下方展示“撤销”按钮；撤销成功后按钮 MUST 变为“还原更改”。涉及 API：`POST /api/agent-runs/{runId}/revert`、`POST /api/agent-runs/{runId}/restore`、run/message 详情。

#### Scenario: 显示撤销按钮
- **WHEN** Agent 消息包含状态为 `APPLIED` 的可逆 change set
- **THEN** 客户端 MUST 在 Diff 摘要中显示“撤销”按钮

#### Scenario: 撤销后显示还原按钮
- **WHEN** 用户成功撤销某个 run 的改动
- **THEN** 客户端 MUST 刷新 Diff 状态，并将按钮显示为“还原更改”

### Requirement: Diff 不可逆文件红色标识
客户端 MUST 在 Diff 文件列表中对不可逆文件显示红色文字“该文件无法自动撤销”，并可展示后端返回的不可逆原因。涉及页面：对话消息列表 Diff 卡片。

#### Scenario: 展示不可逆提示
- **WHEN** Diff 文件项包含 `reversible=false`
- **THEN** 客户端 MUST 在该文件旁以红色字体显示“该文件无法自动撤销”

#### Scenario: 不可逆原因提示
- **WHEN** 后端返回不可逆原因
- **THEN** 客户端 SHALL 在文件详情或提示中展示该原因

### Requirement: Agent 消息底部模型展示
客户端 MUST 在 Agent 任务完成、取消或失败后，在 Agent 消息底部以灰色小字展示本次执行的模型展示名。涉及 API：message 详情、run 详情。涉及字段：`modelDisplayName`。

#### Scenario: 成功消息显示模型名称
- **WHEN** Agent Run 状态为 `SUCCEEDED`
- **THEN** Agent 消息底部 MUST 显示本次执行模型的 displayName

#### Scenario: 取消或失败消息显示模型名称
- **WHEN** Agent Run 状态为 `CANCELLED` 或 `FAILED`
- **THEN** Agent 消息底部 MUST 仍显示本次执行模型的 displayName
