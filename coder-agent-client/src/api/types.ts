export type ApiResponse<T> = {
  code: string;
  message: string;
  data: T;
};

export type Workspace = {
  workspaceKey: string;
  rootPath: string;
  status: string;
  createdAt?: string;
  updatedAt?: string;
  deletedAt?: string | null;
};

export type WorkspaceList = {
  workspaces: Workspace[];
};

export type Conversation = {
  conversationId: string;
  workspaceKey: string;
  title: string;
  defaultModel?: string;
  defaultPermissionLevel: string;
  createdAt?: string;
  updatedAt?: string;
};

export type ConversationMessage = {
  messageId: string;
  conversationId: string;
  runId?: string;
  role: "USER" | "AGENT" | "SYSTEM" | string;
  content: string;
  status?: string;
  progress?: string;
  transient?: boolean;
  createdAt?: string;
};

export type PermissionLevel = {
  code: string;
  displayName: string;
  description: string;
  allowedFeatures: string[];
  forbiddenFeatures: string[];
  riskNotice: string;
};

export type RunArtifact = {
  artifactType: string;
  relativePath: string;
  fileSize: number;
};

export type AgentRun = {
  runId: string;
  workspaceKey: string;
  conversationId?: string;
  task: string;
  model: string;
  permissionLevel: string;
  status: string;
  finalAnswer?: string;
  failureReason?: string;
  gitBranch?: string;
  commitHash?: string;
  changed?: boolean;
  changedFileCount?: number;
  testStatus?: string;
  stepCount?: number;
  modelCallCount?: number;
  toolCallCount?: number;
  durationMs?: number;
  createdAt?: string;
  startedAt?: string;
  endedAt?: string;
  artifacts?: RunArtifact[];
};

export type CreateRunPayload = {
  workspaceKey: string;
  task: string;
  model: string;
  conversationId?: string;
  permissionLevel: string;
  sourceMessageId?: string;
};

export type CreateRunResponse = {
  runId: string;
  status: string;
  createdAt?: string;
};

export type TraceQueryResponse = {
  runId: string;
  events: Array<{ event: Record<string, unknown> }>;
};

export type AgentRunEvent = {
  eventId: string;
  runId: string;
  type: string;
  time?: string;
  payload?: Record<string, unknown>;
};

export type BackendProcessStatus = {
  running: boolean;
  pid?: number;
  message?: string;
  log_path?: string;
};
