# v4 框架适配决策

## 结论

v4 采用“自研 Harness 主循环 + 框架局部适配”的方式实现。任何外部框架都不得接管 Agent Run 生命周期、上下文快照、权限治理、工具审批、审计事件和工件落盘。

## 适配原则

- Domain/Case 层只依赖本项目定义的端口。
- Infrastructure 层可以引入框架实现端口，但必须保留自研实现或可替换实现。
- 框架输出必须转换为本项目统一的领域对象和审计事件。
- 框架不得隐藏模型调用、工具调用、上下文裁剪、记忆召回和审批等待状态。

## 框架对比

| 能力 | Spring AI Alibaba | LangChain4j | Embabel | 自研 OkHttp/JDBC |
| --- | --- | --- | --- | --- |
| Spring Boot 配置适配 | 强 | 中 | 强 | 强 |
| DashScope 生态 | 强 | 中 | 强 | 强 |
| OpenAI-compatible Chat 调用 | 中 | 强 | 中 | 强 |
| Responses API streaming | 不作为 v4 主依赖 | 不作为 v4 主依赖 | 不作为 v4 主依赖 | 强 |
| Tool Calling 协议控制 | 中 | 中 | 中 | 强 |
| Embedding 调用 | 强 | 强 | 中 | 强 |
| Vector Store 抽象 | 中 | 强 | 强 | 中 |
| Agent 编排 | 中 | 中 | 强 | 强 |
| 与现有审计/工件边界 | 中 | 中 | 中 | 强 |

## v4 采用方案

- 模型 streaming 网关：优先自研 OkHttp，实现 Chat Completions streaming 和 Responses API streaming，精确控制 delta、tool call arguments、失败、取消和审计事件。
- Embedding：优先自研 OpenAI-compatible embeddings 网关；后续可在 Infrastructure 层局部适配 Spring AI Alibaba。
- pgvector：优先自研 JDBC Repository，避免向量存储抽象遮蔽 workspaceKey、freshness、chunk 元数据和审计字段。
- Token 估算：先使用轻量估算器，预留 LangChain4j tokenizer 适配点。
- Agent 编排：不接入 Embabel 或 LangChain4j Agent 主循环。v4 的状态机、预算、审批、记忆和工件必须由本项目控制。

## 后续演进

后续版本可以在 Infrastructure 层引入 Spring AI Alibaba 或 LangChain4j 作为 embedding、tokenizer 或模型调用的可替换适配器，但不得改变 Domain/Case 层端口，也不得让框架持有 Agent Run 服务端状态。
