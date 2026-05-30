## ADDED Requirements

### Requirement: workspaceKey 工作区解析

系统 SHALL 只接受 `workspaceKey` 作为工作区输入，并通过服务端配置解析为本地 workspace 根目录。

涉及 API：`POST /api/agent-runs`  
涉及表：`agent_run`、`audit_event`

#### Scenario: 解析已配置工作区

- **GIVEN** 服务端配置存在 `workspaceKey=coder-agent`
- **WHEN** 客户端创建运行并传入该 `workspaceKey`
- **THEN** 系统解析到对应 workspace 根目录
- **AND** `agent_run` 仅记录 `workspaceKey`，不默认暴露完整绝对路径

#### Scenario: 拒绝未知工作区

- **GIVEN** 请求中的 `workspaceKey` 未配置
- **WHEN** 客户端创建运行
- **THEN** 系统拒绝请求
- **AND** 记录安全审计事件

### Requirement: 路径边界校验

系统 MUST 对所有文件工具参数进行规范路径解析，并拒绝访问 workspace 根目录之外的路径。

涉及 API：`POST /api/agent-runs`、`GET /api/agent-runs/{runId}/trace`  
涉及表：`tool_call`、`audit_event`

#### Scenario: 拦截路径逃逸

- **GIVEN** 模型请求读取 `../secret.txt`
- **WHEN** 工具执行器解析规范路径
- **THEN** 系统拒绝工具调用
- **AND** 记录 `REJECTED` 工具结果和路径逃逸审计事件

#### Scenario: 允许 workspace 内路径

- **GIVEN** 模型请求读取 workspace 内的 `pom.xml`
- **WHEN** 工具执行器解析规范路径
- **THEN** 系统允许继续执行工具调用

### Requirement: Shell 两层安全策略

系统 MUST 在 Windows PowerShell 中执行 `run_shell`，并同时执行命令前缀白名单检查和危险 token 拒绝检查。首版不兼容 Linux/macOS Shell。

涉及 API：`POST /api/agent-runs`、`GET /api/agent-runs/{runId}/trace`  
涉及表：`tool_call`、`audit_event`

#### Scenario: 允许默认白名单命令

- **GIVEN** workspace 使用默认 Shell 白名单
- **WHEN** 模型请求执行 `git status`、`git diff`、`git log`、`mvn test`、`mvn -q test`、`mvn clean test`、`mvn package`、`mvn -pl` 或 `java -version` 前缀命令
- **THEN** 系统允许执行
- **AND** 记录命令输出摘要

#### Scenario: 使用 PowerShell 执行命令

- **GIVEN** 模型请求执行通过安全策略的 Shell 命令
- **WHEN** 系统启动本地进程
- **THEN** 系统使用 Windows PowerShell 执行命令
- **AND** 不要求兼容 bash、sh、zsh 或其他系统 Shell

#### Scenario: 拒绝危险 token

- **GIVEN** 模型请求执行包含 `&&`、`|`、`>`、`rm`、`del`、`git reset`、`git push` 或 `git commit` 的命令
- **WHEN** Shell 策略检查命令
- **THEN** 系统拒绝执行
- **AND** 记录安全拒绝审计事件

#### Scenario: 拒绝非白名单命令

- **GIVEN** 模型请求执行未配置允许前缀的命令
- **WHEN** Shell 策略检查命令
- **THEN** 系统拒绝执行
- **AND** 不启动本地进程
