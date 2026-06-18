## ADDED Requirements

### Requirement: 会话最后模型
系统 SHALL 按 conversation 保存最后一次选择的模型 key。创建 Agent Run 时，如果请求未显式传入 model，系统 MUST 使用该 conversation 的 `lastModelKey`。涉及 API：conversation 创建/更新/详情、`POST /api/agent-runs`。涉及表：`agent_conversation`、`agent_model_provider`、`agent_run`。

#### Scenario: 创建 run 使用会话模型
- **WHEN** conversation 保存了 `lastModelKey=glm-4.5-air` 且创建 run 请求未传 model
- **THEN** 系统 MUST 使用 `glm-4.5-air` 创建 Agent Run

#### Scenario: 请求显式模型覆盖会话模型
- **WHEN** 创建 run 请求显式传入启用模型 key
- **THEN** 系统 MUST 使用请求中的模型，并更新该 conversation 的 `lastModelKey`

### Requirement: 新会话模型默认值
系统 MUST 在新 conversation 无模型选择时使用启用模型列表排名最上方的模型作为初始 `lastModelKey`；如果没有启用模型，`lastModelKey` MUST 为空，客户端显示“请配置模型”。涉及 API：`POST /api/conversations`、`GET /api/model-providers?enabledOnly=true`。

#### Scenario: 新会话有启用模型
- **WHEN** 用户创建新 conversation 且启用模型列表非空
- **THEN** 系统 SHALL 将排名最上方的启用模型写入该 conversation 的 `lastModelKey`

#### Scenario: 新会话无启用模型
- **WHEN** 用户创建新 conversation 但没有启用模型
- **THEN** 系统 MUST 允许 conversation 创建但 `lastModelKey` 为空，创建 run 时 MUST 返回模型未配置错误

### Requirement: 模型展示名装配
系统 SHALL 在 run/message 查询响应中返回模型展示名。若模型配置仍存在，返回 `agent_model_provider.display_name`；若模型配置已删除或停用，返回 run 保存的 model key 作为降级展示值。涉及 API：run/message 查询。涉及表：`agent_run`、`agent_model_provider`。

#### Scenario: 返回模型 displayName
- **WHEN** 用户查询包含 run 的 Agent 消息
- **THEN** 系统 MUST 返回该 run 对应模型的 displayName

#### Scenario: 模型配置已删除
- **WHEN** 用户查询历史 run 且对应模型配置已删除
- **THEN** 系统 MUST 返回 run 保存的 model key，保证客户端仍可展示模型信息
