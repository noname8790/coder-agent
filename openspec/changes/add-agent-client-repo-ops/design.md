## Context

`coder-agent` 第二版已经具备 REST 异步运行、动态 workspace、READ_ONLY/EDIT 模式、安全新增/修改文件、测试/构建命令、审计记录和 `.coder/runs/{runId}` 工件。用户已经验证这些能力可用，但当前使用体验仍偏 API 调试，且仓库操作能力停留在安全编辑 MVP。

第三版面向“本地代码仓库 Agent 客户端”。用户希望客户端具备类似常见 Agent 客户端的主侧栏 + 对话输入 + 项目上下文布局，客户端能启动/停止后端服务，通过 REST + SSE 观察任务，并开放更完整的基础代码仓库 Agent 能力：覆盖文件、删除文件、本地 Git 分支/提交、PR 草稿和会话历史。

约束：
- 输出和文档使用中文。
- 后端继续使用 Spring Boot 3.x + JDK 21 + MyBatis-Plus + MySQL + Maven 多模块 DDD。
- 客户端采用适合本地 Agent 工具的桌面技术栈，优先方便与后端 REST/SSE 交互。
- 第三版只做 PR 草稿和本地 commit，不接 GitHub/GitLab，不执行 push。
- 任务运行中不支持变更权限等级；权限等级在会话/运行创建或下一次运行前确定。

## Goals / Non-Goals

**Goals:**

- 新增 Tauri + React + TypeScript 桌面客户端模块 `coder-agent-client`。
- 客户端支持启动/停止本地后端服务，并展示后端连接状态。
- 客户端支持 workspace 管理、会话列表、会话详情、任务输入、模型选择和权限等级选择。
- 后端新增 conversation/message 数据模型，支持会话历史和会话下多次 run。
- 后端以权限等级替代用户侧 capabilities 勾选，并用权限等级控制工具可见性和执行边界。
- 后端开放 L1-L3 基础代码仓库 Agent 能力：只读分析、安全编辑、仓库写入、本地分支、本地 commit、PR 草稿。
- 后端新增 SSE 运行事件流，客户端实时展示运行状态、模型调用、工具调用、文件变更、测试、Git 和审计事件。
- 删除/覆盖/修改文件时生成审查工件和回滚材料。

**Non-Goals:**

- 不做长期记忆、向量召回、上下文压缩传输或记忆 UI。
- 不做多用户登录、远程权限系统或团队协作。
- 不接入 GitHub/GitLab API，不创建真实远程 PR，不执行 `git push`。
- 不默认开放 L4 高风险能力；`git clean`、`reset --hard`、强制清理类能力只在设计中预留。
- 不支持 Linux/macOS Shell；后端本地命令仍以 Windows PowerShell 为目标。
- 不把客户端做成浏览器 Web 站点；它是桌面客户端。

## Decisions

### Decision 1: 客户端采用 Tauri + React + TypeScript

第三版新增 `coder-agent-client`，使用 Tauri 作为桌面壳，React + TypeScript + Vite 构建 UI。客户端通过 HTTP REST 调用后端，通过 SSE 订阅运行事件。

理由：Tauri 体积较小，适合本地开发者工具；React/TypeScript 对复杂状态、列表、对话、diff 和事件流 UI 更成熟；SSE 可直接复用浏览器运行时能力。相比 JavaFX，Tauri 更适合快速构建类 Agent 客户端界面；相比 Electron，Tauri 更轻量。

替代方案：JavaFX 能保持 Java 技术栈统一，但 UI 生态和 SSE/Markdown/diff 展示成本更高；Electron 生态成熟但体积较大。

### Decision 2: 客户端负责启动/停止后端服务

客户端提供后端服务控制能力：选择或配置后端 jar 路径、工作目录和端口，启动后端进程，检测健康状态，停止由客户端启动的后端进程。客户端也允许连接已启动的后端，便于开发调试。

理由：既然第三版目标是本地客户端，就不能要求用户总是先手动启动 Java 服务。保留“连接已有后端”可以降低实现风险，也便于 IDE 调试。

替代方案：只连接已启动后端。实现简单，但不像完整客户端；用户体验仍接近 API 工具。

### Decision 3: 权限模型从 workspace capabilities 改为权限等级

第三版用户侧不再勾选单个 capability。workspace 只表示本地项目目录和状态；运行或会话指定 `permissionLevel`。权限等级控制工具可见性和执行边界。

初始等级：
- `L1_READ_ONLY`：读取、搜索、`git status/diff/log`、生成分析结论。
- `L2_SAFE_EDIT`：L1 + 新增文件、patch 修改、测试/构建、diff 和审查工件。
- `L3_REPO_WRITE`：L2 + 覆盖文件、删除文件、本地分支、`git add`、`git commit`、PR 草稿、回滚材料。
- `L4_DANGEROUS_LOCAL`：只预留，不在第三版默认实现。

理由：用户更容易理解“只读 / 安全编辑 / 仓库写入”这种等级，而不是理解一组底层 capability。权限等级也便于客户端展示风险说明。

替代方案：继续使用 workspace capabilities。灵活但产品体验差，且用户难以理解组合风险。

### Decision 4: 运行中不允许变更权限等级

权限等级在创建会话或创建运行时确定。会话的默认权限等级可在下一次运行前调整，但已启动的 run 不会动态升级或降级。

理由：运行中变更权限会让工具可见性、模型上下文和审计边界变复杂，也容易导致模型在旧上下文下执行新权限操作。第三版先保证边界清晰。

