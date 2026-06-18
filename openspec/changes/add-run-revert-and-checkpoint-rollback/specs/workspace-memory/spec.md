## ADDED Requirements

### Requirement: 回滚后的记忆召回过滤
系统 MUST 在记忆召回时排除已回滚 checkpoint 之后且 rollback 之前产生的 MySQL memory item 和 pgvector chunk。历史记忆不物理删除，仅不得进入后续 prompt。涉及表：`agent_memory_item`、`memory_recall`、PostgreSQL `coder_agent_memory_chunk`、`agent_checkpoint`。

#### Scenario: 排除已回滚记忆
- **WHEN** conversation 已回滚到 checkpoint
- **THEN** 记忆召回 MUST 不返回 checkpoint 之后且 rollback 之前产生的 memory chunk

#### Scenario: 保留审计数据
- **WHEN** conversation 已回滚到 checkpoint
- **THEN** 系统 MUST 保留被排除的 memory item 和 pgvector chunk，供历史审计查询

### Requirement: 撤销状态影响文件记忆新鲜度
系统 MUST 在 run 撤销或还原后更新相关文件摘要记忆的新鲜度或有效性，使后续召回不使用与当前文件状态冲突的摘要。涉及表：`agent_memory_item`、PostgreSQL `coder_agent_memory_chunk`。

#### Scenario: 撤销后旧摘要失效
- **WHEN** 用户撤销某个修改文件的 run
- **THEN** 系统 MUST 将该 run 产生的文件摘要标记为不参与当前召回

#### Scenario: 还原后摘要可重新参与召回
- **WHEN** 用户还原某个已撤销 run
- **THEN** 系统 SHALL 允许该 run 对应且仍新鲜的记忆重新参与后续召回

### Requirement: 召回审计记录过滤信息
系统 MUST 在 memory recall 记录中保存因 checkpoint 或 run revert 被过滤的数量，便于排查上下文缺失。涉及表：`memory_recall`。

#### Scenario: 记录记忆过滤数量
- **WHEN** 记忆召回过滤掉已回滚或已撤销来源
- **THEN** 系统 MUST 在召回记录中保存过滤原因和数量
