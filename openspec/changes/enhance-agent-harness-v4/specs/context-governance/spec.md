## ADDED Requirements

### Requirement: 分层上下文装配
系统 SHALL 使用上下文治理引擎按层装配每次模型调用输入，至少包含 system instruction、workspace profile、current task、permission policy、recent messages、conversation summary、memory recall、file summaries、raw snippets、tool results 和 run trace summary。涉及 API：`POST /api/agent-runs`。涉及表：`model_call`、`context_snapshot`。

#### Scenario: 创建模型调用上下文
- **WHEN** Agent Run 准备调用模型
- **THEN** 系统 MUST 通过上下文治理引擎生成最终上下文，而不是直接拼接完整历史消息

### Requirement: 模型预算裁剪
系统 MUST 根据模型配置级预算或全局默认预算裁剪上下文，并保留 system、权限策略、当前任务和必要工具状态。涉及配置：`CONTEXT_*`。涉及表：`context_snapshot`、`model_call`。

#### Scenario: 候选上下文超过预算
- **WHEN** 候选上下文估算 token 超过当前模型预算
- **THEN** 系统 MUST 按优先级裁剪，并记录每个被裁剪候选的原因

### Requirement: Context Snapshot 审计
系统 MUST 在每次模型调用前保存摘要版 context snapshot，记录候选上下文、入选上下文、裁剪原因、估算 token、预算来源、compressionRatio 和 memoryHitCount。涉及工件：`.coder/runs/{runId}/context-snapshot/*.json`。涉及表：`context_snapshot`。

#### Scenario: 查询运行结果
- **WHEN** 用户查询 Agent Run 详情
- **THEN** 系统 SHALL 返回本次运行的上下文压缩指标和 context snapshot 工件索引

### Requirement: 工具输出压缩
系统 SHALL 对超过预算的工具输出执行截断或摘要化，原始完整输出仍按第三版规则保存到 tool-output 工件。涉及工件：`tool-output/*.txt`、`context-snapshot/*.json`。涉及表：`tool_call`。

#### Scenario: 工具输出过长
- **WHEN** 工具调用输出超过 toolResultBudgetTokens
- **THEN** 系统 MUST 将进入 prompt 的内容压缩，并保留完整工具输出工件路径

