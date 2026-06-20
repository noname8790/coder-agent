import {
  AlertTriangle,
  BookOpen,
  Bot,
  Check,
  CheckCircle2,
  ChevronDown,
  CircleStop,
  Copy,
  Pencil,
  FileDiff,
  Folder,
  GitBranch,
  History,
  Loader2,
  MessageSquarePlus,
  Plug,
  Plus,
  RefreshCcw,
  Send,
  Server,
  Settings,
  Shield,
  ShieldAlert,
  ShieldCheck,
  ShieldQuestion,
  Trash2,
  X
} from "lucide-react";
import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import type { ReactNode } from "react";
import { createApi, normalizeBaseUrl, selectWorkspaceDirectory, startBackend } from "./api/client";
import type {
  AgentRun,
  Conversation,
  ConversationMessage,
  ModelProvider,
  ModelProviderPayload,
  PermissionLevel,
  ToolApproval,
  Workspace
} from "./api/types";
import { useSseRunEvents } from "./hooks/useSseRunEvents";
import {
  applyRunEventToTransientMessage,
  buildTransientAgentMessage,
  isStatusOnlyAgentMessage,
  isTerminalRunStatus
} from "./messageState";
import { workspaceKeyFromPath } from "./workspace";

const STORAGE_KEY = "coder-agent-client-settings";
const THINKING_TEXT = "思考中...";
const RUNNING_TEXT = "正在运行...";

type SettingsState = {
  baseUrl: string;
  javaPath: string;
  jarPath: string;
  workDir: string;
  port: number;
};

const defaultSettings: SettingsState = {
  baseUrl: "http://127.0.0.1:8080",
  javaPath: "java",
  jarPath: "../coder-agent-app/target/coder-agent.jar",
  workDir: "..",
  port: 8080
};

function loadSettings(): SettingsState {
  try {
    const loaded = { ...defaultSettings, ...JSON.parse(localStorage.getItem(STORAGE_KEY) || "{}") };
    if (loaded.jarPath.endsWith("coder-agent-app.jar")) {
      loaded.jarPath = loaded.jarPath.replace(/coder-agent-app\.jar$/, "coder-agent.jar");
      localStorage.setItem(STORAGE_KEY, JSON.stringify(loaded));
    }
    return loaded;
  } catch {
    return defaultSettings;
  }
}

function statusLabel(status?: string) {
  const map: Record<string, string> = {
    CREATED: "已创建",
    RUNNING: "运行中",
    WAITING_APPROVAL: "等待审批",
    SUCCEEDED: "成功",
    FAILED: "失败",
    CANCELLED: "已取消"
  };
  return status ? map[status] || status : "未运行";
}

function stripThinking(content?: string) {
  const value = content || "";
  return value.endsWith(THINKING_TEXT) ? value.slice(0, -THINKING_TEXT.length).trimEnd() : value;
}

function appendThinking(content?: string) {
  const value = stripThinking(content);
  return value ? `${value}\n\n${THINKING_TEXT}` : THINKING_TEXT;
}

function terminalLine(status: string, reason?: string) {
  const normalized = status.toUpperCase();
  if (normalized === "CANCELLED" || normalized === "CANCELED") {
    return "已取消";
  }
  if (normalized === "FAILED" || normalized === "FAILURE") {
    return `运行失败：${reason || "未知原因"}`;
  }
  return "";
}
function terminalParts(content: string, terminal?: string) {
  const markers = ["\n\n???", "\n\n?????", "\n\n????:"];
  const matchedMarker = markers.find((marker) => content.includes(marker));
  let body = content;
  let terminalText = terminal || "";
  if (matchedMarker) {
    const markerStart = content.lastIndexOf(matchedMarker);
    body = content.slice(0, markerStart);
    terminalText = terminalText || content.slice(markerStart + 2);
  }
  return { body, terminalText };
}

