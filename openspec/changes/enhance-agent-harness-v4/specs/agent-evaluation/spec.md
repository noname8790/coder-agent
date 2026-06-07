## ADDED Requirements

### Requirement: Benchmark 定义
系统 SHALL 支持在后端定义固定 benchmark 任务，包括 workspaceKey、task、permissionLevel、modelKey、expectedOutcome、timeout 和 evaluatorType。涉及 API：`/api/evals/benchmarks`。涉及表：`eval_benchmark`。

#### Scenario: 创建 benchmark
- **WHEN** 用户提交合法 benchmark 定义
- **THEN** 系统 SHALL 保存 benchmark 并返回 benchmarkId

### Requirement: Eval 运行与报告
系统 SHALL 支持启动 eval run，按 benchmark 集合运行 Agent，并生成 `.coder/evals/{evalId}/` 报告工件。涉及 API：`POST /api/evals/runs`、`GET /api/evals/runs/{evalId}`。涉及表：`eval_run`、`eval_case_result`、`agent_run`。

#### Scenario: 执行模型对比评测
- **WHEN** 用户选择多个模型配置运行同一 benchmark 集合
- **THEN** 系统 SHALL 分别执行并输出模型对比报告

### Requirement: Eval 指标汇总
系统 MUST 汇总 pass_rate、attempts、model_calls、tool_calls、tool_steps、duration、failure_category、context_compression_ratio 和 memory_hit_count。涉及表：`eval_case_result`、`model_call`、`tool_call`、`context_snapshot`。

#### Scenario: 查询 eval 结果
- **WHEN** 用户查询 eval run 详情
- **THEN** 系统 SHALL 返回每个 case 的结果和整体指标汇总

### Requirement: Eval 回归对比
系统 SHALL 支持将当前 eval run 与历史 eval run 对比，展示 pass rate、成本指标和失败分类变化。涉及 API：`GET /api/evals/runs/{evalId}/compare?baselineId=`。涉及表：`eval_run`、`eval_case_result`。

#### Scenario: 对比历史基线
- **WHEN** 用户指定 baseline evalId
- **THEN** 系统 SHALL 返回当前结果相对基线的差异摘要

