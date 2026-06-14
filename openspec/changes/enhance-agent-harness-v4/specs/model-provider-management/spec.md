## MODIFIED Requirements

### Requirement: 模型配置来源
系统 MUST 使用数据库中的模型配置作为 Agent Run 的唯一运行时 chat 模型来源，取代 v3 固定三模型静态白名单。

#### Scenario: 使用已启用模型
- **WHEN** 用户创建 Agent Run 并传入 model key
- **THEN** 系统 MUST 从 `agent_model_provider` 查询启用的模型配置

#### Scenario: 模型未配置
- **WHEN** 用户传入的 model key 不存在或未启用
- **THEN** 系统 MUST 返回 `MODEL_NOT_CONFIGURED`

### Requirement: API Key 安全存储
系统 SHALL 保存模型 API Key 的加密值，并在查询模型列表时只返回脱敏值。

#### Scenario: 查询模型配置列表
- **WHEN** 用户查询模型配置
- **THEN** 系统 MUST 不返回明文 API Key

### Requirement: 模型级上下文预算
系统 SHALL 支持模型配置级上下文预算，并在未配置时使用全局默认预算。

#### Scenario: 模型配置包含预算
- **WHEN** Agent Run 使用含上下文预算的模型
- **THEN** 上下文治理引擎 MUST 优先使用模型级预算
