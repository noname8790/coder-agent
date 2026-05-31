## Why

首版 `coder-agent` 已经能围绕服务端配置的 workspace 完成读仓库、搜索、运行受限诊断命令和生成结论，但还不能修改代码。第二版需要把能力推进到“稳定、安全地改代码”，让 Agent 能在用户选择的本地项目中完成受控文件修改、测试验证和 diff 交付。

同时，`coder-agent` 不应只服务当前自身仓库，而应允许用户通过 API 注册任意本地项目目录作为 workspace，并在创建任务时通过 `workspaceKey` 选择目标项目。

## What Changes

- 新增 workspace 管理 API，支持注册、查询、停用用户本地项目 workspace。
- 新增 `agent_workspace` 表，保存 workspaceKey、rootPath、能力开关、状态和审计字段。
- 创建 Agent Run 时继续使用 `workspaceKey` 选择目标项目，workspaceRoot 从数据库中解析。
- 创建 Agent Run 新增 `mode` 字段，支持 `READ_ONLY` 和 `EDIT`；默认 `READ_ONLY`。
- 新增 workspace 能力枚举：`READ_REPOSITORY`、`GIT_READ`、`RUN_TEST`、`RUN_BUILD`、`ADD_FILE`、`MODIFY_FILE`。
- 新增安全编辑工具：
  - `apply_patch`：只允许修改 workspace 内已有文件。
  - `write_file`：只允许新建文件，不允许覆盖已有文件。
- 第二版不开放删除文件，不开放 Git commit/push/reset，不自动生成 PR。
- 编辑工具必须同时满足本次运行 `mode=EDIT` 和 workspace capability 授权。
- 新增变更工件：`patch.diff`、`changed-files.json`、`test-report.json`、`review-summary.md`。
- 测试/构建命令通过 workspace capability 与全局默认命令策略映射，不要求用户理解底层命令前缀。

## Capabilities

### New Capabilities

- `workspace-management`: 管理用户注册的本地项目 workspace，包括注册、查询、停用、路径校验和能力配置。
- `safe-code-editing`: 在 `EDIT` 模式下提供受控文件新增、补丁修改、测试验证、diff 工件和审查摘要。

### Modified Capabilities

- `agent-run-lifecycle`: 创建运行新增 `mode` 字段，并在运行结果中暴露编辑模式、变更文件、测试结果和审查摘要索引。
- `agent-tool-calling`: 工具体系新增编辑工具，并按 workspace capability 和运行 mode 做权限控制。
- `workspace-governance`: workspace 解析从静态配置扩展为数据库动态注册，并继续保持路径边界校验。
- `run-audit-artifacts`: 运行工件新增代码变更、测试报告和审查摘要相关文件。

## Impact

- 影响模块：
  - `coder-agent-api`：新增 workspace DTO，扩展创建运行 DTO。
  - `coder-agent-domain`：新增 workspace 领域对象、能力枚举、编辑相关值对象和端口。
  - `coder-agent-case`：新增 workspace 用例，扩展 Agent 执行循环和编辑权限判断。
  - `coder-agent-infrastructure`：新增 workspace 持久化、编辑工具、diff 生成和工件写入实现。
  - `coder-agent-trigger`：新增 workspace REST Controller，扩展 Agent Run API。
  - `coder-agent-app`：新增配置项和测试配置。
- 影响数据库表：
  - 新增 `agent_workspace`。
  - 可能扩展 `agent_run`，增加 `mode`。
  - 可能扩展或新增编辑记录表，用于记录文件修改摘要。
- 影响 API：
  - 新增 `/api/workspaces` 系列接口。
  - 扩展 `POST /api/agent-runs` 请求体。
  - 扩展 `GET /api/agent-runs/{runId}` 响应体。
- 回滚方案：
  - 停止使用 `/api/workspaces` 和 `mode=EDIT`。
  - 禁用编辑工具注册，仅保留首版只读工具。
  - 将创建运行默认退回 `READ_ONLY`。
  - 保留 `agent_workspace` 和编辑工件历史数据不再写入，必要时通过 SQL 删除新增表和新增列。
  - 删除 workspace 下对应 `.coder/runs/{runId}/patch.diff`、`changed-files.json`、`test-report.json`、`review-summary.md` 工件。
