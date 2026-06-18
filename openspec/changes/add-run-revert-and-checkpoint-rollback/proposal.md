## Why

v4.1 已经具备 Markdown、Diff、Git/PR 和权限体验，但 Agent 任务产生的代码改动仍缺少可逆能力，用户无法针对单次任务安全撤销，也无法把长会话回退到某个可信检查点继续工作。模型选择也还没有像权限等级一样按会话恢复，导致多会话切换时用户需要重复选择模型。

本变更用于补齐本地 coding agent 的“可回退工作流”：让每次任务的代码改动可以撤销/还原，让会话可以在原会话内还原到检查点，并让这些状态进入后续上下文，避免已回滚的消息、记忆和代码改动继续污染后续任务。

## What Changes

- 新增 run 级撤销/还原能力：Agent 改动文件后记录可逆变更集，Diff 区域展示“撤销/还原更改”按钮。
- 新增不可逆文件标识：二进制、超大文件、缺少快照或 hash 冲突的文件在 Diff 文件旁以红色文字显示“该文件无法自动撤销”。
- 新增会话内 checkpoint 回滚：Agent 消息下方展示“还原到此检查点”，用户确认后在当前 workspace 内回滚该检查点之后的代码改动。
- checkpoint 回滚不新增会话、不创建 git worktree、不复制向量记忆；检查点之后的消息折叠保留，仅用于历史审计，不再参与后续上下文和记忆召回。
- run 撤销、还原和 checkpoint 回滚状态必须作为后续任务的上下文输入，明确当前代码状态已包含或不包含哪些任务改动。
- Agent 任务完成、取消、失败后，在 Agent 消息底部以灰色小字展示本次执行模型的展示名。
- 会话记录最后一次选择的模型；切换会话时恢复该会话模型选择。新会话首次默认选择当前模型下拉栏排名最上方的启用模型；无启用模型时显示“请配置模型”并禁止发送任务。
- 修改删除/回滚相关清理规则：被 checkpoint 标记为回滚后的消息、run、记忆和上下文快照不物理删除，但必须从后续上下文候选和向量召回范围中排除。

## Capabilities

### New Capabilities

- `agent-run-change-revert`: run 级文件变更集、撤销/还原状态、不可逆文件标识、冲突检测与后续上下文注入。
- `agent-checkpoint-rollback`: 会话内检查点、回滚确认、检查点之后消息折叠审计、后续上下文/记忆作用域裁剪。

### Modified Capabilities

- `agent-message-markdown-diff`: Agent 消息底部展示模型显示名，Diff 区域新增撤销/还原按钮和不可逆文件红色标识。
- `agent-client-v4`: 客户端恢复会话最后模型选择；新会话使用模型列表第一项；checkpoint 后消息折叠边界清晰且可展开审计。
- `context-governance`: 上下文候选必须排除已回滚 checkpoint 之后的消息、run、记忆和工具结果，并注入撤销/还原状态摘要。
- `workspace-memory`: 记忆召回必须排除已回滚 checkpoint 之后产生的 memory item 和 pgvector chunk；不物理删除，仅按作用域过滤。
- `model-provider-management`: 模型选择从全局默认扩展为会话最后选择模型，未配置模型时保持“请配置模型”的空状态。

## Impact

- 后端 API：新增 run 撤销/还原接口、checkpoint 回滚接口；扩展 run/message/conversation 查询响应，返回 change set、revert 状态、checkpoint 状态、模型展示名和折叠消息状态。
- 数据库：MySQL 新增或扩展 run 文件变更集、checkpoint、消息状态、会话最后模型字段；PostgreSQL/pgvector 不新增复制表，但检索需支持按有效 run 范围过滤。
- 后端模块：`coder-agent-api`、`coder-agent-case`、`coder-agent-domain`、`coder-agent-infrastructure`、`coder-agent-trigger`。
- 前端模块：`coder-agent-client` 的消息渲染、Diff 展示、模型选择、checkpoint 回滚确认和折叠审计 UI。
- 工件：run artifact 需要保存 before/after 快照或等价 patch 信息，供撤销和还原使用。
- 回滚方案：移除新增接口和前端入口，保留数据库字段但停止写入；若需数据库回滚，删除新增 change set/checkpoint 表和新增字段，客户端恢复 v4.1 的只读 Diff 展示。
