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
  lastPermissionLevel: string;
  createdAt?: string;
  updatedAt?: string;
};

export type DiffFile = {
  path: string;
  changeType: string;
  addedLines: number;
  deletedLines: number;
};

export type DiffSummary = {
  totalFiles: number;
  totalAddedLines: number;
  totalDeletedLines: number;
  files: DiffFile[];
};

export type ConversationMessage = {
  messageId: string;
  conversationId: string;
  runId?: string;
  role: "USER" | "AGENT" | "SYSTEM" | string;
  content: string;
  status?: string;
  failureReason?: string;
  progress?: string;
  transient?: boolean;
  createdAt?: string;
  diffSummary?: DiffSummary;
};

export type PermissionLevel = {
  code: string;
  displayName: string;
  description: string;
  allowedFeatures: string[];
  forbiddenFeatures: string[];
  riskNotice: string;
  icon?: string;
  dangerous?: boolean;
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
  rawContextTokens?: number;
  finalContextTokens?: number;
  contextCompressionRatio?: number;
  memoryHitCount?: number;
  staleMemoryCount?: number;
  selectedFileSummaryCount?: number;
  selectedRawSnippetCount?: number;
  latestContextSnapshotPath?: string;
  durationMs?: number;
  createdAt?: string;
  startedAt?: string;
  endedAt?: string;
  artifacts?: RunArtifact[];
  diffSummary?: DiffSummary;
};

export type AgentRunDraft = {
  runId: string;
  content: string;
  status?: string;
  failureReason?: string;
  updatedAt?: string;
};

export type ContextBudget = {
  maxContextTokens?: number;
  maxOutputTokens?: number;
  inputBudgetTokens?: number;
  memoryBudgetTokens?: number;
  fileSummaryBudgetTokens?: number;
  rawFileBudgetTokens?: number;
  toolResultBudgetTokens?: number;
  recentMessageBudgetTokens?: number;
};

export type ModelProvider = {
  modelKey: string;
  displayName: string;
  provider: string;
  baseUrl: string;
  apiKeyMasked?: string;
  modelName: string;
  endpointType: "chat-completions" | "responses" | string;
  timeoutSeconds?: number;
  temperature?: number;
  streamingEnabled: boolean;
  toolCallingEnabled: boolean;
  status: string;
  defaultModel: boolean;
  budget?: ContextBudget;
};

export type ModelProviderList = {
  models: ModelProvider[];
};

export type ModelProviderPayload = {
  modelKey: string;
  displayName: string;
  provider?: string;
  baseUrl: string;
  apiKey?: string;
  modelName: string;
  endpointType: string;
  timeoutSeconds?: number;
  temperature?: number;
  streamingEnabled?: boolean;
  toolCallingEnabled?: boolean;
  defaultModel?: boolean;
  budget?: ContextBudget;
};

export type ToolApproval = {
  approvalId: string;
  runId: string;
  workspaceKey: string;
  toolName: string;
  argumentsJson: string;
  riskSummary: string;
  diffSummary?: string;
  status: string;
  requestedAt?: string;
  decidedAt?: string;
  decisionReason?: string;
};

export type ToolApprovalList = {
  approvals: ToolApproval[];
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
