## Context

当前仓库已初始化 OpenSpec 和 Superpowers，但尚无应用代码。项目目标是构建面向本地代码仓库长链路任务的 Java 服务端 Agent Harness。它通过 REST API 接收任务，面向服务端配置的本地 workspace 工作，不提供本地 CLI 客户端。首版优先打通“REST API 创建任务 -> 后台执行 -> OpenAI-compatible Responses API + Tool Calling -> 受控本地工具 -> MySQL 审计 -> workspace 内 `.coder` 工件落盘 -> 可回放 trace”的核心闭环。

约束条件：

- 输出与项目文档使用中文。
- 技术栈采用 Spring Boot 3.x、JDK 17/21、MyBatis-Plus、MySQL、Docker、SLF4J。
- 架构采用 Maven 多模块 DDD，遵守 `ddd-skills` 的 Trigger / API / Case / Domain / Infrastructure 分层规则。
- 首版只开放开发可用能力，不开放文件编辑、自动提交和高风险 Shell。
- 首版运行环境限定为 Windows PowerShell，不做 Linux/macOS 兼容。
- 首版必须跑通真实 OpenAI-compatible API，不以 Mock 模型作为交付验收。
- 首版可作为开发者本机运行的服务端进程，默认建议监听 `127.0.0.1`，不要求部署到额外云服务器。

## Goals / Non-Goals

**Goals:**

- 提供 REST API 异步创建、查询、取消 Agent 运行。
- 通过 OpenAI-compatible Responses API 优先调用模型，并使用标准 Tool Calling 协议。
- 提供 `list_files`、`read_file`、`search_text`、`run_shell` 四类首版工具。
- 使用 `workspaceKey` 绑定服务端预配置工作区，并对所有路径做工作区边界校验。
- 将运行、步骤、模型调用、工具调用、审计事件和工件索引写入 MySQL。
- 将长 trace、上下文快照、工具输出和最终结果写入 `{workspaceRoot}/.coder/runs/{runId}/`。
- 实现基础上下文组装和预算保护，避免长任务无限循环。
- 限制首版最大并发运行数为 2。
- 生成 README、curl 示例和一条可跑通的本地仓库任务示例。

**Non-Goals:**

- 不实现文件编辑工具、自动 Git commit/push、Git 分支创建和 PR 生成。
- 不实现结构化记忆、文件摘要缓存、freshness 校验和向量召回。
- 不接入 PostgreSQL/PGVector、Elasticsearch。
- 不实现 DashScope 专用 API，仅保留模型网关扩展点。
- 不实现完整 benchmark 报表和多模型成本效果对照。
- 不实现多 Agent 协作。
- 不实现 API 鉴权，首版按本地开发服务处理。
- 不兼容 Linux/macOS Shell。

## Decisions

### Decision 1: 使用 Maven 多模块 DDD

采用 `coder-agent-types`、`coder-agent-api`、`coder-agent-domain`、`coder-agent-case`、`coder-agent-infrastructure`、`coder-agent-trigger`、`coder-agent-app` 的多模块结构。

理由：Agent Harness 会长期演进，模型、工具、安全、审计、记忆和评测都是独立变化点。多模块 DDD 能让 Domain 定义稳定接口，Infrastructure 替换模型和工具实现时不影响用例编排。

替代方案：单模块 Spring Boot。该方案落地更快，但后续接入记忆、评测、多个模型后端时边界容易混乱。

### Decision 2: REST API 异步运行

`POST /api/agent-runs` 只创建并启动后台任务，立即返回 `runId`；客户端通过查询接口获取状态、trace 和最终结果。

理由：代码仓库任务可能持续数分钟，异步模型更适合取消、超时、审计和后续评测。

替代方案：同步阻塞返回。实现简单，但对长链路任务、超时控制和 trace 查询不友好。

### Decision 3: OpenAI-compatible Responses API + Tool Calling

首版优先使用 OpenAI-compatible Responses API 和标准 tools schema。模型返回的工具调用在内部转换为统一 `ToolInvocation`。如果实际供应商只兼容 Chat Completions，可以作为 Infrastructure 层备用适配实现，但 Domain 和 Case 层不绑定具体协议。

理由：目标描述以 OpenAI 兼容 Responses API 为主，Tool Calling 是核心能力之一。首版采用 Responses API 优先可以更贴近目标定位，并减少后续迁移成本。DashScope 专用 API 后续通过新增 `DashScopeModelGateway` 适配。首版验收必须配置真实 OpenAI-compatible API 并完成一次真实模型驱动的运行闭环。

替代方案：模型输出 JSON Action。实现更快，但协议不标准，后续接入真实 tool calling 需要迁移。

### Decision 4: 使用 workspaceKey 而不是 workspacePath

API 请求只传 `workspaceKey`，真实路径由服务端配置维护。所有文件路径在执行前解析为规范路径，并校验必须位于 workspace 根目录内。

理由：避免 API 调用者传入任意本地路径，降低本地代码执行服务的安全风险。