替代方案：运行中允许升级权限。交互更灵活，但需要暂停运行、重组工具定义、重新提示模型并写入更强审计，适合后续版本。

### Decision 5: 本地 Git 只开放分支、add、commit 和只读命令

第三版开放：
- `git status`
- `git diff`
- `git log`
- `git checkout -b <branch>`
- `git add <paths>`
- `git commit -m <message>`

第三版禁止：
- `git push`
- `git push --force`
- `git reset --hard`
- `git clean`
- 修改 `.git/` 内部文件

理由：本地 commit 是基础代码 Agent 闭环的重要一步；push 和远程 PR 涉及凭证、远程仓库策略和不可逆风险，应等 GitHub/GitLab 集成版本再做。

替代方案：第三版直接支持 push。功能更完整，但凭证和风险边界过大。

### Decision 6: PR 能力先做草稿工件

后端生成 `.coder/runs/{runId}/pull-request.md`，内容包括标题、摘要、变更文件、测试结果、风险点、回滚方案和 reviewer checklist。客户端展示该草稿并支持复制。

理由：PR 草稿能帮助用户审查和后续手动提交，但不需要远程平台凭证。

替代方案：接入 GitHub/GitLab API 创建真实 PR。需要 token、安全存储、remote 解析、分支 push 和 API 错误处理，超出第三版范围。

### Decision 7: SSE 事件流作为实时观察协议

新增 `GET /api/agent-runs/{runId}/events`，以 SSE 输出运行事件。事件来源包括运行状态变更、模型调用、工具调用、审计事件、文件变更、测试/构建结果、Git 操作、PR 草稿生成和最终结果。

理由：SSE 对单向运行日志非常合适，复杂度低于 WebSocket，Tauri/React 客户端易于消费。

替代方案：前端轮询 trace。实现简单但实时性差，且容易造成无效请求。

### Decision 8: UI 采用侧栏项目/会话 + 中心对话 + 右侧审查面板

参考用户提供的 Agent 客户端示例图，第三版 UI 使用浅色、低干扰、信息密度适中的布局：
- 左侧：新对话、项目/workspace、会话列表、设置入口。
- 中心：当前项目对话、任务输入、权限等级、模型选择、运行事件流。
- 右侧或详情页：diff、changed files、test report、commit、PR 草稿和 rollback 材料。

理由：该布局符合本地 Agent 客户端使用习惯，同时能承载代码审查信息。

替代方案：传统后台管理表格布局。实现快，但不像 Agent 客户端，运行中的对话体验较弱。

## Risks / Trade-offs

- [Risk] 客户端启动后端失败或端口冲突 -> 客户端提供端口配置、健康检查、错误日志入口和“连接已有后端”模式。
- [Risk] L3 删除/覆盖文件造成损失 -> 每次删除/覆盖前生成 `file-backup/` 和 `rollback.patch`，并写入审计事件。
- [Risk] Git commit 内容不符合用户预期 -> commit 前必须生成 diff、测试结果和 PR 草稿；客户端明确展示 commit hash 和变更文件。
- [Risk] SSE 事件丢失或客户端重连 -> 事件仍落盘到 `trace.jsonl` 和数据库审计；客户端重连后可拉取历史 trace 补齐。
- [Risk] 权限等级描述不清导致用户误用 -> 客户端权限选择器必须展示每个等级允许/禁止能力，并对 L3 显示高风险提示。
- [Risk] 第三版范围较大 -> 后端能力和客户端 UI 分阶段实现，先打通 L1/L2/L3 最小闭环，再补充体验细节。

## Migration Plan

1. 新增数据库表：`agent_conversation`、`agent_message`、`agent_permission_audit`。
2. 扩展 `agent_run`：新增 `conversation_id`、`permission_level`、`git_branch`、`commit_hash`。
3. 扩展 `run_artifact` 枚举和工件写入逻辑，支持 `pull-request.md`、`rollback.patch`、`file-backup/`。
4. 保留第二版 `agent_workspace.capabilities` 字段用于兼容，但第三版客户端不再暴露该配置。
5. 新增权限等级和工具映射，逐步替换 ToolGateway 对 workspace capabilities 的用户侧依赖。
6. 新增会话 API、运行事件 SSE API 和仓库操作工具。
7. 新增 `coder-agent-client`，先支持连接/启动后端、workspace、会话、运行和事件流，再补审查页。
8. 使用 Pencil 设计稿验证客户端主要页面后再实现 UI。

回滚策略：
- 停用客户端模块和新增客户端控制 API。
- 将新增运行默认限制为 `L1_READ_ONLY`，或回退到第二版 `READ_ONLY/EDIT`。
- 从工具注册表移除 `overwrite_file`、`delete_file`、Git branch/add/commit 和 PR 草稿工具。
- 保留新增数据库表/字段但停止写入；必要时执行 SQL 删除第三版新增表和字段。
- 删除第三版新增工件目录中的 `pull-request.md`、`rollback.patch`、`file-backup/`。

## Open Questions

- 客户端启动后端时，后端 jar 路径是默认使用当前构建产物，还是允许用户在设置页手动选择。建议第三版两者都支持，默认自动探测。
- L3 本地 commit 是否需要二次确认。建议客户端在创建 L3 任务时确认一次，run 内不再弹窗阻塞。
- L4 高风险等级是否只写入设计预留，不进入第三版任务。建议第三版不实现 L4。
