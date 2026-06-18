## ADDED Requirements

### Requirement: 会话级模型选择恢复
客户端 MUST 在打开或切换 conversation 时读取后端返回的 `lastModelKey` 并设置为当前模型选择。若该会话没有 lastModelKey，客户端 MUST 使用启用模型列表排名最上方的模型。涉及 API：conversation 列表/详情、model providers 列表。

#### Scenario: 打开已有会话恢复模型
- **WHEN** 用户打开保存了 `lastModelKey=glm-4.5-air` 的 conversation
- **THEN** 客户端模型选择器 MUST 显示 `glm-4.5-air`

#### Scenario: 新会话使用模型列表第一项
- **WHEN** 用户创建新 conversation 且启用模型列表非空
- **THEN** 客户端 MUST 默认选择模型列表排名最上方的模型

#### Scenario: 无启用模型显示配置提示
- **WHEN** 启用模型列表为空
- **THEN** 客户端模型选择器 MUST 显示“请配置模型”，并禁止发送 Agent 任务

### Requirement: 切换模型持久化到会话
客户端 MUST 在用户切换当前 conversation 的模型后调用后端保存该会话的 `lastModelKey`。创建 Agent Run 时若请求未显式传 model，后端 MUST 使用 conversation 的 `lastModelKey`。涉及 API：更新 conversation、创建 Agent Run。涉及表：`agent_conversation`、`agent_run`。

#### Scenario: 保存会话最后模型
- **WHEN** 用户在 conversation A 中选择模型 B
- **THEN** 系统 MUST 保存 conversation A 的 `lastModelKey=B`

#### Scenario: 不同会话模型互不覆盖
- **WHEN** conversation A 保存模型 B，conversation C 保存模型 D
- **THEN** 用户在 A 和 C 之间切换时客户端 MUST 分别恢复 B 和 D

### Requirement: Checkpoint 折叠边界 UI
客户端 MUST 对已回滚的历史消息显示清晰折叠边界，并在展开时保留视觉上的历史审计区域，不得与当前有效对话混排。涉及页面：对话消息列表。

#### Scenario: 折叠边界可识别
- **WHEN** 当前 conversation 包含已回滚消息
- **THEN** 客户端 MUST 使用独立边界展示这些消息已不参与后续上下文

#### Scenario: 展开后不影响当前滚动焦点
- **WHEN** 用户展开已回滚历史消息
- **THEN** 客户端 SHALL 保持当前有效对话和输入区可继续使用，不得把展开内容作为新消息流状态