function markdownInline(text: string, keyPrefix: string) {
  const parts: ReactNode[] = [];
  const pattern = /(\*\*[^*]+\*\*|`[^`]+`)/g;
  let lastIndex = 0;
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(text)) !== null) {
    if (match.index > lastIndex) {
      parts.push(<span key={`${keyPrefix}-t-${lastIndex}`}>{text.slice(lastIndex, match.index)}</span>);
    }
    const token = match[0];
    if (token.startsWith("**")) {
      parts.push(<strong key={`${keyPrefix}-b-${match.index}`}>{token.slice(2, -2)}</strong>);
    } else {
      parts.push(<code key={`${keyPrefix}-c-${match.index}`}>{token.slice(1, -1)}</code>);
    }
    lastIndex = match.index + token.length;
  }
  if (lastIndex < text.length) {
    parts.push(<span key={`${keyPrefix}-t-${lastIndex}`}>{text.slice(lastIndex)}</span>);
  }
  return parts.length > 0 ? parts : text;
}

function normalizeMarkdownBody(body: string) {
  return body
    .split(/(```[\s\S]*?```)/g)
    .map((part) => {
      if (part.startsWith("```")) {
        return part;
      }
      const withHeadingBreaks = part
        .replace(/([^\n])(\#{1,6}\s*\S)/g, "$1\n\n$2")
        .replace(/^(\#{1,6})(\S)/gm, "$1 $2");
      const lines = withHeadingBreaks.split(/\r?\n/);
      const normalizedLines: string[] = [];
      for (let index = 0; index < lines.length; index += 1) {
        if (/^\s*#{1,6}\s*$/.test(lines[index])) {
          while (index + 1 < lines.length && !lines[index + 1].trim()) {
            index += 1;
          }
          continue;
        }
        normalizedLines.push(lines[index]);
      }
      return normalizedLines.join("\n");
    })
    .join("");
}

function MarkdownMessage({ content, terminal }: { content: string; terminal?: string }) {
  const { body, terminalText } = terminalParts(content, terminal);
  const normalizedBody = normalizeMarkdownBody(body);
  const lines = normalizedBody.split(/\r?\n/);
  const blocks: ReactNode[] = [];
  let codeBuffer: string[] = [];
  let inCode = false;

  function flushCode(index: number) {
    if (codeBuffer.length > 0) {
      blocks.push(
        <pre className="markdown-code" key={`code-${index}`}>
          <code>{codeBuffer.join("\n")}</code>
        </pre>
      );
      codeBuffer = [];
    }
  }

  lines.forEach((line, index) => {
    if (line.trim().startsWith("```")) {
      if (inCode) {
        flushCode(index);
        inCode = false;
      } else {
        inCode = true;
      }
      return;
    }
    if (inCode) {
      codeBuffer.push(line);
      return;
    }
    if (!line.trim()) {
      blocks.push(<div className="markdown-gap" key={`gap-${index}`} />);
      return;
    }
    const trimmedLine = line.trim();
    if (trimmedLine === THINKING_TEXT || trimmedLine === "\u601d\u8003\u4e2d...") {
      blocks.push(
        <p className="markdown-thinking" key={`thinking-${index}`}>
          {line}
        </p>
      );
      return;
    }
    const heading = /^(#{1,6})\s*(.+)$/.exec(line);
    if (heading) {
      const Tag = (`h${Math.min(heading[1].length + 2, 5)}` as keyof JSX.IntrinsicElements);
      blocks.push(<Tag key={`h-${index}`}>{markdownInline(heading[2], `h-${index}`)}</Tag>);
      return;
    }
    const bullet = /^\s*[-*]\s+(.+)$/.exec(line);
    if (bullet) {
      blocks.push(
        <p className="markdown-list" key={`li-${index}`}>
          <span className="markdown-list-marker" aria-hidden="true">
            -
          </span>
          <span>{markdownInline(bullet[1], `li-${index}`)}</span>
        </p>
      );
      return;
    }
    blocks.push(<p key={`p-${index}`}>{markdownInline(line, `p-${index}`)}</p>);
  });
  flushCode(lines.length + 1);

  return (
    <div className="markdown-message">
      {blocks}
      {terminalText ? (
        <p className="terminal-line">
          <strong>{terminalText}</strong>
        </p>
      ) : null}
    </div>
  );
}

function PermissionIcon({ level }: { level: PermissionLevel }) {
  if (level.code === "READ_ONLY") {
    return <BookOpen size={18} />;
  }
  if (level.code === "FULL_ACCESS") {
    return <ShieldAlert size={18} />;
  }
  return <ShieldCheck size={18} />;
}

function patchLines(snippet?: string) {
  return (snippet || "")
    .split(/\r?\n/)
    .filter((line) => line.length > 0)
    .map((line, index) => {
      const sign = line.charAt(0);
      const match = /^([+\- ])\s*(\d+)(?:\s{2,}(.*))?$/.exec(line);
      return {
        key: `${index}-${line}`,
        kind: sign === "+" ? "add" : sign === "-" ? "delete" : "context",
        sign,
        lineNo: match?.[2] || "",
        text: match ? (match[3] ?? "") : line.slice(1).trimStart()
      };
    });
}

function DiffSummaryCard({
  diff,
  runId,
  onRevert,
  onRestore
}: {
  diff?: ConversationMessage["diffSummary"];
  runId?: string;
  onRevert?: (runId: string) => void;
  onRestore?: (runId: string) => void;
}) {
  const [expanded, setExpanded] = useState(false);
  const [openFiles, setOpenFiles] = useState<Set<string>>(() => new Set());
  if (!diff || !diff.files || diff.files.length === 0) {
    return null;
  }
  const visibleFiles = expanded ? diff.files : diff.files.slice(0, 3);
  const hiddenCount = Math.max(0, diff.files.length - visibleFiles.length);
  const toggleFile = (key: string) => {
    setOpenFiles((current) => {
      const next = new Set(current);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  };
  return (
    <div className="diff-card">
      <div className="diff-card-head">
        <FileDiff size={18} />
        <div>
          <strong>已编辑 {diff.totalFiles} 个文件</strong>
          <span className="diff-stats">
            <span className="diff-added">+{diff.totalAddedLines}</span>
            <span className="diff-deleted">-{diff.totalDeletedLines}</span>
          </span>
        </div>
        {runId && diff.changeSetStatus ? (
          <button
            className="diff-action"
            onClick={() => (diff.changeSetStatus === "REVERTED" ? onRestore?.(runId) : onRevert?.(runId))}
            disabled={!diff.reversible}
            title={diff.reversible ? "" : "存在无法自动撤销的文件"}
          >
            <RefreshCcw size={14} />
            {diff.changeSetStatus === "REVERTED" ? "还原更改" : "撤销更改"}
          </button>
        ) : null}
      </div>
      <div className="diff-files">
        {visibleFiles.map((file, index) => {
          const key = `${file.path}-${file.changeType}-${index}`;
          const isOpen = openFiles.has(key);
          const lines = patchLines((file as { patchSnippet?: string }).patchSnippet);
          return (
            <div className="diff-file" key={key}>
              <div className="diff-file-row">
                <span title={file.path}>{file.path}</span>
                <button className="diff-file-toggle" onClick={() => toggleFile(key)} disabled={lines.length === 0}>
                  <span className="diff-added">+{file.addedLines}</span>
                  <span className="diff-deleted">-{file.deletedLines}</span>
                  <ChevronDown className={isOpen ? "open" : ""} size={15} />
                </button>
              </div>
              {file.reversible === false ? (
                <div className="irreversible-file">该文件无法自动撤销{file.irreversibleReason ? `：${file.irreversibleReason}` : ""}</div>
              ) : null}
              {isOpen && lines.length > 0 ? (
                <div className="diff-patch">
                  {lines.map((line) => (
                    <div className={`diff-patch-line ${line.kind}`} key={line.key}>
                      <span className="diff-sign">{line.sign}</span>
                      <span className="diff-line-no">{line.lineNo}</span>
                      <code>{line.text || "\u00A0"}</code>
                    </div>
                  ))}
                </div>
              ) : null}
            </div>
          );
        })}
      </div>
      {hiddenCount > 0 || expanded ? (
        <button className="diff-expand" onClick={() => setExpanded((value) => !value)}>
          {expanded ? "收起" : `再显示 ${hiddenCount} 个文件`}
          <ChevronDown size={15} />
        </button>
      ) : null}
    </div>
  );
}

export function App() {
  const [settings, setSettings] = useState<SettingsState>(() => loadSettings());
  const [connection, setConnection] = useState<"checking" | "online" | "offline">("checking");
  const [backendMessage, setBackendMessage] = useState("尚未检测");
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [selectedWorkspace, setSelectedWorkspace] = useState<string>("");
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [selectedConversation, setSelectedConversation] = useState<string>("");
  const [messages, setMessages] = useState<ConversationMessage[]>([]);
  const [transientMessages, setTransientMessages] = useState<ConversationMessage[]>([]);
  const [loadedConversationId, setLoadedConversationId] = useState<string>("");
  const [editingMessageId, setEditingMessageId] = useState("");
  const [editingContent, setEditingContent] = useState("");
  const [permissionLevels, setPermissionLevels] = useState<PermissionLevel[]>([]);
  const [permissionLevel, setPermissionLevel] = useState("DEFAULT");
  const [permissionMenuOpen, setPermissionMenuOpen] = useState(false);
  const [modelProviders, setModelProviders] = useState<ModelProvider[]>([]);
  const [model, setModel] = useState("");
  const [view, setView] = useState<"chat" | "model-settings">("chat");
  const [modelForm, setModelForm] = useState<ModelProviderPayload>({
    modelKey: "",
    displayName: "",
    provider: "openai-compatible",
    baseUrl: "",
    apiKey: "",
    modelName: "",
    endpointType: "chat-completions",
    timeoutSeconds: 120,
    temperature: 0.2,
    streamingEnabled: true,
    toolCallingEnabled: true,
    defaultModel: false,
    budget: {}
  });
  const [editingModelKey, setEditingModelKey] = useState("");
  const [pendingApprovals, setPendingApprovals] = useState<ToolApproval[]>([]);
  const [task, setTask] = useState("");
  const [workspaceForm, setWorkspaceForm] = useState({ workspaceKey: "", rootPath: "" });
  const [activeRunId, setActiveRunId] = useState<string>("");
  const [activeRun, setActiveRun] = useState<AgentRun | null>(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [copiedMessageId, setCopiedMessageId] = useState("");
  const [showRolledBackMessages, setShowRolledBackMessages] = useState(false);
  const conversationSurfaceRef = useRef<HTMLElement | null>(null);
  const copyResetTimerRef = useRef<number | null>(null);
  const backendStarting = useRef(false);
  const initialDataLoaded = useRef(false);
  const followLatestMessage = useRef(true);
  const completedRunRef = useRef("");
  const terminalSyncRef = useRef("");
  const selectedConversationRef = useRef("");
  const appliedConversationDefaultsRef = useRef("");
  const processedSseEventIdsRef = useRef<Set<string>>(new Set());
  const streamCallNoByRunRef = useRef<Record<string, string>>({});
  const api = useMemo(() => createApi(settings.baseUrl), [settings.baseUrl]);
  const sse = useSseRunEvents(settings.baseUrl, activeRunId);
  const selectedPermission = permissionLevels.find((item) => item.code === permissionLevel);
  const hasModelCandidates = modelProviders.length > 0;
  const selectedModelLabel = modelProviders.find((item) => item.modelKey === model)?.displayName || model || "请配置模型";
  const modelPickerWidth = Math.max(12, Math.min(34, selectedModelLabel.length + 7));
  const currentWorkspace = workspaces.find((item) => item.workspaceKey === selectedWorkspace);
  const currentConversation = conversations.find((item) => item.conversationId === selectedConversation);
  const activeInSelectedConversation =
    !!activeRunId &&
    activeRun?.conversationId === selectedConversation &&
    (activeRun.status === "CREATED" || activeRun.status === "RUNNING" || activeRun.status === "WAITING_APPROVAL");
  const displayMessages = useMemo(() => {
    const selectedTransientMessages = transientMessages.filter((message) => message.conversationId === selectedConversation);
    const transientKeys = new Set(selectedTransientMessages.map((message) => `${message.runId || ""}:${message.role}`));
    const stableMessages = messages
      .filter((message) => showRolledBackMessages || message.visibilityStatus !== "ROLLED_BACK")
      .filter((message) => !transientKeys.has(`${message.runId || ""}:${message.role}`));
    return [...stableMessages, ...selectedTransientMessages];
  }, [messages, selectedConversation, showRolledBackMessages, transientMessages]);
  const rolledBackSegments = useMemo(() => {
    const segments = new Map<string, ConversationMessage[]>();
    let checkpointMessageId = "__conversation_start__";
    for (const message of messages) {
      if (message.visibilityStatus === "ROLLED_BACK") {
        const current = segments.get(checkpointMessageId) || [];
        current.push(message);
        segments.set(checkpointMessageId, current);
      } else {
        checkpointMessageId = message.messageId;
      }
    }
    return segments;
  }, [messages]);
  const latestVisibleMessageId = useMemo(() => {
    return [...displayMessages]
      .reverse()
      .find((message) => !message.transient && message.visibilityStatus !== "ROLLED_BACK")?.messageId || "";
  }, [displayMessages]);

  useEffect(() => {
    selectedConversationRef.current = selectedConversation;
    if (!selectedConversation) {
      appliedConversationDefaultsRef.current = "";
    }
  }, [selectedConversation]);

  useEffect(() => {
    if (!selectedConversation || appliedConversationDefaultsRef.current === selectedConversation) {
      return;
    }
    const conversation = conversations.find((item) => item.conversationId === selectedConversation);
    if (!conversation) {
      return;
    }
    if (conversation.lastPermissionLevel && permissionLevels.some((item) => item.code === conversation.lastPermissionLevel)) {
      setPermissionLevel(conversation.lastPermissionLevel);
    }
    const conversationModel = conversation.lastModelKey || conversation.defaultModel;
    if (conversationModel && modelProviders.some((item) => item.modelKey === conversationModel)) {
      setModel(conversationModel);
    }
    appliedConversationDefaultsRef.current = selectedConversation;
  }, [conversations, modelProviders, permissionLevels, selectedConversation]);

  useEffect(() => {
    processedSseEventIdsRef.current.clear();
    streamCallNoByRunRef.current = {};
  }, [activeRunId]);

  const saveSettings = useCallback((next: SettingsState) => {
    setSettings(next);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  }, []);

  const refreshAll = useCallback(async () => {
    setConnection("checking");
    try {
      const [workspaceData, levels, providerData] = await Promise.all([
        api.listWorkspaces(),
        api.listPermissionLevels(),
        api.listModelProviders(true)
      ]);
      setWorkspaces(workspaceData.workspaces || []);
      setPermissionLevels(levels);
      const providers = providerData.models || [];
      setModelProviders(providers);
      setModel((current) =>
        providers.some((provider) => provider.modelKey === current)
          ? current
          : providers.find((provider) => provider.defaultModel)?.modelKey || providers[0]?.modelKey || ""
      );
      setConnection("online");
      setBackendMessage("后端连接正常");
      initialDataLoaded.current = true;
      setSelectedWorkspace((current) =>
        workspaceData.workspaces?.some((workspace) => workspace.workspaceKey === current)
          ? current
          : workspaceData.workspaces?.[0]?.workspaceKey || ""
      );
    } catch (error) {
      initialDataLoaded.current = false;
      setConnection("offline");
      setBackendMessage(error instanceof Error ? error.message : "后端不可用");
    }
  }, [api]);

  const refreshConversations = useCallback(async () => {
    if (!selectedWorkspace || connection !== "online") {
      setConversations([]);
      setSelectedConversation("");
      setMessages([]);
      setTransientMessages([]);
      setLoadedConversationId("");
      return;
    }
    const data = await api.listConversations(selectedWorkspace);
    setConversations(data);
    setSelectedConversation((current) =>
      data.some((conversation) => conversation.conversationId === current) ? current : data[0]?.conversationId || ""
    );
  }, [api, connection, selectedWorkspace]);

  const refreshMessages = useCallback(async () => {
    if (!selectedConversation || connection !== "online") {
      setMessages([]);
      setLoadedConversationId("");
      return;
    }
    const conversationId = selectedConversation;
    try {
      const data = await api.listMessages(conversationId);
      const visibleData = data.filter((message) => !isStatusOnlyAgentMessage(message));
      setMessages(visibleData);
      setShowRolledBackMessages(false);
    } catch (error) {
      setMessages([]);
      setNotice(error instanceof Error ? error.message : "加载对话失败");
    } finally {
      setLoadedConversationId(conversationId);
    }
  }, [api, connection, selectedConversation]);

  const restoreRunDraft = useCallback(
    async (run: AgentRun) => {
      if (!run.runId || !run.conversationId || isTerminalRunStatus(run.status)) {
        return;
      }
      const draft = await api.getRunDraft(run.runId);
      if (selectedConversationRef.current !== run.conversationId) {
        return;
      }
      setTransientMessages((current) => {
        const messageId = `transient-agent-${run.runId}`;
        const existing = current.find((item) => item.messageId === messageId);
        const savedAgentMessage = messages.find(
          (message) => message.runId === run.runId && message.conversationId === run.conversationId && message.role === "AGENT"
        );
        const visibleContent = draft.content || savedAgentMessage?.content || existing?.content || "";
        const nextMessage: ConversationMessage = {
          ...(existing ||
            buildTransientAgentMessage({
              runId: run.runId,
              conversationId: run.conversationId || ""
            })),
          messageId,
          conversationId: run.conversationId || "",
          runId: run.runId,
          role: "AGENT",
          content: appendThinking(visibleContent),
          progress: RUNNING_TEXT,
          status: draft.status || run.status,
          failureReason: draft.failureReason || run.failureReason,
          transient: true
        };
        return [...current.filter((item) => item.messageId !== messageId), nextMessage];
      });
    },
    [api, messages]
  );

  const syncFinalMessages = useCallback(
    async (conversationId: string, runId: string) => {
      for (let attempt = 0; attempt < 20; attempt += 1) {
        const data = await api.listMessages(conversationId);
        const visibleData = data.filter((message) => !isStatusOnlyAgentMessage(message));
        const savedAgentMessage = data.some(
          (message) => message.runId === runId && message.role === "AGENT" && !isStatusOnlyAgentMessage(message)
        );
        if (selectedConversationRef.current === conversationId) {
          setMessages(visibleData);
          setLoadedConversationId(conversationId);
        }
        if (savedAgentMessage) {
          setTransientMessages((current) =>
            current.filter((message) => !(message.runId === runId && message.conversationId === conversationId))
          );
          return;
        }
        await new Promise((resolve) => window.setTimeout(resolve, 500));
      }
      // 如果后端最终消息尚未可见，保留当前 transient 内容，避免失败/取消反馈在前端消失。
    },
    [api]
  );

  const refreshRun = useCallback(async () => {
    if (!activeRunId || connection !== "online") {
      return;
    }
    const run = await api.getRun(activeRunId);
    setActiveRun(run);
    if (!isTerminalRunStatus(run.status)) {
      void restoreRunDraft(run);
    }
    if (run.status === "WAITING_APPROVAL") {
      const approvals = await api.listPendingApprovals(activeRunId);
      setPendingApprovals(approvals.approvals || []);
    } else if (pendingApprovals.length > 0) {
      setPendingApprovals([]);
    }
    if (isTerminalRunStatus(run.status) && run.conversationId) {
      void syncFinalMessages(run.conversationId, activeRunId);
    }
  }, [activeRunId, api, connection, pendingApprovals.length, restoreRunDraft, syncFinalMessages]);

  const scrollToLatestIfFollowing = useCallback(() => {
    const surface = conversationSurfaceRef.current;
    if (!surface || !followLatestMessage.current) {
      return;
    }
    surface.scrollTop = surface.scrollHeight;
  }, []);


  const copyMessageContent = useCallback(async (messageId: string, content: string) => {
    try {
      await navigator.clipboard.writeText(stripThinking(content));
      if (copyResetTimerRef.current) {
        window.clearTimeout(copyResetTimerRef.current);
      }
      setCopiedMessageId(messageId);
      copyResetTimerRef.current = window.setTimeout(() => {
        setCopiedMessageId("");
        copyResetTimerRef.current = null;
      }, 1200);
      setNotice("???????");
    } catch {
      setNotice("????????????");
    }
  }, []);

  useEffect(() => {
    return () => {
      if (copyResetTimerRef.current) {
        window.clearTimeout(copyResetTimerRef.current);
      }
    };
  }, []);

  useEffect(() => {
    let disposed = false;

    async function ensureBackendAvailable() {
      try {
        await api.health();
        if (!disposed) {
          setConnection("online");
          setBackendMessage("后端连接正常");
          if (!initialDataLoaded.current) {
            await refreshAll();
          }
        }
      } catch {
        if (backendStarting.current) {
          return;
        }
        backendStarting.current = true;
        try {
          const result = await startBackend({
            javaPath: settings.javaPath,
            jarPath: settings.jarPath,
            workDir: settings.workDir,
            port: settings.port
          });
          if (!disposed && result && !result.running) {
            setNotice(result.message || "后端自动启动失败");
          }
          if (!disposed) {
            await refreshAll();
          }
        } finally {
          backendStarting.current = false;
        }
      }
    }

    void refreshAll().then(() => ensureBackendAvailable());
    const timer = window.setInterval(() => void ensureBackendAvailable(), 5000);
    return () => {
      disposed = true;
      window.clearInterval(timer);
    };
  }, [api, refreshAll, settings.jarPath, settings.javaPath, settings.port, settings.workDir]);

  useEffect(() => {
    void refreshConversations();
  }, [refreshConversations]);

  useEffect(() => {
    void refreshMessages();
  }, [refreshMessages]);

  useEffect(() => {
    void refreshRun();
  }, [refreshRun]);

  useEffect(() => {
    if (!activeRunId || isTerminalRunStatus(activeRun?.status)) {
      return;
    }
    const timer = window.setInterval(() => void refreshRun(), 1500);
    return () => window.clearInterval(timer);
  }, [activeRun?.status, activeRunId, refreshRun]);

  useEffect(() => {
    if (!activeRunId || !activeRun?.conversationId || !isTerminalRunStatus(activeRun.status)) {
      return;
    }
    const syncKey = `${activeRunId}:${activeRun.status}`;
    if (terminalSyncRef.current === syncKey) {
      return;
    }
    terminalSyncRef.current = syncKey;
    setTransientMessages((current) => {
      const messageId = `transient-agent-${activeRunId}`;
      const existing = current.find((item) => item.messageId === messageId);
      const savedAgentMessage = messages.find(
        (message) => message.runId === activeRunId && message.conversationId === activeRun.conversationId && message.role === "AGENT"
      );
      const nextMessage = applyRunEventToTransientMessage({
        runId: activeRunId,
        conversationId: activeRun.conversationId || "",
        event: {
          eventId: `poll-terminal-${activeRunId}-${activeRun.status}`,
          runId: activeRunId,
          type: "run_finished",
          payload: { status: activeRun.status, reason: activeRun.failureReason || "" }
        },
        existing,
        savedContent: savedAgentMessage?.content,
        runStatus: activeRun.status,
        failureReason: activeRun.failureReason
      });
      if (!nextMessage) {
        return current;
      }
      return [...current.filter((item) => item.messageId !== messageId), nextMessage];
    });
    void syncFinalMessages(activeRun.conversationId, activeRunId);
  }, [activeRun?.conversationId, activeRun?.failureReason, activeRun?.status, activeRunId, messages, syncFinalMessages]);

  useEffect(() => {
    if (!activeRunId || !activeRun?.conversationId || isTerminalRunStatus(activeRun.status)) {
      return;
    }
    void restoreRunDraft(activeRun);
    setTransientMessages((current) => {
      const exists = current.some(
        (message) => message.runId === activeRunId && message.conversationId === activeRun.conversationId
      );
      if (exists) {
        return current;
      }
      const savedAgentMessage = messages.find(
        (message) => message.runId === activeRunId && message.conversationId === activeRun.conversationId && message.role === "AGENT"
      );
      return [
        ...current,
        {
          ...buildTransientAgentMessage({
            runId: activeRunId,
            conversationId: activeRun.conversationId || ""
          }),
          content: appendThinking(savedAgentMessage?.content),
          progress: RUNNING_TEXT
        }
      ];
    });
  }, [activeRun?.conversationId, activeRun?.status, activeRunId, messages, selectedConversation]);

  useEffect(() => {
    if (!notice) {
      return;
    }
    const timer = window.setTimeout(() => setNotice(null), 3200);
    return () => window.clearTimeout(timer);
  }, [notice]);

  useEffect(() => {
    if (!activeRunId || !activeRun?.conversationId || sse.events.length === 0) {
      return;
    }
    const latestEvent = sse.events[sse.events.length - 1];
    const eventKey = latestEvent?.eventId || `${latestEvent?.type}:${JSON.stringify(latestEvent?.payload || {})}`;
    if (eventKey && processedSseEventIdsRef.current.has(eventKey)) {
      return;
    }
    if (eventKey) {
      processedSseEventIdsRef.current.add(eventKey);
    }
    const visibleEventTypes = new Set([
      "assistant_message_started",
      "assistant_delta",
      "assistant_message_cancelled",
      "model_stream_failure",
      "run_finished"
    ]);
    if (!latestEvent || !visibleEventTypes.has(latestEvent.type)) {
      return;
    }

    const callNo = latestEvent.payload?.["callNo"] == null ? "" : String(latestEvent.payload["callNo"]);
    const previousCallNo = streamCallNoByRunRef.current[activeRunId];
    const resetBody = latestEvent.type === "assistant_message_started" && !!callNo && !!previousCallNo && previousCallNo !== callNo;
    if (latestEvent.type === "assistant_message_started" && callNo) {
      streamCallNoByRunRef.current[activeRunId] = callNo;
    }

    setTransientMessages((current) => {
      const messageId = `transient-agent-${activeRunId}`;
      const existing = current.find((item) => item.messageId === messageId);
      const savedAgentMessage = messages.find(
        (message) => message.runId === activeRunId && message.conversationId === activeRun.conversationId && message.role === "AGENT"
      );
      const nextMessage = applyRunEventToTransientMessage({
        runId: activeRunId,
        conversationId: activeRun.conversationId || "",
        event: latestEvent,
        existing,
        savedContent: savedAgentMessage?.content,
        runStatus: activeRun.status,
        failureReason: activeRun.failureReason,
        resetBody
      });
      if (!nextMessage) {
        return current;
      }
      return [...current.filter((item) => item.messageId !== messageId), nextMessage];
    });

    if (latestEvent.type === "run_finished") {
      const status = latestEvent.payload?.["status"] ? String(latestEvent.payload["status"]) : activeRun.status;
      if (completedRunRef.current !== activeRunId) {
        completedRunRef.current = activeRunId;
        setActiveRun((current) => (current && current.status !== status ? { ...current, status } : current));
        void refreshRun();
        void syncFinalMessages(activeRun.conversationId, activeRunId);
      }
    }
  }, [activeRun?.conversationId, activeRun?.status, activeRunId, messages, refreshRun, sse.events, syncFinalMessages]);

  useLayoutEffect(() => {
    if (!selectedConversation || loadedConversationId !== selectedConversation) {
      return;
    }
    scrollToLatestIfFollowing();
  }, [displayMessages, loadedConversationId, scrollToLatestIfFollowing, selectedConversation]);

  function handleConversationScroll() {
    const surface = conversationSurfaceRef.current;
    if (!surface) {
      return;
    }
    followLatestMessage.current = surface.scrollHeight - surface.scrollTop - surface.clientHeight < 48;
  }

  async function chooseWorkspaceDirectory() {
    const rootPath = await selectWorkspaceDirectory();
    if (!rootPath) {
      return;
    }
    setWorkspaceForm({
      rootPath,
      workspaceKey: workspaceKeyFromPath(rootPath)
    });
  }

  function selectWorkspace(workspaceKey: string) {
    setSelectedWorkspace(workspaceKey);
    setSelectedConversation("");
    setConversations([]);
    setMessages([]);
    setTransientMessages([]);
    setLoadedConversationId("");
    setEditingMessageId("");
    setEditingContent("");
    followLatestMessage.current = true;
  }

  async function createWorkspace() {
    if (!workspaceForm.workspaceKey || !workspaceForm.rootPath) {
      setNotice("请填写 workspaceKey 和本地路径");
      return;
    }
    setBusy(true);
    try {
      const created = await api.createWorkspace(workspaceForm.workspaceKey, workspaceForm.rootPath);
      setWorkspaceForm({ workspaceKey: "", rootPath: "" });
      selectWorkspace(created.workspaceKey);
      await refreshAll();
      setNotice("工作区已添加");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "添加工作区失败");
    } finally {
      setBusy(false);
    }
  }

  async function createConversation() {
    if (!selectedWorkspace) {
      setNotice("请先选择工作区");
      return;
    }
    if (!model) {
      setView("model-settings");
      setNotice("请先配置并启用模型");
      return;
    }
    setBusy(true);
    try {
      const created = await api.createConversation({
        workspaceKey: selectedWorkspace,
        title: currentWorkspace?.workspaceKey ? `${currentWorkspace.workspaceKey} 新对话` : "新对话",
        defaultModel: model,
        lastPermissionLevel: permissionLevel
      });
      setSelectedConversation(created.conversationId);
      await refreshConversations();
      setNotice("新对话已创建");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "创建对话失败");
    } finally {
      setBusy(false);
    }
  }

  async function deleteWorkspace(workspace: Workspace) {
    if (!window.confirm(`确定删除工作区 "${workspace.workspaceKey}" 吗？`)) {
      return;
    }
    setBusy(true);
    try {
      await api.deleteWorkspace(workspace.workspaceKey);
      if (selectedWorkspace === workspace.workspaceKey) {
        setSelectedWorkspace("");
        setSelectedConversation("");
        setMessages([]);
        setLoadedConversationId("");
      }
      await refreshAll();
      setNotice("工作区已删除");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "删除工作区失败");
    } finally {
      setBusy(false);
    }
  }

  async function deleteConversation(conversation: Conversation) {
    if (!window.confirm(`确定删除对话 "${conversation.title}" 吗？关联运行和消息记录仍会保留。`)) {
      return;
    }
    setBusy(true);
    try {
      await api.deleteConversation(conversation.conversationId);
      if (selectedConversation === conversation.conversationId) {
        setSelectedConversation("");
        setMessages([]);
        setLoadedConversationId("");
      }
      await refreshConversations();
      setNotice("对话已删除");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "删除对话失败");
    } finally {
      setBusy(false);
    }
  }

  async function revertRun(runId: string) {
    setBusy(true);
    try {
      await api.revertRun(runId);
      await refreshMessages();
      if (activeRunId === runId) {
        await refreshRun();
      }
      setNotice("已撤销本次任务改动");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "撤销更改失败");
    } finally {
      setBusy(false);
    }
  }

  async function restoreRun(runId: string) {
    setBusy(true);
    try {
      await api.restoreRun(runId);
      await refreshMessages();
      if (activeRunId === runId) {
        await refreshRun();
      }
      setNotice("已还原本次任务改动");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "还原更改失败");
    } finally {
      setBusy(false);
    }
  }

  async function rollbackCheckpoint(message: ConversationMessage) {
    if (!selectedConversation || !message.messageId) {
      return;
    }
    if (!window.confirm("还原到此检查点会回滚该消息之后的代码改动，并折叠后续历史消息。确定继续吗？")) {
      return;
    }
    setBusy(true);
    try {
      await api.rollbackCheckpoint(selectedConversation, message.messageId);
      await refreshMessages();
      await refreshConversations();
      setNotice("已还原到该检查点");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "还原检查点失败");
    } finally {
      setBusy(false);
    }
  }

  async function deleteMessage(message: ConversationMessage) {
    if (!selectedConversation || message.transient) {
      return;
    }
    const label = message.role === "USER" ? "这条用户消息及同轮 Agent 回复" : "这条 Agent 消息";
    if (!window.confirm(`确定删除${label}吗？`)) {
      return;
    }
    setBusy(true);
    try {
      await api.deleteMessage(selectedConversation, message.messageId);
      setMessages((current) =>
        message.role === "USER" && message.runId
          ? current.filter((item) => item.runId !== message.runId)
          : current.filter((item) => item.messageId !== message.messageId)
      );
      setTransientMessages((current) =>
        message.runId
          ? current.filter((item) => item.runId !== message.runId)
          : current.filter((item) => item.messageId !== message.messageId)
      );
      if (message.runId && message.runId === activeRunId) {
        setActiveRunId("");
        setActiveRun(null);
        completedRunRef.current = "";
        terminalSyncRef.current = "";
        processedSseEventIdsRef.current.clear();
      }
      await refreshMessages();
      setNotice("消息已删除");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "删除消息失败");
    } finally {
      setBusy(false);
    }
  }

  async function saveEditedMessage(message: ConversationMessage) {
    if (!selectedWorkspace || !selectedConversation || !editingContent.trim()) {
      setNotice("编辑内容不能为空");
      return;
    }
    if (!model) {
      setView("model-settings");
      setNotice("请先配置并启用模型");
      return;
    }
    setBusy(true);
    try {
      const updated = await api.updateMessage(selectedConversation, message.messageId, editingContent.trim());
      const run = await api.createRun({
        workspaceKey: selectedWorkspace,
        task: updated.content,
        model,
        conversationId: selectedConversation,
        permissionLevel,
        sourceMessageId: updated.messageId
      });
      setEditingMessageId("");
      setEditingContent("");
      setActiveRunId(run.runId);
      completedRunRef.current = "";
      terminalSyncRef.current = "";
      setActiveRun({
        runId: run.runId,
        workspaceKey: selectedWorkspace,
        conversationId: selectedConversation,
        task: updated.content,
        model,
        permissionLevel,
        status: run.status
      });
      setTransientMessages([
        {
          messageId: `transient-agent-${run.runId}`,
          conversationId: selectedConversation,
          runId: run.runId,
          role: "AGENT",
          content: THINKING_TEXT,
          progress: RUNNING_TEXT,
          transient: true
        }
      ]);
      followLatestMessage.current = true;
      await refreshMessages();
      setNotice("已重新提交任务");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "修改消息失败");
    } finally {
      setBusy(false);
    }
  }

  async function submitTask() {
    if (!selectedWorkspace || !task.trim()) {
      setNotice("请选择工作区并输入任务");
      return;
    }
    if (!model) {
      setView("model-settings");
      setNotice("请先配置并启用模型");
      return;
    }
    setBusy(true);
    try {
      let conversationId = selectedConversation;
      const submittedTask = task.trim();
      if (!conversationId) {
        const conversation = await api.createConversation({
          workspaceKey: selectedWorkspace,
          title: submittedTask.slice(0, 28),
          defaultModel: model,
          lastPermissionLevel: permissionLevel
        });
        conversationId = conversation.conversationId;
        setSelectedConversation(conversationId);
      }
      const run = await api.createRun({
        workspaceKey: selectedWorkspace,
        task: submittedTask,
        model,
        conversationId,
        permissionLevel
      });
      setTask("");
      setLoadedConversationId(conversationId);
      setTransientMessages([
        {
          messageId: `transient-agent-${run.runId}`,
          conversationId,
          runId: run.runId,
          role: "AGENT",
          content: THINKING_TEXT,
          progress: RUNNING_TEXT,
          transient: true
        }
      ]);
      setActiveRunId(run.runId);
      completedRunRef.current = "";
      terminalSyncRef.current = "";
      setActiveRun({
        runId: run.runId,
        workspaceKey: selectedWorkspace,
        conversationId,
        task: submittedTask,
        model,
        permissionLevel,
        status: run.status
      });
      followLatestMessage.current = true;
      await refreshMessages();
      await refreshConversations();
      setNotice("任务已提交");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "任务提交失败");
    } finally {
      setBusy(false);
    }
  }

  async function cancelRun() {
    if (!activeRunId) return;
    const runId = activeRunId;
    setBusy(true);
    try {
      await api.cancelRun(runId);
      setActiveRun((current) => (current && current.runId === runId ? { ...current, status: "CANCELLED" } : current));
      setTransientMessages((current) =>
        current.map((message) => {
          if (message.runId !== runId) {
            return message;
          }
          return {
            ...message,
            content: stripThinking(message.content),
            progress: undefined,
            status: "CANCELLED"
          };
        })
      );
      await refreshRun();
      setNotice("已取消任务");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "取消失败");
    } finally {
      setBusy(false);
    }
  }

  function editModelProvider(provider: ModelProvider) {
    setEditingModelKey(provider.modelKey);
    setModelForm({
      modelKey: provider.modelKey,
      displayName: provider.displayName,
      provider: provider.provider,
      baseUrl: provider.baseUrl,
      apiKey: "",
      modelName: provider.modelName,
      endpointType: provider.endpointType,
      timeoutSeconds: provider.timeoutSeconds || 120,
      temperature: provider.temperature ?? 0.2,
      streamingEnabled: true,
      toolCallingEnabled: provider.toolCallingEnabled,
      defaultModel: provider.defaultModel,
      budget: provider.budget || {}
    });
    setView("model-settings");
  }

  async function saveModelProvider() {
    if (!modelForm.modelKey || !modelForm.baseUrl || !modelForm.modelName) {
      setNotice("请填写模型 key、Base URL 和模型名称");
      return;
    }
    setBusy(true);
    try {
      if (editingModelKey) {
        await api.updateModelProvider(editingModelKey, modelForm);
      } else {
        await api.createModelProvider(modelForm);
      }
      await refreshAll();
      setEditingModelKey("");
      setModelForm({ ...modelForm, modelKey: "", displayName: "", apiKey: "", modelName: "" });
      setNotice("模型配置已保存");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "模型配置保存失败");
    } finally {
      setBusy(false);
    }
  }

  async function removeModelProvider(provider: ModelProvider) {
    if (!window.confirm(`确定删除模型配置 "${provider.modelKey}" 吗？`)) {
      return;
    }
    setBusy(true);
    try {
      await api.deleteModelProvider(provider.modelKey);
      await refreshAll();
      setNotice("模型配置已删除");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "模型配置删除失败");
    } finally {
      setBusy(false);
    }
  }

  async function decideApproval(approval: ToolApproval, approved: boolean) {
    setBusy(true);
    try {
      if (approved) {
        await api.approveToolApproval(approval.approvalId, "客户端批准");
      } else {
        await api.rejectToolApproval(approval.approvalId, "客户端拒绝");
      }
      await refreshRun();
      setNotice(approved ? "已批准工具执行" : "已拒绝工具执行");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "审批处理失败");
    } finally {
      setBusy(false);
    }
  }

