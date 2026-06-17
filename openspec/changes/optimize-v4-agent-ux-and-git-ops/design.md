## Context

本变更版本定位为 v4.1，是在 v4 已交付的 Harness、流式、记忆、工具治理和客户端基础上做产品化收敛优化。

v4 已经具备模型配置、流式输出、上下文治理、结构化记忆、审批和基础客户端，但当前风险等级仍沿用 L1/L2/L3 语义，用户理解成本高；Agent 消息仍偏普通文本展示，无法很好承载模型输出的 Markdown；Git/PR 任务存在超时和反馈不稳定问题，导致仓库级任务无法稳定闭环。

本次变更是 v4 优化，不重新设计 Harness 主循环，也不引入远程 GitHub/GitLab PR 创建能力。重点是把已有能力收敛成可交付的产品体验：更清晰的权限模型、可读的 Agent 消息、可审查的 Diff 摘要、可验证的 Git/PR 工具链。

## Goals / Non-Goals

**Goals:**
- 用“只读 / 默认 / 完全控制”替换旧风险等级，并按 conversation 持久化最后一次选择。
- 将删除、Git、PR、本地 commit 等能力纳入“默认”档，但高风险动作仍请求审批。
- “完全控制”档解锁全部本地仓库能力，高风险动作不再请求审批，但仍受 workspace 路径边界、参数校验、脱敏和审计约束。
- 前端权限选择器对齐 Codex 风格：图标、标题、说明、选中标识和黄色高风险提示。
- Agent 消息使用 Markdown 安全渲染，并支持复制消息原文。
- Agent 修改文件后展示 Diff 摘要，默认展示前 3 个文件，展开展示全部文件。
- 修复 Git/PR 工具超时和 Harness 推进问题，并要求使用真实本地仓库冒烟测试验证。

**Non-Goals:**
- 不实现远程 push、远程 PR 创建、GitHub/GitLab OAuth 或仓库托管平台集成。
- 不取消 workspace 路径边界、敏感路径保护、参数校验和审计。
- 不把“完全控制”解释为可访问 workspace 外任意路径。
- 不重做 v4 的上下文治理、记忆系统和模型配置体系，只在本变更需要时补充 Diff/Git 相关上下文和审计。

## Decisions

### 1. 权限等级采用产品语义枚举，内部保留安全策略矩阵

对外枚举改为：
- `READ_ONLY`：只读
- `DEFAULT`：默认
- `FULL_ACCESS`：完全控制

内部不再使用旧 `L1_READ_ONLY`、`L2_SAFE_EDIT`、`L3_REPO_WRITE` 作为 API 或数据库值。工具可执行性由 `PermissionPolicy` 矩阵统一判断：工具类别、命令类别、是否需要审批、是否允许绕过审批、是否需要审计。

备选方案是仅改前端文案，后端继续存旧枚举。该方案会造成 API、数据库和 UI 语义不一致，不利于后续维护，因此不采用。

### 2. conversation 记录最后一次权限等级

第一次打开或创建 conversation 时默认 `DEFAULT`。用户在对话中切换权限等级后，后端立即更新 `agent_conversation` 的最后一次权限等级。再次打开该 conversation 时，客户端从会话详情读取该值并作为默认选择。

这避免把权限偏好存在客户端本地，也避免同一 workspace 下不同会话互相覆盖权限等级。`agent_conversation.default_permission` 的旧“默认权限”语义不再保留，应迁移/重命名为 `last_permission_level`，表示该会话最后一次选择的权限等级。

### 3. 默认档保留审批，完全控制档绕过审批但写审计

`DEFAULT` 允许删除、覆盖、Git 写入、PR 草稿、本地 commit 等基础仓库能力，但当工具被标记为高风险时必须创建审批请求。用户批准后继续执行原工具调用，不允许重复请求同一审批。

`FULL_ACCESS` 不创建审批请求，直接执行高风险工具；但必须写入 `audit_event`，记录 permissionLevel、toolName、arguments 摘要、runId、workspaceKey 和绕过审批原因。

### 4. Git/PR 命令使用专用工具优先，Shell 作为受控兜底

Git 能力应尽量通过专用工具实现，例如 `git_status`、`git_diff`、`git_log`、`git_add`、`git_commit`、`generate_pr_draft`。专用工具负责超时、参数规范化、输出截断、错误分类和审计。`run_shell` 仅作为受控兜底，避免 LLM 拼出不稳定命令。

Git 命令超时独立于普通 shell 超时，默认应短于长任务总超时，且支持按命令类别配置：
- read-only Git：10-20 秒
- diff/status/log：20-30 秒
- add/commit：30-60 秒

### 5. Diff 摘要由后端生成结构化数据，前端只负责渲染

后端在 run 结束或每次文件变更后生成 `changed-files.json`，并通过 run/message 详情返回：
- 文件路径
- 变更类型：ADD / MODIFY / DELETE / RENAME
- addedLines / deletedLines
- 是否可展开
- 可选 patch 片段路径

前端默认展示前 3 个文件和总增删行数；展开后显示全部文件。前端不自行执行 git diff，也不解析非结构化 shell 输出。

### 6. Markdown 渲染必须安全降级

前端使用 Markdown 渲染 Agent 消息，但必须禁用原始 HTML 或进行严格清洗，避免模型输出 HTML/脚本造成风险。复制按钮复制消息原始文本，不复制渲染后的 HTML。

## Risks / Trade-offs

- [Risk] 旧数据库中已有旧权限枚举值。→ 通过迁移脚本映射：`L1_READ_ONLY -> READ_ONLY`，`L2_SAFE_EDIT -> DEFAULT`，`L3_REPO_WRITE -> DEFAULT`，未知值降级为 `DEFAULT` 并写迁移日志。
- [Risk] “完全控制”绕过审批可能造成误操作。→ 仍保留 workspace 边界、敏感路径保护、危险命令拒绝、审计事件和客户端黄色风险提示。
- [Risk] Git 命令在不同仓库状态下输出差异大。→ 专用 Git 工具统一解析 exit code、stdout、stderr，并将失败原因结构化回灌给 Agent。
- [Risk] Markdown 渲染引入 XSS 风险。→ 禁用 raw HTML，外链不自动执行，代码块只渲染文本。
- [Risk] Diff 大文件导致前端卡顿。→ 后端只返回摘要和截断 patch；完整 patch 作为工件路径按需读取。

## Migration Plan

1. 增加数据库迁移：将 `agent_conversation.default_permission` 迁移/重命名为 `last_permission_level`，run/message 权限字段迁移到新枚举。
2. 增加后端枚举和映射层，删除旧枚举对外暴露。
3. 调整工具治理矩阵和审批策略。
4. 增加 Git 专用工具和 Diff 摘要生成。
5. 调整 run/message API 返回 Diff 摘要。
6. 调整客户端权限选择器、Markdown 渲染、复制按钮和 Diff 摘要组件。
7. 执行单元测试、集成测试和真实冒烟测试。

回滚时先回滚客户端展示，再回滚后端枚举映射和数据库字段；Git/PR 工具可通过配置开关降级为只生成文本 PR 草稿。

## Open Questions

- 是否需要把“完全控制”的黄色提示文案固定为产品级文案，还是允许后端配置返回。
- Diff 展开后是否只显示文件列表，还是同时显示 patch 片段。本次默认只要求文件列表和增删统计，patch 片段可作为实现增强。
