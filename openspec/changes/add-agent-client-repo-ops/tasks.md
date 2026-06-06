## 1. UI 设计与客户端脚手架

- [x] 1.1 使用 Pencil 在 `openspec/changes/add-agent-client-repo-ops/design-ui/` 记录客户端主界面设计稿，覆盖侧栏、项目列表、会话列表、任务输入、权限等级选择和运行状态区。
- [x] 1.2 使用 Pencil 记录运行详情/审查页设计稿，覆盖 changed files、diff、test report、commit 信息、PR 草稿和 rollback 材料。
- [x] 1.3 创建 `coder-agent-client` Tauri + React + TypeScript + Vite 客户端模块。
- [x] 1.4 添加客户端基础布局、状态管理和 API client。
- [x] 1.5 实现客户端后端地址配置、健康检查、连接状态和错误提示。
- [x] 1.6 实现客户端自动启动本地后端进程、退出时停止托管进程，并保留连接已有后端模式。

## 2. 数据库与领域模型

- [x] 2.1 编写数据库迁移 SQL，新增 `agent_conversation`、`agent_message`、`agent_permission_audit`。
- [x] 2.2 扩展 `agent_run`，新增 `conversation_id`、`permission_level`、`git_branch`、`commit_hash`。
- [x] 2.3 扩展工件类型，新增 PR 草稿、rollback patch、file backup 相关枚举。
- [x] 2.4 在 Domain 层新增 Conversation、AgentMessage、AgentPermissionLevel、PermissionAudit 等领域对象。
- [x] 2.5 在 Domain 层定义权限等级到工具能力的映射规则。
- [x] 2.6 在 Infrastructure 层新增会话、消息、权限审计 MyBatis-Plus PO、DAO 和 Repository。

## 3. 会话与历史 API

- [x] 3.1 使用 Given/When/Then 编写会话创建、列表、详情和消息历史测试。
- [x] 3.2 实现 `POST /api/conversations` 创建会话。
- [x] 3.3 实现 `GET /api/conversations` 查询会话列表。
- [x] 3.4 实现 `GET /api/conversations/{conversationId}` 查询会话详情。
- [x] 3.5 实现 `GET /api/conversations/{conversationId}/messages` 查询消息历史。
- [x] 3.6 改造创建 Agent Run 用例，支持 `conversationId` 并自动写入用户消息。
- [x] 3.7 改造 Agent Run 终态处理，写入 Agent 最终回答、失败反馈或取消反馈消息。
- [x] 3.8 实现会话删除、消息删除、用户消息修改并重跑。

## 4. 权限等级改造

- [x] 4.1 使用 Given/When/Then 编写 L1/L2/L3 工具权限测试。
- [x] 4.2 新增 `GET /api/permission-levels`，返回权限等级说明、允许能力、禁止能力和风险提示。
- [x] 4.3 扩展创建运行请求，支持 `permissionLevel`，默认使用 `L1_READ_ONLY`。
- [x] 4.4 改造 `AgentContextAssembler`，将权限等级和允许/禁止能力注入模型上下文。
- [x] 4.5 改造 `ToolGateway`，由权限等级控制工具定义可见性和执行权限。
- [x] 4.6 创建 L3 运行时写入 `agent_permission_audit`。
- [x] 4.7 保留第二版 workspace capabilities 字段兼容，但客户端和第三版主流程不再暴露 capability 勾选。

## 5. 仓库文件操作扩展

- [x] 5.1 使用 TDD 编写 `overwrite_file` 工具测试，覆盖成功覆盖、缺少 L3、路径逃逸、受保护路径、目录覆盖拒绝。
- [x] 5.2 实现 `overwrite_file`，覆盖前记录 beforeHash/afterHash 并生成备份。
- [x] 5.3 使用 TDD 编写 `delete_file` 工具测试，覆盖成功删除、缺少 L3、路径逃逸、受保护路径、目录删除拒绝。
- [x] 5.4 实现 `delete_file`，删除前备份原文件并记录 DELETE 变更。
- [x] 5.5 扩展 changed-files 记录，支持 ADD/MODIFY/OVERWRITE/DELETE。
- [x] 5.6 扩展 `patch.diff` 生成，覆盖覆盖文件和删除文件场景。
- [x] 5.7 实现 `rollback.patch` 和 `file-backup/` 生成。

## 6. Git 本地提交与 PR 草稿

- [x] 6.1 使用 TDD 编写 Git 权限测试，覆盖 L1/L2 拒绝 commit、L3 允许 branch/add/commit、所有等级拒绝 push/clean/reset hard。
- [x] 6.2 扩展 `run_shell`，支持 `git checkout -b`、`git add`、`git commit`。
- [x] 6.3 记录 gitBranch、commitHash、Git 命令输出和失败原因。
- [x] 6.4 实现 PR 草稿生成器，输出标题、摘要、变更文件、测试结果、风险点、回滚说明和 reviewer checklist。
- [x] 6.5 将 `pull-request.md` 写入 `.coder/runs/{runId}/` 并登记 `run_artifact`。
- [x] 6.6 扩展 `final-result.json`，包含 permissionLevel、gitBranch、commitHash、prDraftPath、rollbackArtifacts。

## 7. SSE 运行事件流

- [x] 7.1 定义 AgentRunEvent 对象和事件类型枚举。
- [x] 7.2 实现运行事件发布端口，在运行状态、模型调用、工具调用、文件变更、测试报告、Git commit、PR 草稿和终态时发布事件。
- [x] 7.3 实现 `GET /api/agent-runs/{runId}/events` SSE 接口。
- [x] 7.4 添加 SSE Controller 测试，覆盖运行中订阅、终态事件和错误 runId。
- [x] 7.5 支持客户端重连后通过 trace/history 补齐事件。

## 8. 客户端功能实现

- [x] 8.1 实现 workspace 创建、列表、详情和停用页面。
- [x] 8.2 实现会话侧栏、新对话、会话列表、会话删除和会话详情。
- [x] 8.3 实现任务输入框，支持模型选择、权限等级选择和风险说明。
- [x] 8.4 实现 SSE 订阅，用于运行中临时消息进度、终态同步和断流后的运行状态刷新；客户端不再展示独立事件流面板。
- [x] 8.5 实现运行详情和审查区，展示 changed files、test report、commit hash、PR 草稿和 rollback 材料路径。
- [x] 8.6 实现取消运行、刷新状态、空状态、失败状态和 loading 状态。
- [x] 8.7 实现工作区目录选择器，避免用户手工输入路径。
- [x] 8.8 实现用户消息修改/删除、Agent 消息删除和修改后重跑。

## 9. 文档、验证与回滚

- [x] 9.1 更新 README，说明客户端启动、后端自动托管、权限等级、会话历史、SSE、Git commit 和 PR 草稿。
- [x] 9.2 更新 SQL 初始化和迁移说明，标注新增表、字段和回滚 SQL。
- [x] 9.3 使用实验项目完成 L1 只读分析冒烟测试。
- [x] 9.4 使用实验项目完成 L2 安全编辑 + 测试验证冒烟测试。
- [x] 9.5 使用实验项目完成 L3 覆盖/删除文件、本地 commit 和 PR 草稿冒烟测试。
- [x] 9.6 运行 `mvn test` 或相关后端测试验证。
- [x] 9.7 运行客户端单元测试和构建命令。
- [x] 9.8 运行 `openspec validate add-agent-client-repo-ops` 验证规格一致性。
- [x] 9.9 明确第三版回滚步骤，覆盖客户端模块、数据库表字段、工具注册和新增工件。
