## ADDED Requirements

### Requirement: Register local workspace

系统 SHALL 允许调用方通过 REST API 注册一个本地项目目录作为 workspace。注册信息 MUST 至少包含 `workspaceKey`、`rootPath` 和可选 `capabilities`。涉及 API：`POST /api/workspaces`。涉及表：`agent_workspace`。

#### Scenario: 注册有效 workspace

- **WHEN** 调用方提交存在的本地绝对目录作为 `rootPath`
- **THEN** 系统 MUST 保存规范化后的 workspace 记录
- **AND** 响应 MUST 返回 `workspaceKey`、规范化 `rootPath`、状态和 capabilities

#### Scenario: 拒绝非法路径

- **WHEN** 调用方提交空路径、相对路径、Windows 盘符相对路径、不存在路径或文件路径
- **THEN** 系统 MUST 拒绝注册
- **AND** 响应 MUST 返回明确的路径非法错误

#### Scenario: 允许任意父目录

- **WHEN** 调用方提交任意父目录下的合法本地绝对目录
- **THEN** 系统 MUST 允许注册
- **AND** 系统 MUST NOT 要求路径位于固定 allowed-roots 下

### Requirement: Workspace capabilities

系统 SHALL 为 workspace 保存能力枚举，用于控制工具和命令权限。第二版能力集合 MUST 包含：`READ_REPOSITORY`、`GIT_READ`、`RUN_TEST`、`RUN_BUILD`、`ADD_FILE`、`MODIFY_FILE`。涉及 API：`POST /api/workspaces`、`GET /api/workspaces/{workspaceKey}`。涉及表：`agent_workspace`。

#### Scenario: 使用显式 capabilities

- **WHEN** 调用方注册 workspace 并传入 capabilities
- **THEN** 系统 MUST 保存这些能力
- **AND** 后续 Agent Run MUST 按该 workspace 能力判断工具权限

#### Scenario: 使用默认 capabilities

- **WHEN** 调用方注册 workspace 且未传入 capabilities
- **THEN** 系统 MUST 使用全局默认能力
- **AND** 默认能力 MUST NOT 包含 `ADD_FILE` 或 `MODIFY_FILE`

#### Scenario: 拒绝未知 capability

- **WHEN** 调用方传入不支持的 capability
- **THEN** 系统 MUST 拒绝注册或更新
- **AND** 响应 MUST 返回无效能力错误

### Requirement: Query and deactivate workspace

系统 SHALL 支持查询 workspace 列表、查询单个 workspace、停用 workspace。删除操作 MUST 采用逻辑停用，不物理删除历史记录。涉及 API：`GET /api/workspaces`、`GET /api/workspaces/{workspaceKey}`、`DELETE /api/workspaces/{workspaceKey}`。涉及表：`agent_workspace`、`agent_run`。

#### Scenario: 查询 workspace 列表

- **WHEN** 调用方请求 workspace 列表
- **THEN** 系统 MUST 返回 active workspace 列表
- **AND** 每项 MUST 包含 workspaceKey、rootPath、capabilities 和状态

#### Scenario: 停用 workspace

- **WHEN** 调用方删除某个 workspace
- **THEN** 系统 MUST 将该 workspace 标记为 inactive
- **AND** 历史 Agent Run 查询 MUST 仍然可用

#### Scenario: 禁止使用停用 workspace 创建运行

- **WHEN** 调用方使用 inactive workspaceKey 创建 Agent Run
- **THEN** 系统 MUST 拒绝创建
- **AND** 响应 MUST 返回 workspace 不可用错误
