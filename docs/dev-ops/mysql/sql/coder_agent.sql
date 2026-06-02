CREATE DATABASE IF NOT EXISTS `coder-agent` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `coder-agent`;

CREATE TABLE IF NOT EXISTS agent_run (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL UNIQUE COMMENT '运行ID',
    workspace_key VARCHAR(128) NOT NULL COMMENT '工作区标识',
    conversation_id VARCHAR(64) NULL COMMENT '会话ID',
    task TEXT NOT NULL COMMENT '用户任务',
    model VARCHAR(128) NULL COMMENT '模型配置标识',
    permission_level VARCHAR(32) NOT NULL DEFAULT 'L1_READ_ONLY' COMMENT '权限等级：L1只读，L2安全编辑，L3仓库写入',
    status VARCHAR(32) NOT NULL COMMENT '运行状态',
    final_answer MEDIUMTEXT NULL COMMENT '最终回答',
    failure_reason TEXT NULL COMMENT '失败原因',
    git_branch VARCHAR(255) NULL COMMENT '本地Git分支',
    commit_hash VARCHAR(128) NULL COMMENT '本地提交哈希',
    max_steps INT NOT NULL DEFAULT 25 COMMENT '最大步骤数',
    max_model_calls INT NOT NULL DEFAULT 25 COMMENT '最大模型调用次数',
    max_tool_calls INT NOT NULL DEFAULT 50 COMMENT '最大工具调用次数',
    timeout_seconds INT NOT NULL DEFAULT 300 COMMENT '运行超时时间（秒）',
    step_count INT NOT NULL DEFAULT 0 COMMENT '已执行步骤数',
    model_call_count INT NOT NULL DEFAULT 0 COMMENT '已调用模型次数',
    tool_call_count INT NOT NULL DEFAULT 0 COMMENT '已调用工具次数',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    started_at DATETIME NULL COMMENT '开始时间',
    ended_at DATETIME NULL COMMENT '结束时间',
    duration_ms BIGINT NULL COMMENT '运行耗时（毫秒）',
    INDEX idx_agent_run_status (status),
    INDEX idx_agent_run_workspace (workspace_key),
    INDEX idx_agent_run_conversation (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent运行记录';

CREATE TABLE IF NOT EXISTS agent_conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL UNIQUE COMMENT '会话ID',
    workspace_key VARCHAR(128) NOT NULL COMMENT '工作区标识',
    title VARCHAR(255) NOT NULL COMMENT '会话标题',
    default_model VARCHAR(128) NULL COMMENT '默认模型配置标识',
    default_permission_level VARCHAR(32) NOT NULL DEFAULT 'L1_READ_ONLY' COMMENT '默认权限等级',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    INDEX idx_agent_conversation_workspace (workspace_key),
    INDEX idx_agent_conversation_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent会话记录';

CREATE TABLE IF NOT EXISTS agent_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id VARCHAR(64) NOT NULL UNIQUE COMMENT '消息ID',
    conversation_id VARCHAR(64) NOT NULL COMMENT '会话ID',
    run_id VARCHAR(64) NULL COMMENT '关联运行ID',
    role VARCHAR(32) NOT NULL COMMENT '消息角色：USER用户，AGENT助手，SYSTEM系统',
    content MEDIUMTEXT NOT NULL COMMENT '消息内容',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    INDEX idx_agent_message_conversation (conversation_id, created_at),
    INDEX idx_agent_message_run (run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent会话消息';

CREATE TABLE IF NOT EXISTS agent_permission_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id VARCHAR(64) NULL COMMENT '运行ID',
    conversation_id VARCHAR(64) NULL COMMENT '会话ID',
    workspace_key VARCHAR(128) NOT NULL COMMENT '工作区标识',
    permission_level VARCHAR(32) NOT NULL COMMENT '权限等级',
    action VARCHAR(64) NOT NULL COMMENT '审计动作',
    detail TEXT NULL COMMENT '审计详情',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    INDEX idx_permission_audit_run (run_id),
    INDEX idx_permission_audit_workspace (workspace_key),
    INDEX idx_permission_audit_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent权限审计记录';

CREATE TABLE IF NOT EXISTS agent_workspace (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_key VARCHAR(128) NOT NULL UNIQUE COMMENT '工作区标识',
    root_path VARCHAR(1024) NOT NULL COMMENT '本地项目根目录',
    status VARCHAR(32) NOT NULL COMMENT '工作区状态',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    deleted_at DATETIME NULL COMMENT '停用时间',
    INDEX idx_agent_workspace_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent工作区记录';

CREATE TABLE IF NOT EXISTS agent_step (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL COMMENT '运行ID',
    step_no INT NOT NULL COMMENT '步骤序号',
    step_type VARCHAR(64) NOT NULL COMMENT '步骤类型',
    summary TEXT NULL COMMENT '步骤摘要',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    INDEX idx_agent_step_run (run_id, step_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent步骤记录';

CREATE TABLE IF NOT EXISTS model_call (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL COMMENT '运行ID',
    call_no INT NOT NULL COMMENT '调用序号',
    provider VARCHAR(64) NOT NULL COMMENT '模型提供方',
    model VARCHAR(128) NULL COMMENT '实际调用模型',
    request_summary MEDIUMTEXT NULL COMMENT '请求摘要',
    response_summary MEDIUMTEXT NULL COMMENT '响应摘要',
    status VARCHAR(32) NOT NULL COMMENT '调用状态',
    latency_ms BIGINT NULL COMMENT '调用耗时（毫秒）',
    error_message TEXT NULL COMMENT '错误信息',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    INDEX idx_model_call_run (run_id, call_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型调用记录';

CREATE TABLE IF NOT EXISTS tool_call (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL COMMENT '运行ID',
    call_no INT NOT NULL COMMENT '调用序号',
    tool_name VARCHAR(128) NOT NULL COMMENT '工具名称',
    arguments_summary TEXT NULL COMMENT '参数摘要',
    result_summary MEDIUMTEXT NULL COMMENT '结果摘要',
    exit_code INT NULL COMMENT '退出码',
    status VARCHAR(32) NOT NULL COMMENT '调用状态',
    latency_ms BIGINT NULL COMMENT '调用耗时（毫秒）',
    error_message TEXT NULL COMMENT '错误信息',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    INDEX idx_tool_call_run (run_id, call_no),
    INDEX idx_tool_call_name (tool_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工具调用记录';

CREATE TABLE IF NOT EXISTS audit_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL COMMENT '运行ID',
    event_type VARCHAR(64) NOT NULL COMMENT '审计事件类型',
    message VARCHAR(512) NOT NULL COMMENT '事件消息',
    detail TEXT NULL COMMENT '事件详情',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    INDEX idx_audit_event_run (run_id),
    INDEX idx_audit_event_type (event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计事件记录';

CREATE TABLE IF NOT EXISTS run_artifact (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL COMMENT '运行ID',
    artifact_type VARCHAR(64) NOT NULL COMMENT '工件类型',
    relative_path VARCHAR(512) NOT NULL COMMENT '相对路径',
    file_size BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    INDEX idx_run_artifact_run (run_id),
    INDEX idx_run_artifact_type (artifact_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运行工件记录';

-- 从第二版数据库升级到第三版时，如果 agent_run 已存在但缺少以下字段，请按需执行：
-- ALTER TABLE agent_run ADD COLUMN conversation_id VARCHAR(64) NULL COMMENT '会话ID' AFTER workspace_key;
-- ALTER TABLE agent_run ADD COLUMN permission_level VARCHAR(32) NOT NULL DEFAULT 'L1_READ_ONLY' COMMENT '权限等级：L1只读，L2安全编辑，L3仓库写入' AFTER model;
-- ALTER TABLE agent_run ADD COLUMN git_branch VARCHAR(255) NULL COMMENT '本地Git分支' AFTER failure_reason;
-- ALTER TABLE agent_run ADD COLUMN commit_hash VARCHAR(128) NULL COMMENT '本地提交哈希' AFTER git_branch;
-- ALTER TABLE agent_run ADD INDEX idx_agent_run_conversation (conversation_id);
-- 如果旧库仍保留第二版 mode、capabilities 字段，可执行：
-- ALTER TABLE agent_run DROP COLUMN mode;
-- ALTER TABLE agent_workspace DROP COLUMN capabilities;
-- 第三版回滚 SQL：
-- DROP TABLE IF EXISTS agent_permission_audit;
-- DROP TABLE IF EXISTS agent_message;
-- DROP TABLE IF EXISTS agent_conversation;
-- ALTER TABLE agent_run DROP COLUMN conversation_id, DROP COLUMN permission_level, DROP COLUMN git_branch, DROP COLUMN commit_hash;
