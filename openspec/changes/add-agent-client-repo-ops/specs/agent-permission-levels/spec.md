## ADDED Requirements

### Requirement: 权限等级定义
系统 SHALL 定义运行权限等级并用其控制工具可见性和执行边界。第三版 MUST 实现 `L1_READ_ONLY`、`L2_SAFE_EDIT`、`L3_REPO_WRITE`，并预留但不默认启用 `L4_DANGEROUS_LOCAL`。涉及 API：`GET /api/permission-levels`、`POST /api/agent-runs`。涉及表：`agent_run`、`agent_permission_audit`。

#### Scenario: 查询权限等级说明
- **WHEN** 客户端请求权限等级列表
- **THEN** 系统返回每个等级的名称、说明、允许能力、禁止能力和风险提示

#### Scenario: 创建运行时指定权限等级
- **WHEN** 客户端创建 Agent Run 并传入 `permissionLevel=L2_SAFE_EDIT`
- **THEN** 系统保存该权限等级并按 L2 边界组装工具定义

### Requirement: L1 只读权限
`L1_READ_ONLY` SHALL 只允许读取、搜索、列目录、Git 只读命令和生成结论。系统 MUST 拒绝任何文件写入、测试构建、Git 写入和 PR 草稿生成。涉及工具：`list_files`、`read_file`、`search_text`、受限 `run_shell`。涉及表：`tool_call`、`audit_event`。

#### Scenario: L1 拒绝修改文件
- **WHEN** L1 运行请求执行 `apply_patch`
- **THEN** 系统拒绝该工具调用并写入权限审计事件

#### Scenario: L1 允许 Git 只读命令
- **WHEN** L1 运行执行 `git status`
- **THEN** 系统允许命令并记录 tool_call

### Requirement: L2 安全编辑权限
`L2_SAFE_EDIT` SHALL 允许 L1 能力、新增文件、patch 修改已有文本文件、运行测试和运行构建。系统 MUST 拒绝覆盖文件、删除文件、本地 commit 和 PR 草稿生成。涉及工具：`write_file`、`apply_patch`、受限 `run_shell`。涉及表：`tool_call`、`audit_event`。

#### Scenario: L2 允许 patch 修改
- **WHEN** L2 运行执行 `apply_patch` 修改 workspace 内文本文件
- **THEN** 系统写入文件变更并生成 changed file 记录

#### Scenario: L2 拒绝 Git commit
- **WHEN** L2 运行请求执行 `git commit`
- **THEN** 系统拒绝该命令并写入权限审计事件

### Requirement: L3 仓库写入权限
`L3_REPO_WRITE` SHALL 允许 L2 能力、覆盖文件、删除文件、本地分支、`git add`、`git commit` 和 PR 草稿生成。系统 MUST 继续拒绝 `git push`、`git reset --hard`、`git clean` 和远程 PR 创建。涉及工具：`overwrite_file`、`delete_file`、Git 写入工具、PR 草稿工具。涉及表：`tool_call`、`audit_event`、`run_artifact`。

#### Scenario: L3 允许本地 commit
- **WHEN** L3 运行完成文件变更并执行本地 commit
- **THEN** 系统记录 commit hash 并写入 final-result

#### Scenario: L3 拒绝 git push
- **WHEN** L3 运行请求执行 `git push`
- **THEN** 系统拒绝该命令并写入审计事件

### Requirement: 权限审计
系统 SHALL 对权限等级选择、权限拒绝和高风险能力使用写入审计。涉及 API：`POST /api/agent-runs`、工具调用入口。涉及表：`agent_permission_audit`、`audit_event`。

#### Scenario: 创建 L3 运行记录权限审计
- **WHEN** 用户创建 `L3_REPO_WRITE` 运行
- **THEN** 系统写入权限审计记录，包含 permissionLevel、workspaceKey、conversationId、runId 和创建时间

#### Scenario: 工具因权限不足被拒绝
- **WHEN** 工具调用超出当前权限等级
- **THEN** 系统写入 audit_event 并返回 REJECTED tool_call