替代方案：直接传 `workspacePath`。开发方便，但路径逃逸和权限风险更高。

### Decision 5: Shell 安全采用两层策略

`run_shell` 通过 Windows PowerShell 执行。执行前先匹配允许命令前缀，再拒绝危险 token，例如删除、移动、重定向、管道、链式执行、Git reset/push/commit 等。首版默认允许 `git status`、`git diff`、`git log`、`mvn test`、`mvn -q test`、`mvn clean test`、`mvn package`、`mvn -pl`、`java -version`。

理由：单纯前缀白名单容易被附加参数绕过，两层策略能保留开发可用性，同时降低破坏性风险。

替代方案：固定命令模板。安全性更高，但不足以覆盖真实仓库任务中的 Maven、Git 等常见诊断命令。

### Decision 6: MySQL 存索引，workspace 的 `.coder` 存大工件

MySQL 保存运行结构化记录和工件索引，每次运行的大工件默认保存到 `{workspaceRoot}/.coder/runs/{runId}/`，包括 `run-meta.json`、`trace.jsonl`、`context-snapshot/*.json`、`tool-output/*.txt`、`final-result.json`。

理由：审计查询适合结构化表，长 trace 和工具输出适合追加文件。工件落到 workspace 的 `.coder` 目录，更贴近本地代码助手围绕当前仓库连续工作的设计。`trace.jsonl` 一行一个事件，更适合长任务增量写入和回放。

替代方案：全部存 MySQL。查询集中但大文本写入和回放成本更高。

### Decision 7: search_text 默认使用 Java 扫描

`search_text` 默认使用 Java 内置文件扫描实现(无可用`rg`命令)。

理由：目标运行环境是 Windows PowerShell，且不要求安装 BurntSushi.ripgrep.MSVC。Java 内置扫描可以减少首版外部依赖，保证开箱可用。

替代方案：优先调用 `rg` 并 fallback 到 Java 扫描。该方案性能更好，但会增加 Windows 环境依赖和安装前置条件。

### Decision 8: 首版不加 API 鉴权

首版 REST API 不增加 token 或登录鉴权，定位为本地开发环境服务。

理由：当前目标是打通本地 Agent Harness 闭环，鉴权会增加非核心复杂度。后续若暴露给其他服务，再增加 `X-Agent-Token` 或更完整的认证机制。

替代方案：首版加入可选 token。安全性更好，但当前用户确认首版不加。

## Risks / Trade-offs

- [Risk] 模型陷入循环或频繁工具调用 -> 通过 `max_steps=25`、`max_model_calls=25`、`max_tool_calls=50`、`timeout_seconds=300` 强制停止。
- [Risk] Shell 白名单仍可能漏掉危险组合 -> 首版拒绝链式执行、管道、重定向和高风险命令，并记录 `REJECTED` 审计事件。
- [Risk] OpenAI-compatible API 的不同供应商响应格式存在差异 -> Infrastructure 层做网关适配，Domain 只依赖统一 `ModelGateway`。
- [Risk] 本地工件目录膨胀 -> 首版按 runId 分目录落盘，后续增加保留策略和清理任务。
- [Risk] 首版不支持文件编辑，无法完成完整修复任务 -> 首版定位为开发可用的分析和诊断闭环，文件编辑作为后续高风险审批能力开放。
- [Risk] 无 API 鉴权导致服务不适合暴露到公网或共享网络 -> 首版 README 明确仅限本地开发使用，生产或共享环境必须先增加鉴权。
- [Risk] 限定 Windows PowerShell 降低跨平台能力 -> 首版接受该限制，后续如需跨平台再抽象多平台 Shell 策略。
- [Risk] 断网或模型 API 不可用导致 Agent 无法继续规划 -> Spring Boot 服务保持运行，当前 AgentRun 标记为 `FAILED`，并在 trace 与 `final-result.json` 中记录模型调用失败原因。

## Migration Plan

1. 生成 Maven 多模块 DDD 项目骨架。
2. 新增 MySQL 初始化 SQL 和 MyBatis Mapper。
3. 配置 OpenAI-compatible Responses API 参数、workspaceKey、工具白名单和 `.coder` 工件目录策略。
4. 启动 Spring Boot 应用，验证健康检查和 REST API。
5. 使用真实 OpenAI-compatible API 和一个本地仓库任务验证创建运行、工具调用、trace 查询和最终结果落盘。

回滚策略：

- 停止 `coder-agent-app` 服务。
- 删除或停用新增 REST API 路由。
- 回滚新增数据库表或保留空表不再写入。
- 删除新增运行工件目录。
- 移除模型和 workspace 配置。
- 删除 workspace 内 `.coder/runs/{runId}/` 运行工件。

## Open Questions

- 首版是否启用 Redis 暂不确定；当前设计不依赖 Redis。
- 首版真实可用模型名称由运行环境配置决定，文档默认使用 OpenAI-compatible `model` 配置项。
