## ADDED Requirements

### Requirement: 128K 上下文预算
系统 MUST 使用 128K 上下文预算作为默认基线，并允许通过 `.env` 调整总输入、输出、安全余量和各上下文层预算。

#### Scenario: 读取上下文预算配置
- **GIVEN** `.env` 配置 `CONTEXT_MAX_CONTEXT_TOKENS=131072`
- **WHEN** 服务启动
- **THEN** ContextEngine MUST 使用该配置计算输入预算、输出预算和各 section 上限

### Requirement: 当前请求不可裁剪
系统 MUST 保证当前用户请求、工具调用协议、权限边界和关键安全约束永远不会被压缩或裁剪。

#### Scenario: 上下文超预算仍保留当前请求
- **GIVEN** 历史上下文超过预算
- **WHEN** ContextEngine 裁剪候选
- **THEN** 当前用户请求 MUST 保留在 prompt 末尾
- **AND** 历史消息、工具输出和低优先级记忆 MUST 优先被压缩或外置
