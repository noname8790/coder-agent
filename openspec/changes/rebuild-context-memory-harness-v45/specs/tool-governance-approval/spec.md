## ADDED Requirements

### Requirement: 工具结果成为上下文证据
系统 MUST 将成功、失败、拒绝、超时和审批结果统一转换为 ToolObservation，并可作为上下文候选和记忆 evidence。

#### Scenario: 工具失败反馈给模型
- **GIVEN** `run_shell` 因超时失败
- **WHEN** 下一轮模型调用装配 prompt
- **THEN** 系统 MUST 注入结构化失败原因、命令、exitCode、timeoutMs 和可尝试的下一步约束

### Requirement: 低可信记忆不得绕过工具治理
系统 MUST 在执行高风险工具前检查依据来源。如果依据来自低可信记忆，系统 MUST 要求 Agent 获取当前 workspace 的新证据。

#### Scenario: 删除文件前证据不足
- **GIVEN** 召回记忆声称某文件可删除但 freshness 未通过
- **WHEN** Agent 调用 `delete_file`
- **THEN** 工具治理层 MUST 拒绝执行
- **AND** 返回结构化拒绝原因，要求先读取或列出当前文件
