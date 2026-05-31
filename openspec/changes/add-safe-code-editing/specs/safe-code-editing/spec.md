## ADDED Requirements

### Requirement: Agent run edit mode

系统 SHALL 在创建 Agent Run 时支持 `mode` 字段。`mode` MUST 支持 `READ_ONLY` 和 `EDIT`，未传入时 MUST 默认为 `READ_ONLY`。涉及 API：`POST /api/agent-runs`、`GET /api/agent-runs/{runId}`。涉及表：`agent_run`。

#### Scenario: 默认只读模式

- **WHEN** 调用方创建 Agent Run 且未传入 `mode`
- **THEN** 系统 MUST 使用 `READ_ONLY`
- **AND** 编辑工具 MUST 不可用

#### Scenario: 显式编辑模式

- **WHEN** 调用方创建 Agent Run 并传入 `mode=EDIT`
- **THEN** 系统 MUST 保存该运行模式
- **AND** 编辑工具是否可用 MUST 继续受 workspace capabilities 控制

### Requirement: Apply patch to existing files

系统 SHALL 在 `EDIT` 模式下提供 `apply_patch` 工具，用于修改 workspace 内已有文本文件。该工具 MUST 在写入前校验路径、文件存在性、禁止路径和 patch 格式。涉及工具：`apply_patch`。涉及表：`tool_call`、`audit_event`。

#### Scenario: 成功修改已有文件

- **WHEN** Agent 在 `EDIT` 模式下调用 `apply_patch` 修改 workspace 内已有允许文件
- **THEN** 系统 MUST 应用 patch
- **AND** 系统 MUST 记录工具调用、变更文件和 diff

#### Scenario: READ_ONLY 模式拒绝修改

- **WHEN** Agent 在 `READ_ONLY` 模式下调用 `apply_patch`
- **THEN** 系统 MUST 拒绝工具调用
- **AND** 系统 MUST 写入审计事件

#### Scenario: workspace 未授权修改时拒绝

- **WHEN** Agent 在 `EDIT` 模式下调用 `apply_patch` 但 workspace 不包含 `MODIFY_FILE`
- **THEN** 系统 MUST 拒绝工具调用
- **AND** 响应 MUST 说明缺少修改文件能力

#### Scenario: 禁止修改敏感路径

- **WHEN** Agent 调用 `apply_patch` 修改 `.env`、`.git/`、`.coder/`、`target/` 或密钥类文件
- **THEN** 系统 MUST 拒绝工具调用
- **AND** 系统 MUST 写入安全审计事件

### Requirement: Write new files only

系统 SHALL 在 `EDIT` 模式下提供 `write_file` 工具，用于在 workspace 内新建文本文件。该工具 MUST NOT 覆盖已有文件。涉及工具：`write_file`。涉及表：`tool_call`、`audit_event`。

#### Scenario: 成功新建文件

- **WHEN** Agent 在 `EDIT` 模式下调用 `write_file` 写入一个不存在且允许的 workspace 内路径
- **THEN** 系统 MUST 创建文件
- **AND** 系统 MUST 记录工具调用、变更文件和 diff

#### Scenario: 拒绝覆盖已有文件

- **WHEN** Agent 调用 `write_file` 写入已存在文件
- **THEN** 系统 MUST 拒绝工具调用
- **AND** 响应 MUST 说明不允许覆盖已有文件

#### Scenario: workspace 未授权新增时拒绝

- **WHEN** Agent 在 `EDIT` 模式下调用 `write_file` 但 workspace 不包含 `ADD_FILE`
- **THEN** 系统 MUST 拒绝工具调用
- **AND** 系统 MUST 写入审计事件

### Requirement: No file deletion in v2

系统 MUST NOT 在第二版开放删除文件工具。涉及工具：`delete_file`。

#### Scenario: 删除工具不可用

- **WHEN** 模型请求调用 `delete_file`
- **THEN** 系统 MUST 按未知工具或不支持工具拒绝
- **AND** 系统 MUST NOT 删除任何文件
