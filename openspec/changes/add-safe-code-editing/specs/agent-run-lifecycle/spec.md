## ADDED Requirements

### Requirement: Create run with execution mode

系统 SHALL 允许调用方在创建 Agent Run 时传入 `mode`，并在运行记录和查询结果中保留该模式。涉及 API：`POST /api/agent-runs`、`GET /api/agent-runs/{runId}`。涉及表：`agent_run`。

#### Scenario: 查询运行返回 mode

- **WHEN** 调用方查询某个 Agent Run
- **THEN** 响应 MUST 包含该运行的 `mode`
- **AND** 若运行产生变更工件，响应 MUST 包含对应工件索引

### Requirement: Edit mode requires active workspace

系统 MUST 在创建 Agent Run 时校验 workspace 是否存在且 active。涉及 API：`POST /api/agent-runs`。涉及表：`agent_workspace`、`agent_run`。

#### Scenario: 使用 active workspace 创建运行

- **WHEN** 调用方使用 active workspaceKey 创建 Agent Run
- **THEN** 系统 MUST 允许创建运行
- **AND** Agent MUST 在该 workspaceRoot 内执行

#### Scenario: 使用 missing workspace 创建运行

- **WHEN** 调用方使用不存在的 workspaceKey 创建 Agent Run
- **THEN** 系统 MUST 拒绝创建
- **AND** 系统 MUST 不写入 `agent_run`

### Requirement: Final result includes edit summary

系统 SHALL 在 Agent Run 终态结果中记录编辑摘要，包括是否发生文件变更、变更文件数量、测试执行状态和审查摘要路径。涉及 API：`GET /api/agent-runs/{runId}`。涉及工件：`final-result.json`、`changed-files.json`、`test-report.json`、`review-summary.md`。

#### Scenario: 编辑运行成功结束

- **WHEN** `EDIT` 模式运行完成且发生文件变更
- **THEN** 最终结果 MUST 包含变更摘要
- **AND** 查询接口 MUST 能返回相关工件索引

#### Scenario: 只读运行无变更

- **WHEN** `READ_ONLY` 模式运行完成
- **THEN** 最终结果 MUST 标记未发生文件变更
- **AND** 系统 MUST NOT 生成 patch diff
