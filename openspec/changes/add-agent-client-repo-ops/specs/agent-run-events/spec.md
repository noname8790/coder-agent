## ADDED Requirements

### Requirement: SSE 运行事件流
系统 SHALL 提供 `GET /api/agent-runs/{runId}/events` SSE 接口，向客户端实时推送运行事件。事件 MUST 至少包含 eventId、runId、type、time 和 payload。涉及 API：`GET /api/agent-runs/{runId}/events`。涉及表：可复用 `audit_event`、`tool_call`、`model_call`、`run_artifact`，并可新增事件缓存实现。

#### Scenario: 订阅运行事件
- **WHEN** 客户端订阅正在运行的 run 事件流
- **THEN** 系统持续推送运行状态、模型调用、工具调用和最终结果事件

#### Scenario: 运行结束关闭事件流
- **WHEN** run 进入终态
- **THEN** 系统推送 `run_finished` 事件并结束 SSE 流

### Requirement: 事件类型
系统 SHALL 定义稳定事件类型，包括 `run_started`、`model_call_started`、`model_call_completed`、`tool_call_started`、`tool_call_completed`、`audit_event`、`file_changed`、`test_reported`、`git_committed`、`pr_draft_generated`、`run_finished`。涉及 API：SSE 事件流。涉及表：`agent_run`、`model_call`、`tool_call`、`audit_event`、`run_artifact`。

#### Scenario: 文件变更事件
- **WHEN** 工具成功新增、修改、覆盖或删除文件
- **THEN** 系统推送 `file_changed` 事件，payload 包含 relativePath 和 changeType

#### Scenario: Git commit 事件
- **WHEN** 本地 commit 成功
- **THEN** 系统推送 `git_committed` 事件，payload 包含 branch 和 commitHash

### Requirement: SSE 重连补偿
系统 SHALL 支持客户端断线后重新拉取 run trace 或历史事件以补齐展示。涉及 API：`GET /api/agent-runs/{runId}/trace`、`GET /api/agent-runs/{runId}/events`。涉及表：`audit_event`、`tool_call`、`model_call`、`run_artifact`。

#### Scenario: 客户端断线后恢复
- **WHEN** 客户端 SSE 连接中断后重新进入 run 详情页
- **THEN** 客户端先拉取 trace 或历史事件，再重新订阅 SSE

#### Scenario: 已完成运行查看事件
- **WHEN** 客户端打开已完成 run
- **THEN** 系统返回历史 trace，使客户端能展示完整运行过程
