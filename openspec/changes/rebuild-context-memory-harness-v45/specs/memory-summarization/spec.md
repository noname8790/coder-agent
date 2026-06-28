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

### Requirement: 内部摘要不得保存长篇可见回复或源码全文
系统 MUST 在任务结束、文件读取和文件变更后生成用于系统内部的结构化摘要。该摘要 MUST 优先由当前任务模型生成，模型不可用或输出不合规时 MUST 回退规则摘要。内部摘要 MUST NOT 直接保存用户可见最终回复全文、文件全文或变更后源码全文。

#### Scenario: 任务结束生成内部上下文摘要
- **GIVEN** Agent 完成一次长回复任务
- **WHEN** 系统写入 RUN_SUMMARY
- **THEN** 系统 MUST 保存用户目标、完成事项、涉及文件、验证结果、后续事项和风险等结构化字段
- **AND** MUST NOT 直接保存完整 finalAnswer 原文
- **AND** 后续同一会话的旧消息压缩 MUST 优先使用该 RUN_SUMMARY 进入 `<context>`

#### Scenario: 文件变更生成内部记忆摘要
- **GIVEN** Agent 使用写入、覆盖、补丁或删除工具修改文件
- **WHEN** 系统写入 TOOL_EDIT 记忆
- **THEN** 系统 MUST 保存 path、change_type、hash、summary、verification 和 risk 等结构化字段
- **AND** MUST NOT 直接保存变更后源码全文
