## ADDED Requirements

### Requirement: 结构化审计记录

系统 SHALL 记录模型调用、工具调用、状态变更、安全拒绝和异常事件，支持后续 trace 查询和回放。

涉及 API：`GET /api/agent-runs/{runId}/trace`  
涉及表：`agent_step`、`model_call`、`tool_call`、`audit_event`

#### Scenario: 记录模型调用

- **GIVEN** Agent 准备调用模型
- **WHEN** 模型调用完成
- **THEN** 系统记录请求摘要、响应摘要、模型名称、耗时和结果状态

#### Scenario: 记录工具调用

- **GIVEN** Agent 执行一个工具调用
- **WHEN** 工具调用完成或被拒绝
- **THEN** 系统记录工具名称、参数摘要、结果摘要、退出码、耗时和状态

#### Scenario: 记录安全拒绝

- **GIVEN** 路径边界或 Shell 策略拒绝一次工具调用
- **WHEN** 系统生成拒绝结果
- **THEN** 系统写入 `audit_event`
- **AND** trace 中包含拒绝原因

### Requirement: 运行工件落盘

系统 MUST 为每次运行在 `{workspaceRoot}/.coder/runs/{runId}/` 创建独立工件目录，并保存 `run-meta.json`、`trace.jsonl`、`context-snapshot/*.json`、`tool-output/*.txt`、`final-result.json`。

涉及 API：`POST /api/agent-runs`、`GET /api/agent-runs/{runId}`、`GET /api/agent-runs/{runId}/trace`  
涉及表：`run_artifact`

#### Scenario: 创建运行元信息

- **GIVEN** 新运行创建成功
- **WHEN** 系统初始化工件目录
- **THEN** 系统在 `{workspaceRoot}/.coder/runs/{runId}/` 写入 `run-meta.json`
- **AND** 在 `run_artifact` 中记录工件索引

#### Scenario: 追加 trace 事件

- **GIVEN** 运行过程中产生模型调用、工具调用或状态变更事件
- **WHEN** 系统记录事件
- **THEN** 系统向 `trace.jsonl` 追加一行 JSON 事件
- **AND** 每行事件可独立解析

#### Scenario: 保存最终结果

- **GIVEN** 运行进入终态
- **WHEN** 系统汇总运行结果
- **THEN** 系统写入 `final-result.json`
- **AND** 文件包含最终结论、状态、attempts、tool_steps、model_calls、tool_calls 和 duration

### Requirement: 上下文快照与工具长输出

系统 SHALL 在模型调用前保存摘要版上下文快照，并将较长工具输出写入 `tool-output/*.txt`。

涉及 API：`GET /api/agent-runs/{runId}/trace`  
涉及表：`model_call`、`tool_call`、`run_artifact`

#### Scenario: 保存上下文快照

- **GIVEN** Agent 准备发起模型调用
- **WHEN** 上下文组装完成
- **THEN** 系统保存摘要版 `context-snapshot/*.json`
- **AND** 快照不得包含无限制的完整长工具输出

#### Scenario: 保存长工具输出

- **GIVEN** 工具输出超过上下文内联阈值
- **WHEN** 系统处理工具结果
- **THEN** 系统将完整输出写入 `tool-output/*.txt`
- **AND** 数据库和模型上下文只保存摘要与工件路径
