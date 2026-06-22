## ADDED Requirements

### Requirement: 上下文与记忆专项 benchmark
系统 MUST 提供固定 benchmark，覆盖长上下文压缩、记忆召回、freshness、污染拦截、重复读文件和工具治理回归场景。涉及表：`agent_eval_case`、`agent_eval_run`、`agent_eval_result`。

#### Scenario: 运行 v4.5 benchmark
- **GIVEN** 开发者触发 v4.5 eval
- **WHEN** 系统执行固定 benchmark
- **THEN** 系统 MUST 汇总 pass_rate、compression_ratio、retained_anchor_rate、memory_recall_precision、memory_recall_recall_at_k、stale_block_rate 和 repeated_read_count

### Requirement: 评测报告工件
系统 MUST 在 `.coder/evals/{evalId}/` 下生成 `report.json` 和 markdown summary，并保存回归对比所需指标。

#### Scenario: 生成评测报告
- **GIVEN** eval run 已完成
- **WHEN** 用户查询评测结果
- **THEN** 系统 MUST 返回报告路径、总体指标、失败分类和关联 trace
- **AND** 报告 MUST 可用于和上一次 eval run 对比
