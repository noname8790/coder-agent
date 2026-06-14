import type {
  AgentRun,
  AgentRunDraft,
  ApiResponse,
  BackendProcessStatus,
  Conversation,
  ConversationMessage,
  CreateRunPayload,
  CreateRunResponse,
  ModelProvider,
  ModelProviderList,
  ModelProviderPayload,
  PermissionLevel,
  ToolApprovalList,
  TraceQueryResponse,
  Workspace,
  WorkspaceList
} from "./types";

const DEFAULT_BASE_URL = "http://127.0.0.1:8080";

export function normalizeBaseUrl(value: string | null | undefined): string {
  const trimmed = (value || "").trim().replace(/\/+$/, "");
  return trimmed || DEFAULT_BASE_URL;
}

async function request<T>(baseUrl: string, path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${normalizeBaseUrl(baseUrl)}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers || {})
    }
  });
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
  }
  const body = (await response.json()) as ApiResponse<T>;
  if (body.code !== "0000" && body.code !== "OK" && body.code !== "SUCCESS") {
    throw new Error(body.message || body.code || "请求失败");
  }
  return body.data;
}

export function createApi(baseUrl: string) {
  return {
    health: () => request<WorkspaceList>(baseUrl, "/api/workspaces"),
    listWorkspaces: () => request<WorkspaceList>(baseUrl, "/api/workspaces"),
    createWorkspace: (workspaceKey: string, rootPath: string) =>
      request<Workspace>(baseUrl, "/api/workspaces", {
        method: "POST",
        body: JSON.stringify({ workspaceKey, rootPath })
      }),
    deleteWorkspace: (workspaceKey: string) =>
      request<Workspace>(baseUrl, `/api/workspaces/${encodeURIComponent(workspaceKey)}`, { method: "DELETE" }),
    listConversations: (workspaceKey?: string) => {
      const query = workspaceKey ? `?workspaceKey=${encodeURIComponent(workspaceKey)}` : "";
      return request<Conversation[]>(baseUrl, `/api/conversations${query}`);
    },
    createConversation: (payload: {
      workspaceKey: string;
      title: string;
      defaultModel?: string;
      defaultPermissionLevel?: string;
    }) =>
      request<Conversation>(baseUrl, "/api/conversations", {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    deleteConversation: (conversationId: string) =>
      request<Conversation>(baseUrl, `/api/conversations/${encodeURIComponent(conversationId)}`, { method: "DELETE" }),
    listMessages: (conversationId: string) =>
      request<ConversationMessage[]>(baseUrl, `/api/conversations/${encodeURIComponent(conversationId)}/messages`),
    updateMessage: (conversationId: string, messageId: string, content: string) =>
      request<ConversationMessage>(
        baseUrl,
        `/api/conversations/${encodeURIComponent(conversationId)}/messages/${encodeURIComponent(messageId)}`,
        {
          method: "PUT",
          body: JSON.stringify({ content })
        }
      ),
    deleteMessage: (conversationId: string, messageId: string) =>
      request<ConversationMessage>(
        baseUrl,
        `/api/conversations/${encodeURIComponent(conversationId)}/messages/${encodeURIComponent(messageId)}`,
        { method: "DELETE" }
      ),
    listPermissionLevels: () => request<PermissionLevel[]>(baseUrl, "/api/permission-levels"),
    listModelProviders: (enabledOnly = true) =>
      request<ModelProviderList>(baseUrl, `/api/model-providers?enabledOnly=${enabledOnly}`),
    createModelProvider: (payload: ModelProviderPayload) =>
      request<ModelProvider>(baseUrl, "/api/model-providers", {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    updateModelProvider: (modelKey: string, payload: ModelProviderPayload) =>
      request<ModelProvider>(baseUrl, `/api/model-providers/${encodeURIComponent(modelKey)}`, {
        method: "PUT",
        body: JSON.stringify(payload)
      }),
    deleteModelProvider: (modelKey: string) =>
      request<ModelProvider>(baseUrl, `/api/model-providers/${encodeURIComponent(modelKey)}`, { method: "DELETE" }),
    enableModelProvider: (modelKey: string) =>
      request<ModelProvider>(baseUrl, `/api/model-providers/${encodeURIComponent(modelKey)}/enable`, { method: "POST" }),
    setDefaultModelProvider: (modelKey: string) =>
      request<ModelProvider>(baseUrl, `/api/model-providers/${encodeURIComponent(modelKey)}/default`, { method: "POST" }),
    createRun: (payload: CreateRunPayload) =>
      request<CreateRunResponse>(baseUrl, "/api/agent-runs", {
        method: "POST",
        body: JSON.stringify(payload)
      }),
    getRun: (runId: string) => request<AgentRun>(baseUrl, `/api/agent-runs/${encodeURIComponent(runId)}`),
    getRunDraft: (runId: string) =>
      request<AgentRunDraft>(baseUrl, `/api/agent-runs/${encodeURIComponent(runId)}/draft`),
    getTrace: (runId: string) =>
      request<TraceQueryResponse>(baseUrl, `/api/agent-runs/${encodeURIComponent(runId)}/trace`),
    cancelRun: (runId: string) =>
      request<{ runId: string; status: string }>(baseUrl, `/api/agent-runs/${encodeURIComponent(runId)}/cancel`, {
        method: "POST"
      }),
    listPendingApprovals: (runId: string) =>
      request<ToolApprovalList>(baseUrl, `/api/tool-approvals/pending/${encodeURIComponent(runId)}`),
    approveToolApproval: (approvalId: string, reason?: string) =>
      request(baseUrl, `/api/tool-approvals/${encodeURIComponent(approvalId)}/approve`, {
        method: "POST",
        body: JSON.stringify({ reason })
      }),
    rejectToolApproval: (approvalId: string, reason?: string) =>
      request(baseUrl, `/api/tool-approvals/${encodeURIComponent(approvalId)}/reject`, {
        method: "POST",
        body: JSON.stringify({ reason })
      })
  };
}

export async function invokeBackendCommand<T>(
  command: string,
  args?: Record<string, unknown>
): Promise<T | null> {
  try {
    const mod = await import("@tauri-apps/api/core");
    return await mod.invoke<T>(command, args);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    if (
      message.includes("__TAURI_INTERNALS__") ||
      message.includes("is not a function") ||
      message.includes("not available")
    ) {
      return null;
    }
    throw error;
  }
}

export async function isTauriRuntime(): Promise<boolean> {
  try {
    const mod = await import("@tauri-apps/api/core");
    await mod.invoke("__coder_agent_missing_probe__");
    return true;
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    if (
      message.includes("__TAURI_INTERNALS__") ||
      message.includes("is not a function") ||
      message.includes("not available")
    ) {
      return false;
    }
    return true;
  }
}

export async function getTauriRuntimeNotice(): Promise<string> {
  const available = await isTauriRuntime();
  if (available) {
    return "Tauri runtime available";
  }
  return "Browser preview runtime";
}

export async function startBackend(args: {
  javaPath: string;
  jarPath: string;
  workDir: string;
  port: number;
}): Promise<BackendProcessStatus | null> {
  try {
    return await invokeBackendCommand<BackendProcessStatus>("start_backend", args);
  } catch (error) {
    return {
      running: false,
      message: error instanceof Error ? error.message : String(error)
    };
  }
}

export async function stopBackend(): Promise<BackendProcessStatus | null> {
  try {
    return await invokeBackendCommand<BackendProcessStatus>("stop_backend");
  } catch (error) {
    return {
      running: false,
      message: error instanceof Error ? error.message : String(error)
    };
  }
}

export async function selectWorkspaceDirectory(): Promise<string | null> {
  return await invokeBackendCommand<string | null>("select_workspace_directory");
}
