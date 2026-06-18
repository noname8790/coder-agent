## ADDED Requirements

### Requirement: Codex 风格权限选择器
客户端 MUST 将权限选择器显示为三项纵向选项：只读、默认、完全控制。每项左侧显示图标，标题使用深色文本，描述使用灰色文本；选中项右侧显示 “√”；“完全控制”项 MUST 使用黄色风险标识。涉及页面：对话输入区权限下拉栏。

#### Scenario: 展示权限选项
- **WHEN** 用户打开权限选择器
- **THEN** 客户端 MUST 显示三个权限等级的图标、标题、描述和当前选中标识

#### Scenario: 完全控制黄色提示
- **WHEN** 权限选择器展示“完全控制”
- **THEN** 客户端 MUST 使用黄色风险样式提示该等级风险

### Requirement: conversation 权限恢复
客户端 MUST 在切换或打开 conversation 时读取后端保存的 `lastPermissionLevel` 并设置为当前选择。若后端未返回该值，客户端 MUST 使用“默认”。涉及 API：conversation 列表/详情。

#### Scenario: 打开已有 conversation 恢复权限
- **WHEN** 用户打开一个保存了 `READ_ONLY` 的 conversation
- **THEN** 客户端权限选择器 MUST 显示“只读”

#### Scenario: 新 conversation 使用默认权限
- **WHEN** 用户第一次打开新 conversation
- **THEN** 客户端权限选择器 MUST 显示“默认”

#### Scenario: 切换会话恢复各自权限
- **WHEN** 用户从保存为 `FULL_ACCESS` 的 conversation 切换到保存为 `DEFAULT` 的 conversation
- **THEN** 客户端权限选择器 MUST 显示“默认”，不得沿用上一个会话的“完全控制”

### Requirement: 消息复制入口
客户端 MUST 在每条用户消息和 Agent 消息下方显示复制图标。用户点击后 MUST 复制该消息原始文本，并显示短暂成功提示。涉及页面：对话消息列表。

#### Scenario: 复制 Agent 消息
- **WHEN** 用户点击 Agent 消息下方复制图标
- **THEN** 客户端 MUST 将 Agent 消息原始 Markdown 文本复制到剪贴板

#### Scenario: 复制用户消息
- **WHEN** 用户点击用户消息下方复制图标
- **THEN** 客户端 MUST 将用户消息原始文本复制到剪贴板

### Requirement: Diff 摘要卡片
客户端 MUST 在有文件变更的 Agent 消息下方展示 Diff 摘要卡片。卡片默认展示前 3 个文件、总文件数、总新增行、总删除行；当变更超过 3 个文件时，客户端 MUST 提供展开入口显示全部文件。每个文件行 MUST 可按需展开截断 patch 片段，并用颜色和行号区分新增/删除内容。涉及页面：对话消息列表。

#### Scenario: 默认展示前三个文件
- **WHEN** Agent 消息包含 13 个文件变更
- **THEN** 客户端 MUST 默认显示前 3 个文件，并显示“再显示 10 个文件”入口

#### Scenario: 展开全部文件
- **WHEN** 用户点击展开入口
- **THEN** 客户端 MUST 显示全部变更文件及每个文件的增删行统计

#### Scenario: 展开单文件 patch
- **WHEN** 用户点击某个变更文件的展开入口
- **THEN** 客户端 MUST 显示该文件的截断 patch 片段，并用绿色标识新增行、红色标识删除行
