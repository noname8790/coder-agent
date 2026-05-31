## Context

首版 `coder-agent` 已经实现 REST 异步运行、模型调用、只读仓库工具、受限 PowerShell 命令、MySQL 审计和 `.coder/runs/{runId}` 工件落盘。当前能力适合分析、诊断和生成结论，但不能修改代码，也只能依赖服务端静态配置 workspace。

第二版目标是让 `coder-agent` 服务于用户本地任意项目，而不是固定服务当前 `coder-agent` 自身仓库。用户通过 API 注册本地项目目录，创建任务时选择 `workspaceKey`。Agent 在该 workspace 内完成安全读写、测试验证和 diff 交付。

约束条件：

- 仍采用 Spring Boot 3.x + JDK 21 + MyBatis-Plus + MySQL + Maven 多模块 DDD。
- 仍只支持 Windows PowerShell。
- 默认运行模式必须保持只读，避免升级后自动扩大破坏面。
- 第二版允许 `EDIT` 模式自动落盘修改文件，但不自动 commit、push 或创建 PR。
- 所有路径和命令能力必须按 workspace 隔离。

## Goals / Non-Goals

**Goals:**

- 支持通过 REST API 动态注册、查询、停用 workspace。
- 允许用户注册任意本地绝对目录，不限制父目录位置。
- 为 workspace 配置能力枚举：`READ_REPOSITORY`、`GIT_READ`、`RUN_TEST`、`RUN_BUILD`、`ADD_FILE`、`MODIFY_FILE`。
- 创建 Agent Run 时支持 `mode=READ_ONLY|EDIT`，默认 `READ_ONLY`。
- 在 `EDIT` 模式且 workspace 授权时开放安全编辑工具。
- 通过 `apply_patch` 修改已有文件，通过 `write_file` 新建文件。
- 修改后生成 diff、变更文件清单、测试报告和审查摘要。
- 修改后允许 Agent 运行测试/构建命令，并将结果纳入最终结论和工件。

**Non-Goals:**

- 不实现文件删除工具。
- 不执行 Git commit、push、reset、branch、PR 生成。
- 不做 Web UI。
- 不接入长期记忆、向量库、Elasticsearch 或 benchmark 平台。
- 不做多用户认证和授权。
- 不支持 Linux/macOS Shell。

## Decisions

### Decision 1: workspace 改为数据库动态注册

新增 `agent_workspace` 表保存 workspaceKey、rootPath、capabilities、状态和审计字段。首版 `application.yml` 中的静态 workspace 继续作为启动默认值或兼容入口，但运行时解析优先从数据库读取 active workspace。

理由：用户项目位置各不相同，静态配置不适合长期使用。数据库注册可以支持查询、停用和后续扩展。

替代方案：继续只用配置文件。实现简单，但无法满足用户动态选择本地项目。

### Decision 2: 注册任意绝对目录，只做基础合法性校验

注册 workspace 时允许任意本地绝对路径，但必须满足：路径存在、是目录、不是 Windows 盘符相对路径、能规范化。系统不限制父目录，例如不要求位于 `E:/IdeaProjects`。

理由：项目位置是用户习惯，服务端不应强制改变。安全边界主要通过 workspace 内路径隔离、编辑禁止列表和命令能力控制实现。

替代方案：配置 allowed-roots。安全性更高，但会破坏用户自由选择项目目录的需求。

### Decision 3: 使用 mode + capability 双保险控制编辑

Agent 能编辑文件必须同时满足：

- 本次 Agent Run 的 `mode=EDIT`。
- workspace capabilities 包含 `ADD_FILE` 或 `MODIFY_FILE`。

如果任一条件不满足，编辑工具返回 `REJECTED` 并写入审计事件。

理由：workspace 能力表达长期授权，本次 mode 表达当前任务意图。双保险能避免用户误把普通分析任务变成修改任务。

替代方案：只靠 mode。实现更简单，但 workspace 级安全边界不足。

### Decision 4: 第二版只开放 patch 修改和新建文件

