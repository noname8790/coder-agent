## 1. 数据库与领域模型

- [ ] 1.1 新增 MySQL 迁移脚本，创建 `agent_run_change_set`、`agent_run_file_change`、`agent_checkpoint`，并补充中文 COMMENT 与回滚 SQL
- [ ] 1.2 扩展 `agent_message`，增加会话内 `sequence_no`、`visibility_status`、`rolled_back_by_checkpoint_id` 或等价字段
- [ ] 1.3 扩展 `agent_conversation`，将会话模型偏好明确为 `last_model_key`，并迁移旧 `default_model` 数据
- [ ] 1.4 新增领域实体和值对象：RunChangeSet、RunFileChange、Checkpoint、RevertConflict、RollbackScope
- [ ] 1.5 新增仓储端口和基础设施实现，覆盖 change set、file change、checkpoint、message visibility、conversation lastModelKey

## 2. Run 级撤销与还原

- [ ] 2.1 在文件写入、覆盖、删除、patch、Git/PR 相关工具执行后记录 before/after hash 和 before/after 快照
- [ ] 2.2 实现可逆文件判定，覆盖二进制文件、超大文件、缺少快照和 hash 失败等不可逆原因
- [ ] 2.3 实现 `POST /api/agent-runs/{runId}/revert`，成功撤销时更新 change set 状态并写入 audit_event
- [ ] 2.4 实现 `POST /api/agent-runs/{runId}/restore`，成功还原时更新 change set 状态并写入 audit_event
- [ ] 2.5 实现 hash 冲突检测，冲突时拒绝撤销/还原并返回冲突文件列表，不覆盖用户手动修改
- [ ] 2.6 扩展 run/message 查询响应，返回 change set 状态、文件可逆状态、不可逆原因和撤销/还原可用性

## 3. Checkpoint 回滚

- [ ] 3.1 为已结束 Agent 消息提供 checkpoint 查询/创建能力，记录 messageSeq、runId 和 workspaceKey
- [ ] 3.2 实现 `POST /api/conversations/{conversationId}/checkpoints/{checkpointId}/rollback`，按时间倒序撤销 checkpoint 之后的有效 run
- [ ] 3.3 回滚采用全有或全无策略：任一文件不可逆或 hash 冲突时整体拒绝，并返回具体文件原因
- [ ] 3.4 回滚成功后将 checkpoint 之后、rollback 之前的消息标记为 `ROLLED_BACK`
- [ ] 3.5 回滚成功后将 checkpoint 之后的 change set 状态更新为 `REVERTED` 或等价状态
- [ ] 3.6 查询会话消息时返回折叠审计边界所需字段，保证前端能区分有效消息和已回滚消息

## 4. 上下文与记忆作用域

- [ ] 4.1 调整 AgentContextAssembler，使 recent messages、conversation summary、tool results、run trace summary 排除已回滚消息和 run
- [ ] 4.2 在上下文中注入撤销/还原状态摘要和 checkpoint 回滚摘要
- [ ] 4.3 调整 memory recall，按 conversation 当前有效 run 范围过滤 MySQL memory item 和 pgvector chunk
- [ ] 4.4 撤销或还原 run 后更新相关文件摘要记忆的新鲜度或当前有效性
- [ ] 4.5 扩展 context snapshot 和 memory recall 审计，记录因 checkpoint/revert 被过滤的数量和原因

## 5. 会话模型偏好

- [ ] 5.1 创建 conversation 时，若启用模型列表非空，将排名最上方模型写入 `lastModelKey`
- [ ] 5.2 若无启用模型，允许创建 conversation 但保持 `lastModelKey` 为空，创建 run 时返回明确模型未配置错误
- [ ] 5.3 用户切换模型时更新当前 conversation 的 `lastModelKey`
- [ ] 5.4 创建 Agent Run 未显式传入 model 时读取 conversation 的 `lastModelKey`
- [ ] 5.5 run/message 查询响应返回 `modelDisplayName`，模型配置缺失时降级为 run.model

## 6. 客户端交互

- [ ] 6.1 Diff 摘要卡片新增“撤销/还原更改”按钮，调用对应 API 并及时刷新消息状态
- [ ] 6.2 Diff 文件列表对 `reversible=false` 文件显示红色“该文件无法自动撤销”，并展示不可逆原因
- [ ] 6.3 Agent 消息下方新增“还原到此检查点”，点击前展示会修改当前 workspace 文件的风险确认弹窗
- [ ] 6.4 会话消息列表按折叠边界展示 `ROLLED_BACK` 消息，默认折叠，点击后仅作为历史审计展开
- [ ] 6.5 Agent 消息完成、取消、失败后在底部显示灰色小字模型 displayName
- [ ] 6.6 切换 conversation 时恢复该会话 `lastModelKey`；新会话使用模型列表第一项；无模型时显示“请配置模型”并禁用发送

## 7. 测试与验证

- [ ] 7.1 单元测试覆盖 run change set 记录、撤销、还原、hash 冲突和不可逆文件判定
- [ ] 7.2 单元测试覆盖 checkpoint 回滚成功、不可逆拒绝、hash 冲突拒绝和消息 `ROLLED_BACK` 标记
- [ ] 7.3 单元测试覆盖上下文治理过滤已回滚消息、已撤销 run 和记忆召回结果
- [ ] 7.4 单元测试覆盖 conversation lastModelKey 创建、切换、run 默认模型选择和模型展示名降级
- [ ] 7.5 前端组件测试覆盖 Diff 撤销/还原、不可逆文件标识、checkpoint 确认、折叠审计消息和模型恢复
- [ ] 7.6 使用真实本地测试仓库冒烟验证：创建文件 -> 撤销 -> 还原 -> 后续 Agent 能识别当前状态
- [ ] 7.7 使用真实本地测试仓库冒烟验证：多轮改动后还原到 checkpoint，后续 prompt 不再引用已回滚消息和记忆
- [ ] 7.8 运行 `mvn test`、前端构建/测试和 `openspec validate add-run-revert-and-checkpoint-rollback`

## 8. 文档与交付

- [ ] 8.1 更新 README 或开发说明，补充撤销/还原、checkpoint 回滚、不可逆文件和会话模型恢复行为
- [ ] 8.2 补充 API curl 示例，覆盖 run revert、run restore、checkpoint rollback 和 conversation lastModelKey
- [ ] 8.3 记录回滚方案：隐藏前端入口、禁用后端接口、保留审计数据或执行数据库回滚脚本
