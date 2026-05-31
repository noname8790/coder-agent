## 1. Workspace 数据模型与持久化

- [ ] 1.1 使用 Given/When/Then 编写 workspace 注册领域/用例测试，覆盖有效目录、非法路径、重复 workspaceKey、默认 capabilities
- [ ] 1.2 新增 `agent_workspace` 初始化 SQL，字段包含 workspaceKey、rootPath、capabilities、status、createdAt、updatedAt、deletedAt
- [ ] 1.3 在 Domain 层新增 Workspace 聚合、WorkspaceCapability 枚举、WorkspaceStatus 枚举和 WorkspaceRepository 端口
- [ ] 1.4 在 Infrastructure 层新增 workspace PO、Mapper、Repository 实现和 JSON capabilities 转换
- [ ] 1.5 改造 `WorkspacePort`，优先从 `agent_workspace` 解析 active workspace，并保留首版配置 workspace 兼容逻辑
- [ ] 1.6 添加 workspace 持久化测试，验证注册、查询、停用和 active 过滤

## 2. Workspace REST API

- [ ] 2.1 在 API 层新增 workspace 注册、查询列表、查询详情、停用 DTO
- [ ] 2.2 在 Case 层实现 `CreateWorkspaceCase`、`QueryWorkspaceCase`、`DeactivateWorkspaceCase`
- [ ] 2.3 在 Trigger 层新增 `WorkspaceController`
- [ ] 2.4 实现 `POST /api/workspaces`，校验 rootPath 必须是存在的本地绝对目录且不是盘符相对路径
- [ ] 2.5 实现 `GET /api/workspaces` 和 `GET /api/workspaces/{workspaceKey}`
- [ ] 2.6 实现 `DELETE /api/workspaces/{workspaceKey}` 逻辑停用
- [ ] 2.7 添加 REST API 测试，覆盖注册成功、非法路径、未知 workspace、停用后不可创建运行

## 3. Agent Run 模式与查询增强

- [ ] 3.1 扩展 `agent_run` 表，新增 `mode` 字段，默认 `READ_ONLY`
- [ ] 3.2 在 API 层扩展 `CreateAgentRunRequestDTO` 和 `AgentRunResponseDTO`，包含 `mode` 和编辑摘要字段
- [ ] 3.3 在 Domain 层新增 `AgentRunMode` 枚举
- [ ] 3.4 改造创建运行用例，未传 mode 时使用 `READ_ONLY`，传入未知 mode 时拒绝
- [ ] 3.5 改造执行上下文提示词，让模型知道当前运行模式和可用能力
- [ ] 3.6 添加创建运行测试，覆盖默认只读、显式 EDIT、停用 workspace、查询返回 mode

## 4. 编辑权限与工具注册

- [ ] 4.1 定义工具权限判断服务，根据 run mode 和 workspace capabilities 判断工具是否可执行
- [ ] 4.2 扩展工具注册表，支持按本次运行上下文过滤 `apply_patch`、`write_file` 等工具定义
- [ ] 4.3 新增权限拒绝审计事件类型，覆盖缺少 capability、READ_ONLY 禁止编辑、受保护路径
- [ ] 4.4 改造 `run_shell`，将 `GIT_READ`、`RUN_TEST`、`RUN_BUILD` capability 映射到内部命令白名单
- [ ] 4.5 添加工具权限测试，覆盖 READ_ONLY 拒绝编辑、EDIT 但缺 capability 拒绝、命令 capability 不足拒绝

## 5. 安全编辑工具

- [ ] 5.1 编写 `apply_patch` 失败用例，覆盖不存在文件、路径逃逸、受保护路径、非法 patch、READ_ONLY
- [ ] 5.2 实现 `apply_patch` 工具，只允许修改 workspace 内已有文本文件
- [ ] 5.3 编写 `write_file` 失败用例，覆盖覆盖已有文件、路径逃逸、受保护路径、缺少 ADD_FILE
- [ ] 5.4 实现 `write_file` 工具，只允许新建 workspace 内文本文件
- [ ] 5.5 实现受保护路径策略，禁止 `.env`、`.env.*`、`.git/`、`.coder/`、`target/` 和密钥类文件
- [ ] 5.6 确保第二版不注册 `delete_file`，并添加未知工具/不支持工具测试

## 6. 变更记录与工件

- [ ] 6.1 设计并实现变更文件记录对象，包含 relativePath、changeType、beforeHash、afterHash、toolCallNo
- [ ] 6.2 在编辑工具成功后记录变更文件摘要
- [ ] 6.3 实现 `patch.diff` 生成，覆盖修改文件和新增文件
- [ ] 6.4 实现 `changed-files.json` 写入和 `run_artifact` 索引
- [ ] 6.5 实现 `test-report.json` 写入，记录测试/构建命令、退出码、耗时、状态和摘要
- [ ] 6.6 实现 `review-summary.md` 写入，包含任务、模式、模型、变更文件、测试结果和审查重点
- [ ] 6.7 扩展 `final-result.json`，包含 editMode、changed、changedFileCount、testStatus 和新增工件路径
- [ ] 6.8 添加工件测试，验证 JSON 可解析、diff 存在、审查摘要生成

## 7. Agent 执行闭环

- [ ] 7.1 改造模型工具定义组装，让模型仅看到当前 run 可用工具
- [ ] 7.2 改造工具执行后消息摘要，向模型反馈编辑结果、测试结果和失败原因
- [ ] 7.3 在 EDIT 模式下支持“修改 -> 测试 -> 继续修复 -> 最终总结”的多轮循环
- [ ] 7.4 运行终态前汇总编辑结果和测试结果
- [ ] 7.5 添加执行循环测试，覆盖 EDIT 成功修改、EDIT 测试失败、READ_ONLY 只分析、预算耗尽保留 diff

## 8. 文档、真实验证与回滚

- [ ] 8.1 更新 README，说明 workspace 注册、capabilities、READ_ONLY/EDIT、编辑工件和安全边界
- [ ] 8.2 更新 docker/MySQL 初始化说明，包含 `agent_workspace` 和迁移 SQL
- [ ] 8.3 使用真实本地测试仓库完成一次 EDIT 模式冒烟：新建文件或修改小文件、运行测试、生成 diff
- [ ] 8.4 运行 `mvn test` 验证全部测试
- [ ] 8.5 运行 `openspec validate add-safe-code-editing` 验证规格一致性
- [ ] 8.6 标注回滚步骤、影响模块、涉及表和 API，确保文档与实现一致
