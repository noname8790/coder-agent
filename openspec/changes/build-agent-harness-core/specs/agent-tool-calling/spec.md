## ADDED Requirements

### Requirement: OpenAI-compatible Responses API Tool Calling

系统 SHALL 优先使用 OpenAI-compatible Responses API 暴露工具 schema，并将模型返回的工具调用转换为内部 `ToolInvocation` 执行。首版交付验收 MUST 使用真实 OpenAI-compatible API 跑通一次完整运行闭环。

涉及 API：`POST /api/agent-runs`  
涉及表：`model_call`、`tool_call`、`agent_step`

#### Scenario: 模型请求工具调用

- **GIVEN** 模型响应包含一个或多个 `tool_calls`
- **WHEN** Agent 执行循环处理模型响应
- **THEN** 系统为每个工具调用创建 `ToolInvocation`
- **AND** 在执行前进行工具名称、参数和安全策略校验

#### Scenario: 模型返回最终回答

- **GIVEN** 模型响应不包含 `tool_calls` 且包含最终回答
- **WHEN** Agent 执行循环处理模型响应
- **THEN** 系统保存最终回答
- **AND** 结束运行循环

#### Scenario: 真实模型 API 冒烟验证

- **GIVEN** 服务端配置了真实可用的 OpenAI-compatible `base-url`、`api-key` 和 `model`
- **WHEN** 客户端创建一个本地仓库分析任务
- **THEN** 系统通过真实模型 API 完成至少一次模型调用
- **AND** 运行产生 `model_call` 记录、trace 事件和最终结果

#### Scenario: 模型 API 不可用

- **GIVEN** 服务端断网或 OpenAI-compatible API 不可用
- **WHEN** AgentRun 执行到模型调用步骤
- **THEN** 系统将当前运行标记为 `FAILED`
- **AND** 服务进程不得崩溃
- **AND** trace 和 `final-result.json` 记录模型调用失败原因

### Requirement: 首版工具集合

系统 MUST 在首版提供 `list_files`、`read_file`、`search_text`、`run_shell` 四类工具。

涉及 API：`POST /api/agent-runs`、`GET /api/agent-runs/{runId}/trace`  
涉及表：`tool_call`、`audit_event`

#### Scenario: 列目录

- **GIVEN** 模型请求调用 `list_files` 且路径位于 workspace 内
- **WHEN** 工具执行器处理调用
- **THEN** 系统返回目录项摘要
- **AND** 记录工具调用结果

#### Scenario: 读取文件

- **GIVEN** 模型请求调用 `read_file` 且文件位于 workspace 内
- **WHEN** 文件大小和类型通过限制检查
- **THEN** 系统返回文本内容或摘要
- **AND** 长内容不得直接无限制写入模型上下文

#### Scenario: 搜索文本

- **GIVEN** 模型请求调用 `search_text` 并提供搜索关键词
- **WHEN** `rg` 命令可用且执行成功
- **THEN** 系统返回匹配文件、行号和摘要
- **AND** 大结果写入 `tool-output/*.txt`

#### Scenario: 搜索命令不可用时回退

- **GIVEN** 运行环境未安装 `rg` 或 `rg` 执行失败
- **WHEN** 工具执行器处理搜索请求
- **THEN** 系统仍使用 Java 内置文件扫描在 workspace 内搜索
- **AND** 记录 fallback 事件或工具结果摘要

#### Scenario: 执行受限 Shell

- **GIVEN** 模型请求调用 `run_shell` 且命令通过白名单和危险 token 检查
- **WHEN** 工具执行器运行命令
- **THEN** 系统记录退出码、stdout 摘要、stderr 摘要和耗时

### Requirement: 工具结果回传模型

系统 SHALL 将工具执行结果追加到对话上下文中，并在预算允许时继续下一轮模型调用。

涉及 API：`POST /api/agent-runs`  
涉及表：`agent_step`、`tool_call`、`model_call`

#### Scenario: 工具执行成功后继续模型循环

- **GIVEN** 工具调用执行成功且未达到预算上限
- **WHEN** 系统完成工具结果持久化
- **THEN** 系统将工具结果摘要加入上下文
- **AND** 发起下一次模型调用

#### Scenario: 工具执行失败

- **GIVEN** 工具调用执行失败
- **WHEN** 系统捕获失败结果
- **THEN** 系统记录失败的 `tool_call`
- **AND** 根据失败类型决定继续模型循环或终止运行
