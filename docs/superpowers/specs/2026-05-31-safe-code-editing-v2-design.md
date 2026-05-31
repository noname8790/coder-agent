# Safe Code Editing V2 Design

## 目标

第二版将 `coder-agent` 从“只读分析 Agent”升级为“可安全修改代码的 Agent”。它不再只面向当前 `coder-agent` 仓库，而是允许用户通过 REST API 注册任意本地项目目录为 workspace，再通过 `workspaceKey` 选择目标项目执行任务。

核心闭环：

```text
注册 workspace
-> 创建 Agent Run，选择 workspaceKey 和 mode
-> Agent 读仓库、搜索、分析
-> EDIT 模式下受控修改文件
-> 运行测试/构建命令
-> 生成 diff、变更文件清单、测试报告、审查摘要
-> 用户人工审查并决定是否提交
```

## 范围

第二版包含两条主线：

- Workspace 管理：新增注册、查询、停用 workspace 的 REST API 和数据库表。
- 安全编辑闭环：新增 `READ_ONLY/EDIT` 运行模式、受控编辑工具、测试验证和审查工件。

第二版不做：

- Git commit / push / reset / PR
- 文件删除
- Web UI
- 长期记忆、向量库、benchmark 平台
- 多用户认证
- Linux/macOS Shell 兼容

## Workspace 管理

用户可通过 API 注册任意本地绝对目录作为 workspace。系统不限制项目所在父目录，但会做基础合法性校验：

- 必须是绝对路径
- 路径必须存在
- 必须是目录
- 不能是 Windows 盘符相对路径，例如 `E:project`
- 保存前进行规范化

新增接口：

```text
POST   /api/workspaces
GET    /api/workspaces
GET    /api/workspaces/{workspaceKey}
DELETE /api/workspaces/{workspaceKey}
```

删除采用逻辑停用，不物理删除历史记录。

## Workspace 能力

workspace 注册时可以选择能力：

```text
READ_REPOSITORY
GIT_READ
RUN_TEST
RUN_BUILD
ADD_FILE
MODIFY_FILE
```

不传 capabilities 时使用全局默认能力。默认能力应保持保守，不包含 `ADD_FILE` 或 `MODIFY_FILE`。

能力与内部工具/命令映射：

- `READ_REPOSITORY`：`list_files`、`read_file`、`search_text`
- `GIT_READ`：`git status`、`git diff`、`git log`
- `RUN_TEST`：测试命令
- `RUN_BUILD`：构建命令
- `ADD_FILE`：`write_file`
- `MODIFY_FILE`：`apply_patch`

## 运行模式

创建 Agent Run 时新增 `mode`：

```text
READ_ONLY
EDIT
```

默认 `READ_ONLY`。

Agent 能修改文件必须同时满足：

```text
本次任务 mode=EDIT
workspace capabilities 包含 ADD_FILE 或 MODIFY_FILE
```

这形成双保险：workspace 是长期授权，本次 mode 是任务意图。

## 编辑工具

第二版新增：

- `apply_patch`：只允许修改 workspace 内已有文本文件。
- `write_file`：只允许新建文件，不允许覆盖已有文件。

第二版不开放：

- `delete_file`
- 整文件覆盖
- Git commit / push / reset

编辑工具必须拒绝：

- 路径逃逸
- `.env`、`.env.*`
- `.git/`
- `.coder/`
- `target/`
- 密钥类文件名

## 工件

在原有 `.coder/runs/{runId}/` 基础上新增：

```text
patch.diff
changed-files.json
test-report.json
review-summary.md
```

`changed-files.json` 记录：

- workspace 相对路径
- 变更类型
- 修改前摘要
- 修改后摘要
- 关联工具调用编号

`review-summary.md` 面向人工审查，包含任务、模式、模型、变更文件、测试结果和审查重点。

## 风险与处理

- 用户注册过大目录：允许注册，但工具继续限制输出数量、读取大小和路径边界。
- Agent 修改敏感文件：受保护路径策略拒绝并写审计。
- Patch 无法应用：不写文件，记录工具失败。
- 测试失败：保留 diff 和测试报告，最终结果标记测试未通过，不自动回滚。
- workspace 被移动：创建运行和执行前重新校验，不可用时拒绝或失败。

## 验收标准

- 能通过 API 注册一个本地项目 workspace。
- 能使用该 workspace 创建 `READ_ONLY` 任务并保持只读。
- 能使用 `EDIT` 模式完成一次受控文件修改。
- 能生成 `patch.diff`、`changed-files.json`、`test-report.json`、`review-summary.md`。
- 能在权限不足时拒绝编辑工具并写审计事件。
- 能跑通 Maven 测试和 OpenSpec 校验。
