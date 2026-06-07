## ADDED Requirements

### Requirement: 自动文件摘要生成
系统 SHALL 在文件被读取、搜索命中或摘要 stale 时，按预算自动生成文件摘要。摘要生成 MUST 跳过受保护路径和疑似敏感文件。涉及工具：`read_file`、`search_text`。涉及表：`agent_memory_item`、`memory_chunk`。

#### Scenario: 读取无摘要文件
- **WHEN** Agent 读取 workspace 内普通文本文件且该文件没有有效摘要
- **THEN** 系统 SHALL 在预算允许时生成摘要、写入记忆并记录 trace

### Requirement: 摘要预算控制
系统 MUST 限制每次 run 的自动摘要文件数、embedding 调用数、摘要输入字节数、摘要耗时和摘要模型调用数。超限时 SHALL 跳过摘要生成，不阻断主任务。涉及配置：`MEMORY_MAX_*`。涉及表：`audit_event`、`model_call`。

#### Scenario: 摘要预算耗尽
- **WHEN** 当前 run 的自动摘要调用达到上限
- **THEN** 系统 SHALL 跳过后续自动摘要，并记录 skipped reason

### Requirement: 会话与运行摘要
系统 SHALL 在会话消息超过阈值或 Agent Run 结束时生成会话摘要、任务摘要和运行摘要，并写入结构化记忆。涉及 API：`/api/conversations/{conversationId}/messages`、`GET /api/agent-runs/{runId}`。涉及表：`agent_memory_item`、`agent_message`、`agent_run`。

#### Scenario: 运行成功后生成运行摘要
- **WHEN** Agent Run 进入 SUCCEEDED
- **THEN** 系统 SHALL 生成运行摘要，包含任务、关键文件、工具结果、最终结论和后续注意事项

### Requirement: 摘要敏感信息脱敏
系统 MUST 在保存摘要、embedding 输入、trace 和 context snapshot 前执行敏感信息脱敏。涉及表：`agent_memory_item`、`memory_chunk`、`audit_event`。

#### Scenario: 文件内容包含疑似密钥
- **WHEN** 摘要输入包含 token、password、credential 或私钥特征
- **THEN** 系统 MUST 脱敏或拒绝摘要，并记录安全审计事件

