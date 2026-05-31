## Why

当前 Agent 运行只依赖单套静态模型配置。后续用户可能希望在创建任务时切换不同模型，用于对比效果、成本、速度或绕开某个模型暂时不可用的问题。

本变更为 Agent 运行增加“按请求选择模型”的入口和配置解析规则，在保持默认模型兼容的前提下，让业务能够根据用户选择调用对应模型后端。

## What Changes

- `POST /api/agent-runs` 的 `model` 字段明确升级为模型选择参数，允许传入已配置的模型 key。
- 新增多模型配置结构，支持配置默认模型和多个候选模型，每个模型可拥有独立的 `baseUrl`、`apiKey`、模型名、endpoint 类型、temperature 和 timeout。
- 创建 Agent Run 时校验用户选择的模型 key；未传时使用默认模型，传入未知 key 时拒绝创建任务。
- Agent 执行模型调用时根据 run 记录中的模型选择解析对应模型配置，而不是始终使用全局静态配置。
- 审计与运行工件继续记录实际使用的模型信息，便于回放和排查。
- 回滚方案：保留当前单模型配置兼容路径；如多模型选择出现问题，可只配置默认模型并停止向 API 传入 `model` 参数，业务回退到原静态模型调用行为。

## Capabilities

### New Capabilities

- `model-selection`: Agent Run 支持按请求选择已配置模型，并在模型调用时路由到对应模型配置。

### Modified Capabilities

- 无。

## Impact

- 影响模块范围：
  - `coder-agent-api`：明确 `CreateAgentRunRequestDTO.model` 的语义和响应模型信息。
  - `coder-agent-case`：创建任务时校验模型选择并保存到 `agent_run.model`。
  - `coder-agent-domain`：模型请求继续携带运行选择的模型标识。
  - `coder-agent-infrastructure`：新增模型配置解析与 HTTP 调用配置选择。
  - `coder-agent-app`：调整 `application.yml` 多模型配置示例和默认值。
  - `coder-agent-trigger`：REST 入参校验与错误响应覆盖。
- 涉及 API：
  - `POST /api/agent-runs`
  - `GET /api/agent-runs/{runId}`
- 涉及数据库表：
  - `agent_run`：复用 `model` 字段保存用户选择的模型 key 或最终模型标识。
  - `model_call`：继续记录实际调用模型。
- 不新增第三方依赖。
