# 项目指南

## 语言要求
- 所有输出请用中文

## 技术栈
Spring Boot 3.x + MyBatis-Plus + JDK 17/21 + MySQL + Elasticsearch + PostgreSQL/PGVctor + Redis + Docker + Spring AI Alibaba/ LangChain4j + OpenAI/阿里云百炼dashscope API + Tool Calling + SLF4J + ...

## OpenSpec 工作流
| 命令 | 说明 |
|------|------|
| /opsx:new | 创建变更 |
| /opsx:ff | 快速生成所有文档 |
| /opsx:apply | 执行实现 |
| /opsx:verify | 验证一致性 |
| /opsx:archive | 归档 |

## 开发规范
- 使用 Given/When/Then 格式描述测试场景
- 变更必须包含回滚方案
- 标注影响的模块和数据库表



## OpenSpec + Superpowers 协同规则

### 规划阶段
- 创建变更时指定 schema：`/opsx:new 任务名`
- 自动调用 `superpowers:brainstorming`

### 实现阶段
- `/opsx:apply` 自动调用 `superpowers:subagent-driven-development`
- 每个任务强制 TDD：RED → GREEN → REFACTOR
- 代码审查：两阶段 subagent 审查

### 验证阶段
- 5 项检查：结构验证、任务完成、Delta Spec 同步、设计/规格一致性、实现信号