## ADDED Requirements

### Requirement: Create Agent Run with configured model selection

系统 SHALL 允许调用方在 `POST /api/agent-runs` 请求中通过 `model` 字段选择一个服务端已配置的模型 key。若请求未提供 `model`，系统 MUST 使用默认模型 key 创建运行任务。涉及 API：`POST /api/agent-runs`、`GET /api/agent-runs/{runId}`。涉及表：`agent_run`。

#### Scenario: 使用默认模型创建任务

- **WHEN** 调用方提交 `POST /api/agent-runs` 且未传入 `model`
- **THEN** 系统创建 Agent Run，并在 `agent_run.model` 中保存默认模型 key

#### Scenario: 使用指定模型创建任务

- **WHEN** 调用方提交 `POST /api/agent-runs` 且 `model` 为已配置模型 key
- **THEN** 系统创建 Agent Run，并在响应和 `agent_run.model` 中体现该模型 key

#### Scenario: 拒绝未知模型

- **WHEN** 调用方提交 `POST /api/agent-runs` 且 `model` 不是已配置模型 key
- **THEN** 系统 MUST 拒绝创建任务，并返回明确的模型未配置错误

### Requirement: Resolve model backend configuration during execution

系统 SHALL 在 Agent 执行模型调用时，根据 Agent Run 保存的模型 key 解析对应的模型后端配置，并使用该配置发起真实模型调用。每个模型配置 MUST 至少包含真实模型名、baseUrl、apiKey、endpointType 和 timeoutSeconds。涉及表：`agent_run`、`model_call`。

#### Scenario: 调用指定模型后端

- **WHEN** Agent Run 的模型 key 指向一个已配置模型
- **THEN** 模型网关 MUST 使用该模型配置的 baseUrl、apiKey、endpointType、真实模型名和 timeoutSeconds 发起请求

#### Scenario: 模型调用记录实际模型

- **WHEN** 模型调用完成或失败
- **THEN** 系统 MUST 在 `model_call` 中记录可用于审计的模型信息

### Requirement: Preserve single-model compatibility

系统 MUST 保持现有单模型配置兼容。当多模型配置为空时，系统 SHALL 从当前 `coder-agent.model` 单模型字段构造默认模型配置，未传 `model` 的旧请求 MUST 继续可用。涉及 API：`POST /api/agent-runs`。涉及表：`agent_run`、`model_call`。

#### Scenario: 旧配置继续可用

- **WHEN** 服务只配置当前单模型字段且调用方未传入 `model`
- **THEN** 系统 MUST 使用该单模型配置创建并执行 Agent Run

#### Scenario: 多模型配置优先

- **WHEN** 服务同时存在单模型字段和多模型 `models` 配置
- **THEN** 系统 MUST 优先使用多模型配置和默认模型 key 解析模型

### Requirement: Do not accept client-provided backend secrets

系统 MUST 禁止调用方通过创建任务请求提交 baseUrl、apiKey 或其他模型后端敏感配置。模型后端配置只能来自服务端配置。涉及 API：`POST /api/agent-runs`。

#### Scenario: 请求只能选择模型 key

- **WHEN** 调用方创建 Agent Run
- **THEN** 系统 MUST 只接受模型 key 作为模型选择输入，不得从请求体读取模型服务地址或密钥
