## ADDED Requirements

### Requirement: 桌面客户端启动与连接后端
系统 SHALL 提供桌面客户端，用于启动、停止或连接本地 `coder-agent-app` 后端服务。客户端 MUST 展示后端连接状态、端口、启动错误和最近后端日志入口。涉及 API：`GET /actuator/health` 或等价健康检查接口、客户端本地进程控制接口。涉及表：无。

#### Scenario: 客户端启动后端成功
- **WHEN** 用户在客户端点击启动后端服务
- **THEN** 客户端启动本地后端进程并显示连接成功状态

#### Scenario: 客户端连接已有后端
- **WHEN** 用户配置 `http://localhost:8080` 并点击连接
- **THEN** 客户端通过健康检查确认后端可用并进入主界面

#### Scenario: 后端端口冲突
- **WHEN** 客户端启动后端时端口已被占用
- **THEN** 客户端显示端口冲突提示并允许用户修改端口或连接已有服务

### Requirement: Workspace 客户端管理
客户端 SHALL 支持 workspace 注册、列表、详情和停用。第三版客户端 MUST 不再暴露第二版 capability 勾选项，workspace 仅代表项目目录和状态。涉及 API：`POST /api/workspaces`、`GET /api/workspaces`、`GET /api/workspaces/{workspaceKey}`、`DELETE /api/workspaces/{workspaceKey}`。涉及表：`agent_workspace`。

#### Scenario: 注册本地项目
- **WHEN** 用户在客户端选择本地项目目录并输入 workspaceKey
- **THEN** 客户端调用注册 API 并在项目列表中展示该 workspace

#### Scenario: 停用项目
- **WHEN** 用户停用一个 workspace
- **THEN** 客户端调用停用 API 并从 active 项目列表中移除该 workspace

### Requirement: Agent 对话式任务输入
客户端 SHALL 提供类似 Agent 客户端的对话输入界面，支持选择 workspace、模型、权限等级并发送任务。界面 MUST 使用侧栏项目/会话、中心对话输入、审查详情区域的布局。涉及 API：`POST /api/conversations`、`POST /api/agent-runs`。涉及表：`agent_conversation`、`agent_message`、`agent_run`。

#### Scenario: 创建新对话并发送任务
- **WHEN** 用户选择 workspace、模型、权限等级并输入任务
- **THEN** 客户端创建或复用会话并创建一次 Agent Run

#### Scenario: 权限等级说明展示
- **WHEN** 用户打开权限等级选择器
- **THEN** 客户端展示每个等级允许和禁止的功能说明

### Requirement: 审查视图
客户端 SHALL 展示 Agent Run 的审查材料，包括 changed files、patch diff、test report、commit hash、PR 草稿和 rollback 材料。涉及 API：`GET /api/agent-runs/{runId}`、`GET /api/agent-runs/{runId}/trace`、后续工件读取 API。涉及表：`run_artifact`。

#### Scenario: 查看 diff 和测试报告
- **WHEN** Agent Run 完成并生成编辑工件
- **THEN** 客户端展示变更文件、diff、测试状态和测试报告

#### Scenario: 查看 PR 草稿
- **WHEN** Agent Run 在 L3 权限下生成 PR 草稿
- **THEN** 客户端展示 `pull-request.md` 内容并提供复制入口
