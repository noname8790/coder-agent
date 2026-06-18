## Context

v4.1 已经把 coder-agent 推进到可用的本地 coding agent：支持用户模型配置、流式输出、上下文治理、workspace 记忆、权限审批、Git/PR 工具、Markdown 消息和 Diff 摘要。当前缺口是“改动可逆”和“长会话可回退”：Agent 一旦改动代码，用户只能手动处理文件变化；长会话中如果某一步方向错误，后续上下文和记忆仍会继承错误状态。

本设计面向 v4.2，目标是在不引入 git worktree、不新增会话分支、不重做 Harness 主循环的前提下，提供 run 级撤销/还原和会话内 checkpoint 回滚。系统仍只服务当前 workspace 的真实本地目录，所有回滚都会改变该目录下的实际文件。

## Goals / Non-Goals

**Goals:**
- 为每个产生文件改动的 run 保存可逆变更集，支持撤销和还原。
- 在 Diff UI 中展示撤销/还原按钮，并对不可逆文件显示红色“该文件无法自动撤销”。
- 支持在原会话内“还原到此检查点”，回滚该检查点之后的代码改动。
- checkpoint 之后的消息折叠保留，仅供历史审计，不参与后续上下文与记忆召回。
- 将撤销、还原、checkpoint 回滚状态注入后续 prompt，使 Agent 明确当前代码状态。
- 在 Agent 消息底部展示本次执行模型的 displayName。
- 按 conversation 记录最后一次选择的模型；新会话首次使用模型列表排名最上方的启用模型，无模型时显示“请配置模型”。

**Non-Goals:**
- 不新增会话分支，不创建派生 conversation。
- 不引入 git worktree、独立沙箱目录或每个会话独立文件系统。
- 不支持远程 GitHub/GitLab 分支、push 或远程 PR 创建。
- 不保证二进制文件、超大文件、外部生成目录和缺少快照的文件可自动撤销。
- 不物理删除 checkpoint 之后的历史消息、run、记忆和审计记录；它们保留用于审计，但从后续上下文作用域中排除。

## Decisions

### 1. 用 run change set 作为撤销/还原的唯一事实来源

后端在每次工具产生文件改动时记录 run 级 change set。建议新增：
- `agent_run_change_set`：`run_id`、`workspace_key`、`conversation_id`、`status(APPLIED/REVERTED/PARTIAL/CONFLICTED)`、`reversible`、`failure_reason`、`created_at`、`updated_at`。
- `agent_run_file_change`：`run_id`、`file_path`、`change_type(ADD/MODIFY/OVERWRITE/DELETE/RENAME)`、`before_hash`、`after_hash`、`before_snapshot_path`、`after_snapshot_path`、`reversible`、`irreversible_reason`、`current_state`。

撤销时根据 `after_hash` 校验当前文件状态，匹配后恢复 `before_snapshot` 或删除新增文件。还原时根据 `before_hash` 校验当前文件状态，匹配后恢复 `after_snapshot` 或重新写入新增文件。hash 不匹配时拒绝操作，不覆盖用户手动改动。

备选方案是直接依赖 `git apply -R`。该方案对未跟踪文件、已被用户手动编辑的文件、非 Git workspace、以及工具输出不完整场景不稳定，因此仅可作为辅助生成 diff，不作为撤销事实来源。

### 2. checkpoint 回滚通过 cutoff 标记裁剪上下文，不复制会话

checkpoint 是会话内的一个稳定边界，建议新增 `agent_checkpoint`：
- `checkpoint_id`
- `conversation_id`
- `workspace_key`
- `message_id`
- `run_id`
- `message_seq`
- `created_at`
- `rollback_at`
- `rollback_status`

当用户点击“还原到此检查点”并确认后，后端找出该 conversation 中 `message_seq` 大于 checkpoint 的所有 run，按时间倒序执行撤销。成功后：
- checkpoint 之后的消息标记为 `ROLLED_BACK` 或等价状态。
- checkpoint 之后 run 的 change set 状态保持为 `REVERTED`。
- 上下文装配、最近消息、memory recall、tool result summary、run trace summary 均以 checkpoint cutoff 过滤。
- 前端将 checkpoint 之后消息折叠为明确边界块，用户点击后可展开审计。

备选方案是创建新 conversation 并复制 checkpoint 前所有消息和记忆。该方案需要处理真实文件系统分支和向量记忆继承，若不引入 git worktree 会导致不同会话共享同一个 workspace 状态，语义不可靠，因此本版不采用。

### 3. checkpoint 之后的记忆不删除，只按作用域排除

