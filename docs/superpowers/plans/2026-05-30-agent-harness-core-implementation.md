# Agent Harness Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建首版 Java 服务端 Agent Harness，支持 REST 异步运行、OpenAI-compatible Responses API、受限工具调用、MySQL 审计和 `.coder` 工件落盘。

**Architecture:** 按 Maven 多模块 DDD 分层实现：API 定义 DTO，Domain 定义模型和端口，Case 编排异步执行循环，Infrastructure 负责 MySQL、模型网关、工具和文件工件，Trigger 暴露 REST，App 负责启动和配置。首版只提供分析/诊断工具，不开放文件编辑、commit、push 或 PR。

**Tech Stack:** Spring Boot 3.x, JDK 21, Maven, MyBatis-Plus, MySQL, OkHttp, Jackson, Lombok, JUnit 5, Windows PowerShell.

---

### Task 1: Maven DDD 骨架

**Files:**
- Create: `pom.xml`
- Create: `coder-agent-types/pom.xml`
- Create: `coder-agent-api/pom.xml`
- Create: `coder-agent-domain/pom.xml`
- Create: `coder-agent-case/pom.xml`
- Create: `coder-agent-infrastructure/pom.xml`
- Create: `coder-agent-trigger/pom.xml`
- Create: `coder-agent-app/pom.xml`

- [ ] **Step 1: 写父 POM 与模块 POM**
  建立 `types -> domain/api -> case -> infrastructure/trigger -> app` 依赖结构。

- [ ] **Step 2: 写最小编译测试**
  为后续模块提供 JUnit 5 基础依赖。

- [ ] **Step 3: 运行验证**
  Run: `mvn test`
  Expected: 当前环境如 JDK 可用则编译通过；若 JDK 不可用，记录环境阻塞。

### Task 2: 领域模型和端口

**Files:**
- Create under `coder-agent-types/src/main/java/cn/noname/coder/agent/types/**`
- Create under `coder-agent-domain/src/main/java/cn/noname/coder/agent/domain/agent/**`

- [ ] **Step 1: 写状态和通用异常**
  定义 `AgentRunStatus`、`CallStatus`、`ToolCallStatus`、`ArtifactType`、`AppException`、`Response`。

- [ ] **Step 2: 写领域对象**
  定义 `AgentRun`、`AgentStep`、`ModelCall`、`ToolCall`、`AuditEvent`、`RunArtifact`。

- [ ] **Step 3: 写端口接口**
  定义运行仓储、记录仓储、模型网关、工具网关、工件端口、workspace 端口。

### Task 3: REST API 与用例编排

**Files:**
- Create under `coder-agent-api/src/main/java/cn/noname/coder/agent/api/**`
- Create under `coder-agent-case/src/main/java/cn/noname/coder/agent/case/**`
- Create under `coder-agent-trigger/src/main/java/cn/noname/coder/agent/trigger/http/**`

- [ ] **Step 1: 写 DTO**
  定义创建、查询、trace、取消请求/响应。

- [ ] **Step 2: 写用例**
  实现创建运行、查询运行、查询 trace、取消运行。

- [ ] **Step 3: 写 Controller**
  暴露 `POST /api/agent-runs`、`GET /api/agent-runs/{runId}`、`GET /api/agent-runs/{runId}/trace`、`POST /api/agent-runs/{runId}/cancel`。

### Task 4: Agent 执行循环

**Files:**
- Create: `coder-agent-case/src/main/java/cn/noname/coder/agent/case/agent/AgentRunExecutor.java`
- Create: `coder-agent-case/src/main/java/cn/noname/coder/agent/case/agent/AgentContextAssembler.java`

- [ ] **Step 1: 写失败优先测试**
  覆盖预算耗尽、取消、异常失败。

- [ ] **Step 2: 实现预算、并发限制和终止条件**
  默认 `max_steps=25`、`max_model_calls=25`、`max_tool_calls=50`、`timeout_seconds=300`、`max_concurrent_runs=2`。

- [ ] **Step 3: 串联模型和工具**
  模型返回 final answer 则成功；返回 tool call 则执行工具并继续下一轮。

### Task 5: Infrastructure 适配

**Files:**
- Create under `coder-agent-infrastructure/src/main/java/cn/noname/coder/agent/infrastructure/dao/**`
- Create under `coder-agent-infrastructure/src/main/java/cn/noname/coder/agent/infrastructure/adapter/**`
- Create under `coder-agent-infrastructure/src/main/java/cn/noname/coder/agent/infrastructure/gateway/**`
- Create under `coder-agent-infrastructure/src/main/resources/mybatis/mapper/**`

- [ ] **Step 1: 写 MyBatis PO/DAO/Repository**
  禁止 `persistent` 包，DAO 放 `dao`，PO 放 `dao/po`，Repository 放 `adapter/repository`。

- [ ] **Step 2: 写 OpenAI Responses 网关**
  支持 `base-url`、`api-key`、`model`、`temperature`、`timeout`，解析 `function_call` 与 `output_text`。

- [ ] **Step 3: 写工具系统**
  实现 `list_files`、`read_file`、`search_text`、`run_shell`，其中 `search_text` 优先 `rg`，失败 fallback Java 扫描。

- [ ] **Step 4: 写工件端口**
  在 `{workspaceRoot}/.coder/runs/{runId}/` 写 `run-meta.json`、`trace.jsonl`、`context-snapshot/*.json`、`tool-output/*.txt`、`final-result.json`。

### Task 6: SQL、配置、文档和验证

**Files:**
- Create: `docs/dev-ops/mysql/sql/coder_agent.sql`
- Create: `docs/dev-ops/docker-compose-environment.yml`
- Create: `coder-agent-app/src/main/resources/application.yml`
- Create: `README.md`

- [ ] **Step 1: 写建表 SQL**
  创建 `agent_run`、`agent_step`、`model_call`、`tool_call`、`audit_event`、`run_artifact`。

- [ ] **Step 2: 写配置**
  数据源、OpenAI URL/API Key/model、workspace root 等值留空或环境变量占位。

- [ ] **Step 3: 写 README**
  覆盖配置、启动、curl、workspaceKey、工具白名单、真实 API 冒烟验证和回滚方案。

- [ ] **Step 4: 运行验证**
  Run: `mvn test`
  Run: `openspec validate build-agent-harness-core`
  Expected: OpenSpec 通过；Maven 取决于本地 JDK 可用性。

