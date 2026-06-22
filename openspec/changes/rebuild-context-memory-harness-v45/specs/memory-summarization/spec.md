## ADDED Requirements

### Requirement: 摘要准入与晋升
系统 MUST 根据证据类型和可信度决定摘要是否可写入项目级、文件级、任务级或运行级记忆。

#### Scenario: 项目惯例晋升
- **GIVEN** 多次 run 的工具结果均显示项目使用 Maven 测试命令
- **WHEN** 系统生成项目级摘要
- **THEN** 系统 MUST 将该摘要作为可晋升的 PROJECT_MEMORY 写入
- **AND** MUST 保存关联 tool_call_id 或 run_id 证据

### Requirement: 文件摘要包含 freshness 指纹
系统 MUST 为 FILE_MEMORY 保存 path、language、symbols、summary、hash、workspaceKey、createdRunId 和 evidenceRefs。

#### Scenario: 生成文件摘要
- **GIVEN** Agent 读取 `Calculator.java`
- **WHEN** 文件大小在摘要预算内
- **THEN** 系统 MUST 生成 FILE_MEMORY
- **AND** MUST 写入 hash 以支持后续 freshness 校验
