## ADDED Requirements

### Requirement: 文件摘要生成
系统 SHALL 在文件读取、搜索命中或文件 stale 时按预算生成文件摘要。

#### Scenario: 读取代码文件
- **WHEN** Agent 工具读取一个允许摘要的代码文件
- **THEN** 系统 SHALL 在预算允许时生成结构化文件摘要并保存记忆

### Requirement: 摘要安全过滤
系统 MUST 跳过 `.env`、`.git/`、`.coder/`、`target/`、密钥和疑似敏感文件。

#### Scenario: 命中敏感文件
- **WHEN** 摘要候选文件属于敏感路径或疑似密钥文件
- **THEN** 系统 MUST 拒绝生成摘要，并记录跳过原因

### Requirement: 运行结束摘要
系统 SHALL 在运行结束后生成任务摘要和运行摘要，并在预算允许时向量化。

#### Scenario: Agent Run 成功结束
- **WHEN** Agent Run 进入终态
- **THEN** 系统 SHALL 保存运行摘要，并将其作为后续同 workspace 任务可召回记忆

### Requirement: 摘要脱敏
系统 MUST 对摘要输入、摘要结果、embedding 输入、trace 和 snapshot 执行敏感信息脱敏。

#### Scenario: 摘要内容包含密钥形态文本
- **WHEN** 摘要内容包含 API Key、token、password 或 private key
- **THEN** 系统 MUST 在入库和写工件前脱敏
