## ADDED Requirements

### Requirement: 新权限等级定义
系统 MUST 使用 `READ_ONLY`、`DEFAULT`、`FULL_ACCESS` 作为对外权限等级，分别显示为“只读”、“默认”、“完全控制”。旧等级 `L1_READ_ONLY`、`L2_SAFE_EDIT`、`L3_REPO_WRITE` MUST NOT 出现在 API 响应、客户端选项和新写入数据库记录中。涉及 API：`GET /api/permission-levels`、`POST /api/agent-runs`、conversation 详情接口。涉及表：`agent_conversation`、`agent_run`、`audit_event`。

#### Scenario: 查询新权限等级
- **WHEN** 客户端请求权限等级列表
- **THEN** 系统 MUST 返回“只读”、“默认”、“完全控制”三个等级及其图标、描述、风险提示和是否需要审批的信息

#### Scenario: 拒绝旧权限等级写入
- **WHEN** 客户端创建 run 时提交旧等级 `L2_SAFE_EDIT`
- **THEN** 系统 MUST 拒绝请求或转换为明确的新等级，并在响应和新记录中不再保留旧枚举值

### Requirement: 只读权限
`READ_ONLY` SHALL 仅允许读取仓库、列目录、搜索、查看 Git 状态/日志/diff、生成分析结论等只读能力。系统 MUST 拒绝新增、修改、覆盖、删除文件、运行构建/测试、Git 写入、本地 commit 和 PR 草稿生成。涉及工具：`list_files`、`read_file`、`search_text`、只读 Git 工具。涉及表：`tool_call`、`audit_event`。

#### Scenario: 只读允许查看 Git 状态
- **WHEN** `READ_ONLY` run 调用 `git_status`
- **THEN** 系统 MUST 执行工具并记录 `tool_call=SUCCESS`

#### Scenario: 只读拒绝删除文件
- **WHEN** `READ_ONLY` run 调用 `delete_file`
- **THEN** 系统 MUST 拒绝工具调用，记录 `tool_call=REJECTED` 和 `audit_event`

### Requirement: 默认权限
`DEFAULT` SHALL 允许读取、搜索、常规编辑、覆盖、删除、运行测试、Git 本地操作、PR 草稿生成和本地 commit。系统 MUST 对高风险操作创建审批请求，审批通过后继续执行原工具调用，审批拒绝后把结构化拒绝结果回灌给 Agent。涉及 API：审批查询、审批通过、审批拒绝接口。涉及表：`tool_approval_request`、`tool_call`、`audit_event`。

#### Scenario: 默认权限首次打开会话
- **WHEN** 用户第一次打开一个未保存最后权限等级的 conversation
- **THEN** 客户端 MUST 默认选择“默认”，后端 MUST 将后续选择持久化到该 conversation

#### Scenario: 默认权限删除文件需要审批
- **WHEN** `DEFAULT` run 调用 `delete_file`
- **THEN** 系统 MUST 创建 `tool_approval_request` 并将 run 置为 `WAITING_APPROVAL`

#### Scenario: 默认权限审批通过继续执行
- **WHEN** 用户批准 `DEFAULT` run 的高风险工具请求
- **THEN** 系统 MUST 恢复 run 并继续执行原工具调用，不得重复生成同一审批请求

### Requirement: 完全控制权限
`FULL_ACCESS` SHALL 解锁全部本地 workspace 内仓库操作，包括删除、覆盖、Git 写入、PR 草稿和本地 commit。系统 MUST 不为高风险操作创建审批请求，但 MUST 保留 workspace 边界、受保护路径、危险命令、参数校验、敏感信息脱敏和审计。涉及表：`tool_call`、`audit_event`。

#### Scenario: 完全控制删除文件无需审批
- **WHEN** `FULL_ACCESS` run 调用 `delete_file`
- **THEN** 系统 MUST 直接执行工具，不创建 `tool_approval_request`，并写入绕过审批审计事件

#### Scenario: 完全控制仍拒绝 workspace 外路径
- **WHEN** `FULL_ACCESS` run 请求修改 workspace 外路径
- **THEN** 系统 MUST 拒绝工具调用并写入 `audit_event`

### Requirement: conversation 级权限记忆
系统 MUST 记录每个 conversation 最后一次选择的权限等级。用户切换 conversation 后，客户端 MUST 使用该 conversation 上次保存的等级；仅当 conversation 无历史选择时才默认“默认”。`agent_conversation.default_permission` 旧版属性 MUST 被移除语义并迁移/替换为最后一次权限等级字段，例如 `last_permission_level`。涉及 API：conversation 创建、conversation 更新、conversation 详情。涉及表：`agent_conversation`。

#### Scenario: 保存最后一次权限等级
- **WHEN** 用户在 conversation A 中将权限等级切换为 `FULL_ACCESS`
- **THEN** 系统 MUST 保存 conversation A 的 `lastPermissionLevel=FULL_ACCESS`

#### Scenario: 重新打开 conversation 恢复权限等级
- **WHEN** 用户重新打开 conversation A
- **THEN** 客户端 MUST 显示上次保存的 `FULL_ACCESS`，而不是重新回到“默认”

#### Scenario: 同一 workspace 下会话互不影响
- **WHEN** 同一 workspace 下 conversation A 保存为 `READ_ONLY`，conversation B 保存为 `FULL_ACCESS`
- **THEN** 客户端打开 conversation A 时 MUST 显示“只读”，打开 conversation B 时 MUST 显示“完全控制”

#### Scenario: 创建 run 使用会话权限
- **WHEN** 客户端在 conversation A 中创建 run 且未显式传入权限等级
- **THEN** 系统 MUST 使用 conversation A 的 `lastPermissionLevel` 作为该 run 的权限等级

#### Scenario: 更新会话权限
- **WHEN** 用户在 conversation A 中切换权限等级为 `DEFAULT`
- **THEN** 系统 MUST 更新 `agent_conversation.last_permission_level=DEFAULT`，不得写回旧字段语义 `default_permission`

## REMOVED Requirements

### Requirement: 旧 L1/L2/L3 权限等级
**Reason**: 旧等级“只读分析 / 安全编辑 / 仓库写入”不符合目标客户端产品语义，且和审批边界绑定过死。
**Migration**: 数据迁移时将 `L1_READ_ONLY` 映射为 `READ_ONLY`，将 `L2_SAFE_EDIT` 和 `L3_REPO_WRITE` 映射为 `DEFAULT`；用户之后可手动切换到 `FULL_ACCESS`。
