# coder-agent 客户端 UI 设计稿

Pencil MCP 当前要求 VS Code 中打开 `.pen` 文件后才能写入画布，本次会话无法直接操作画布文件。第三版客户端 UI 设计按以下稿件落地，并已同步到 `coder-agent-client` 实现。

## 主界面

- 左侧栏：品牌区、新对话、工作区添加/列表、对话列表、设置入口。
- 中心区：当前工作区标题、会话消息、任务输入框、权限等级选择、模型选择、提交按钮、权限风险提示。
- 右侧审查区：后端连接状态、运行状态、变更文件、测试状态、本地分支、commit hash、工件列表、最终回答。

## 运行详情/审查页

- changed files：通过 `AgentRunResponseDTO.artifacts` 和 `changedFileCount` 展示。
- diff / rollback / PR 草稿：通过工件列表展示 `patch.diff`、`rollback.patch`、`pull-request.md` 等路径。
- test report：展示 `testStatus` 和相关工件。
- commit 信息：展示 `gitBranch`、`commitHash`。
- 运行进度：通过 `GET /api/agent-runs/{runId}/events` SSE 驱动临时消息进度和终态同步，客户端不展示独立事件流面板。

## 视觉约束

- 桌面 Agent 客户端布局，不做浏览器 landing page。
- 浅色、低干扰、偏开发工具风格。
- 控件圆角不超过 8px。
- 权限等级和风险提示必须在提交前可见。
