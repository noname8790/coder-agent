## ADDED Requirements

### Requirement: workspace 隔离召回
系统 MUST 保证所有记忆写入、召回、freshness 校验和 pgvector 查询都绑定 workspaceKey，不得跨 workspace 使用记忆。

#### Scenario: 跨 workspace 查询被隔离
- **GIVEN** workspace A 存在 `pom.xml` 的 FILE_MEMORY
- **WHEN** 用户在 workspace B 发起相似任务
- **THEN** 系统 MUST NOT 召回 workspace A 的记忆

### Requirement: 多路召回与重排
系统 MUST 组合向量相似、路径精确命中、符号命中、模块命中、测试名命中、最近文件摘要和 task memory 进行候选召回，并按 freshness、trustScore、证据强度和时间衰减重排。

#### Scenario: 路径命中优先于泛语义相似
- **GIVEN** 当前任务明确提到 `src/main/java/App.java`
- **WHEN** 向量召回返回多个语义相似文件
- **THEN** 该路径对应的 FRESH 文件记忆 MUST 优先进入候选
- **AND** 低可信语义相似候选 MUST 降权或排除