新增 `apply_patch` 和 `write_file`：

- `apply_patch` 只修改 workspace 内已存在文本文件。
- `write_file` 只允许新建文件，不允许覆盖已有文件。
- `delete_file` 第二版不开放。

理由：patch 天然可审查，新建文件风险相对可控。删除和整文件覆盖更容易造成不可逆损失，留到后续版本。

替代方案：开放完整写文件和删除。功能更强，但第二版安全面过大。

### Decision 5: workspace capability 映射工具和命令能力

用户注册 workspace 时选择易懂能力项，而不是直接填写底层命令前缀。内部映射示例：

- `READ_REPOSITORY` -> `list_files`、`read_file`、`search_text`
- `GIT_READ` -> `git status`、`git diff`、`git log`
- `RUN_TEST` -> `mvn test`、`mvn -q test`、`mvn -pl ... test`、后续可扩展 `npm test`
- `RUN_BUILD` -> `mvn package`、`mvn clean package`、后续可扩展 `npm run build`
- `ADD_FILE` -> `write_file`
- `MODIFY_FILE` -> `apply_patch`

理由：用户更容易理解“添加文件/修改文件/运行测试”，系统内部仍能保持严格白名单。

替代方案：注册时直接传 allowedCommands。灵活但更容易误配，也不适合产品化。

### Decision 6: 编辑工件固定落到目标 workspace

每次运行继续使用 `{workspaceRoot}/.coder/runs/{runId}/`，第二版新增：

- `patch.diff`
- `changed-files.json`
- `test-report.json`
- `review-summary.md`

理由：审查材料应跟随目标项目，而不是 `coder-agent` 服务自身项目。这样用户可以在目标仓库内直接查看 Agent 运行产物。

替代方案：统一保存到服务端工作目录。集中管理更简单，但违背本地项目助手定位。

## Risks / Trade-offs

- [Risk] 用户注册磁盘根目录或过大目录 -> 允许注册但在响应中返回风险提示；工具仍限制输出数量、读取大小和路径边界。
- [Risk] Agent 修改敏感文件 -> 通过禁止路径规则拒绝 `.env`、`.git/`、`.coder/`、`target/`、密钥类文件，并记录审计事件。
- [Risk] patch 格式不正确 -> `apply_patch` 先解析校验，失败不写文件，返回 `REJECTED` 或 `FAILED`。
- [Risk] 测试命令过于多样 -> 第二版只实现常用 Maven 命令映射，保留配置扩展点。
- [Risk] 修改后测试失败 -> 保留 diff、changed-files 和失败测试报告，最终结果标记测试失败但不自动回滚。
- [Risk] 注册 workspace 被删除或移动 -> 创建运行和执行前都重新校验 rootPath，失败时 run 标记 `FAILED` 或拒绝创建。

## Migration Plan

1. 新增 `agent_workspace` 表和必要索引。
2. 启动时可将配置中的默认 workspace 注册为 active workspace，保持首版本地体验。
3. 新增 workspace REST API 和对应用例。
4. 扩展 Agent Run 创建请求和数据库字段，默认 `READ_ONLY`。
5. 新增编辑工具和权限拦截。
6. 新增编辑工件写入和查询结果返回。
7. 增加端到端测试，覆盖注册 workspace、READ_ONLY 拒绝编辑、EDIT 成功修改、测试失败/成功、工件生成。

回滚策略：

- 停用或隐藏 workspace 管理 API。
- 将所有新建任务强制设为 `READ_ONLY`。
- 从工具注册表移除 `apply_patch` 和 `write_file`。
- 保留新增表和字段不再写入；必要时执行 SQL 回滚删除新增表/列。
- 删除受影响运行的新增工件文件。

## Open Questions

- 第二版是否需要把 workspace 风险提示字段持久化，还是只在注册响应中返回。
- 第二版是否只内置 Maven 命令映射，还是同时内置 npm 测试/构建映射。
- 编辑失败后是否需要提供“一键恢复本次 run 修改”的 API；当前设计暂不实现自动回滚。
