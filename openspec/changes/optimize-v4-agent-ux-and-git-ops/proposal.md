## Version

v4.1

## Why

v4 已完成基础 Harness、流式、记忆和客户端能力，但权限等级命名、Agent 消息展示、Git/PR 工具链仍不符合目标产品体验。当前高风险审批、Markdown 输出、复制、Diff 展示和 Git 任务超时问题会直接影响用户对 coding agent 的信任和可用性，因此需要在 v4 基础上做一次收敛优化。

## What Changes

- **BREAKING**：将旧风险等级“只读分析 / 安全编辑 / 仓库写入”替换为“只读 / 默认 / 完全控制”。
- **BREAKING**：默认风险等级改为“默认”，但仅在第一次打开某个会话时使用；后续按 conversation 记住用户在该会话中最后一次选择的等级。
- 调整工具治理策略：
  - “只读”：仅允许仓库读取、搜索、诊断类低风险工具。
  - “默认”：允许常规编辑、删除、Git、PR 草稿和本地 commit 等基础仓库操作；检测到高风险操作时需要审批。
  - “完全控制”：解锁全部本地仓库操作，高风险操作不再请求审批；仍保留路径边界、参数校验、敏感信息脱敏和运行审计。
- 前端权限选择器改为类似 Codex 的分组选项样式：左侧图标、黑色等级名称、灰色描述、选中项右侧 “√”，其中“完全控制”使用黄色风险提示。
- Agent 消息正文使用 Markdown 渲染，并在每条用户/Agent 消息下方提供复制图标。
- Agent 任务改动文件后，在 Agent 消息下方展示 Diff 摘要：默认折叠显示前 3 个文件，展开后显示全部变更文件和增删行统计。
- 修复 Git/PR 相关工具超时与 Harness 推进问题，确保生成 PR 草稿、本地 commit、git diff/status/log 等任务可完成并有结构化反馈。
- 对 Git、PR、Diff 展示、权限等级持久化和审批行为补充真实冒烟测试要求。

## Capabilities

### New Capabilities
- `agent-message-markdown-diff`: Agent 消息 Markdown 渲染、复制操作和文件 Diff 摘要展示。

### Modified Capabilities
- `agent-permission-levels`: 替换旧风险等级命名、默认值、审批边界和 conversation 级持久化行为。
- `tool-governance-approval`: 调整高风险审批策略，支持“完全控制”绕过审批但保留安全边界和审计。
- `agent-repo-operations`: 修复并强化 Git、PR、本地 commit 和 Diff 生成任务的可执行性与反馈要求。
- `agent-client-v4`: 调整客户端权限选择器样式、消息操作入口和 Diff 展示交互。

## Impact

- 影响模块：
  - `coder-agent-api`：请求/响应 DTO 中的权限等级枚举、conversation 记录字段和 Diff 数据结构。
  - `coder-agent-domain`：权限等级模型、工具治理策略、审批策略、仓库操作领域对象。
  - `coder-agent-case`：创建/恢复 conversation 权限等级、Agent run 执行流程、PR/Git/Diff 结果装配。
  - `coder-agent-infrastructure`：工具白名单、Git/Shell 命令执行超时、Diff 收集、审计和数据库持久化。
  - `coder-agent-trigger`：conversation 权限等级 API、审批 API、run 详情和消息详情接口。
  - `coder-agent-client`：权限选择器、Markdown 渲染、复制按钮、Diff 摘要组件。
- 影响对象/服务/方法：
  - `AgentConversation` 相关实体/PO/DTO：移除 `defaultPermission` 语义，替换为 `lastPermissionLevel`。
  - conversation 创建、查询、切换、更新权限等级用例：首次会话默认 `DEFAULT`，已有会话恢复 `lastPermissionLevel`。
  - Agent run 创建用例：未显式传入权限等级时，读取当前 conversation 的 `lastPermissionLevel`。
  - 客户端会话切换逻辑：切换会话时刷新权限选择器，不再使用 workspace 级权限缓存。
- 影响数据库表：
  - `agent_conversation.default_permission` 旧字段语义需要移除，迁移/替换为会话最后一次选择的权限等级记录，例如 `last_permission_level`。
  - run/message 详情需要能关联 changed files / diff summary。
  - tool call、approval、audit_event 需要覆盖 Git/PR 和完全控制绕过审批的审计记录。
- 依赖影响：
  - 前端需要 Markdown 渲染库和安全渲染策略。
  - 后端无需新增外部服务，但需要统一 Git 命令执行超时和输出截断策略。
- 回滚方案：
  - 保留数据库迁移回滚脚本，将 conversation 权限等级字段回滚到旧枚举值或默认值。
  - 客户端可回滚到普通文本渲染和旧权限选择器。
  - Git/PR 工具修复可按工具开关关闭，回退到仅生成文本 PR 草稿。
