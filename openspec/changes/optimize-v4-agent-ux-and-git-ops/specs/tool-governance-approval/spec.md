## ADDED Requirements

### Requirement: 权限驱动的审批策略
系统 MUST 根据新权限等级决定是否创建审批请求。`DEFAULT` 对高风险工具创建审批请求；`FULL_ACCESS` 对高风险工具不创建审批请求但写审计；`READ_ONLY` 直接拒绝写入类和执行类高风险工具。涉及 API：审批查询、审批通过、审批拒绝。涉及表：`tool_approval_request`、`tool_call`、`audit_event`。

#### Scenario: 默认档创建审批
- **WHEN** `DEFAULT` run 请求执行高风险工具 `git_commit`
- **THEN** 系统 MUST 创建审批请求并暂停 run

#### Scenario: 完全控制绕过审批
- **WHEN** `FULL_ACCESS` run 请求执行高风险工具 `git_commit`
- **THEN** 系统 MUST 不创建审批请求，直接执行并写入 `audit_event`

#### Scenario: 只读拒绝高风险工具
- **WHEN** `READ_ONLY` run 请求执行高风险工具 `overwrite_file`
- **THEN** 系统 MUST 拒绝工具调用并把拒绝原因作为工具结果回灌给 Agent

### Requirement: 审批幂等继续执行
系统 MUST 对同一 run 内相同工具和规范化参数的审批请求做幂等处理。审批通过后，Harness MUST 继续执行已批准的原工具调用，并把执行结果写入 `tool_call`；不得让 Agent 反复请求同一审批。涉及表：`tool_approval_request`、`tool_call`、`agent_step`。

#### Scenario: 重复审批请求复用
- **WHEN** 同一 run 再次请求相同 `delete_file` 参数且已有 PENDING 审批
- **THEN** 系统 MUST 复用已有审批请求，不新增记录

#### Scenario: 审批通过后执行原工具
- **WHEN** 用户批准 `delete_file` 请求
- **THEN** 系统 MUST 执行原 `delete_file` 工具，记录 `tool_call=SUCCESS` 或 `FAILED`，并推进下一步

### Requirement: 完全控制审计
系统 MUST 对 `FULL_ACCESS` 下所有本应审批的操作写入审计事件，事件 MUST 包含 runId、workspaceKey、permissionLevel、toolName、argumentsDigest、riskLevel、decision=`BYPASSED_BY_FULL_ACCESS`。涉及表：`audit_event`。

#### Scenario: 记录完全控制绕过审批事件
- **WHEN** `FULL_ACCESS` run 执行 `delete_file`
- **THEN** 系统 MUST 写入一条绕过审批审计事件
