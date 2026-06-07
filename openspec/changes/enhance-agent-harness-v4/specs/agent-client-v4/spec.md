## ADDED Requirements

### Requirement: 模型配置管理界面
客户端 SHALL 提供模型配置管理界面，支持新增、编辑、删除、启用和测试 chat 模型配置。涉及 API：`/api/model-providers`。

#### Scenario: 用户新增模型配置
- **WHEN** 用户在客户端填写模型 baseUrl、apiKey、modelName、endpointType 和预算
- **THEN** 客户端 SHALL 调用后端保存配置并展示脱敏后的结果

### Requirement: 启动时加载可用模型列表
客户端 SHALL 在启动并连接后端后读取已启用的 chat 模型配置，并将返回结果作为对话输入区的唯一模型候选来源。客户端不得显示第三版固定模型候选项。涉及 API：`GET /api/model-providers`。

#### Scenario: 已存在模型配置
- **WHEN** 客户端启动且后端返回一个或多个已启用模型配置
- **THEN** 对话输入区模型选择器 SHALL 展示这些已启用模型，并默认选中用户上次选择或后端默认模型

#### Scenario: 没有保存模型配置
- **WHEN** 客户端启动且后端没有返回任何已启用模型配置
- **THEN** 对话输入区模型选择器 SHALL 只展示“请配置模型”，且发送任务按钮 MUST 处于不可用状态

#### Scenario: 点击请配置模型
- **WHEN** 用户点击模型选择器中的“请配置模型”
- **THEN** 客户端 SHALL 跳转到模型配置管理界面

#### Scenario: 保存模型后刷新候选项
- **WHEN** 用户在模型配置管理界面成功保存并启用模型配置
- **THEN** 客户端 SHALL 重新读取模型列表，并在对话输入区展示新保存的模型候选项

### Requirement: 模型文本流式展示
客户端 SHALL 消费 assistant_delta SSE 事件并实时追加到当前 Agent 消息。工具事件不得作为对话正文长期保留。涉及 API：`GET /api/agent-runs/{runId}/events`、`/api/conversations/{conversationId}/messages`。

#### Scenario: Agent 正在回复
- **WHEN** 后端推送 assistant_delta
- **THEN** 客户端 SHALL 在当前 Agent 消息中实时展示新增文本并保持底部自动跟随

### Requirement: 高风险审批弹窗
客户端 SHALL 在 run 进入 WAITING_APPROVAL 时展示审批弹窗，包含工具名、参数、风险说明、文件路径、diff 摘要和批准/拒绝按钮。涉及 API：`/api/tool-approvals/{approvalId}/approve`、`/api/tool-approvals/{approvalId}/reject`。

#### Scenario: 用户批准审批
- **WHEN** 用户点击批准
- **THEN** 客户端 SHALL 调用批准 API，并将 run 状态恢复为运行中展示

### Requirement: 当前 Run 上下文与记忆指标展示
客户端 SHALL 在当前运行详情中展示 context tokens、compression ratio、memory hit、stale memory、embedding calls 和 snapshot 工件路径。涉及 API：`GET /api/agent-runs/{runId}`。

#### Scenario: 运行完成后查看指标
- **WHEN** 用户查看已完成 Agent Run
- **THEN** 客户端 SHALL 展示本次运行的上下文治理和记忆召回指标
