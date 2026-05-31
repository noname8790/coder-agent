## ADDED Requirements

### Requirement: Patch diff artifact

系统 SHALL 在发生文件变更的 Agent Run 中生成 `patch.diff`，记录本次运行产生的统一 diff。涉及工件：`patch.diff`。涉及表：`run_artifact`。

#### Scenario: 编辑运行生成 diff

- **WHEN** `EDIT` 模式运行修改或新增文件
- **THEN** 系统 MUST 写入 `.coder/runs/{runId}/patch.diff`
- **AND** 系统 MUST 在 `run_artifact` 中保存工件索引

### Requirement: Changed files artifact

系统 SHALL 生成 `changed-files.json`，记录每个变更文件的相对路径、变更类型、修改前摘要、修改后摘要和工具调用来源。涉及工件：`changed-files.json`。涉及表：`run_artifact`。

#### Scenario: 记录变更文件

- **WHEN** 编辑工具成功修改或新增文件
- **THEN** 系统 MUST 将该文件写入变更文件清单
- **AND** 变更文件路径 MUST 使用 workspace 相对路径

### Requirement: Test report artifact

系统 SHALL 在 Agent 执行测试或构建命令后生成 `test-report.json`，记录命令、退出码、耗时、状态和输出摘要。涉及工件：`test-report.json`。涉及表：`tool_call`、`run_artifact`。

#### Scenario: 测试命令执行成功

- **WHEN** Agent 成功执行测试命令
- **THEN** 系统 MUST 记录测试命令成功状态
- **AND** 最终结果 MUST 标记测试通过

#### Scenario: 测试命令执行失败

- **WHEN** Agent 执行测试命令返回非零退出码
- **THEN** 系统 MUST 记录失败状态和输出摘要
- **AND** 最终结果 MUST 标记测试未通过

### Requirement: Review summary artifact

系统 SHALL 在运行结束时生成 `review-summary.md`，用于人工审查本次 Agent 修改。该摘要 MUST 包含任务、模式、模型、变更文件、测试结果和建议审查重点。涉及工件：`review-summary.md`。涉及表：`run_artifact`。

#### Scenario: 生成审查摘要

- **WHEN** Agent Run 达到终态
- **THEN** 系统 SHALL 生成审查摘要
- **AND** 查询运行结果 MUST 能返回该工件索引
