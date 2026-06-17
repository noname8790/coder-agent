## 0. 版本范围

- [ ] 0.1 将本变更作为 v4.1 版本交付，并在交付说明中标注 v4.1 的功能边界、破坏性变更和回滚方式

## 1. 数据模型与枚举迁移

- [ ] 1.1 新增数据库迁移脚本，将 conversation/run/message 中旧权限值迁移为 `READ_ONLY`、`DEFAULT`、`FULL_ACCESS`
- [ ] 1.2 将 `agent_conversation.default_permission` 迁移/重命名为会话最后一次权限等级字段，例如 `last_permission_level`，并补充中文 COMMENT 与回滚 SQL
- [ ] 1.3 更新 `AgentConversation` 相关实体、PO、DTO、Mapper 和 Repository，将 `defaultPermission` 替换为 `lastPermissionLevel`
- [ ] 1.4 更新 API DTO、领域枚举和映射逻辑，移除对外暴露的 `L1_READ_ONLY`、`L2_SAFE_EDIT`、`L3_REPO_WRITE`
- [ ] 1.5 补充权限等级迁移单元测试，覆盖旧值映射、conversation 级新值写入和字段回滚

## 2. 权限治理与审批策略

- [ ] 2.1 实现 `READ_ONLY`、`DEFAULT`、`FULL_ACCESS` 的工具能力矩阵
- [ ] 2.2 调整 `DEFAULT` 档：删除、覆盖、Git 写入、PR 草稿和本地 commit 可用但高风险动作需审批
- [ ] 2.3 调整 `FULL_ACCESS` 档：高风险工具无需审批但必须写 `audit_event`
- [ ] 2.4 修复审批通过后继续执行原工具调用的幂等链路，避免重复审批和重复请求
- [ ] 2.5 更新 conversation 创建、查询、切换和权限等级更新用例，确保首次会话默认 `DEFAULT`，已有会话恢复最后一次选择
- [ ] 2.6 更新 Agent run 创建用例，未显式传入权限等级时读取当前 conversation 的 `lastPermissionLevel`
- [ ] 2.7 补充审批策略测试，覆盖只读拒绝、默认审批、完全控制绕过审批和审计记录

## 3. Git / PR 工具链修复

- [ ] 3.1 梳理现有 Git/PR 工具和 `run_shell` 白名单，定位 git 命令超时原因
- [ ] 3.2 实现或强化 `git_status`、`git_diff`、`git_log`、`git_add`、`git_commit` 专用工具
- [ ] 3.3 为 Git 工具增加独立超时、输出截断、进程释放和结构化失败返回
- [ ] 3.4 实现或修复 `generate_pr_draft`，生成 `pull-request.md` 且不执行远程 push/API
- [ ] 3.5 补充 Git/PR 工具测试，覆盖成功、超时、失败、无 Git 仓库和权限不足场景

## 4. Diff 摘要与 API

- [ ] 4.1 在文件变更后生成 `changed-files.json`，包含路径、变更类型、增删行统计和总计
- [ ] 4.2 将 Diff 摘要关联到 run 或 Agent 消息，并在 run/message 详情 API 中返回
- [ ] 4.3 处理无文件变更、未初始化 Git 仓库和大 diff 截断场景
- [ ] 4.4 补充 Diff 摘要测试，覆盖新增、修改、删除、多文件和无变更

## 5. 客户端权限与消息体验

- [ ] 5.1 将权限选择器改为 Codex 风格三项选项，包含图标、标题、灰色描述、选中 √ 和完全控制黄色提示
- [ ] 5.2 客户端打开 conversation 时读取 `lastPermissionLevel`，无历史值时使用“默认”，并确保同一 workspace 下不同会话互不覆盖
- [ ] 5.3 Agent 消息改为 Markdown 安全渲染，禁用或清洗危险 HTML
- [ ] 5.4 每条用户消息和 Agent 消息下方增加复制图标，复制原始文本并显示短暂提示
- [ ] 5.5 在有文件变更的 Agent 消息下方展示 Diff 摘要卡片，默认显示前 3 个文件，支持展开全部
- [ ] 5.6 补充客户端组件测试或手动验证记录，覆盖权限选择、Markdown、复制和 Diff 展示

## 6. 真实冒烟测试与交付验证

- [ ] 6.1 使用真实本地测试仓库验证 `READ_ONLY` 下读仓库、搜索、git status/diff 不被误拦截
- [ ] 6.2 使用真实模型验证 `DEFAULT` 下删除文件触发审批，批准后继续执行且不重复请求审批
- [ ] 6.3 使用真实模型验证 `FULL_ACCESS` 下删除/覆盖/Git commit 不请求审批但写审计
- [ ] 6.4 使用真实模型验证“修改文件并生成 PR 草稿”任务，确认 `changed-files.json`、`pull-request.md` 和 Diff 卡片正确
- [ ] 6.5 验证 git/status/diff/log/add/commit 不再长时间超时，超时时能结构化失败并释放 run
- [ ] 6.6 运行后端 Maven 测试和前端构建测试，记录无法执行的测试原因
- [ ] 6.7 更新 README 或开发说明中的权限等级、Git/PR 能力和回滚说明
