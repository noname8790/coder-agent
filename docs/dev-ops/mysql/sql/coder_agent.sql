CREATE DATABASE IF NOT EXISTS `coder-agent` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `coder-agent`;

CREATE TABLE IF NOT EXISTS agent_run (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL UNIQUE COMMENT '运行ID',
    workspace_key VARCHAR(128) NOT NULL COMMENT '工作区标识',
    task TEXT NOT NULL COMMENT '用户任务',
    model VARCHAR(128) NULL COMMENT '模型配置标识',
    status VARCHAR(32) NOT NULL COMMENT '运行状态',
    final_answer MEDIUMTEXT NULL COMMENT '最终回答',
    failure_reason TEXT NULL COMMENT '失败原因',
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
    INDEX idx_agent_run_workspace (workspace_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent运行记录';

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
