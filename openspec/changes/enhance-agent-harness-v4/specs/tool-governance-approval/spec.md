## ADDED Requirements

### Requirement: 工具参数 Schema 校验
系统 MUST 在执行任意工具前基于工具 schema 校验工具名称、参数类型、必填字段、路径边界和权限等级。涉及工具：所有 Agent 工具。涉及表：`tool_call`、`audit_event`。

#### Scenario: 工具参数缺失
- **WHEN** 模型请求调用工具但缺少必填参数
- **THEN** 系统 MUST 拒绝执行工具并记录结构化拒绝原因

### Requirement: 重复工具调用拦截
系统 SHALL 识别同一 run 内短时间重复的等价工具调用，并按策略返回缓存结果或拒绝重复调用。涉及工具：`read_file`、`search_text`、`run_shell`、文件写入工具。涉及表：`tool_call`、`audit_event`。

#### Scenario: 重复读取同一未变化文件
- **WHEN** Agent 重复读取同一路径且文件 freshness 未变化
- **THEN** 系统 SHALL 优先返回已读片段或摘要，并记录重复调用拦截

### Requirement: 高风险工具人工审批
系统 MUST 对 overwrite_file、delete_file、git commit 等高风险工具创建审批请求，并将 run 状态置为 WAITING_APPROVAL。涉及 API：`POST /api/tool-approvals/{approvalId}/approve`、`POST /api/tool-approvals/{approvalId}/reject`。涉及表：`tool_approval_request`、`agent_run`、`audit_event`。

#### Scenario: 用户批准高风险工具
- **WHEN** 用户批准等待中的工具审批请求
- **THEN** 系统 MUST 恢复 Agent Run 并执行该工具

#### Scenario: 用户拒绝高风险工具
- **WHEN** 用户拒绝等待中的工具审批请求
- **THEN** 系统 MUST 将拒绝结果返回 Agent，并允许 Agent 继续规划或结束

### Requirement: 敏感信息脱敏
系统 MUST 对工具参数、工具输出、审计事件、trace、SSE 事件和最终结果中的敏感信息执行脱敏。涉及表：`tool_call`、`audit_event`、`run_artifact`。

#### Scenario: Shell 输出包含密钥
- **WHEN** run_shell 输出中包含疑似密钥或 token
- **THEN** 系统 MUST 在展示和入库前脱敏，完整敏感内容不得进入 prompt

