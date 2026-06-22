## ADDED Requirements

### Requirement: 分层上下文候选
系统 MUST 将每次模型调用的上下文建模为分层候选，并记录候选的来源、作用域、token 估算、freshness、可信度、是否必选、是否可压缩和 evidence 引用。涉及表：`agent_context_snapshot`、`agent_context_section`。涉及 API：运行详情查询 API 应返回上下文指标。

#### Scenario: 构建分层候选
- **GIVEN** 用户在一个已有会话中发起代码任务
- **WHEN** AgentRun 准备调用模型
- **THEN** 系统 MUST 生成 prefix、working memory、relevant memory、recent messages、file summaries、raw snippets、tool results、run trace、current request 的候选集合
- **AND** 每个候选 MUST 包含 sourceRef、scope、tokenEstimate、freshnessStatus、trustScore 和 evidenceRefs

### Requirement: 预算水位压缩
系统 MUST 按 128K 模型预算执行水位控制：75% 规则去重，85% 模型压缩，95% 强制锚点保护，100% 拒绝继续拼接并恢复到最近有效 checkpoint。

#### Scenario: 达到模型压缩水位
- **GIVEN** 当前候选总 token 超过 `CONTEXT_MAX_INPUT_TOKENS` 的 85%
- **WHEN** 系统准备组装 prompt
- **THEN** 系统 MUST 使用当前任务模型执行结构化压缩
- **AND** 压缩输出 MUST 包含 task_state、key_decisions、changed_files、failed_attempts、open_questions 和 evidence_refs

### Requirement: 压缩快照可审计
系统 MUST 在每次模型调用前保存 context snapshot，记录候选、入选、压缩、裁剪、预算和失败原因。

#### Scenario: 保存压缩快照
- **GIVEN** 一次模型调用触发上下文压缩
- **WHEN** prompt 装配完成
- **THEN** 系统 MUST 写入 `context-snapshot/*.json`
- **AND** MySQL MUST 记录 snapshot 元数据、压缩水位、压缩率、入选 section 数和被裁剪 section 数
