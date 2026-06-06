import type { AgentRunEvent, ConversationMessage } from "./api/types";

const TERMINAL_STATUSES = new Set(["SUCCEEDED", "SUCCESS", "SUCCEED", "FAILED", "FAILURE", "CANCELLED", "CANCELED"]);

function normalizeStatus(status?: string) {
  return (status || "").trim().replace(/-/g, "_").toUpperCase();
}

function eventLabel(type?: string) {
  const map: Record<string, string> = {
    run_created: "创建运行",
    run_started: "开始运行",
    model_call_started: "调用模型",
    model_call_completed: "模型完成",
    tool_call_started: "调用工具",
    tool_call_completed: "工具完成",
    file_changed: "文件变更",
    test_reported: "测试报告",
    git_committed: "本地提交",
    pr_draft_generated: "PR 草稿",
    audit_event: "审计事件",
    run_finished: "运行结束"
  };
  return type ? map[type] || type : "事件";
}

function summarizePayload(payload?: Record<string, unknown>) {
  if (!payload || Object.keys(payload).length === 0) {
    return "暂无详情";
  }
  return Object.entries(payload)
    .filter(([key]) => key !== "status")
    .slice(0, 4)
    .map(([key, value]) => `${key}=${typeof value === "object" ? JSON.stringify(value) : String(value)}`)
    .join("，");
}

export function isTerminalRunStatus(status?: string) {
  return TERMINAL_STATUSES.has(normalizeStatus(status));
}

export function isCancelledRunStatus(status?: string) {
  const normalized = normalizeStatus(status);
  return normalized === "CANCELLED" || normalized === "CANCELED";
}

export function isStatusOnlyAgentMessage(message: Pick<ConversationMessage, "role" | "content">) {
  if (message.role !== "AGENT") {
    return false;
  }
  const content = (message.content || "").trim();
  return (
    /^运行结束[:：]/.test(content) ||
    /^运行失败[:：]/.test(content) ||
    /^运行已取消/.test(content) ||
    /^已取消$/.test(content) ||
    /^成功$/.test(content) ||
    content.includes("已执行进度：")
  );
}

export function buildTransientAgentMessage(input: {
  runId: string;
  conversationId: string;
  latestEvent?: AgentRunEvent;
}): ConversationMessage {
  const detail = input.latestEvent ? summarizePayload(input.latestEvent.payload) : "正在运行...";
  return {
    messageId: `transient-agent-${input.runId}`,
    conversationId: input.conversationId,
    runId: input.runId,
    role: "AGENT",
    content: "思考中...",
    progress:
      input.latestEvent && input.latestEvent.type !== "run_finished"
        ? `${eventLabel(input.latestEvent.type)}${detail ? `：${detail}` : ""}`
        : input.latestEvent
          ? undefined
          : detail,
    transient: true
  };
}
