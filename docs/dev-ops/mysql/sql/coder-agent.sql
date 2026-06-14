/*
 Navicat Premium Data Transfer

 Source Server         : docker-mysql
 Source Server Type    : MySQL
 Source Server Version : 80032
 Source Host           : localhost:13306
 Source Schema         : coder-agent

 Target Server Type    : MySQL
 Target Server Version : 80032
 File Encoding         : 65001

 Date: 14/06/2026 21:12:43
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for agent_conversation
-- ----------------------------
DROP TABLE IF EXISTS `agent_conversation`;
CREATE TABLE `agent_conversation`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `conversation_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '会话ID',
  `workspace_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '工作区标识',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '会话标题',
  `default_model` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '默认模型配置标识',
  `default_permission_level` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'L1_READ_ONLY' COMMENT '默认权限等级',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  `updated_at` datetime NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `conversation_id`(`conversation_id`) USING BTREE,
  INDEX `idx_agent_conversation_workspace`(`workspace_key`) USING BTREE,
  INDEX `idx_agent_conversation_updated`(`updated_at`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 17 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Agent会话记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for agent_memory_item
-- ----------------------------
DROP TABLE IF EXISTS `agent_memory_item`;
CREATE TABLE `agent_memory_item`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `memory_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '记忆ID',
  `workspace_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '工作区标识',
  `source_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '来源类型',
  `source_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '来源ID',
  `file_path` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件路径',
  `content_hash` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '内容哈希',
  `file_mtime` datetime NULL DEFAULT NULL COMMENT '文件修改时间',
  `summary_version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'v1' COMMENT '摘要版本',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '记忆标题',
  `summary` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '摘要内容',
  `metadata_json` json NULL COMMENT '记忆元数据',
  `freshness_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'FRESH' COMMENT '新鲜度状态',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  `updated_at` datetime NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `memory_id`(`memory_id`) USING BTREE,
  INDEX `idx_memory_workspace`(`workspace_key`) USING BTREE,
  INDEX `idx_memory_source`(`source_type`, `source_id`) USING BTREE,
  INDEX `idx_memory_file`(`workspace_key`, `file_path`(512)) USING BTREE,
  INDEX `idx_memory_freshness`(`freshness_status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 175 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '结构化记忆元数据' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for agent_message
-- ----------------------------
DROP TABLE IF EXISTS `agent_message`;
CREATE TABLE `agent_message`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `message_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '消息ID',
  `conversation_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '会话ID',
  `run_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '关联运行ID',
  `role` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '消息角色：USER用户，AGENT助手，SYSTEM系统',
  `content` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '消息内容',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `message_id`(`message_id`) USING BTREE,
  INDEX `idx_agent_message_conversation`(`conversation_id`, `created_at`) USING BTREE,
  INDEX `idx_agent_message_run`(`run_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 240 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Agent会话消息' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for agent_model_provider
-- ----------------------------
DROP TABLE IF EXISTS `agent_model_provider`;
CREATE TABLE `agent_model_provider`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `model_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模型配置标识',
  `display_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模型显示名称',
  `provider` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模型提供方',
  `base_url` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模型接口基础地址',
  `api_key_cipher` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '加密后的 API Key',
  `model_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '实际模型名称',
  `endpoint_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '接口协议类型：chat-completions 或 responses',
  `temperature` decimal(4, 3) NOT NULL DEFAULT 0.200 COMMENT '采样温度',
  `timeout_seconds` int NOT NULL DEFAULT 180 COMMENT '调用超时时间（秒）',
  `streaming_enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否支持流式输出',
  `tool_calling_enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否支持工具调用',
  `default_model` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否默认模型',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'ENABLED' COMMENT '配置状态',
  `max_context_tokens` int NULL DEFAULT NULL COMMENT '最大上下文 Token',
  `max_output_tokens` int NULL DEFAULT NULL COMMENT '最大输出 Token',
  `input_budget_tokens` int NULL DEFAULT NULL COMMENT '输入预算 Token',
  `memory_budget_tokens` int NULL DEFAULT NULL COMMENT '记忆预算 Token',
  `file_summary_budget_tokens` int NULL DEFAULT NULL COMMENT '文件摘要预算 Token',
  `raw_file_budget_tokens` int NULL DEFAULT NULL COMMENT '原始文件片段预算 Token',
  `tool_result_budget_tokens` int NULL DEFAULT NULL COMMENT '工具结果预算 Token',
  `recent_message_budget_tokens` int NULL DEFAULT NULL COMMENT '最近消息预算 Token',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  `updated_at` datetime NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `model_key`(`model_key`) USING BTREE,
  INDEX `idx_model_provider_status`(`status`) USING BTREE,
  INDEX `idx_model_provider_default`(`default_model`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '模型配置' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for agent_permission_audit
-- ----------------------------
DROP TABLE IF EXISTS `agent_permission_audit`;
CREATE TABLE `agent_permission_audit`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `run_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '运行ID',
  `conversation_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '会话ID',
  `workspace_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '工作区标识',
  `permission_level` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '权限等级',
  `action` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '审计动作',
  `detail` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '审计详情',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_permission_audit_run`(`run_id`) USING BTREE,
  INDEX `idx_permission_audit_workspace`(`workspace_key`) USING BTREE,
  INDEX `idx_permission_audit_action`(`action`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 42 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Agent权限审计记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for agent_run
-- ----------------------------
DROP TABLE IF EXISTS `agent_run`;
CREATE TABLE `agent_run`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `run_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '运行ID',
  `workspace_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '工作区标识',
  `conversation_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '会话ID',
  `task` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户任务',
  `model` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '模型配置标识',
  `permission_level` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'L1_READ_ONLY' COMMENT '权限等级：L1只读，L2安全编辑，L3仓库写入',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '运行状态',
  `final_answer` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '最终回答',
  `failure_reason` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '失败原因',
  `git_branch` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '本地Git分支',
  `commit_hash` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '本地提交哈希',
  `max_steps` int NOT NULL DEFAULT 25 COMMENT '最大步骤数',
  `max_model_calls` int NOT NULL DEFAULT 25 COMMENT '最大模型调用次数',
  `max_tool_calls` int NOT NULL DEFAULT 50 COMMENT '最大工具调用次数',
  `timeout_seconds` int NOT NULL DEFAULT 300 COMMENT '运行超时时间（秒）',
  `step_count` int NOT NULL DEFAULT 0 COMMENT '已执行步骤数',
  `model_call_count` int NOT NULL DEFAULT 0 COMMENT '已调用模型次数',
  `tool_call_count` int NOT NULL DEFAULT 0 COMMENT '已调用工具次数',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  `started_at` datetime NULL DEFAULT NULL COMMENT '开始时间',
  `ended_at` datetime NULL DEFAULT NULL COMMENT '结束时间',
  `duration_ms` bigint NULL DEFAULT NULL COMMENT '运行耗时（毫秒）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `run_id`(`run_id`) USING BTREE,
  INDEX `idx_agent_run_status`(`status`) USING BTREE,
  INDEX `idx_agent_run_workspace`(`workspace_key`) USING BTREE,
  INDEX `idx_agent_run_conversation`(`conversation_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 163 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Agent运行记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for agent_step
-- ----------------------------
DROP TABLE IF EXISTS `agent_step`;
CREATE TABLE `agent_step`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `run_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '运行ID',
  `step_no` int NOT NULL COMMENT '步骤序号',
  `step_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '步骤类型',
  `summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '步骤摘要',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_agent_step_run`(`run_id`, `step_no`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 255 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Agent步骤记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for agent_workspace
-- ----------------------------
DROP TABLE IF EXISTS `agent_workspace`;
CREATE TABLE `agent_workspace`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `workspace_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '工作区标识',
  `root_path` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '本地项目根目录',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '工作区状态',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  `updated_at` datetime NOT NULL COMMENT '更新时间',
  `deleted_at` datetime NULL DEFAULT NULL COMMENT '停用时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `workspace_key`(`workspace_key`) USING BTREE,
  INDEX `idx_agent_workspace_status`(`status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 14 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Agent工作区记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for audit_event
-- ----------------------------
DROP TABLE IF EXISTS `audit_event`;
CREATE TABLE `audit_event`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `run_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '运行ID',
  `event_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '审计事件类型',
  `message` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '事件消息',
  `detail` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '事件详情',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_audit_event_run`(`run_id`) USING BTREE,
  INDEX `idx_audit_event_type`(`event_type`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 555 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '审计事件记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for context_snapshot
-- ----------------------------
DROP TABLE IF EXISTS `context_snapshot`;
CREATE TABLE `context_snapshot`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `snapshot_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '上下文快照ID',
  `run_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '运行ID',
  `workspace_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '工作区标识',
  `model_call_no` int NOT NULL COMMENT '模型调用序号',
  `model_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模型配置标识',
  `budget_source` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '预算来源',
  `raw_estimated_tokens` int NOT NULL DEFAULT 0 COMMENT '原始候选 Token 估算',
  `final_estimated_tokens` int NOT NULL DEFAULT 0 COMMENT '最终上下文 Token 估算',
  `compression_ratio` decimal(8, 4) NOT NULL DEFAULT 0.0000 COMMENT '压缩率',
  `memory_hit_count` int NOT NULL DEFAULT 0 COMMENT '记忆命中数量',
  `stale_memory_count` int NOT NULL DEFAULT 0 COMMENT '过期记忆数量',
  `selected_file_summary_count` int NOT NULL DEFAULT 0 COMMENT '入选文件摘要数量',
  `selected_raw_snippet_count` int NOT NULL DEFAULT 0 COMMENT '入选原始片段数量',
  `snapshot_path` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '快照工件相对路径',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `snapshot_id`(`snapshot_id`) USING BTREE,
  INDEX `idx_context_snapshot_run`(`run_id`, `model_call_no`) USING BTREE,
  INDEX `idx_context_snapshot_workspace`(`workspace_key`) USING BTREE,
  INDEX `idx_context_snapshot_model`(`model_key`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 836 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '上下文快照指标' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for eval_benchmark
-- ----------------------------
DROP TABLE IF EXISTS `eval_benchmark`;
CREATE TABLE `eval_benchmark`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `benchmark_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '评测任务ID',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '评测任务名称',
  `workspace_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '工作区标识',
  `task` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户任务',
  `permission_level` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '权限等级',
  `model_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '默认模型配置',
  `expected_outcome` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '期望结果',
  `evaluator_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'MANUAL' COMMENT '评测器类型',
  `timeout_seconds` int NOT NULL DEFAULT 600 COMMENT '超时时间（秒）',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  `updated_at` datetime NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `benchmark_id`(`benchmark_id`) USING BTREE,
  INDEX `idx_eval_benchmark_workspace`(`workspace_key`) USING BTREE,
  INDEX `idx_eval_benchmark_status`(`status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '评测基准任务' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for eval_case_result
-- ----------------------------
DROP TABLE IF EXISTS `eval_case_result`;
CREATE TABLE `eval_case_result`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `eval_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '评测运行ID',
  `benchmark_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '评测任务ID',
  `run_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'Agent 运行ID',
  `model_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模型配置标识',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用例状态',
  `passed` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否通过',
  `attempts` int NOT NULL DEFAULT 0 COMMENT '尝试次数',
  `model_calls` int NOT NULL DEFAULT 0 COMMENT '模型调用次数',
  `tool_calls` int NOT NULL DEFAULT 0 COMMENT '工具调用次数',
  `tool_steps` int NOT NULL DEFAULT 0 COMMENT '工具步骤数',
  `duration_ms` bigint NULL DEFAULT NULL COMMENT '耗时（毫秒）',
  `failure_category` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '失败分类',
  `context_compression_ratio` decimal(8, 4) NULL DEFAULT NULL COMMENT '上下文压缩率',
  `memory_hit_count` int NOT NULL DEFAULT 0 COMMENT '记忆命中数量',
  `result_path` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '结果工件路径',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_eval_case_eval`(`eval_id`) USING BTREE,
  INDEX `idx_eval_case_benchmark`(`benchmark_id`) USING BTREE,
  INDEX `idx_eval_case_model`(`model_key`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '评测用例结果' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for eval_run
-- ----------------------------
DROP TABLE IF EXISTS `eval_run`;
CREATE TABLE `eval_run`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `eval_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '评测运行ID',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '评测运行名称',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '运行状态',
  `model_keys` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '参与评测的模型',
  `pass_rate` decimal(8, 4) NULL DEFAULT NULL COMMENT '通过率',
  `report_path` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '报告路径',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  `started_at` datetime NULL DEFAULT NULL COMMENT '开始时间',
  `ended_at` datetime NULL DEFAULT NULL COMMENT '结束时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `eval_id`(`eval_id`) USING BTREE,
  INDEX `idx_eval_run_status`(`status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '评测运行' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for memory_recall
-- ----------------------------
DROP TABLE IF EXISTS `memory_recall`;
CREATE TABLE `memory_recall`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `recall_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '召回ID',
  `run_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '运行ID',
  `workspace_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '工作区标识',
  `query_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '召回查询文本',
  `top_k` int NOT NULL COMMENT '召回数量上限',
  `min_score` decimal(8, 4) NOT NULL COMMENT '最低分数',
  `hit_count` int NOT NULL DEFAULT 0 COMMENT '命中数量',
  `selected_count` int NOT NULL DEFAULT 0 COMMENT '入选数量',
  `detail_json` json NULL COMMENT '召回明细',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `recall_id`(`recall_id`) USING BTREE,
  INDEX `idx_memory_recall_run`(`run_id`) USING BTREE,
  INDEX `idx_memory_recall_workspace`(`workspace_key`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 107 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '记忆召回记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for model_call
-- ----------------------------
DROP TABLE IF EXISTS `model_call`;
CREATE TABLE `model_call`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `run_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '运行ID',
  `call_no` int NOT NULL COMMENT '调用序号',
  `provider` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模型提供方',
  `model` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '实际调用模型',
  `request_summary` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '请求摘要',
  `response_summary` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '响应摘要',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '调用状态',
  `latency_ms` bigint NULL DEFAULT NULL COMMENT '调用耗时（毫秒）',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '错误信息',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_model_call_run`(`run_id`, `call_no`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1011 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '模型调用记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for run_artifact
-- ----------------------------
DROP TABLE IF EXISTS `run_artifact`;
CREATE TABLE `run_artifact`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `run_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '运行ID',
  `artifact_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '工件类型',
  `relative_path` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '相对路径',
  `file_size` bigint NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_run_artifact_run`(`run_id`) USING BTREE,
  INDEX `idx_run_artifact_type`(`artifact_type`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 45587 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '运行工件记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tool_approval_request
-- ----------------------------
DROP TABLE IF EXISTS `tool_approval_request`;
CREATE TABLE `tool_approval_request`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `approval_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '审批ID',
  `run_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '运行ID',
  `workspace_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '工作区标识',
  `tool_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '工具名称',
  `arguments_json` json NOT NULL COMMENT '工具参数',
  `risk_summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '风险说明',
  `diff_summary` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '差异摘要',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '审批状态',
  `requested_at` datetime NOT NULL COMMENT '请求时间',
  `decided_at` datetime NULL DEFAULT NULL COMMENT '决策时间',
  `decision_reason` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '决策原因',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `approval_id`(`approval_id`) USING BTREE,
  INDEX `idx_tool_approval_run`(`run_id`) USING BTREE,
  INDEX `idx_tool_approval_status`(`status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 37 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '高风险工具审批请求' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tool_call
-- ----------------------------
DROP TABLE IF EXISTS `tool_call`;
CREATE TABLE `tool_call`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `run_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '运行ID',
  `call_no` int NOT NULL COMMENT '调用序号',
  `tool_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '工具名称',
  `arguments_summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '参数摘要',
  `result_summary` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '结果摘要',
  `exit_code` int NULL DEFAULT NULL COMMENT '退出码',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '调用状态',
  `latency_ms` bigint NULL DEFAULT NULL COMMENT '调用耗时（毫秒）',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '错误信息',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tool_call_run`(`run_id`, `call_no`) USING BTREE,
  INDEX `idx_tool_call_name`(`tool_name`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1244 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '工具调用记录' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
