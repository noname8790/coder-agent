## ADDED Requirements

### Requirement: 工具参数治理
系统 MUST 在工具执行前校验必填参数、参数类型、路径边界、权限等级和敏感路径。

#### Scenario: 工具参数缺失
- **WHEN** Agent 请求执行缺少必填参数的工具
- **THEN** 系统 MUST 拒绝执行并记录治理事件

### Requirement: 重复调用拦截
系统 SHALL 识别同一 run 内重复读文件、重复搜索、重复 shell 和重复写入等工具调用。

#### Scenario: 重复工具调用
- **WHEN** Agent 在同一 run 中提交等价工具调用
- **THEN** 系统 SHALL 拦截或提示复用已有结果

### Requirement: 敏感信息脱敏
系统 MUST 对工具参数、工具输出、trace、SSE 和 final-result 中的敏感信息脱敏。

#### Scenario: 工具输出包含 token
- **WHEN** 工具输出包含 token、password、api_key 或 private key
- **THEN** 系统 MUST 在外显和入库前脱敏

### Requirement: 高风险工具审批
系统 SHALL 对 overwrite_file、delete_file、git commit 等高风险工具创建审批请求，并将 run 切换为 `WAITING_APPROVAL`。

#### Scenario: 审批通过
- **WHEN** 用户批准高风险工具请求
- **THEN** 系统 MUST 将 run 恢复为 `RUNNING` 并继续执行

#### Scenario: 审批拒绝
- **WHEN** 用户拒绝高风险工具请求
- **THEN** 系统 MUST 将结构化拒绝结果回灌给 Agent，而不是直接丢弃上下文

### Requirement: 高风险审批幂等
系统 MUST 对同一 run 内相同工具和规范化参数的高风险审批请求做幂等处理，避免重复创建审批记录。

#### Scenario: 同一删除请求已处于待审批
- **GIVEN** run 内已经存在相同 toolName 和 argumentsJson 的 PENDING 审批记录
- **WHEN** Agent 再次请求同一高风险工具
- **THEN** 系统 MUST 复用既有审批记录并保持 run 为 WAITING_APPROVAL
- **AND** 系统 MUST NOT 创建新的重复审批记录。
