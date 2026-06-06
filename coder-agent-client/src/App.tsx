import {
  AlertTriangle,
  Bot,
  CheckCircle2,
  CircleStop,
  Pencil,
  FileDiff,
  Folder,
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
  Trash2,
  X
} from "lucide-react";
import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { createApi, normalizeBaseUrl, selectWorkspaceDirectory, startBackend } from "./api/client";
import type {
  AgentRun,
  Conversation,
  ConversationMessage,
  PermissionLevel,
  Workspace
} from "./api/types";
import { useSseRunEvents } from "./hooks/useSseRunEvents";
import {
  buildTransientAgentMessage,
  isStatusOnlyAgentMessage,
  isTerminalRunStatus
} from "./messageState";
import { workspaceKeyFromPath } from "./workspace";

const MODEL_OPTIONS = ["glm-5", "qwen3.6-plus", "deepseek-v4-flash"];
const STORAGE_KEY = "coder-agent-client-settings";

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
    SUCCEEDED: "成功",
    FAILED: "失败",
    CANCELLED: "已取消"
  };
  return status ? map[status] || status : "未运行";
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
  const [permissionLevel, setPermissionLevel] = useState("L1_READ_ONLY");
  const [model, setModel] = useState("glm-5");
  const [task, setTask] = useState("");
  const [workspaceForm, setWorkspaceForm] = useState({ workspaceKey: "", rootPath: "" });
  const [activeRunId, setActiveRunId] = useState<string>("");
  const [activeRun, setActiveRun] = useState<AgentRun | null>(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const conversationSurfaceRef = useRef<HTMLElement | null>(null);
  const backendStarting = useRef(false);
  const initialDataLoaded = useRef(false);
  const followLatestMessage = useRef(true);
  const completedRunRef = useRef("");
  const terminalSyncRef = useRef("");
  const selectedConversationRef = useRef("");
  const api = useMemo(() => createApi(settings.baseUrl), [settings.baseUrl]);
  const sse = useSseRunEvents(settings.baseUrl, activeRunId);
  const selectedPermission = permissionLevels.find((item) => item.code === permissionLevel);
  const currentWorkspace = workspaces.find((item) => item.workspaceKey === selectedWorkspace);
  const currentConversation = conversations.find((item) => item.conversationId === selectedConversation);
  const activeInSelectedConversation =
    !!activeRunId &&
    activeRun?.conversationId === selectedConversation &&
    (activeRun.status === "CREATED" || activeRun.status === "RUNNING");
  const displayMessages = useMemo(
    () => [...messages, ...transientMessages.filter((message) => message.conversationId === selectedConversation)],
    [messages, selectedConversation, transientMessages]
  );

  useEffect(() => {
    selectedConversationRef.current = selectedConversation;
  }, [selectedConversation]);

  const saveSettings = useCallback((next: SettingsState) => {
    setSettings(next);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  }, []);

  const refreshAll = useCallback(async () => {
    setConnection("checking");
    try {
      const [workspaceData, levels] = await Promise.all([api.listWorkspaces(), api.listPermissionLevels()]);
      setWorkspaces(workspaceData.workspaces || []);
      setPermissionLevels(levels);
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
    const data = await api.listMessages(conversationId);
    const visibleData = data.filter((message) => !isStatusOnlyAgentMessage(message));
    setMessages(visibleData);
    setTransientMessages((current) =>
      current.filter(
        (message) =>
          message.conversationId !== conversationId ||
          !visibleData.some((saved) => saved.runId === message.runId && saved.role === message.role)
      )
    );
    setLoadedConversationId(conversationId);
  }, [api, connection, selectedConversation]);

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
      setTransientMessages((current) =>
        current.filter((message) => !(message.runId === runId && message.conversationId === conversationId))
      );
    },
    [api]
  );

  const refreshRun = useCallback(async () => {
    if (!activeRunId || connection !== "online") {
      return;
    }
    const run = await api.getRun(activeRunId);
    setActiveRun(run);
    if (isTerminalRunStatus(run.status) && run.conversationId) {
      void syncFinalMessages(run.conversationId, activeRunId);
    }
  }, [activeRunId, api, connection, syncFinalMessages]);

  const scrollToLatestIfFollowing = useCallback(() => {
    const surface = conversationSurfaceRef.current;
    if (!surface || !followLatestMessage.current) {
      return;
    }
    surface.scrollTop = surface.scrollHeight;
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
    void syncFinalMessages(activeRun.conversationId, activeRunId);
  }, [activeRun?.conversationId, activeRun?.status, activeRunId, syncFinalMessages]);

  useEffect(() => {
    if (!activeRunId || !activeRun?.conversationId || isTerminalRunStatus(activeRun.status)) {
      return;
    }
    setTransientMessages((current) => {
      const exists = current.some(
        (message) => message.runId === activeRunId && message.conversationId === activeRun.conversationId
      );
      if (exists) {
        return current;
      }
      return [
        ...current,
        buildTransientAgentMessage({
          runId: activeRunId,
          conversationId: activeRun.conversationId || ""
        })
      ];
    });
  }, [activeRun?.conversationId, activeRun?.status, activeRunId, selectedConversation]);

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
    const finalEvent = [...sse.events].reverse().find((event) => event.type === "run_finished");
    const latestEvent = sse.events[sse.events.length - 1];
    const status = finalEvent?.payload?.["status"] ? String(finalEvent.payload["status"]) : activeRun.status;
    if (finalEvent) {
      if (completedRunRef.current !== activeRunId) {
        completedRunRef.current = activeRunId;
        setActiveRun((current) => (current && current.status !== status ? { ...current, status } : current));
        void refreshRun();
        void syncFinalMessages(activeRun.conversationId, activeRunId);
      }
      return;
    }
    setTransientMessages((current) => {
      const message = buildTransientAgentMessage({
        runId: activeRunId,
        conversationId: activeRun.conversationId || "",
        latestEvent
      });
      return [...current.filter((item) => item.messageId !== message.messageId), message];
    });
  }, [activeRun?.conversationId, activeRun?.status, activeRunId, refreshRun, sse.events, syncFinalMessages]);

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
    setBusy(true);
    try {
      const created = await api.createConversation({
        workspaceKey: selectedWorkspace,
        title: currentWorkspace?.workspaceKey ? `${currentWorkspace.workspaceKey} 新对话` : "新对话",
        defaultModel: model,
        defaultPermissionLevel: permissionLevel
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
      setNotice("缂栬緫鍐呭涓嶈兘涓虹┖");
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
          content: "思考中...",
          progress: "正在运行...",
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
    setBusy(true);
    try {
      let conversationId = selectedConversation;
      const submittedTask = task.trim();
      if (!conversationId) {
        const conversation = await api.createConversation({
          workspaceKey: selectedWorkspace,
          title: submittedTask.slice(0, 28),
          defaultModel: model,
          defaultPermissionLevel: permissionLevel
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
          content: "思考中...",
          progress: "正在运行...",
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
    setBusy(true);
    try {
      await api.cancelRun(activeRunId);
      await refreshRun();
      setNotice("已发送取消请求");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "取消失败");
    } finally {
      setBusy(false);
    }
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
                  <small>{conversation.defaultPermissionLevel}</small>
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

        <button className="settings-link">
          <Settings size={17} />
          设置
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
              {displayMessages.map((message) => (
                <article className={`message ${message.role.toLowerCase()}`} key={message.messageId}>
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
                    <p>{message.content}</p>
                  )}
                </article>
              ))}
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
            <select value={permissionLevel} onChange={(event) => setPermissionLevel(event.target.value)}>
              {permissionLevels.map((level) => (
                <option value={level.code} key={level.code}>
                  {level.displayName}
                </option>
              ))}
              {permissionLevels.length === 0 && <option value="L1_READ_ONLY">只读分析</option>}
            </select>
            <select value={model} onChange={(event) => setModel(event.target.value)}>
              {MODEL_OPTIONS.map((item) => (
                <option value={item} key={item}>
                  {item}
                </option>
              ))}
            </select>
            <button
              className={`send-button ${activeInSelectedConversation ? "cancel" : ""}`}
              onClick={activeInSelectedConversation ? cancelRun : submitTask}
              disabled={busy || (!activeInSelectedConversation && !task.trim())}
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
          </div>
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
