## ADDED Requirements

### Requirement: Git 专用工具
系统 SHALL 提供或优先使用 Git 专用工具完成 `status`、`diff`、`log`、`add`、`commit` 等本地仓库操作。Git 工具 MUST 结构化返回 stdout、stderr、exitCode、timeout、changedFiles 和 failureReason，并避免通过不稳定 shell 拼接推进任务。涉及工具：`git_status`、`git_diff`、`git_log`、`git_add`、`git_commit`。涉及表：`tool_call`、`audit_event`、`agent_run`。

#### Scenario: 查询 Git 状态
- **WHEN** Agent 调用 `git_status`
- **THEN** 系统 MUST 在限定超时内返回结构化状态，不得卡住 run

#### Scenario: Git 命令超时结构化失败
- **WHEN** Git 工具执行超时
- **THEN** 系统 MUST 记录 `tool_call=FAILED`、failureReason，并把失败原因回灌给 Agent

### Requirement: Git 超时治理
系统 MUST 为 Git 工具设置独立超时和输出截断策略。只读 Git 命令默认不超过 30 秒，写入类 Git 命令默认不超过 60 秒；超过后 MUST 杀死子进程并释放 run。涉及配置：Git 工具 timeout。涉及表：`tool_call`、`audit_event`。

#### Scenario: Git diff 不长时间挂起
- **WHEN** Agent 在正常本地仓库中调用 `git_diff`
- **THEN** 系统 MUST 在超时范围内返回 diff 摘要或结构化失败

#### Scenario: Git commit 超时释放 run
- **WHEN** `git_commit` 超时
- **THEN** 系统 MUST 标记工具失败并允许 Agent 输出失败原因或尝试下一步，不得让 run 永久 RUNNING

### Requirement: PR 草稿生成
系统 SHALL 支持生成本地 PR 草稿工件 `pull-request.md`，内容 MUST 包含标题、摘要、变更文件、测试结果、风险、回滚说明和 reviewer checklist。系统 MUST 不执行 `git push`，不调用远程 Git 平台 API。涉及工具：`generate_pr_draft`。涉及表：`run_artifact`、`tool_call`。

#### Scenario: 生成 PR 草稿
- **WHEN** Agent 完成本地变更并请求生成 PR 草稿
- **THEN** 系统 MUST 写入 `pull-request.md` 工件并在 run 结果中返回路径

#### Scenario: PR 草稿不触发远程操作
- **WHEN** 系统生成 PR 草稿
- **THEN** 系统 MUST NOT 执行 `git push` 或调用 GitHub/GitLab API

### Requirement: 文件变更 Diff 摘要
系统 MUST 在 Agent 任务改动文件后生成结构化 Diff 摘要，并关联到 run 或 Agent 消息。Diff 摘要 MUST 包含总文件数、总新增行、总删除行、每个文件的路径、变更类型、新增行、删除行。涉及工件：`changed-files.json`。涉及 API：run 详情、conversation message 详情。涉及表：`run_artifact` 或 message/run 关联表。

#### Scenario: 任务修改文件后返回 Diff 摘要
- **WHEN** Agent run 修改了 4 个文件
- **THEN** run/message 详情 MUST 返回 4 个文件的 Diff 摘要和总增删行统计

#### Scenario: 无文件变更不显示 Diff
- **WHEN** Agent run 未修改任何文件
- **THEN** run/message 详情 MUST 返回空 Diff 摘要，客户端不得显示文件变更卡片

### Requirement: Git/PR 真实冒烟测试
实现交付前 MUST 使用真实本地测试仓库和配置模型完成 Git/PR 冒烟测试，覆盖只读 Git、生成文件变更、生成 Diff 摘要、生成 PR 草稿、本地 commit、审批通过和完全控制绕过审批。测试结果 MUST 记录在任务交付说明或验证日志中。

#### Scenario: 完整 Git/PR 冒烟通过
- **WHEN** 执行“修改文件并生成 PR 草稿”的真实 Agent 任务
- **THEN** 系统 MUST 成功生成文件变更、Diff 摘要、`pull-request.md`，且 run 结束状态为成功或给出明确可解释失败
