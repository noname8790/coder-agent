## 1. 项目骨架与依赖

- [x] 1.1 使用 DDD 脚手架规范创建 Maven 多模块项目，坐标为 `cn.noname:coder-agent`，根包名为 `cn.noname.coder.agent`
- [x] 1.2 建立 `types`、`api`、`domain`、`case`、`infrastructure`、`trigger`、`app` 模块依赖关系
- [x] 1.3 配置 Spring Boot 3.x、JDK 21、MyBatis-Plus、MySQL、HTTP 客户端、Jackson、Lombok、JUnit 5
- [x] 1.4 添加 `application.yml` 配置结构，包含 OpenAI-compatible Responses API、workspace、预算、`.coder` 工件目录和工具白名单配置
- [x] 1.5 添加 Windows PowerShell-only 运行说明、Dockerfile 和本地 MySQL docker-compose 环境说明

## 2. 数据库与领域模型

- [x] 2.1 设计并创建 `agent_run`、`agent_step`、`model_call`、`tool_call`、`audit_event`、`run_artifact` 初始化 SQL
- [x] 2.2 在 Domain 层定义 AgentRun、AgentStep、ModelCall、ToolCall、AuditEvent、RunArtifact 领域对象和值对象
- [x] 2.3 在 Domain 层定义运行仓储、模型端口、工具端口、工件端口和 workspace 端口接口
- [x] 2.4 在 Infrastructure 层创建 DAO、PO、Mapper XML 和 Repository 实现，禁止使用 `persistent` 包
- [x] 2.5 添加数据库持久化单元测试，覆盖运行创建、状态变更、调用记录和工件索引写入

## 3. REST API 与用例编排

- [x] 3.1 在 API 层定义创建运行、查询运行、查询 trace、取消运行的 Request/Response DTO
- [x] 3.2 在 Case 层实现 `CreateAgentRunCase`、`QueryAgentRunCase`、`QueryRunTraceCase`、`CancelAgentRunCase`
- [x] 3.3 在 Trigger 层实现 `POST /api/agent-runs`
- [x] 3.4 在 Trigger 层实现 `GET /api/agent-runs/{runId}`
- [x] 3.5 在 Trigger 层实现 `GET /api/agent-runs/{runId}/trace`
- [x] 3.6 在 Trigger 层实现 `POST /api/agent-runs/{runId}/cancel`
- [x] 3.7 添加 REST API 测试，使用 Given/When/Then 覆盖创建成功、未知 workspace、查询运行和取消运行

## 4. Agent 执行循环

- [x] 4.1 实现异步后台执行器，创建运行后立即返回 `runId`
- [x] 4.2 实现运行状态机：`CREATED`、`RUNNING`、`SUCCEEDED`、`FAILED`、`CANCELLED`、`REJECTED`
- [x] 4.3 实现预算检查：`max_steps=25`、`max_model_calls=25`、`max_tool_calls=50`、`timeout_seconds=300`
- [x] 4.4 实现最大并发运行数限制：`max_concurrent_runs=2`
- [x] 4.5 实现基础上下文组装，包含系统提示词、用户任务、workspace 信息、最近工具结果摘要和预算使用情况
- [x] 4.6 实现模型循环终止条件：最终回答、取消、失败、拒绝、预算耗尽、超时
- [x] 4.7 添加执行循环测试，覆盖成功结束、预算耗尽、并发拒绝、取消和异常失败

## 5. 模型接入与 Tool Calling

- [x] 5.1 定义统一 `ModelGateway`、`ModelRequest`、`ModelResponse`、`ToolDefinition`、`ToolInvocation`
- [x] 5.2 实现 OpenAI-compatible Responses API Gateway，支持 `base-url`、`api-key`、`model`、`temperature`、`timeout`
- [x] 5.3 实现 OpenAI-compatible tools schema 构造和 `tool_calls` 解析
- [x] 5.4 将模型请求摘要、响应摘要、耗时和状态写入 `model_call`
- [x] 5.5 添加模型网关契约测试，使用 mock HTTP 响应覆盖最终回答和工具调用两类响应
- [x] 5.6 添加真实 OpenAI-compatible API 冒烟验证说明和可配置跳过机制，最终验收必须真实跑通

## 6. 工具系统与安全治理

- [x] 6.1 实现工具注册表，注册 `list_files`、`read_file`、`search_text`、`run_shell`
- [x] 6.2 实现 workspaceKey 解析和路径规范化校验，所有文件路径必须位于 workspace 根目录内
- [x] 6.3 实现 `list_files` 工具，返回目录项摘要并限制输出数量
- [x] 6.4 实现 `read_file` 工具，限制文本文件类型和读取大小
- [x] 6.5 实现 `search_text` 工具，优先调用 `rg`，不可用或执行失败时 fallback 到 Java 文件扫描，并限制搜索范围、文件大小和结果数量
- [x] 6.6 实现 Windows PowerShell 版 `run_shell` 工具，采用命令前缀白名单 + 危险 token 拒绝策略
- [x] 6.7 配置默认允许命令：`git status`、`git diff`、`git log`、`mvn test`、`mvn -q test`、`mvn clean test`、`mvn package`、`mvn -pl`、`java -version`
- [x] 6.8 将工具参数摘要、结果摘要、退出码、耗时和状态写入 `tool_call`
- [x] 6.9 将路径逃逸、无效参数、危险命令和非白名单命令写入 `audit_event`
- [x] 6.10 添加工具安全测试，覆盖路径逃逸、危险 token 和非白名单命令

## 7. 审计与运行工件

- [x] 7.1 在 `{workspaceRoot}/.coder/runs/{runId}/` 创建每次运行的工件目录并写入 `run-meta.json`
- [x] 7.2 实现 `trace.jsonl` 事件追加，一行一个可独立解析 JSON 事件
- [x] 7.3 实现模型调用前的 `context-snapshot/*.json` 摘要版快照
- [x] 7.4 实现长工具输出落盘到 `tool-output/*.txt`
- [x] 7.5 实现终态写入 `final-result.json`，包含最终结论、状态、attempts、tool_steps、model_calls、tool_calls 和 duration
- [x] 7.6 将所有工件索引写入 `run_artifact`
- [x] 7.7 添加工件测试，覆盖 JSONL 可解析和最终结果生成

## 8. 验证与文档

- [x] 8.1 添加项目 README，说明配置、启动、curl API 示例、workspaceKey、工具白名单和一条可跑通的本地仓库任务示例
- [x] 8.2 添加本地 MySQL 初始化和运行说明
- [x] 8.3 添加一组最小端到端测试，验证创建运行到最终 trace 和工件生成的闭环
- [x] 8.4 使用真实 OpenAI-compatible API 跑通一次本地仓库任务冒烟验证
- [x] 8.5 验证断网或模型 API 不可用时 AgentRun 标记为 `FAILED`，服务进程不崩溃，并写入 trace 和 `final-result.json`
- [x] 8.6 运行 Maven 测试并修复失败用例
- [x] 8.7 使用 `openspec validate build-agent-harness-core` 验证变更文档
- [x] 8.8 标注回滚步骤、影响模块、涉及表和 API，确保文档与实现一致


