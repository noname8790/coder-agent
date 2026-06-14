## ADDED Requirements

### Requirement: Benchmark 管理
系统 SHALL 提供后端 benchmark 管理能力，支持固定任务定义、workspaceKey、权限等级和期望结果。

#### Scenario: 创建 benchmark
- **WHEN** 用户提交 benchmark 定义
- **THEN** 系统 SHALL 持久化 benchmark 并可用于后续 eval run

### Requirement: Eval Run 执行
系统 SHALL 支持按模型配置批量运行 benchmark，并记录每个 case 的结果。

#### Scenario: 启动 eval run
- **WHEN** 用户为一个或多个模型启动 eval run
- **THEN** 系统 MUST 为每个 benchmark/model 组合创建 Agent Run 并汇总结果

### Requirement: 指标汇总
系统 MUST 汇总 pass_rate、attempts、model_calls、tool_calls、tool_steps、duration、failure_category、context compression 和 memory hit 指标。

#### Scenario: Eval run 完成
- **WHEN** 所有 case 进入终态
- **THEN** 系统 SHALL 生成 eval summary 并保存到数据库

### Requirement: Eval 报告工件
系统 SHALL 生成 `.coder/evals/{evalId}/summary.json`、`report.md` 和 `cases/*.json`。

#### Scenario: 查询 eval run
- **WHEN** 用户查询 eval run 结果
- **THEN** 系统 SHALL 返回指标和报告工件路径
