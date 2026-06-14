import { describe, expect, it } from "vitest";
import {
  applyRunEventToTransientMessage,
  buildTransientAgentMessage,
  isStatusOnlyAgentMessage,
  isTerminalRunStatus
} from "./messageState";

describe("transient agent message", () => {
  it("should not render final run status as agent message content", () => {
    const message = buildTransientAgentMessage({
      runId: "run_1",
      conversationId: "conv_1",
      latestEvent: {
        eventId: "evt_1",
        runId: "run_1",
        type: "run_finished",
        payload: { status: "SUCCESS", finalAnswer: "done" }
      }
    });

    expect(message.content).toBe("思考中...");
    expect(message.progress).toBeUndefined();
    expect(message.status).toBeUndefined();
  });

  it("should summarize non-final progress without exposing model call success as run status", () => {
    const message = buildTransientAgentMessage({
      runId: "run_1",
      conversationId: "conv_1",
      latestEvent: {
        eventId: "evt_2",
        runId: "run_1",
        type: "model_call_completed",
        payload: { status: "SUCCESS", callNo: 1, model: "glm-5" }
      }
    });

    expect(message.content).toBe("思考中...");
    expect(message.progress).toContain("模型完成");
    expect(message.progress).toContain("callNo=1");
    expect(message.progress).not.toContain("status=SUCCESS");
  });

  it("should detect terminal run statuses from backend and event payload variants", () => {
    expect(isTerminalRunStatus("SUCCEEDED")).toBe(true);
    expect(isTerminalRunStatus("SUCCESS")).toBe(true);
    expect(isTerminalRunStatus("succeed")).toBe(true);
    expect(isTerminalRunStatus("canceled")).toBe(true);
    expect(isTerminalRunStatus("RUNNING")).toBe(false);
  });

  it("should hide status-only agent messages and keep real assistant content", () => {
    expect(isStatusOnlyAgentMessage({ role: "AGENT", content: "运行结束：SUCCEEDED" })).toBe(true);
    expect(isStatusOnlyAgentMessage({ role: "AGENT", content: "运行已取消\n\n已执行进度：模型调用 1 次" })).toBe(true);
    expect(isStatusOnlyAgentMessage({ role: "AGENT", content: "任务已取消" })).toBe(true);
    expect(isStatusOnlyAgentMessage({ role: "AGENT", content: "运行失败：运行超时" })).toBe(true);
    expect(isStatusOnlyAgentMessage({ role: "AGENT", content: "这是模型的真实回答" })).toBe(false);
  });

  it("should append streaming delta before thinking marker without overwriting previous output", () => {
    const first = applyRunEventToTransientMessage({
      runId: "run_1",
      conversationId: "conv_1",
      event: {
        eventId: "evt_1",
        runId: "run_1",
        type: "assistant_delta",
        payload: { delta: "我来检查" }
      }
    });
    const second = applyRunEventToTransientMessage({
      runId: "run_1",
      conversationId: "conv_1",
      existing: first || undefined,
      event: {
        eventId: "evt_2",
        runId: "run_1",
        type: "assistant_delta",
        payload: { delta: "测试文件。" }
      }
    });

    expect(second?.content).toBe("我来检查测试文件。\n\n思考中...");
    expect(second?.progress).toBe("正在回复");
  });

  it("should keep visible streaming body when a new model call starts", () => {
    const existing = applyRunEventToTransientMessage({
      runId: "run_1",
      conversationId: "conv_1",
      event: {
        eventId: "evt_1",
        runId: "run_1",
        type: "assistant_delta",
        payload: { delta: "上一轮模型输出" }
      }
    });

    const next = applyRunEventToTransientMessage({
      runId: "run_1",
      conversationId: "conv_1",
      existing: existing || undefined,
      event: {
        eventId: "evt_2",
        runId: "run_1",
        type: "assistant_message_started",
        payload: { callNo: 2 }
      },
      resetBody: true
    });

    expect(next?.content).toBe("上一轮模型输出\n\n思考中...");
    expect(next?.progress).toBe("正在回复");
  });

  it("should mark terminal failure without appending status into content", () => {
    const result = applyRunEventToTransientMessage({
      runId: "run_1",
      conversationId: "conv_1",
      existing: {
        messageId: "transient-agent-run_1",
        conversationId: "conv_1",
        runId: "run_1",
        role: "AGENT",
        content: "已经完成一部分输出\n\n思考中...",
        transient: true
      },
      event: {
        eventId: "evt_2",
        runId: "run_1",
        type: "run_finished",
        payload: { status: "FAILED", reason: "运行超时" }
      }
    });

    expect(result?.content).toBe("已经完成一部分输出");
    expect(result?.status).toBe("FAILED");
    expect(result?.failureReason).toBe("运行超时");
    expect(result?.progress).toBeUndefined();
  });

  it("should mark terminal cancellation without appending duplicated status into content", () => {
    const first = applyRunEventToTransientMessage({
      runId: "run_1",
      conversationId: "conv_1",
      existing: {
        messageId: "transient-agent-run_1",
        conversationId: "conv_1",
        runId: "run_1",
        role: "AGENT",
        content: "已经输出的内容\n\n思考中...",
        transient: true
      },
      event: {
        eventId: "evt_1",
        runId: "run_1",
        type: "run_finished",
        payload: { status: "CANCELLED", reason: "用户取消" }
      }
    });
    const second = applyRunEventToTransientMessage({
      runId: "run_1",
      conversationId: "conv_1",
      existing: first || undefined,
      event: {
        eventId: "evt_2",
        runId: "run_1",
        type: "run_finished",
        payload: { status: "CANCELLED", reason: "用户取消" }
      }
    });

    expect(second?.content).toBe("已经输出的内容");
    expect(second?.status).toBe("CANCELLED");
  });
});
