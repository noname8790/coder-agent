## ADDED Requirements

### Requirement: 按业务领域拆分包
系统 MUST 在现有 Maven 模块内按业务边界拆分 `agent`、`context`、`memory`、`tool`、`workspace`、`model`、`evaluation` 包。

#### Scenario: 领域对象归属检查
- **GIVEN** 开发者新增 memory recall 规则
- **WHEN** 代码落地
- **THEN** 领域对象和端口 MUST 位于 memory 或 context 边界内
- **AND** 不得放入 agent 包作为泛化领域

### Requirement: 工具策略化实现
系统 MUST 保持对外 toolName/API 兼容，但内部通过 ToolDescriptor、ToolHandler 和 ToolGovernancePolicy 管理工具 schema、权限、审批、超时、脱敏和结果证据。

#### Scenario: 新增 Git 工具策略
- **GIVEN** 开发者新增 `git_restore` 操作
- **WHEN** 实现该工具
- **THEN** 系统 MUST 通过 GitOperationStrategy 注册命令、风险等级、审批策略和超时
- **AND** 不得复制一套独立且重复的 LocalTool 治理代码

### Requirement: 框架局部适配边界
系统 MUST 仅在 infrastructure adapter 中引入 Spring AI Alibaba、AgentScope、LangChain4j 或 Embabel 的局部能力；domain/case MUST 只依赖项目自定义端口。

#### Scenario: 引入框架工具 schema
- **GIVEN** 框架适配器生成工具 schema
- **WHEN** case 层装配工具列表
- **THEN** case 层 MUST 只看到项目内部 ToolDescriptor
- **AND** 框架类型不得泄漏到 domain 或 case 包
