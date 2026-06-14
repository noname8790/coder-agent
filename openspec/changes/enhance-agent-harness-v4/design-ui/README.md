# v4 客户端设计记录

本目录保留 Pencil 设计稿 `change.pen`。当前 v4 客户端实现按以下设计范围落地。

## 模型配置页面

- 延续第三版浅色三栏客户端，不新增独立视觉风格。
- 配置项按“基础连接”“模型能力”“上下文预算”分组。
- 表单参考用户提供的配置截图：左侧细色条提示配置块，标题加粗，说明文字在输入框上方。
- 必填项：模型 key、展示名、Base URL、API Key、实际模型名、Endpoint Type。
- 可选项：temperature、timeoutSeconds、streamingEnabled、toolCallingEnabled、defaultModel、context budget。
- API Key 在列表中只显示脱敏值，编辑时允许重新输入覆盖。

## 对话与流式消息

- 模型选择器只展示后端返回的启用模型。
- 无启用模型时展示“请配置模型”，点击或发送任务均跳转模型配置页。
- Agent 正文由 `assistant_delta` 逐段追加，工具事件不作为正文长期展示。
- 工具事件最多作为当前消息的一行灰色临时状态，后续版本可进一步隐藏。

## 高风险审批弹窗

- 当 run 进入 `WAITING_APPROVAL` 时弹出审批层。
- 展示工具名、参数、风险说明、diff/目标路径摘要。
- 操作按钮：批准、拒绝。
- 拒绝后后端会把结构化拒绝结果回灌给 Agent，前端继续订阅同一 run 的输出。

## 运行指标展示

- 右侧运行详情展示 context tokens、compression ratio、memory hit、stale memory、selected snippet 和 snapshot 路径。
- 指标以小型统计块展示，不占用对话主区。

## 状态

- `WAITING_APPROVAL` 显示为“等待审批”。
- `SUCCEEDED`、`FAILED`、`CANCELLED` 只作为右侧状态，不追加为独立对话消息。