MySQL memory 和 pgvector chunk 继续保留，避免破坏审计和历史运行复盘。后续召回时，Memory 查询必须带上有效 run 范围或 cutoff 条件：
- 当前 conversation 没有 checkpoint rollback 时，使用该会话未被删除/未失效的 run。
- 当前 conversation 已还原到 checkpoint 时，只允许 checkpoint 之前的 run 和 checkpoint 之后新产生的 run。

如果当前 pgvector 表无法直接按 conversation/run 过滤，则通过 MySQL 先计算有效 `source_id/runId` 集合，再传入向量查询过滤条件；实现不足时先在召回结果返回后做二次过滤，但必须记录过滤数量。

### 4. 消息状态与折叠展示分离

`agent_message` 建议增加：
- `sequence_no`：会话内单调递增序号，用于 checkpoint cutoff。
- `visibility_status`：`VISIBLE`、`ROLLED_BACK`、`DELETED`。
- `rolled_back_by_checkpoint_id`：被哪个 checkpoint 回滚。

前端对 `ROLLED_BACK` 消息不直接混入正常对话流，而是在 checkpoint 位置之后显示折叠边界，例如“已回滚的 6 条历史消息，仅用于审计”。点击后展开这些消息，但展开内容不影响输入框、模型选择或当前上下文状态。

### 5. 会话模型偏好使用 lastModelKey

`agent_conversation.default_model` 旧语义容易被理解为全局默认模型。本版建议迁移或明确为 `last_model_key`：
- conversation 创建时，如果请求未传模型，使用启用模型列表的第一项作为 `last_model_key`。
- 如果没有启用模型，`last_model_key` 为空，客户端显示“请配置模型”并禁止发送任务。
- 创建 run 时请求未传模型，则使用 conversation 的 `last_model_key`。
- 用户在会话中切换模型后，立即更新该 conversation 的 `last_model_key`。

### 6. 模型展示名以后端装配为准

Agent 消息底部展示的模型名称由后端根据 `agent_run.model` 查询 `agent_model_provider.display_name` 装配。若模型配置已删除或不可用，则降级展示 run 中保存的 model key。前端只渲染 `modelDisplayName`，不自行推断。

## Risks / Trade-offs

- [Risk] 用户手动修改了 Agent 改过的文件，撤销会覆盖用户改动。→ 撤销/还原前进行 hash 校验，冲突时拒绝并提示具体文件。
- [Risk] checkpoint 回滚改变真实 workspace 文件，用户可能误解为只是历史回放。→ 前端必须弹出确认提示，明确说明会修改当前工作区文件。
- [Risk] 大文件和二进制文件快照成本高。→ 设置可逆文件大小上限，不可逆文件在 Diff 中红字提示，不阻塞其他可逆文件记录。
- [Risk] checkpoint 后记忆保留但不参与召回的过滤规则复杂。→ 统一通过 conversation cutoff 和有效 runIds 计算上下文作用域，并为召回过滤写审计日志。
- [Risk] 多次撤销/还原造成状态混乱。→ change set 状态机限制为 `APPLIED <-> REVERTED`，冲突进入 `CONFLICTED`，冲突解决前不允许继续切换。
- [Risk] checkpoint 后又产生新 run，不能简单按 message_seq 小于 cutoff 过滤全部未来消息。→ 回滚完成后记录 `rollback_at`，后续新 run 标记为当前有效分支，过滤规则为“checkpoint 前 + rollback 后新产生”。

## Migration Plan

1. 添加 MySQL 迁移脚本：新增 change set、file change、checkpoint 字段/表，扩展 conversation 的 `last_model_key` 和 message 的序号/可见状态。
2. 初始化历史数据：现有消息按创建时间补 `sequence_no`；现有 conversation 的 `last_model_key` 从 `default_model` 或启用模型列表第一项推导。
3. 新增后端端口与用例：记录文件变更快照、撤销/还原 run、创建并执行 checkpoint rollback、查询折叠消息。
4. 修改上下文装配和记忆召回：引入 conversation 有效作用域过滤，并注入撤销/还原/回滚摘要。
5. 修改前端：Diff 撤销/还原按钮、不可逆文件提示、checkpoint 风险确认、折叠审计消息、模型展示名、会话模型恢复。
6. 验证：单元测试覆盖 hash 冲突、不可逆文件、checkpoint cutoff；集成测试覆盖真实文件撤销/还原；客户端测试覆盖折叠与模型恢复。

回滚时先隐藏前端入口，保留数据不再写入；后端接口返回禁用错误。若需要数据库回滚，删除新增表/字段前应先导出审计数据，避免丢失历史可追溯信息。

## Open Questions

- 可逆文件大小上限需要在实现阶段按当前工具输出和本地性能设定，建议默认 2MB，并通过配置项保留调整空间。
- checkpoint 回滚是否允许部分成功：建议首版采用全有或全无，任一文件冲突或不可逆则整体拒绝回滚。