function terminalForMessage(message: ConversationMessage) {
    if (message.role !== "AGENT") {
      return "";
    }
    const messageTerminalStatus = isTerminalRunStatus(message.status) ? message.status : "";
    const status =
      messageTerminalStatus || (message.runId === activeRunId && activeRun?.status ? activeRun.status : message.status);
    const reason =
      messageTerminalStatus && message.failureReason
        ? message.failureReason
        : message.runId === activeRunId && activeRun?.failureReason
          ? activeRun.failureReason
          : message.failureReason;
    return terminalLine(status || "", reason);
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <Bot size={22} />
          <div>
            <strong>coder-agent</strong>
            <span>本地代码仓库 Agent</span>
          </div>
        </div>
        <button className="primary-action" onClick={createConversation} disabled={busy || !selectedWorkspace}>
          <MessageSquarePlus size={18} />
          新对话
        </button>

        <section className="side-section">
          <div className="section-title">
            <Folder size={16} />
            项目
          </div>
          <div className="workspace-form">
            <input
              placeholder="workspaceKey"
              value={workspaceForm.workspaceKey}
              onChange={(event) => setWorkspaceForm((value) => ({ ...value, workspaceKey: event.target.value }))}
            />
            <button className="folder-picker" onClick={chooseWorkspaceDirectory} disabled={busy}>
              <Folder size={16} />
              选择代码仓库目录
            </button>
            {workspaceForm.rootPath && (
              <div className="selected-folder" title={workspaceForm.rootPath}>
                {workspaceForm.rootPath}
              </div>
            )}
            <button onClick={createWorkspace} disabled={busy}>
              <Plus size={16} />
              添加工作区
            </button>
          </div>
          <div className="list">
            {workspaces.map((workspace) => (
              <div className="list-row" key={workspace.workspaceKey}>
                <button
                  className={`list-item ${selectedWorkspace === workspace.workspaceKey ? "active" : ""}`}
                  onClick={() => {
                    selectWorkspace(workspace.workspaceKey);
                  }}
                >
                  <span>{workspace.workspaceKey}</span>
                  <small>{workspace.rootPath}</small>
                </button>
                <button
                  className="delete-list-item"
                  title="删除工作区"
                  aria-label={`删除工作区 ${workspace.workspaceKey}`}
                  onClick={() => void deleteWorkspace(workspace)}
                  disabled={busy}
                >
                  <Trash2 size={15} />
                </button>
              </div>
            ))}
            {workspaces.length === 0 && <p className="empty-small">暂无工作区</p>}
          </div>
        </section>

        <section className="side-section conversations">
          <div className="section-title">
            <History size={16} />
            对话
          </div>
          <div className="list">
            {conversations.map((conversation) => (
              <div className="list-row" key={conversation.conversationId}>
                <button
                  className={`list-item ${selectedConversation === conversation.conversationId ? "active" : ""}`}
                  onClick={() => setSelectedConversation(conversation.conversationId)}
                >
                  <span>{conversation.title}</span>
                  <small>{permissionLevels.find((level) => level.code === conversation.lastPermissionLevel)?.displayName || conversation.lastPermissionLevel || "??"}</small>
                </button>
                <button
                  className="delete-list-item"
                  title="删除对话"
                  aria-label={`删除对话 ${conversation.title}`}
                  onClick={() => void deleteConversation(conversation)}
                  disabled={busy}
                >
                  <Trash2 size={15} />
                </button>
              </div>
            ))}
            {conversations.length === 0 && <p className="empty-small">暂无对话</p>}
          </div>
        </section>

        <button className="settings-link" onClick={() => setView(view === "model-settings" ? "chat" : "model-settings")}>
          <Settings size={17} />
          模型配置
        </button>
      </aside>

      <main className="main-panel">
        <header className="topbar">
          <div>
            <p className="eyebrow">当前项目</p>
            <h1>{currentWorkspace?.workspaceKey || "选择一个工作区"}</h1>
            <span>{currentWorkspace?.rootPath || "添加本地项目目录后开始运行 Agent"}</span>
          </div>
          <div className={`connection ${connection}`}>
            <Plug size={16} />
            {connection === "online" ? "已连接" : connection === "checking" ? "检测中" : "离线"}
          </div>
        </header>

        {view === "model-settings" ? (
          <section className="model-settings-view">
            <div className="settings-heading">
              <h2>模型配置</h2>
              <p>保存 OpenAI-compatible 模型配置后，对话输入区会自动刷新可选模型。</p>
            </div>
            <div className="model-form-grid">
              <ConfigField title="Model Key" description="用于 Agent Run 请求的模型标识。">
                <input value={modelForm.modelKey} onChange={(event) => setModelForm({ ...modelForm, modelKey: event.target.value })} />
              </ConfigField>
              <ConfigField title="Display Name" description="客户端展示名称。">
                <input value={modelForm.displayName} onChange={(event) => setModelForm({ ...modelForm, displayName: event.target.value })} />
              </ConfigField>
              <ConfigField title="Base Url" description="OpenAI Compatible Inference API 的基础 URL。">
                <input value={modelForm.baseUrl} onChange={(event) => setModelForm({ ...modelForm, baseUrl: event.target.value })} />
              </ConfigField>
              <ConfigField title="API Key" description="保存时写入后端数据库，查询列表只显示脱敏值。">
                <input type="password" value={modelForm.apiKey || ""} onChange={(event) => setModelForm({ ...modelForm, apiKey: event.target.value })} />
              </ConfigField>
              <ConfigField title="Model Name" description="供应商实际模型名。">
                <input value={modelForm.modelName} onChange={(event) => setModelForm({ ...modelForm, modelName: event.target.value })} />
              </ConfigField>
              <ConfigField title="Endpoint Type" description="Chat Completions 或 Responses 流式协议。">
                <select value={modelForm.endpointType} onChange={(event) => setModelForm({ ...modelForm, endpointType: event.target.value })}>
                  <option value="chat-completions">chat-completions</option>
                  <option value="responses">responses</option>
                </select>
              </ConfigField>
              <ConfigField title="Timeout Seconds" description="单次模型请求超时时间。">
                <input type="number" value={modelForm.timeoutSeconds || 120} onChange={(event) => setModelForm({ ...modelForm, timeoutSeconds: Number(event.target.value) })} />
              </ConfigField>
              <ConfigField title="Context Budget" description="可选模型级输入预算，留空使用全局默认。">
                <input
                  type="number"
                  placeholder="inputBudgetTokens"
                  value={modelForm.budget?.inputBudgetTokens || ""}
                  onChange={(event) =>
                    setModelForm({
                      ...modelForm,
                      budget: { ...(modelForm.budget || {}), inputBudgetTokens: event.target.value ? Number(event.target.value) : undefined }
                    })
                  }
                />
              </ConfigField>
            </div>
            <div className="form-actions">
              <button onClick={saveModelProvider} disabled={busy}>
                <CheckCircle2 size={16} />
                {editingModelKey ? "保存修改" : "新增模型"}
              </button>
              {editingModelKey && (
                <button onClick={() => setEditingModelKey("")}>
                  <X size={16} />
                  取消编辑
                </button>
              )}
            </div>
            <div className="model-list">
              {modelProviders.map((provider) => (
                <div className="model-row" key={provider.modelKey}>
                  <div>
                    <strong>{provider.displayName || provider.modelKey}</strong>
                    <small>{provider.modelKey} · {provider.endpointType} · {provider.apiKeyMasked || "未显示密钥"}</small>
                  </div>
                  <button onClick={() => editModelProvider(provider)}>
                    <Pencil size={14} />
                  </button>
                  <button onClick={() => void removeModelProvider(provider)}>
                    <Trash2 size={14} />
                  </button>
                </div>
              ))}
              {modelProviders.length === 0 && <p className="empty-small">暂无模型配置</p>}
            </div>
          </section>
        ) : (
          <>
        <section className="conversation-surface" ref={conversationSurfaceRef} onScroll={handleConversationScroll}>
          {selectedConversation && loadedConversationId !== selectedConversation ? (
            <div className="conversation-loading">
              <Loader2 className="spin" size={22} />
              <span>正在加载最新对话</span>
            </div>
          ) : displayMessages.length === 0 ? (
            <div className="welcome">
              <Bot size={36} />
              <h2>在 {currentWorkspace?.workspaceKey || "项目"} 中需要做什么？</h2>
              <p>选择模型和权限等级后提交任务。</p>
            </div>
          ) : (
            <div className="messages">
              {displayMessages.map((message) => {
                const rolledBackSegment = rolledBackSegments.get(message.messageId) || [];
                const showCheckpoint =
                  message.role === "AGENT" &&
                  !message.transient &&
                  message.visibilityStatus !== "ROLLED_BACK" &&
                  message.messageId !== latestVisibleMessageId &&
                  !!message.status &&
                  isTerminalRunStatus(message.status);
                return (
                  <div className={`message-block ${message.role.toLowerCase()}`} key={message.messageId}>
                    <div className="message-content-stack">
                    <article className={`message ${message.role.toLowerCase()} ${message.visibilityStatus === "ROLLED_BACK" ? "rolled-back" : ""}`}>
                      {!message.transient && (
                        <div className={`message-actions side ${message.role === "USER" ? "left" : "right"}`}>
                          {message.role === "USER" && (
                            <button
                              title="修改消息并重新运行"
                              onClick={() => {
                                setEditingMessageId(message.messageId);
                                setEditingContent(message.content);
                              }}
                            >
                              <Pencil size={14} />
                            </button>
                          )}
                          <button title="删除消息" onClick={() => void deleteMessage(message)}>
                            <Trash2 size={14} />
                          </button>
                        </div>
                      )}
                      <div className="message-head">
                        <div className="message-role">
                          {message.role === "USER" ? "你" : "Agent"}
                          {message.status ? <span>{statusLabel(message.status)}</span> : null}
                        </div>
                      </div>
                      {message.progress ? <div className="message-progress">{message.progress}</div> : null}
                      {editingMessageId === message.messageId ? (
                        <div className="message-editor">
                          <textarea value={editingContent} onChange={(event) => setEditingContent(event.target.value)} />
                          <div>
                            <button onClick={() => void saveEditedMessage(message)} disabled={busy || !editingContent.trim()}>
                              <Send size={14} />
                              保存并重跑
                            </button>
                            <button
                              onClick={() => {
                                setEditingMessageId("");
                                setEditingContent("");
                              }}
                            >
                              <X size={14} />
                              取消
                            </button>
                          </div>
                        </div>
                      ) : (
                        <>
                          <MarkdownMessage content={message.content} terminal={terminalForMessage(message)} />
                          <div className="message-footer">
                            <button
                              className={copiedMessageId === message.messageId ? "copied" : ""}
                              title={copiedMessageId === message.messageId ? "已复制" : "复制"}
                              onClick={() => void copyMessageContent(message.messageId, message.content)}
                            >
                              {copiedMessageId === message.messageId ? <Check size={14} /> : <Copy size={14} />}
                            </button>
                          </div>
                        </>
                      )}
                    </article>
                    {message.role === "AGENT" ? (
                      <>
                        {message.modelDisplayName ? <div className="agent-model-outside">{message.modelDisplayName}</div> : null}
                        {rolledBackSegment.length > 0 ? (
                          <button className="rolled-back-toggle inline" onClick={() => setShowRolledBackMessages((value) => !value)}>
                            {showRolledBackMessages
                              ? "收起已回滚历史"
                              : `已回滚的 ${rolledBackSegment.length} 条历史消息，仅用于审计`}
                          </button>
                        ) : null}
                        <DiffSummaryCard
                          diff={message.diffSummary}
                          runId={message.runId}
                          onRevert={(runId) => void revertRun(runId)}
                          onRestore={(runId) => void restoreRun(runId)}
                        />
                      </>
                    ) : null}
                    </div>
                    {showCheckpoint ? (
                      <div className="checkpoint-divider page-wide">
                        <span />
                        <button onClick={() => void rollbackCheckpoint(message)} disabled={busy || !message.runId}>
                          还原检查点
                          <GitBranch size={14} />
                        </button>
                        <span />
                      </div>
                    ) : null}
                  </div>
                );
              })}
            </div>
          )}
        </section>

        <section className="composer">
          <textarea
            placeholder="输入任务，例如：请阅读当前仓库并生成一个 PR 草稿"
            value={task}
            onChange={(event) => setTask(event.target.value)}
          />
          <div className="composer-toolbar">
            <div className="permission-picker">
              <button
                type="button"
                className="permission-trigger"
                onClick={() => setPermissionMenuOpen((value) => !value)}
              >
                {selectedPermission ? <PermissionIcon level={selectedPermission} /> : <ShieldQuestion size={18} />}
                <span>{selectedPermission?.displayName || "??"}</span>
                <ChevronDown size={16} />
              </button>
              {permissionMenuOpen && (
                <div className="permission-menu">
                  {(permissionLevels.length > 0
                    ? permissionLevels
                    : [{ code: "DEFAULT", displayName: "??", description: "???????????????????", allowedFeatures: [], forbiddenFeatures: [], riskNotice: "", icon: "shield-check", dangerous: false }]
                  ).map((level) => (
                    <button
                      type="button"
                      className={`permission-option ${level.code === permissionLevel ? "selected" : ""} ${level.code === "FULL_ACCESS" ? "warning" : ""}`}
                      key={level.code}
                      onClick={() => {
                        setPermissionLevel(level.code);
                        setPermissionMenuOpen(false);
                      }}
                    >
                      <PermissionIcon level={level} />
                      <span>
                        <strong>{level.displayName}</strong>
                        <small>{level.description}</small>
                      </span>
                      {level.code === permissionLevel && <Check size={16} />}
                    </button>
                  ))}
                </div>
              )}
            </div>
            <select
              className="model-picker"
              value={model}
              onChange={(event) => setModel(event.target.value)}
              style={{ width: `${modelPickerWidth}ch` }}
            >
              {hasModelCandidates ? (
                modelProviders.map((item) => (
                  <option value={item.modelKey} key={item.modelKey}>
                    {item.displayName || item.modelKey}
                  </option>
                ))
              ) : (
                <option value="">
                  请配置模型
                </option>
              )}
            </select>
            <button
              className={`send-button ${activeInSelectedConversation ? "cancel" : ""}`}
              onClick={activeInSelectedConversation ? cancelRun : submitTask}
              disabled={busy || (!activeInSelectedConversation && (!task.trim() || !hasModelCandidates))}
              title={activeInSelectedConversation ? "取消当前任务" : "发送任务"}
            >
              {busy ? (
                <Loader2 className="spin" size={18} />
              ) : activeInSelectedConversation ? (
                <CircleStop size={18} />
              ) : (
                <Send size={18} />
              )}
            </button>
          </div>
        </section>
        {selectedPermission && (
          <div className="permission-note">
            <Shield size={17} />
            <span>{selectedPermission.description}</span>
            <strong>{selectedPermission.riskNotice}</strong>
          </div>
        )}
          </>
        )}
      </main>

      <aside className="inspector">
        <section className="panel">
          <div className="panel-title">
            <Server size={17} />
            后端服务
          </div>
          <label>
            地址
            <input
              value={settings.baseUrl}
              onChange={(event) => saveSettings({ ...settings, baseUrl: normalizeBaseUrl(event.target.value) })}
            />
          </label>
          <label>
            Java
            <input
              value={settings.javaPath}
              onChange={(event) => saveSettings({ ...settings, javaPath: event.target.value })}
            />
          </label>
          <label>
            Jar
            <input
              value={settings.jarPath}
              onChange={(event) => saveSettings({ ...settings, jarPath: event.target.value })}
            />
          </label>
          <label>
            WorkDir
            <input
              value={settings.workDir}
              onChange={(event) => saveSettings({ ...settings, workDir: event.target.value })}
            />
          </label>
          <p className="hint">{backendMessage}</p>
          <p className="hint">客户端会自动启动、检测并在退出时停止由本客户端启动的后端服务。</p>
        </section>

        <section className="panel run-panel">
          <div className="panel-title">
            <CheckCircle2 size={17} />
            运行状态
          </div>
          <div className="run-status">
            <strong>{activeRunId || "暂无运行"}</strong>
            <span>{statusLabel(activeRun?.status)}</span>
          </div>
          <div className="metrics">
            <span>步骤 {activeRun?.stepCount ?? 0}</span>
            <span>模型 {activeRun?.modelCallCount ?? 0}</span>
            <span>工具 {activeRun?.toolCallCount ?? 0}</span>
            <span>上下文 {activeRun?.finalContextTokens ?? 0}</span>
            <span>压缩 {activeRun?.contextCompressionRatio ?? 0}</span>
            <span>记忆 {activeRun?.memoryHitCount ?? 0}</span>
            <span>过期 {activeRun?.staleMemoryCount ?? 0}</span>
          </div>
          {activeRun?.latestContextSnapshotPath && <p className="hint">{activeRun.latestContextSnapshotPath}</p>}
          <div className="two-buttons">
            <button onClick={refreshRun} disabled={!activeRunId}>
              <RefreshCcw size={15} />
              刷新
            </button>
            <button onClick={cancelRun} disabled={!activeRunId || busy}>
              <CircleStop size={15} />
              取消
            </button>
          </div>
        </section>

        <section className="panel review-panel">
          <div className="panel-title">
            <FileDiff size={17} />
            审查材料
          </div>
          <div className="review-grid">
            <ReviewItem label="变更文件" value={String(activeRun?.changedFileCount ?? 0)} />
            <ReviewItem label="测试状态" value={activeRun?.testStatus || "NOT_RUN"} />
            <ReviewItem label="本地分支" value={activeRun?.gitBranch || "-"} />
            <ReviewItem label="提交" value={activeRun?.commitHash || "-"} />
          </div>
          <div className="artifact-list">
            {(activeRun?.artifacts || []).map((artifact) => (
              <div className="artifact" key={`${artifact.artifactType}-${artifact.relativePath}`}>
                <span>{artifact.artifactType}</span>
                <small>{artifact.relativePath}</small>
              </div>
            ))}
            {(!activeRun?.artifacts || activeRun.artifacts.length === 0) && <p className="empty-small">暂无工件</p>}
          </div>
          {activeRun?.finalAnswer && <p className="final-answer">{activeRun.finalAnswer}</p>}
        </section>
      </aside>

      {pendingApprovals.map((approval) => (
        <div className="modal-backdrop" key={approval.approvalId}>
          <div className="approval-modal">
            <div className="panel-title">
              <AlertTriangle size={18} />
              高风险工具审批
            </div>
            <h3>{approval.toolName}</h3>
            <p>{approval.riskSummary}</p>
            {approval.diffSummary && <pre>{approval.diffSummary}</pre>}
            <pre>{approval.argumentsJson}</pre>
            <div className="two-buttons">
              <button onClick={() => void decideApproval(approval, true)} disabled={busy}>
                <CheckCircle2 size={15} />
                批准
              </button>
              <button onClick={() => void decideApproval(approval, false)} disabled={busy}>
                <CircleStop size={15} />
                拒绝
              </button>
            </div>
          </div>
        </div>
      ))}

      {notice && (
        <div className="toast" onClick={() => setNotice(null)}>
          <AlertTriangle size={16} />
          {notice}
        </div>
      )}
    </div>
  );
}

function ReviewItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="review-item">
      <span>{label}</span>
      <strong title={value}>{value}</strong>
    </div>
  );
}

function ConfigField({
  title,
  description,
  children
}: {
  title: string;
  description: string;
  children: ReactNode;
}) {
  return (
    <label className="config-field">
      <strong>{title}</strong>
      <span>{description}</span>
      {children}
    </label>
  );
}
