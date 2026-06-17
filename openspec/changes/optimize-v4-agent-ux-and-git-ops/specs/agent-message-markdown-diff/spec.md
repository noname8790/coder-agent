## ADDED Requirements

### Requirement: Agent 消息 Markdown 渲染
客户端 MUST 使用 Markdown 渲染 Agent 消息正文，支持标题、列表、代码块、引用、表格和链接。客户端 MUST 禁用或清洗原始 HTML，避免模型输出脚本或危险标签。涉及页面：对话消息列表。

#### Scenario: 渲染 Markdown 代码块
- **WHEN** Agent 消息包含 Markdown 代码块
- **THEN** 客户端 MUST 以代码块形式展示内容，并保留原文复制能力

#### Scenario: 拦截危险 HTML
- **WHEN** Agent 消息包含 `<script>` 或危险 HTML
- **THEN** 客户端 MUST 不执行脚本，并以安全文本或清洗后的内容展示

### Requirement: 流式 Markdown 稳定显示
客户端 MUST 支持正在流式输出的 Markdown 草稿稳定追加显示。客户端不得因 run 状态刷新、会话切换或 Diff 摘要刷新而清空已输出 chunk。涉及 API：SSE、run 草稿查询、message 详情。

#### Scenario: 流式追加 Markdown
- **WHEN** SSE 持续推送 Agent Markdown delta
- **THEN** 客户端 MUST 顺序追加到当前 Agent 消息，不得重复从头播放或丢失已输出片段

#### Scenario: 切换会话恢复草稿
- **WHEN** 用户切换会话后回到正在运行的 run
- **THEN** 客户端 MUST 恢复已输出 Markdown 草稿，并在末尾继续显示“思考中...”

### Requirement: 消息状态提示
客户端 MUST 根据 Agent 消息状态展示结尾状态提示。`CANCELED` 消息 MUST 在正文末尾额外显示加粗“已取消”；`FAILED` 消息 MUST 在正文末尾额外显示加粗“运行失败：<failureReason>”；成功消息不得显示额外状态提示。状态提示 MUST 仅出现一次。

#### Scenario: 取消消息显示一次
- **WHEN** Agent 消息状态为 `CANCELED`
- **THEN** 客户端 MUST 在消息末尾显示一次加粗“已取消”

#### Scenario: 失败消息显示原因
- **WHEN** Agent 消息状态为 `FAILED` 且包含 failureReason
- **THEN** 客户端 MUST 在消息末尾显示一次加粗“运行失败：<failureReason>”
