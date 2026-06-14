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

  it("should request run draft endpoint for running assistant output", async () => {
    const calls: Array<{ url: string; init?: RequestInit }> = [];
    vi.stubGlobal("fetch", async (url: string, init?: RequestInit) => {
      calls.push({ url, init });
      return Response.json({ code: "0000", message: "ok", data: { runId: "run_1", content: "partial" } });
    });

    const result = await createApi("http://127.0.0.1:8080").getRunDraft("run_1");

    expect(calls[0].url).toBe("http://127.0.0.1:8080/api/agent-runs/run_1/draft");
    expect(result.content).toBe("partial");
    vi.unstubAllGlobals();
  });
});

describe("v4 model provider and approval api", () => {
  it("should load enabled model providers from backend", async () => {
    const calls: Array<{ url: string; init?: RequestInit }> = [];
    vi.stubGlobal("fetch", async (url: string, init?: RequestInit) => {
      calls.push({ url, init });
      return Response.json({
        code: "0000",
        message: "ok",
        data: { models: [{ modelKey: "glm-5", displayName: "GLM", status: "ENABLED" }] }
      });
    });

    const result = await createApi("http://127.0.0.1:8080").listModelProviders(true);

    expect(calls[0].url).toBe("http://127.0.0.1:8080/api/model-providers?enabledOnly=true");
    expect(result.models[0].modelKey).toBe("glm-5");
    vi.unstubAllGlobals();
  });

  it("should create model provider with user supplied endpoint and api key", async () => {
    const calls: Array<{ url: string; init?: RequestInit }> = [];
    vi.stubGlobal("fetch", async (url: string, init?: RequestInit) => {
      calls.push({ url, init });
      return Response.json({ code: "0000", message: "ok", data: { modelKey: "qwen", status: "ENABLED" } });
    });

    await createApi("http://127.0.0.1:8080").createModelProvider({
      modelKey: "qwen",
      displayName: "Qwen",
      baseUrl: "https://dashscope.aliyuncs.com/compatible-mode/v1",
      apiKey: "sk-test",
      modelName: "qwen-plus",
      endpointType: "chat-completions",
      streamingEnabled: true,
      toolCallingEnabled: true
    });

    const body = JSON.parse(String(calls[0].init?.body));
    expect(calls[0].url).toBe("http://127.0.0.1:8080/api/model-providers");
    expect(calls[0].init?.method).toBe("POST");
    expect(body.apiKey).toBe("sk-test");
    expect(body.streamingEnabled).toBe(true);
    vi.unstubAllGlobals();
  });

  it("should approve and reject high risk tool approval requests", async () => {
    const calls: Array<{ url: string; init?: RequestInit }> = [];
    vi.stubGlobal("fetch", async (url: string, init?: RequestInit) => {
      calls.push({ url, init });
      return Response.json({ code: "0000", message: "ok", data: { approvalId: "apv_1" } });
    });

    const api = createApi("http://127.0.0.1:8080");
    await api.approveToolApproval("apv_1", "允许本次操作");
    await api.rejectToolApproval("apv_2", "不允许删除");

    expect(calls[0].url).toBe("http://127.0.0.1:8080/api/tool-approvals/apv_1/approve");
    expect(calls[0].init?.method).toBe("POST");
    expect(calls[0].init?.body).toBe(JSON.stringify({ reason: "允许本次操作" }));
    expect(calls[1].url).toBe("http://127.0.0.1:8080/api/tool-approvals/apv_2/reject");
    expect(calls[1].init?.body).toBe(JSON.stringify({ reason: "不允许删除" }));
    vi.unstubAllGlobals();
  });
});
