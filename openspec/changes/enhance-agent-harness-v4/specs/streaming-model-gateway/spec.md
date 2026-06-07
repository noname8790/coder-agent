## ADDED Requirements

### Requirement: 统一流式模型事件
系统 SHALL 将 Chat Completions streaming 和 Responses API streaming 转换为统一内部事件，包括 assistant_message_started、assistant_delta、tool_call_started、tool_call_arguments_delta、tool_call_completed、model_completed 和 model_failed。涉及 API：`GET /api/agent-runs/{runId}/events`。涉及表：`model_call`、`agent_message`、`audit_event`。

#### Scenario: 模型文本增量输出
- **WHEN** 模型通过任一协议返回文本 delta
- **THEN** 系统 MUST 发布 assistant_delta SSE 事件，并累积到当前 Agent 消息

### Requirement: 流式模型调用为 Agent Run 主流程
系统 MUST 使用流式模型调用驱动 Agent Run 的可见回复。模型配置未启用 streaming 或 endpoint 不支持 streaming 时，系统 MUST 拒绝创建或执行该运行，不得回退到第三版非流式完整响应。涉及 API：`POST /api/agent-runs`、`GET /api/agent-runs/{runId}/events`。涉及表：`agent_model_provider`、`model_call`、`agent_message`。

#### Scenario: 模型未启用 streaming
- **WHEN** 用户选择的模型配置 `streamingEnabled=false`
- **THEN** 系统 MUST 拒绝创建 Agent Run，并返回模型不支持 v4 流式运行的错误原因

#### Scenario: 流式连接失败
- **WHEN** 模型 endpoint 无法建立 streaming 连接
- **THEN** 系统 MUST 将 Agent Run 标记为 FAILED，并记录失败原因，不得改用非流式请求重试

### Requirement: Chat Completions Streaming
系统 SHALL 支持 OpenAI-compatible Chat Completions streaming，包括文本 delta 和 tool call 参数增量累积。涉及模型 endpoint：`/v1/chat/completions`。涉及表：`model_call`、`tool_call`。

#### Scenario: Chat Completions 工具调用流式累积
- **WHEN** Chat Completions streaming 返回 tool_calls delta
- **THEN** 系统 MUST 累积完整工具名称和参数，并在参数完整后进入工具校验流程

### Requirement: Responses API Streaming
系统 SHALL 支持 OpenAI Responses API streaming 作为模型调用协议，不使用 previous_response_id、store 或服务端 hosted state。涉及模型 endpoint：`/v1/responses`。涉及表：`model_call`、`tool_call`。

#### Scenario: Responses 流式输出
- **WHEN** endpointType 为 responses 且模型返回 response.output_text.delta
- **THEN** 系统 MUST 转换为 assistant_delta，并由本地 Harness 管理上下文和状态

### Requirement: 流式消息持久化
系统 MUST 在运行中保存 Agent partial message，并在成功、失败或取消时保存完整可见消息。涉及 API：`/api/conversations/{conversationId}/messages`、`/api/agent-runs/{runId}/cancel`。涉及表：`agent_message`。

#### Scenario: 用户取消运行
- **WHEN** 用户取消正在流式输出的 Agent Run
- **THEN** 系统 MUST 保存已输出的 Agent 文本，并追加可识别的取消结果反馈

## REMOVED Requirements

### Requirement: 非流式一键结果输出
**Reason**: 第三版等待模型调用完成后一次性写入 Agent 消息，导致客户端无法看到真实模型输出进度，也无法在取消时保留已输出内容。v4 以流式 delta 作为对话主体验。

**Migration**: 所有 chat 模型配置必须声明并验证 streaming 能力。旧非流式网关和运行结束后一键写消息流程应从 v4 主链路中移除。

#### Scenario: 不再一次性返回完整 Agent 消息
- **WHEN** Agent Run 正在执行模型调用
- **THEN** 系统 MUST 通过 assistant_delta 增量更新 Agent 消息，而不是等待模型完成后一次性写入完整结果
