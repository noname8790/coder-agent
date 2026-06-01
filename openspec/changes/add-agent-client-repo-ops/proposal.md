## Why

前两版已经验证了模型调用、workspace 隔离、安全编辑、审计工件和基础工具调用闭环，但当前使用方式仍依赖 REST 调试和有限编辑能力，不适合普通用户长期使用。
第三版需要把 `coder-agent` 推进到“本地代码仓库 Agent 客户端”阶段：用户通过桌面客户端选择项目、发起会话、观察实时运行、审查代码变更，并允许 Agent 在受控权限等级下完成更完整的本地仓库任务。

## What Changes

- 新增桌面客户端，采用 Tauri + React + TypeScript，客户端负责启动/停止本地后端服务，并通过 REST + SSE 与后端交互。
- 新增会话体系，支持会话列表、会话详情、会话消息历史和会话下多次 Agent Run；暂不做长期记忆、向量召回或上下文传输优化。
- 将第二版 workspace capability 勾选式授权改为权限等级模型，运行创建时选择权限等级，任务运行中不支持变更权限等级。
- 新增权限等级说明与审计，用户能清楚看到不同等级开放的功能和风险。
- 扩展基础代码仓库 Agent 功能：覆盖文件、删除文件、本地分支、本地 `git add`、本地 `git commit`、PR 草稿生成。
- 新增 SSE 运行事件流，客户端可实时展示运行状态、模型调用、工具调用、文件变更、测试结果、审计事件和最终结果。
- 新增回滚材料，删除/覆盖/修改等变更必须生成可人工恢复的备份或 rollback patch。
- 新增 PR 草稿工件 `pull-request.md`，包含标题、摘要、变更文件、测试结果、风险点、回滚说明和审查清单。
- 不接入 GitHub/GitLab，不执行 `git push`，不创建真实远程 PR。

## Capabilities

### New Capabilities

- `agent-client`: 桌面客户端启动/停止后端、workspace 管理、会话交互、运行观察和审查视图。
- `agent-conversation-history`: 会话、消息和历史运行记录管理。
- `agent-permission-levels`: 对话/运行级权限等级、风险说明、权限审计和工具权限映射。
- `agent-repo-operations`: 覆盖文件、删除文件、本地 Git 分支/提交、PR 草稿和回滚材料。
- `agent-run-events`: 基于 SSE 的运行事件流和实时状态观察。

### Modified Capabilities

- 无。当前仓库没有已归档到 `openspec/specs/` 的正式基线规格；本变更以新能力规格描述第三版行为。

## Impact

- 影响模块：
  - `coder-agent-api`：新增客户端、会话、权限等级、事件流、仓库操作相关 DTO。
  - `coder-agent-domain`：新增会话聚合、消息对象、权限等级值对象、仓库操作审计对象。
  - `coder-agent-case`：新增会话用例、权限变更用例、运行事件发布、仓库操作编排和 PR 草稿生成逻辑。
  - `coder-agent-infrastructure`：新增 MySQL Repository、SSE 事件实现、本地进程启动/停止支持、扩展文件/Git 工具。
  - `coder-agent-trigger`：新增会话、权限等级、事件流、客户端控制相关 REST/SSE API。
  - `coder-agent-app`：补充客户端启动后端所需打包、配置和本地访问配置。
  - 新增 `coder-agent-client`：Tauri + React + TypeScript 桌面客户端。
- 影响数据库表：
  - 新增 `agent_conversation`
  - 新增 `agent_message`
  - 新增 `agent_permission_audit`
  - 扩展 `agent_run`：`conversation_id`、`permission_level`、`git_branch`、`commit_hash`
  - 扩展 `run_artifact`：新增 PR 草稿、rollback patch、file backup 等工件类型。
- 影响本地工件：
  - 新增 `pull-request.md`
  - 新增 `rollback.patch`
  - 新增 `file-backup/`
- 新增依赖：
  - 前端：Tauri、React、TypeScript、Vite。
  - 后端：尽量复用 Spring MVC SSE；如需进程管理，使用 JDK Process API，不新增重型依赖。
- 回滚方案：
  - 停用客户端模块和新增 API，仅保留第二版 REST 能力。
  - 将所有新运行默认限制为 `L1_READ_ONLY` 或第二版等价 `READ_ONLY`。
  - 从工具注册表移除 `overwrite_file`、`delete_file`、Git commit/branch 和 PR 草稿工具。
  - 保留新增数据库表/字段但停止写入；必要时执行 SQL 删除新增表和字段。
  - 删除客户端构建产物和第三版新增 `.coder/runs/{runId}` 工件。
