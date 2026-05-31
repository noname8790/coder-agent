## Context

当前 `CreateAgentRunRequestDTO` 已包含 `model` 字段，`CreateAgentRunCaseImpl` 会把该字段保存到 `agent_run.model`；但基础设施层仍只读取 `coder-agent.model` 这一套静态配置，用户传入的 `model` 只能影响请求体中的模型名，不能切换 baseUrl、apiKey、endpointType、timeout 等完整模型后端配置。

本项目首版定位为服务端 Agent Harness，运行任务需要可审计、可回放、可治理。模型选择必须由服务端配置白名单控制，不能允许客户端提交任意模型服务地址或密钥。

## Goals / Non-Goals

**Goals:**

- 支持创建 Agent Run 时通过 `model` 参数选择一个已配置模型。
- 支持默认模型：未传 `model` 时保持现有行为。
- 支持每个模型独立配置调用参数，包括 provider、baseUrl、apiKey、模型名、endpointType、temperature、timeoutSeconds。
- 在任务记录、模型调用记录和工件中保留可追踪的模型选择信息。
- 对未知模型 key 给出明确错误，避免运行中才失败。

**Non-Goals:**

- 不提供运行时新增、修改、删除模型配置的管理 API。
- 不支持用户在请求中传入 baseUrl、apiKey 或任意第三方地址。
- 不实现自动 fallback 到其他模型；失败重试和模型降级以后单独设计。
- 不改变工具调用协议和 Agent 执行循环预算模型。

## Decisions

### 1. `model` 参数表示服务端配置的模型 key

请求中的 `model` 字段解释为模型 key，例如 `qwen-plus`、`glm-5`、`deepseek-chat`。服务端根据 key 解析完整模型配置。

备选方案是让 `model` 继续表示 provider 的真实模型名。该方案改动较小，但无法切换 `baseUrl/apiKey/endpointType`，也无法表达同名模型在不同 provider 下的配置差异。因此采用模型 key。

### 2. 配置结构保留单模型兼容，同时新增多模型 map

建议配置形态：

```yaml
coder-agent:
  model:
    default-model-key: glm-5
    models:
      glm-5:
        provider: openai-compatible
        base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
        api-key: ${OPENAI_COMPATIBLE_API_KEY:}
        model: glm-5
        endpoint-type: chat-completions
        temperature: 0.2
        timeout-seconds: 180
```

为了降低迁移成本，若 `models` 为空，则继续使用当前 `coder-agent.model.base-url/api-key/model/endpoint-type` 单模型配置，并把旧 `model` 值作为默认模型 key。

### 3. 在 case 层提前校验模型选择

创建任务时通过 `IModelConfigPort` 或等价应用服务能力校验模型 key 是否存在，并把最终模型 key 写入 `agent_run.model`。这样错误会在 `POST /api/agent-runs` 阶段返回，不会创建一个必然失败的异步任务。

备选方案是在执行器调用模型时才校验。该方案会产生失败 run 和工件，但用户反馈更晚，也污染审计数据。因此采用创建时校验。

### 4. 基础设施层按 `ModelRequest.model()` 解析模型配置

`OpenAiResponsesModelGateway` 不再直接使用全局单模型配置，而是根据 `ModelRequest.model()` 解析出 `ResolvedModelConfig`。请求体中的 `model` 字段使用 resolved config 的真实模型名，HTTP URL、鉴权、endpointType、temperature、timeout 使用同一个 resolved config。

### 5. 审计和工件记录模型 key 与真实模型名

`agent_run.model` 保存模型 key。`model_call.model` 记录真实模型名或 `key/modelName` 格式，用于排查具体调用。`run-meta.json`、`final-result.json` 可保留模型 key，必要时增加 provider 和 actualModel 字段。

## Risks / Trade-offs

- [Risk] 用户误以为可以传任意模型名，结果被拒绝。→ 在 README 和错误信息中明确 `model` 是配置 key，并提供查询配置示例。
- [Risk] 旧配置和新配置并存时语义混乱。→ 约定 `models` 非空时优先使用多模型配置；为空时才进入旧配置兼容路径。
- [Risk] API key 继续写入配置文件有泄漏风险。→ 示例使用环境变量占位，禁止在文档中推荐明文 key。
- [Risk] 不支持运行时管理模型，新增模型需要重启服务。→ 首版接受该限制，后续再设计模型配置管理表和管理 API。

## Migration Plan

1. 保留当前单模型配置字段，确保现有 `application.yml` 可继续启动。
2. 新增 `default-model-key` 和 `models` 配置读取能力。
3. 若用户未配置 `models`，使用旧字段构建一个默认模型配置。
4. README 增加多模型配置示例和 `POST /api/agent-runs` 使用示例。
5. 回滚时删除多模型配置，停止传入 `model` 参数，系统回到旧单模型调用路径。

## Open Questions

- 首版是否需要增加“列出可用模型 key”的只读 API？本变更默认不加，避免扩大范围；后续前端或管理台需要时再补。
