## ADDED Requirements

### Requirement: Tool availability follows workspace capability

系统 SHALL 根据 workspace capabilities 和 Agent Run mode 控制工具是否可用。涉及工具：`list_files`、`read_file`、`search_text`、`run_shell`、`apply_patch`、`write_file`。涉及表：`agent_workspace`、`tool_call`、`audit_event`。

#### Scenario: 读取工具需要 READ_REPOSITORY

- **WHEN** workspace 包含 `READ_REPOSITORY`
- **THEN** 系统 MUST 允许 `list_files`、`read_file`、`search_text`

#### Scenario: Git 只读命令需要 GIT_READ

- **WHEN** Agent 调用 `git status`、`git diff` 或 `git log`
- **THEN** 系统 MUST 校验 workspace 包含 `GIT_READ`

#### Scenario: 测试命令需要 RUN_TEST

- **WHEN** Agent 调用测试命令
- **THEN** 系统 MUST 校验 workspace 包含 `RUN_TEST`

#### Scenario: 构建命令需要 RUN_BUILD

- **WHEN** Agent 调用构建命令
- **THEN** 系统 MUST 校验 workspace 包含 `RUN_BUILD`

### Requirement: Capability maps to command presets

系统 SHALL 将用户易懂的 workspace capability 映射为内部工具和命令白名单。系统 MUST NOT 要求用户直接理解底层命令前缀。涉及配置：`coder-agent.tools`。涉及表：`agent_workspace`。

#### Scenario: 未配置 workspace 命令时使用全局默认

- **WHEN** workspace 未单独配置命令细节
- **THEN** 系统 MUST 使用全局默认命令映射
- **AND** 命令执行仍 MUST 经过危险 token 拒绝策略

#### Scenario: capability 不足时拒绝命令

- **WHEN** Agent 调用的命令不属于 workspace 已授权 capability
- **THEN** 系统 MUST 拒绝命令
- **AND** 系统 MUST 记录工具拒绝审计

### Requirement: High-risk git operations remain forbidden

系统 MUST 在第二版继续禁止 Git commit、push、reset、branch 操作，即使 workspace 处于 `EDIT` 模式。涉及工具：`run_shell`。

#### Scenario: 拒绝 git commit

- **WHEN** Agent 调用 `git commit`
- **THEN** 系统 MUST 拒绝执行
- **AND** 系统 MUST NOT 修改 Git 历史

#### Scenario: 拒绝 git push

- **WHEN** Agent 调用 `git push`
- **THEN** 系统 MUST 拒绝执行
- **AND** 系统 MUST NOT 访问远端仓库执行推送
