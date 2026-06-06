import { describe, expect, it, vi } from "vitest";
import { createApi, normalizeBaseUrl } from "./client";

describe("normalizeBaseUrl", () => {
  it("should trim trailing slash given configured backend url", () => {
    expect(normalizeBaseUrl(" http://127.0.0.1:8080/ ")).toBe("http://127.0.0.1:8080");
  });

  it("should use default backend url given empty value", () => {
    expect(normalizeBaseUrl("")).toBe("http://127.0.0.1:8080");
  });
});

describe("conversation message api", () => {
  it("should request update message endpoint given edited content", async () => {
    const calls: Array<{ url: string; init?: RequestInit }> = [];
    vi.stubGlobal("fetch", async (url: string, init?: RequestInit) => {
      calls.push({ url, init });
      return Response.json({ code: "0000", message: "ok", data: { messageId: "msg_1", content: "new task" } });
    });

    await createApi("http://127.0.0.1:8080").updateMessage("conv_1", "msg_1", "new task");

    expect(calls[0].url).toBe("http://127.0.0.1:8080/api/conversations/conv_1/messages/msg_1");
    expect(calls[0].init?.method).toBe("PUT");
    expect(calls[0].init?.body).toBe(JSON.stringify({ content: "new task" }));
    vi.unstubAllGlobals();
  });

  it("should send sourceMessageId when rerunning edited user message", async () => {
    const calls: Array<{ url: string; init?: RequestInit }> = [];
    vi.stubGlobal("fetch", async (url: string, init?: RequestInit) => {
      calls.push({ url, init });
      return Response.json({ code: "0000", message: "ok", data: { runId: "run_1", status: "CREATED" } });
    });

    await createApi("http://127.0.0.1:8080").createRun({
      workspaceKey: "demo",
      task: "new task",
      model: "glm-5",
      conversationId: "conv_1",
      permissionLevel: "L1_READ_ONLY",
      sourceMessageId: "msg_1"
    });

    expect(JSON.parse(String(calls[0].init?.body)).sourceMessageId).toBe("msg_1");
    vi.unstubAllGlobals();
  });
});
