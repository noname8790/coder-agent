## 1. 配置模型与解析能力

- [x] 1.1 为 `AgentRuntimeProperties.Model` 增加 `defaultModelKey` 和 `models` 配置结构，同时保留旧单模型字段
- [x] 1.2 新增模型配置解析单元测试，覆盖多模型命中、默认模型、未知模型和旧配置兼容
- [x] 1.3 实现模型配置解析组件，输出包含 provider、modelKey、actualModel、baseUrl、apiKey、endpointType、temperature、timeoutSeconds 的 resolved config

## 2. 创建任务入口校验

- [x] 2.1 为 `CreateAgentRunCaseImpl` 增加指定模型 key 的 Given/When/Then 测试
- [x] 2.2 为未知模型 key 增加拒绝创建任务测试，校验不会写入 `agent_run`
- [x] 2.3 在创建任务用例中接入模型配置解析，保存最终模型 key 到 `agent_run.model`
- [x] 2.4 更新 REST 控制器测试，覆盖 `POST /api/agent-runs` 传入模型 key 的响应

## 3. 模型调用路由

- [x] 3.1 为 `OpenAiResponsesModelGateway` 增加多模型路由测试，校验请求 URL、Authorization、endpointType 和请求体真实模型名
- [x] 3.2 改造模型网关，根据 `ModelRequest.model()` 获取 resolved config，而不是直接使用全局单模型配置
- [x] 3.3 确保 `model_call` 记录包含可审计的模型信息，必要时使用 `modelKey/actualModel` 格式
- [x] 3.4 保持旧单模型配置下的模型调用测试通过

## 4. 工件、文档与验证

- [x] 4.1 更新 `run-meta.json` / `final-result.json` 相关输出，确保能追踪模型 key 或真实模型名
- [x] 4.2 更新 README 配置示例，说明 `model` 参数是服务端配置 key，不能传 baseUrl 或 apiKey
- [x] 4.3 运行 `mvn test` 验证全部单元测试
- [x] 4.4 已配置真实 API key 并运行真实 API 冒烟测试，指定模型 key `glm-5` 完成一次 Agent Run，测试结果 `BUILD SUCCESS`
- [x] 4.5 运行 `openspec validate add-model-selection` 验证规格一致性
