## MODIFIED Requirements

### Requirement: 流式输出主流程
系统 MUST 使用 streaming delta 作为 Agent 正文输出主流程，取代 v3 运行结束后一次性写入结果的非流式模式。

#### Scenario: 模型返回文本 delta
- **WHEN** 模型网关接收到文本 delta
- **THEN** 系统 MUST 追加到当前 assistant 消息，并通过 SSE 推送 `assistant_delta`

### Requirement: Chat Completions Streaming
系统 SHALL 支持 OpenAI-compatible Chat Completions streaming，并解析文本 delta、tool_calls delta、finish 和异常。

#### Scenario: Chat Completions 返回 tool_calls delta
- **WHEN** 模型返回 tool call 参数片段
- **THEN** 系统 MUST 组装完整 tool call 参数后再执行工具

### Requirement: Responses API Streaming
系统 SHALL 支持 Responses API streaming 协议，但不得使用 `previous_response_id`、`store` 或服务端 hosted state。

#### Scenario: Responses API 流式响应
- **WHEN** 模型返回 Responses API 事件
- **THEN** 系统 MUST 转换为内部 ModelStreamEvent

### Requirement: 流式失败不回退非流式
系统 MUST 在 streaming 失败时记录失败并终止或重试受控流程，不得回退到非流式请求。

#### Scenario: streaming 连接失败
- **WHEN** 模型 streaming 请求失败
- **THEN** 系统 MUST 记录 `model_stream_failure`，不得发起非流式模型请求

### Requirement: 运行中草稿恢复与推理片段过滤
系统 MUST 只把用户可见 assistant delta 推送给客户端，并 SHALL 为运行中的可见回复提供草稿查询能力。

#### Scenario: 隐藏模型推理片段
- **WHEN** Chat Completions streaming 返回 `<think>...</think>` 或推理专用内容
- **THEN** 系统 MUST 过滤推理片段，不得作为 `assistant_delta` 推送给客户端

#### Scenario: 刷新后恢复运行中草稿
- **WHEN** Agent Run 尚未终态且客户端刷新或切换会话
- **THEN** 系统 SHALL 提供当前 run 的可见回复草稿，供客户端恢复已输出 delta

### Requirement: 多轮工具调用草稿恢复
系统 MUST 将工具调用前的模型计划文本写入当前 run 的运行中草稿，并在 run 终态前持续提供恢复能力。

#### Scenario: 新一轮模型调用开始
- **GIVEN** 上一轮模型调用输出了可见草稿并请求工具
- **WHEN** 下一轮模型调用开始
- **THEN** 系统 MUST 保留当前 run 已输出的可见草稿
- **AND** 客户端刷新、切换会话或 SSE 重连后 SHALL 能通过 draft 查询恢复这些可见 delta
- **AND** 重复工具调用 SHOULD 通过工具结果复用或重复拦截治理，不得通过清空运行中草稿来隐藏问题。

### Requirement: 工具规划轮次不得直接持久化为最终消息
系统 MUST 将带工具调用的模型轮次视为运行过程，而不是最终 assistant 消息。

#### Scenario: 模型同时输出可见文本和 tool call
- **WHEN** 模型流式输出了用户可见文本，并在同一轮返回 tool call
- **THEN** 系统 MUST 仅将该文本保存在运行中草稿
- **AND** 系统 MUST NOT 立即将该文本持久化为正式 Agent 消息

#### Scenario: 终态落盘消息
- **WHEN** run 成功结束且最后一轮没有 tool call
- **THEN** 系统 MUST 将最终回答持久化为正式 Agent 消息
- **AND** run 失败或取消时，系统 SHALL 将终态前最后一版可见草稿保存为正式消息正文
