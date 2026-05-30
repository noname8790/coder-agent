## ADDED Requirements

### Requirement: 异步创建 Agent 运行

系统 SHALL 通过 REST API 创建一次异步 Agent 运行，并立即返回 `runId`，后台执行不得阻塞 HTTP 请求直到任务结束。

涉及 API：`POST /api/agent-runs`  
涉及表：`agent_run`

#### Scenario: 创建运行成功

- **GIVEN** 请求体包含有效的 `workspaceKey`、`task` 和可用模型配置
- **WHEN** 客户端调用 `POST /api/agent-runs`
- **THEN** 系统创建 `agent_run` 记录，初始状态为 `CREATED` 或 `RUNNING`
- **AND** 响应返回 `runId`、当前状态和创建时间

#### Scenario: workspaceKey 不存在

- **GIVEN** 请求体包含未配置的 `workspaceKey`
- **WHEN** 客户端调用 `POST /api/agent-runs`
- **THEN** 系统拒绝创建运行
- **AND** 不启动后台执行

### Requirement: 运行状态机

系统 MUST 使用明确的运行状态机管理任务生命周期：`CREATED`、`RUNNING`、`SUCCEEDED`、`FAILED`、`CANCELLED`、`REJECTED`。

涉及 API：`GET /api/agent-runs/{runId}`、`POST /api/agent-runs/{runId}/cancel`  
涉及表：`agent_run`、`agent_step`

#### Scenario: 运行完成

- **GIVEN** 后台任务完成模型循环并生成最终结果
- **WHEN** 系统持久化最终结果
- **THEN** `agent_run` 状态变更为 `SUCCEEDED`
- **AND** 系统记录结束时间和耗时

#### Scenario: 运行失败

- **GIVEN** 模型调用、工具执行或系统处理发生不可恢复异常
- **WHEN** 后台执行捕获异常
- **THEN** `agent_run` 状态变更为 `FAILED`
- **AND** 系统记录失败原因和异常摘要

#### Scenario: 运行取消

- **GIVEN** 某个运行处于 `CREATED` 或 `RUNNING`
- **WHEN** 客户端调用 `POST /api/agent-runs/{runId}/cancel`
- **THEN** 系统将运行标记为 `CANCELLED`
- **AND** 后台执行循环在下一个安全检查点停止

### Requirement: 执行预算限制

系统 MUST 对每次运行应用默认预算：`max_steps=20`、`max_model_calls=8`、`max_tool_calls=16`、`timeout_seconds=300`，并限制首版最大并发运行数为 2。

涉及 API：`POST /api/agent-runs`、`GET /api/agent-runs/{runId}`  
涉及表：`agent_run`、`agent_step`、`model_call`、`tool_call`

#### Scenario: 超过模型调用次数

- **GIVEN** 某次运行已经达到 `max_model_calls`
- **WHEN** 执行循环准备再次调用模型
- **THEN** 系统停止执行循环
- **AND** 将运行标记为 `FAILED` 或带预算耗尽原因的终止状态

#### Scenario: 超时停止

- **GIVEN** 某次运行执行时间超过 `timeout_seconds`
- **WHEN** 后台执行循环检查预算
- **THEN** 系统停止后续模型和工具调用
- **AND** 记录预算耗尽事件

#### Scenario: 超过并发运行数

- **GIVEN** 系统已有 2 个处于 `CREATED` 或 `RUNNING` 状态的运行
- **WHEN** 客户端继续调用 `POST /api/agent-runs`
- **THEN** 系统拒绝创建新的后台运行
- **AND** 响应包含并发限制原因

### Requirement: 查询运行状态

系统 SHALL 支持通过 `runId` 查询运行状态、任务摘要、预算使用情况和最终结果摘要。

涉及 API：`GET /api/agent-runs/{runId}`  
涉及表：`agent_run`、`run_artifact`

#### Scenario: 查询存在的运行

- **GIVEN** 数据库中存在指定 `runId`
- **WHEN** 客户端调用 `GET /api/agent-runs/{runId}`
- **THEN** 系统返回运行状态、workspaceKey、任务摘要、模型、预算使用情况和最终结果摘要

#### Scenario: 查询不存在的运行

- **GIVEN** 数据库中不存在指定 `runId`
- **WHEN** 客户端调用 `GET /api/agent-runs/{runId}`
- **THEN** 系统返回未找到错误
