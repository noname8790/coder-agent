## ADDED Requirements

### Requirement: Workspace path boundary for dynamic workspaces

系统 MUST 对动态注册 workspace 的所有文件路径执行规范化和边界校验，确保工具只能访问 workspaceRoot 内部。涉及 API：`POST /api/workspaces`、`POST /api/agent-runs`。涉及工具：全部文件工具和 shell 工具。

#### Scenario: 允许 workspace 内路径

- **WHEN** 工具请求访问 workspaceRoot 内部路径
- **THEN** 系统 MUST 允许继续执行后续工具校验

#### Scenario: 拒绝路径逃逸

- **WHEN** 工具请求通过 `..`、绝对路径或符号路径访问 workspaceRoot 外部
- **THEN** 系统 MUST 拒绝访问
- **AND** 系统 MUST 写入路径逃逸审计事件

### Requirement: Protected path policy

系统 MUST 在编辑工具中保护敏感路径和生成目录。默认禁止编辑 `.env`、`.env.*`、`.git/`、`.coder/`、`target/`、包含密钥语义的文件名。涉及工具：`apply_patch`、`write_file`。

#### Scenario: 拒绝编辑 .env

- **WHEN** Agent 尝试修改或创建 `.env`
- **THEN** 系统 MUST 拒绝工具调用
- **AND** 系统 MUST 记录安全审计

#### Scenario: 拒绝编辑 .coder 工件目录

- **WHEN** Agent 尝试修改 `.coder/` 下文件
- **THEN** 系统 MUST 拒绝工具调用
- **AND** 系统 MUST NOT 改写运行工件

### Requirement: Workspace root revalidation

系统 SHALL 在创建运行和执行运行前重新校验 workspaceRoot 是否仍然存在且为目录。涉及表：`agent_workspace`、`agent_run`。

#### Scenario: workspace 被移动后创建运行

- **WHEN** workspace 记录存在但 rootPath 已不存在
- **THEN** 系统 MUST 拒绝创建运行
- **AND** 响应 MUST 提示 workspace 路径不可用

#### Scenario: 执行时 workspace 不可用

- **WHEN** Agent Run 已创建但执行前 workspaceRoot 不可用
- **THEN** 系统 MUST 将运行标记为 `FAILED`
- **AND** 最终结果 MUST 记录 workspace 不可用原因
