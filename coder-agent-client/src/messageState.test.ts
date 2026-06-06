import { describe, expect, it } from "vitest";
import { buildTransientAgentMessage, isStatusOnlyAgentMessage, isTerminalRunStatus } from "./messageState";

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

  it("should hide status-only agent messages but keep cancellation feedback", () => {
    expect(isStatusOnlyAgentMessage({ role: "AGENT", content: "运行结束：SUCCEEDED" })).toBe(true);
    expect(isStatusOnlyAgentMessage({ role: "AGENT", content: "运行已取消\n\n已执行进度：模型调用 1 次" })).toBe(true);
    expect(isStatusOnlyAgentMessage({ role: "AGENT", content: "任务已取消" })).toBe(false);
    expect(isStatusOnlyAgentMessage({ role: "AGENT", content: "这是模型的真实回答" })).toBe(false);
  });
});
