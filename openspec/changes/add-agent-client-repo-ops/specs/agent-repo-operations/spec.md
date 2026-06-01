## ADDED Requirements

### Requirement: 覆盖文件工具
系统 SHALL 在 `L3_REPO_WRITE` 下提供覆盖文件工具。覆盖前 MUST 备份原文件内容并记录 beforeHash、afterHash、relativePath 和 toolCallNo。涉及工具：`overwrite_file`。涉及表：`tool_call`、`run_artifact`。

#### Scenario: 覆盖已有文本文件
- **WHEN** L3 运行调用 `overwrite_file` 覆盖 workspace 内已有文本文件
- **THEN** 系统写入新内容、生成变更记录并备份原文件

#### Scenario: 覆盖受保护路径
- **WHEN** 工具请求覆盖 `.env` 或 `.git` 下文件
- **THEN** 系统拒绝操作并写入审计事件

### Requirement: 删除文件工具
系统 SHALL 在 `L3_REPO_WRITE` 下提供删除文件工具。删除前 MUST 备份原文件内容，并禁止删除目录、受保护路径和 workspace 外路径。涉及工具：`delete_file`。涉及表：`tool_call`、`run_artifact`、`audit_event`。

#### Scenario: 删除普通文本文件
- **WHEN** L3 运行调用 `delete_file` 删除 workspace 内普通文件
- **THEN** 系统删除文件、记录 DELETE 变更并生成备份

#### Scenario: 删除目录被拒绝
- **WHEN** 工具请求删除目录
- **THEN** 系统拒绝操作并写入审计事件

### Requirement: 本地 Git 分支和提交
系统 SHALL 在 `L3_REPO_WRITE` 下允许创建本地分支、`git add` 和 `git commit`。系统 MUST 记录分支名、commit hash、命令输出和失败原因。涉及工具：`run_shell` 或专用 Git 工具。涉及表：`tool_call`、`agent_run`、`audit_event`。

#### Scenario: 创建本地分支
- **WHEN** L3 运行执行创建本地分支操作
- **THEN** 系统记录 gitBranch 并允许后续提交

#### Scenario: 本地 commit 成功
- **WHEN** L3 运行执行 `git add` 和 `git commit`
- **THEN** 系统记录 commitHash 并在 final-result 中返回

### Requirement: PR 草稿生成
系统 SHALL 在 `L3_REPO_WRITE` 下生成 PR 草稿工件 `pull-request.md`。草稿 MUST 包含标题、摘要、变更文件、测试结果、风险点、回滚说明和 reviewer checklist。系统 MUST 不调用远程 Git 平台 API。涉及工件：`pull-request.md`。涉及表：`run_artifact`。

#### Scenario: 生成 PR 草稿
- **WHEN** L3 运行完成本地变更和测试
- **THEN** 系统写入 `pull-request.md` 并登记 run_artifact

#### Scenario: 无远程 PR 创建
- **WHEN** PR 草稿生成完成
- **THEN** 系统不执行 push 且不调用 GitHub/GitLab API

### Requirement: 回滚材料
系统 SHALL 为新增、修改、覆盖、删除操作生成可人工恢复的回滚材料。涉及工件：`rollback.patch`、`file-backup/`。涉及表：`run_artifact`。

#### Scenario: 删除文件生成备份
- **WHEN** Agent 删除文件
- **THEN** 系统在 `file-backup/` 保存删除前内容并在 `rollback.patch` 中记录恢复方式

#### Scenario: 覆盖文件生成回滚 patch
- **WHEN** Agent 覆盖文件
- **THEN** 系统生成可恢复到覆盖前内容的 rollback patch
