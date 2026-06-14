## ADDED Requirements

### Requirement: 模型配置页面
客户端 SHALL 提供模型配置管理页面，支持新增、编辑、删除、启用、默认模型和上下文预算配置。

#### Scenario: 保存模型配置
- **WHEN** 用户保存并启用模型配置
- **THEN** 客户端 MUST 刷新模型候选项

### Requirement: 无模型空态
客户端 MUST 在无启用模型时显示“请配置模型”，并禁用发送任务。

#### Scenario: 启动时无模型
- **WHEN** 后端返回空模型列表
- **THEN** 模型选择器 SHALL 展示“请配置模型”，点击后跳转配置页面

### Requirement: 流式消息渲染
客户端 SHALL 消费 assistant delta 事件并实时追加到当前 Agent 消息。

#### Scenario: 接收 assistant_delta
- **WHEN** SSE 推送 assistant_delta
- **THEN** 客户端 MUST 在当前 Agent 消息中追加文本

### Requirement: 审批弹窗
客户端 SHALL 在 run 进入 `WAITING_APPROVAL` 时展示审批弹窗。

#### Scenario: 高风险工具等待审批
- **WHEN** 后端返回待审批请求
- **THEN** 客户端 SHALL 展示工具名、参数、风险说明和批准/拒绝按钮

### Requirement: 运行指标展示
客户端 SHALL 在运行详情侧栏展示 context tokens、compression ratio、memory hit、stale memory、embedding calls 和 snapshot 路径。

#### Scenario: 查询 run 详情
- **WHEN** 当前 run 包含上下文指标
- **THEN** 客户端 SHALL 展示指标而不占用对话正文

### Requirement: 运行中消息草稿恢复
客户端 SHALL 在切换会话、刷新运行状态或 SSE 重新连接时恢复当前 run 的已输出草稿。

#### Scenario: 恢复运行中消息草稿
- **WHEN** 用户切换会话、刷新运行状态或 SSE 重新连接
- **THEN** 客户端 SHALL 查询当前 run 的草稿内容并恢复已输出文本，不得把已有 chunk 覆盖为单独的“思考中...”。

### Requirement: 重跑清理旧运行链路
系统 MUST 在用户修改历史消息并重跑时清理该消息原 runId 对应的完整运行链路，避免旧上下文、记忆和审批污染新 run。

#### Scenario: 修改用户消息并重跑
- **GIVEN** 用户消息已关联旧 runId
- **WHEN** 用户修改该消息并发起重跑
- **THEN** 系统 MUST 删除旧 runId 对应的 run、step、model_call、tool_call、audit_event、artifact、approval、context snapshot、MySQL memory 和 pgvector chunk
- **AND** 新 run MUST 只关联更新后的用户消息和新上下文。
