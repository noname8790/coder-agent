## ADDED Requirements

### Requirement: 评测用例可回归
系统 MUST 将每个 benchmark case 定义为可重复执行的任务，包括 workspace fixture、初始文件、任务输入、期望结果、允许工具和评价器。

#### Scenario: 重复读文件评测
- **GIVEN** benchmark case 要求基于同一文件连续回答两个问题
- **WHEN** 第二轮任务执行
- **THEN** repeated_read_count MUST 低于基线
- **AND** 如果 freshness 校验失败，系统 MUST 重新读取文件并记录原因

### Requirement: 评测指标入库
系统 MUST 将 eval run 的关键指标写入 MySQL，并将详细 trace 保存到 `.coder/evals/` 工件目录。

#### Scenario: 查询历史评测
- **GIVEN** 已执行两次 v4.5 eval
- **WHEN** 用户查询评测历史
- **THEN** 系统 MUST 返回 pass_rate、token cost、压缩率和失败分类的回归对比
