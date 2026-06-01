## ADDED Requirements

### Requirement: 会话创建与列表
系统 SHALL 支持创建代码 Agent 会话，并按更新时间查询会话列表。会话 MUST 关联 workspace、默认模型和默认权限等级。涉及 API：`POST /api/conversations`、`GET /api/conversations`。涉及表：`agent_conversation`。

#### Scenario: 创建会话
- **WHEN** 客户端提交 workspaceKey、标题、默认模型和默认权限等级
- **THEN** 系统创建 `agent_conversation` 记录并返回 conversationId

#### Scenario: 查询会话列表
- **WHEN** 客户端打开侧栏会话列表
- **THEN** 系统返回按更新时间倒序排列的会话摘要

### Requirement: 会话消息历史
系统 SHALL 保存用户消息、Agent 最终回答和关键运行摘要。消息 MUST 归属于 conversationId。第三版 MUST 不做长期记忆召回或向量检索。涉及 API：`GET /api/conversations/{conversationId}`、`GET /api/conversations/{conversationId}/messages`。涉及表：`agent_message`。

#### Scenario: 保存用户任务消息
- **WHEN** 用户在会话中发送任务
- **THEN** 系统写入一条用户消息并关联后续 Agent Run

#### Scenario: 保存 Agent 最终回答
- **WHEN** Agent Run 进入终态并产生 finalAnswer
- **THEN** 系统写入一条 Agent 消息并关联 runId

### Requirement: 会话下多次运行
系统 SHALL 允许一个 conversation 下创建多次 Agent Run。每次 run MUST 记录 conversationId、permissionLevel 和模型。涉及 API：`POST /api/agent-runs`。涉及表：`agent_run`。

#### Scenario: 同一会话继续提问
- **WHEN** 用户在已有会话中发送新任务
- **THEN** 系统创建新的 Agent Run 并保留同一个 conversationId

#### Scenario: 查询会话关联运行
- **WHEN** 客户端打开会话详情
- **THEN** 系统返回该会话的消息和关联运行摘要
