import type { AgentRunEvent, ConversationMessage } from "./api/types";

export const THINKING_TEXT = "思考中...";
export const RUNNING_TEXT = "正在运行...";
export const REPLYING_TEXT = "正在回复";

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
    run_finished: "运行结束",
    assistant_message_started: "开始回复",
    assistant_delta: REPLYING_TEXT,
    assistant_message_completed: "回复完成",
    assistant_message_cancelled: "回复取消",
    model_stream_failure: "流式响应失败"
  };
  return type ? map[type] || type : "事件";
}

function summarizePayload(payload?: Record<string, unknown>) {
  if (!payload || Object.keys(payload).length === 0) {
    return "暂无详情";
  }
  return Object.entries(payload)
    .filter(([key]) => key !== "status" && key !== "delta")
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

export function stripThinking(content?: string) {
  let value = content || "";
  if (value === THINKING_TEXT) {
    return "";
  }
  if (value.endsWith(`\n\n${THINKING_TEXT}`)) {
    value = value.slice(0, -(`\n\n${THINKING_TEXT}`).length).trimEnd();
  } else if (value.endsWith(THINKING_TEXT)) {
    value = value.slice(0, -THINKING_TEXT.length).trimEnd();
  }
  return value;
}

export function appendThinking(content?: string) {
  const value = stripThinking(content);
  return value ? `${value}\n\n${THINKING_TEXT}` : THINKING_TEXT;
}

export function terminalRunLine(status?: string, reason?: string) {
  const normalized = normalizeStatus(status);
  if (normalized === "CANCELLED" || normalized === "CANCELED") {
    return "已取消";
  }
  if (normalized === "FAILED" || normalized === "FAILURE") {
    return `运行失败：${reason || "未知原因"}`;
  }
  return "";
}

export function isStatusOnlyAgentMessage(message: Pick<ConversationMessage, "role" | "content">) {
  if (message.role !== "AGENT") {
    return false;
  }
  const content = (message.content || "").trim();
  return (
    /^运行结束[:：]/.test(content) ||
    /^运行已取消/.test(content) ||
    /^任务已取消$/.test(content) ||
    /^运行失败[:：]/.test(content) ||
    /^成功$/.test(content) ||
    content.includes("已执行进度：")
  );
}

export function buildTransientAgentMessage(input: {
  runId: string;
  conversationId: string;
  latestEvent?: AgentRunEvent;
}): ConversationMessage {
  const detail = input.latestEvent ? summarizePayload(input.latestEvent.payload) : RUNNING_TEXT;
  return {
    messageId: `transient-agent-${input.runId}`,
    conversationId: input.conversationId,
    runId: input.runId,
    role: "AGENT",
    content: THINKING_TEXT,
    progress:
      input.latestEvent && input.latestEvent.type !== "run_finished"
        ? `${eventLabel(input.latestEvent.type)}${detail ? `：${detail}` : ""}`
        : input.latestEvent
          ? undefined
          : detail,
    transient: true
  };
}

export function applyRunEventToTransientMessage(input: {
  runId: string;
  conversationId: string;
  event: AgentRunEvent;
  existing?: ConversationMessage;
  savedContent?: string;
  runStatus?: string;
  failureReason?: string;
  resetBody?: boolean;
}): ConversationMessage | null {
  const messageId = `transient-agent-${input.runId}`;
  const base: ConversationMessage =
    input.existing || {
      messageId,
      conversationId: input.conversationId,
      runId: input.runId,
      role: "AGENT",
      content: input.savedContent || "",
      transient: true
    };

  if (input.event.type === "assistant_message_started") {
    return {
      ...base,
      messageId,
      conversationId: input.conversationId,
      runId: input.runId,
      content: appendThinking(base.content || input.savedContent),
      progress: REPLYING_TEXT,
      transient: true
    };
  }

  if (input.event.type === "assistant_delta") {
    const delta = String(input.event.payload?.["delta"] || "");
    if (!delta) {
      return null;
    }
    const baseContent = stripThinking(base.content || input.savedContent || "");
    return {
      ...base,
      messageId,
      conversationId: input.conversationId,
      runId: input.runId,
      content: appendThinking(baseContent + delta),
      progress: REPLYING_TEXT,
      transient: true
    };
  }

  if (input.event.type === "model_stream_failure") {
    const reason = String(input.event.payload?.["reason"] || input.failureReason || "");
    return {
      ...base,
      messageId,
      conversationId: input.conversationId,
      runId: input.runId,
      content: stripThinking(base.content || input.savedContent || ""),
      progress: undefined,
      status: "FAILED",
      failureReason: reason,
      transient: true
    };
  }

  if (input.event.type === "assistant_message_cancelled") {
    return {
      ...base,
      messageId,
      conversationId: input.conversationId,
      runId: input.runId,
      content: stripThinking(base.content || input.savedContent || ""),
      progress: undefined,
      status: "CANCELLED",
      transient: true
    };
  }

  if (input.event.type === "run_finished") {
    const status = String(input.event.payload?.["status"] || input.runStatus || "");
    const reason = String(input.event.payload?.["reason"] || input.failureReason || "");
    const line = terminalRunLine(status, reason);
    if (line) {
      return {
        ...base,
        messageId,
        conversationId: input.conversationId,
        runId: input.runId,
        content: stripThinking(base.content || input.savedContent || ""),
        progress: undefined,
        status,
        failureReason: reason,
        transient: true
      };
    }
    const content = stripThinking(base.content || input.savedContent || "");
    return content
      ? {
          ...base,
          messageId,
          conversationId: input.conversationId,
          runId: input.runId,
          content,
          progress: undefined,
          status,
          failureReason: reason,
          transient: true
        }
      : null;
  }

  return null;
}
