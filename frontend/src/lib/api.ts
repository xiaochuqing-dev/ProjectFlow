import type { AuthResult } from "./auth";

const CONFIGURED_API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL?.replace(/\/$/, "");
const DEFAULT_API_PORT = process.env.NEXT_PUBLIC_API_PORT ?? "8080";

type ApiResponse<T> = {
  data: T;
  message: string;
};

type ApiErrorResponse = {
  error?: {
    code?: string;
    message?: string;
  };
};

const NETWORK_ERROR_MESSAGE = "暂时连接不到本地服务。请确认启动脚本窗口还在运行，或等待几秒后再试。";
const RESPONSE_ERROR_MESSAGE = "服务返回内容暂时无法识别，请刷新页面后重试。";
const ZIP_UPLOAD_LIMIT_BYTES = 512 * 1024 * 1024;
const ZIP_UPLOAD_TOO_LARGE_MESSAGE = "项目 zip 超过本地导入上限。请删除 node_modules、构建产物、日志和大型二进制资源后重新压缩。";
const NETWORK_RETRY_COUNT = 2;
const NETWORK_RETRY_DELAY_MS = 350;

function apiBaseUrl() {
  if (CONFIGURED_API_BASE_URL) {
    return CONFIGURED_API_BASE_URL;
  }
  if (typeof window !== "undefined") {
    return `${window.location.protocol}//${window.location.hostname}:${DEFAULT_API_PORT}/api`;
  }
  return `http://127.0.0.1:${DEFAULT_API_PORT}/api`;
}

function isNetworkError(error: unknown) {
  return error instanceof TypeError && error.message.toLowerCase().includes("fetch");
}

async function readApiPayload<T>(response: Response): Promise<ApiResponse<T> & ApiErrorResponse> {
  try {
    return (await response.json()) as ApiResponse<T> & ApiErrorResponse;
  } catch {
    if (response.status === 413) {
      throw new Error(ZIP_UPLOAD_TOO_LARGE_MESSAGE);
    }
    throw new Error(RESPONSE_ERROR_MESSAGE);
  }
}

function isRetryableResponse(response: Response) {
  // 5xx is transient (backend GC, H2 lock, spring-boot:run warmup). 4xx is a real error, do not retry.
  return response.status >= 500 && response.status < 600;
}

async function fetchWithFriendlyError(url: string, options: RequestInit): Promise<Response> {
  for (let attempt = 0; ; attempt++) {
    try {
      const response = await fetch(url, options);
      if (isRetryableResponse(response) && attempt < NETWORK_RETRY_COUNT) {
        await new Promise((resolve) => window.setTimeout(resolve, NETWORK_RETRY_DELAY_MS));
        continue;
      }
      return response;
    } catch (error) {
      // Network-level failure (backend not reachable / connection reset). Retry before surfacing.
      if (isNetworkError(error) && attempt < NETWORK_RETRY_COUNT) {
        await new Promise((resolve) => window.setTimeout(resolve, NETWORK_RETRY_DELAY_MS));
        continue;
      }
      if (isNetworkError(error)) {
        throw new Error(NETWORK_ERROR_MESSAGE);
      }
      throw error instanceof Error ? error : new Error("请求失败，请稍后重试");
    }
  }
}

async function postJson<T>(path: string, body: unknown): Promise<T> {
  const response = await fetchWithFriendlyError(`${apiBaseUrl()}${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });

  const payload = await readApiPayload<T>(response);
  if (!response.ok) {
    throw new Error(payload.error?.message ?? "请求失败，请稍后重试");
  }
  return payload.data;
}

async function requestJson<T>(path: string, options: RequestInit): Promise<T> {
  const response = await fetchWithFriendlyError(`${apiBaseUrl()}${path}`, options);
  const payload = await readApiPayload<T>(response);
  if (!response.ok) {
    throw new Error(payload.error?.message ?? "请求失败，请稍后重试");
  }
  return payload.data;
}

export function login(email: string, password: string): Promise<AuthResult> {
  return postJson<AuthResult>("/auth/login", { email, password });
}

export function register(username: string, email: string, password: string): Promise<AuthResult> {
  return postJson<AuthResult>("/auth/register", { username, email, password });
}

export function resetPassword(email: string, newPassword: string, recoveryCode: string): Promise<void> {
  return postJson<void>("/auth/reset-password", { email, newPassword, recoveryCode });
}

export type ProjectStatus = "PLANNING" | "BUILDING" | "PAUSED" | "COMPLETED" | "ARCHIVED";

export type Project = {
  id: string;
  name: string;
  description: string;
  status: ProjectStatus;
  techStack: string[];
  repoUrl: string;
  startDate: string;
  endDate: string | null;
  createdAt: string;
  updatedAt: string;
};

export type ProjectPayload = {
  name: string;
  description: string;
  status: ProjectStatus;
  techStack: string[];
  repoUrl: string;
  startDate: string;
  endDate: string | null;
};

export function listProjects(token: string): Promise<Project[]> {
  return requestJson<Project[]>("/projects", {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export function getProject(token: string, projectId: string): Promise<Project> {
  return requestJson<Project>(`/projects/${projectId}`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export function createProject(token: string, payload: ProjectPayload): Promise<Project> {
  return requestJson<Project>("/projects", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });
}

export function deleteProject(token: string, projectId: string): Promise<void> {
  return requestJson<void>(`/projects/${projectId}`, {
    method: "DELETE",
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export type TaskStatus = "BACKLOG" | "TODO" | "IN_PROGRESS" | "REVIEW" | "DONE";

export type TaskPriority = "LOW" | "MEDIUM" | "HIGH";

export type TaskItem = {
  id: string;
  projectId: string;
  title: string;
  description: string;
  status: TaskStatus;
  priority: TaskPriority;
  dueDate: string | null;
  tags: string[];
  createdAt: string;
  updatedAt: string;
};

export type TaskPayload = {
  title: string;
  description: string;
  status: TaskStatus;
  priority: TaskPriority;
  dueDate: string | null;
  tags: string[];
};

export function listTasks(token: string, projectId: string): Promise<TaskItem[]> {
  return requestJson<TaskItem[]>(`/projects/${projectId}/tasks`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

/** @deprecated Legacy task write API. Current review flow writes project changes first. */
export function createTask(token: string, projectId: string, payload: TaskPayload): Promise<TaskItem> {
  return requestJson<TaskItem>(`/projects/${projectId}/tasks`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });
}

/** @deprecated Legacy task status API. Prefer change review and confirmed project archive flow. */
export function updateTaskStatus(token: string, taskId: string, status: TaskStatus): Promise<TaskItem> {
  return requestJson<TaskItem>(`/tasks/${taskId}/status`, {
    method: "PATCH",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ status }),
  });
}

export type DevLogCategory = "FEATURE" | "BUGFIX" | "REFACTOR" | "RESEARCH" | "REVIEW" | "DEPLOYMENT";

export type DevLog = {
  id: string;
  projectId: string;
  taskId: string | null;
  title: string;
  content: string;
  category: DevLogCategory;
  logDate: string;
  minutesSpent: number;
  blocked: boolean;
  tags: string[];
  createdAt: string;
  updatedAt: string;
};

export type DevLogPayload = {
  taskId: string | null;
  title: string;
  content: string;
  category: DevLogCategory;
  logDate: string;
  minutesSpent: number;
  blocked: boolean;
  tags: string[];
};

export function listDevLogs(token: string, projectId: string): Promise<DevLog[]> {
  return requestJson<DevLog[]>(`/projects/${projectId}/dev-logs`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export function createDevLog(token: string, projectId: string, payload: DevLogPayload): Promise<DevLog> {
  return requestJson<DevLog>(`/projects/${projectId}/dev-logs`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });
}

export type MarkdownPreview = {
  frontMatter: Record<string, string>;
  title: string;
  content: string;
  category: DevLogCategory;
  logDate: string;
  minutesSpent: number;
  blocked: boolean;
  tags: string[];
  warnings: string[];
};

export type ImportRecord = {
  id: string;
  projectId: string;
  devLogId: string;
  title: string;
  source: string;
  warnings: string[];
  createdAt: string;
};

export function previewMarkdownImport(token: string, projectId: string, markdown: string): Promise<MarkdownPreview> {
  return requestJson<MarkdownPreview>("/imports/preview", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ projectId, markdown }),
  });
}

export function confirmMarkdownImport(token: string, projectId: string, taskId: string | null, markdown: string): Promise<DevLog> {
  return requestJson<DevLog>("/imports/confirm", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ projectId, taskId, markdown }),
  });
}

export function listImportRecords(token: string, projectId: string): Promise<ImportRecord[]> {
  return requestJson<ImportRecord[]>(`/projects/${projectId}/imports`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export type AiOutputType = "WEEKLY_REPORT" | "PROJECT_SUMMARY" | "RESUME_BULLET" | "README_SECTION";

export type AiOutput = {
  id: string;
  projectId: string;
  type: AiOutputType;
  title: string;
  content: string;
  fromDate: string | null;
  toDate: string | null;
  provider: string;
  createdAt: string;
  updatedAt: string;
};

export type ModelUsageRecord = {
  id: string;
  projectId: string;
  operation: string;
  providerName: string;
  modelName: string;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  usageEstimated: boolean;
  latencyMs: number;
  status: string;
  errorType: string | null;
  errorMessage: string | null;
  qualityWarnings: string | null;
  createdAt: string;
};

export function listAiOutputs(token: string, projectId: string): Promise<AiOutput[]> {
  return requestJson<AiOutput[]>(`/projects/${projectId}/ai-outputs`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export function generateAiOutput(
  token: string,
  projectId: string,
  type: AiOutputType,
  fromDate: string | null,
  toDate: string | null,
): Promise<AiOutput> {
  return requestJson<AiOutput>(`/projects/${projectId}/ai-outputs`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ type, fromDate, toDate }),
  });
}

export function listProjectModelUsageRecords(token: string, projectId: string): Promise<ModelUsageRecord[]> {
  return requestJson<ModelUsageRecord[]>(`/projects/${projectId}/model-usage-records`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export type AiProviderType = "MOCK" | "DEEPSEEK" | "OPENAI_COMPATIBLE" | "CUSTOM";

export type AiProvider = {
  id: string | null;
  name: string;
  baseUrl: string;
  modelName: string;
  type: AiProviderType;
  temperature: number;
  maxTokens: number;
  defaultEnabled: boolean;
  purposeTags: string[];
  apiKeyConfigured: boolean;
  createdAt: string;
  updatedAt: string;
};

export type AiProviderPayload = {
  name: string;
  baseUrl: string;
  apiKey: string;
  modelName: string;
  type: AiProviderType;
  temperature: number;
  maxTokens: number;
  defaultEnabled: boolean;
  purposeTags: string[];
  clearApiKey?: boolean;
};

export type ProviderTestResult = {
  ok: boolean;
  provider: string;
  message: string;
};

export type DuplicateProviderGroup = {
  groupKey: string;
  recommendedKeeper: AiProvider;
  duplicates: AiProvider[];
};

export function listAiProviders(token: string): Promise<AiProvider[]> {
  return requestJson<AiProvider[]>("/ai-providers", {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export function createAiProvider(token: string, payload: AiProviderPayload): Promise<AiProvider> {
  return requestJson<AiProvider>("/ai-providers", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });
}

export function updateAiProvider(token: string, providerId: string, payload: AiProviderPayload): Promise<AiProvider> {
  return requestJson<AiProvider>(`/ai-providers/${providerId}`, {
    method: "PATCH",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
}

export function deleteAiProvider(token: string, providerId: string): Promise<void> {
  return requestJson<void>(`/ai-providers/${providerId}`, {
    method: "DELETE",
    headers: { Authorization: `Bearer ${token}` },
  });
}

export function listDuplicateAiProviders(token: string): Promise<DuplicateProviderGroup[]> {
  return requestJson<DuplicateProviderGroup[]>("/ai-providers/duplicates", {
    headers: { Authorization: `Bearer ${token}` },
  });
}

export function cleanupDuplicateAiProviders(token: string, providerIds: string[]): Promise<{ deletedCount: number; remainingProviders: AiProvider[] }> {
  return requestJson<{ deletedCount: number; remainingProviders: AiProvider[] }>("/ai-providers/duplicates/cleanup", {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ providerIds }),
  });
}

export function testAiProvider(token: string, providerId: string): Promise<ProviderTestResult> {
  return requestJson<ProviderTestResult>(`/ai-providers/${providerId}/test`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export type MaterialSourceType =
  | "NATURAL_NOTE"
  | "AGENT_SUMMARY"
  | "AGENT_CONVERSATION"
  | "CODEX_OUTPUT"
  | "CLAUDE_CODE_OUTPUT"
  | "CURSOR_OUTPUT"
  | "COMMIT_LOG"
  | "README_MARKDOWN"
  | "TEXT_FILE"
  | "DOCX_FILE"
  | "JSON_LOG"
  | "PROJECT_ZIP"
  | "OTHER";

export type AiSuggestionType =
  | "UPDATE_PROJECT_MEMORY"
  | "CREATE_TASK"
  | "CREATE_DEV_LOG"
  | "RECORD_TECHNICAL_DECISION"
  | "RECORD_RISK"
  | "RECORD_DEVELOPER_LEARNING"
  | "UPDATE_CURRENT_STAGE"
  | "GENERATE_ASSET_SUMMARY";

export type AiSuggestionStatus = "PENDING" | "APPLIED" | "IGNORED";

export type ProjectMaterial = {
  id: string;
  projectId: string;
  sourceType: MaterialSourceType;
  fileName: string | null;
  content: string;
  normalizedSummary: string;
  createdAt: string;
  updatedAt: string;
};

export type ProjectProfile = {
  inferredProjectName: string;
  summary: string;
  techStack: string[];
  moduleStructure: string[];
  currentStage: string;
  hasReadme: boolean;
  hasTests: boolean;
  hasStartScript: boolean;
  hasDeployConfig: boolean;
  looksEmptyShell: boolean;
  mostImportantGap: string;
};

export type AiSuggestion = {
  id: string;
  projectId: string;
  materialId: string | null;
  type: AiSuggestionType;
  status: AiSuggestionStatus;
  title: string;
  reason: string;
  payload: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
  resolvedAt: string | null;
};

export type AnalyzeMaterialResult = {
  materialId: string;
  summary: string;
  suggestions: AiSuggestion[];
};

export type ProjectImportAnalyzeResult = {
  project: Project;
  material: ProjectMaterial;
  projectProfile: ProjectProfile;
  suggestions: AiSuggestion[];
  modelEnhancementAvailable: boolean;
  providerConfigured: boolean;
};

export type ProjectMemory = {
  id: string;
  projectId: string;
  positioning: string;
  currentStage: string;
  completedCapabilities: string;
  inProgressCapabilities: string;
  currentRisks: string;
  technicalDecisions: string;
  developerLearnings: string;
  showcaseAssets: string;
  nextStepSuggestions: string;
  localProjectPath: string | null;
  version: number;
  createdAt: string | null;
  updatedAt: string | null;
};

export type ProjectMemoryPayload = {
  positioning: string;
  currentStage: string;
  completedCapabilities: string;
  inProgressCapabilities: string;
  currentRisks: string;
  technicalDecisions: string;
  developerLearnings: string;
  showcaseAssets: string;
  nextStepSuggestions: string;
};

export type CapabilityCandidate = {
  summary: string;
  problem: string;
  value: string;
  readme: string;
  resume: string;
  interview: string;
};

export type ModelCallDiagnostics = {
  providerName: string;
  modelName: string;
  finishReason: string;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  usageSource: "ACTUAL" | "ESTIMATED" | "UNAVAILABLE";
  providerMaxTokens: number;
  taskPolicyMaxTokens: number;
  effectiveMaxTokens: number;
  providerTemperature: number;
  effectiveTemperature: number;
  timeoutSeconds: number;
  latencyMs: number;
  contentPresent: boolean;
  reasoningPresent: boolean;
  reasoningLength: number;
  outputTruncated: boolean;
  compactRetryAttempted: boolean;
  compactRetrySucceeded: boolean;
  requestCount: number;
  jsonRepaired: boolean;
  partialResult: boolean;
  recoveredItems: number;
  entryPoint: string;
  taskType: string;
  capabilityProfile: string;
  inputSize: number;
  promptSize: number;
  recommendedTemperature: number;
  temperatureSent: boolean;
  temperatureDecision: string;
  maxTokenDecision: string;
  retryType: string;
  reasoningBudgetExhausted: boolean;
  schemaMatched: boolean;
  failureStage: string;
  failureCode: string;
};

export type CapabilityInterpretResponse = {
  degraded: boolean;
  source: "MODEL" | "LOCAL_RULE";
  message: string;
  candidate: CapabilityCandidate;
  diagnostics: ModelCallDiagnostics | null;
};

export type ProjectSnapshot = {
  id: string;
  projectId: string;
  currentStage: string;
  taskStatusSummary: string;
  techStackSummary: string;
  moduleCompletion: string;
  riskSummary: string;
  recentAchievements: string;
  nextStepSuggestions: string;
  memoryVersion: number;
  createdAt: string;
};

export type ProjectEvolutionRecord = {
  id: string;
  projectId: string;
  materialId: string | null;
  summary: string;
  detectedChanges: string;
  keyAchievements: string;
  keyIssues: string;
  technicalDecisions: string;
  developerLearnings: string;
  nextSteps: string;
  createdAt: string;
};

export type ProjectChangeKind =
  | "CAPABILITY"
  | "BUGFIX"
  | "REFACTOR"
  | "CONFIG"
  | "DOCS"
  | "TEST"
  | "RISK"
  | "DECISION"
  | "LEARNING"
  | "ASSET"
  | "UNKNOWN";

export type ProjectChangeImpactLevel = "MAJOR" | "MINOR" | "MAINTENANCE" | "UNCERTAIN";

export type ProjectChangeSourceType =
  | "AGENT_RESULT"
  | "EVIDENCE_BUNDLE"
  | "PROJECT_ZIP"
  | "MATERIAL_UPDATE"
  | "USER_MANUAL"
  | "MODEL_SUMMARY"
  | "DEVELOPMENT_SEGMENT";

export type SedimentAction = "NEW_SEDIMENT" | "MERGE_EXISTING" | "EVIDENCE_ONLY" | "IGNORE";

export type ProjectChangeStatus = "PENDING" | "EDITED" | "ACCEPTED" | "IGNORED" | "MERGED";

export type ProjectChange = {
  id: string;
  projectId: string;
  materialId: string | null;
  linkedSuggestionId: string | null;
  sourceType: ProjectChangeSourceType;
  sourceRef: string;
  changeKind: ProjectChangeKind;
  impactLevel: ProjectChangeImpactLevel;
  status: ProjectChangeStatus;
  title: string;
  summary: string;
  details: string;
  affectedFiles: string;
  relatedTasks: string;
  testEvidence: string;
  buildEvidence: string;
  riskNotes: string;
  decisionNotes: string;
  learningNotes: string;
  assetCandidates: string;
  createdAt: string;
  updatedAt: string;
  reviewedAt: string | null;
  developmentSegmentId: string | null;
  suggestedAction: SedimentAction | null;
  targetSedimentId: string | null;
  problemSolved: string;
  suggestionReason: string;
  evidenceRefs: string[];
  confidence: "HIGH" | "MEDIUM" | "LOW" | null;
  needsUserReview: boolean;
  sourceBatchId: string | null;
  contentSource: "MODEL_RESULT" | "MODEL_PARTIAL_RESULT" | "LOCAL_FACT_DRAFT" | "AGENT_RESULT_DRAFT" | "MANUAL_DRAFT" | "LEGACY_UNKNOWN";
  qualityStatus: string;
  recommendationStrength: "HIGH" | "MEDIUM" | "REFERENCE_ONLY" | "NOT_RECOMMENDED";
  legacyTruncated: boolean;
};

export type ProjectSediment = {
  id: string;
  projectId: string;
  title: string;
  summary: string;
  problemSolved: string;
  sedimentType: string;
  status: string;
  sourceSegmentIds: string[];
  evidenceRefs: string[];
  affectedFiles: string[];
  sourceBatchIds: string[];
  contentSource: string;
  qualityStatus: string;
  capabilityStatus: "PENDING_ANALYSIS" | "CAPABILITY_FORMED" | "ANALYZED_NO_CAPABILITY" | string;
  lastCapabilityAnalysisJobId: string | null;
  lastCapabilityAnalyzedAt: string | null;
  developerNotes: string;
  legacyTruncated: boolean;
  createdAt: string;
  updatedAt: string;
};

export type SedimentConfirmation = {
  changeId: string;
  changeStatus: ProjectChangeStatus;
  sediment: ProjectSediment | null;
  batchStatus: string;
  actionLabel: string;
  resultMessage: string;
  evidenceAdded: number;
  filesAdded: number;
  summaryUpdated: boolean;
  affectsConfirmedCapabilities: boolean;
  usedByNextCapabilityAnalysis: boolean;
  sedimentPath: string;
};

export type SedimentReviewBatch = {
  batchId: string;
  scanStartedAt: string;
  scanFinishedAt: string | null;
  branchName: string;
  batchStatus: string;
  commitCount: number;
  changedFileCount: number;
  agentResultCount: number;
  modelStatus: string;
  modelProvider: string;
  resultSource: string;
  formalSuggestionCount: number;
  localDraftCount: number;
  processedCount: number;
  pendingCount: number;
  ignoredCount: number;
  needsReanalysis: boolean;
  timeGroup: "TODAY" | "YESTERDAY" | "THIS_WEEK" | "EARLIER";
};

export type SedimentReviewItem = {
  changeId: string;
  segmentId: string;
  title: string;
  summary: string;
  status: ProjectChangeStatus;
  contentSource: string;
  qualityStatus: string;
  recommendationStrength: string;
  suggestedAction: SedimentAction | "";
  targetSedimentId: string | null;
  evidenceCount: number;
  affectedFileCount: number;
  createdAt: string;
};

export type SedimentReviewBatchDetail = {
  batch: SedimentReviewBatch;
  formalSuggestions: SedimentReviewItem[];
  localDrafts: DevelopmentSegment[];
};

export type CapabilityAnalysisOverview = {
  lastSuccessfulAt: string | null;
  lastInputSedimentCount: number;
  newSedimentCount: number;
  updatedSedimentCount: number;
  pendingSedimentCount: number;
};

export type SedimentImpactPreview = {
  changeId: string;
  action: SedimentAction;
  actionLabel: string;
  recommendationReason: string;
  targetSedimentId: string | null;
  targetTitle: string;
  targetSummary: string;
  targetUpdatedAt: string | null;
  evidenceToAdd: number;
  filesToAdd: number;
  summaryWillUpdate: boolean;
  affectsConfirmedCapabilities: boolean;
  usedByNextCapabilityAnalysis: boolean;
  updatedFields: string[];
  consequence: string;
};

export type ProjectChangePayload = {
  changeKind: ProjectChangeKind;
  impactLevel: ProjectChangeImpactLevel;
  title: string;
  summary: string;
  details: string;
  affectedFiles: string;
  relatedTasks: string;
  testEvidence: string;
  buildEvidence: string;
  riskNotes: string;
  decisionNotes: string;
  learningNotes: string;
  assetCandidates: string;
};

export type ProjectFactSourceType = "USER_MANUAL" | "ACCEPTED_CHANGE" | "AGENT_RESULT" | "ZIP_ANALYSIS" | "MODEL_SUMMARY";

export type ProjectFactSource = {
  id: string;
  projectId: string;
  fieldKey: keyof ProjectMemoryPayload | string;
  value: string;
  sourceType: ProjectFactSourceType;
  sourceId: string | null;
  confidence: string;
  confirmedByUser: boolean;
  createdAt: string;
  updatedAt: string;
};

export type ProjectAnalysis = {
  summary: string;
  architecture: string;
  modules: string[];
  risks: string[];
  importantFiles: string[];
  evidence: string[];
  limitations: string[];
  providerConfigured: boolean;
  modelUsed: boolean;
  providerName: string | null;
  analysisSource: "LOCAL_RULE" | "MODEL_ANALYSIS" | string;
  confidence: string;
  message: string;
  diagnostics: ModelCallDiagnostics | null;
};

export type ProjectFileAnalysis = {
  path: string;
  fileType: string;
  role: string;
  summary: string;
  importance: string;
  riskLevel: string;
  riskNotes: string;
  evidence: string[];
  relatedFiles: string[];
  limitations: string;
  providerConfigured: boolean;
  modelUsed: boolean;
  providerName: string | null;
  analysisSource: "LOCAL_RULE" | "MODEL_ANALYSIS" | string;
  confidence: string;
  message: string;
  diagnostics: ModelCallDiagnostics | null;
};

export type ProjectAnalysisJobStatus =
  | "QUEUED"
  | "RUNNING"
  | "CANCEL_REQUESTED"
  | "CANCELLED"
  | "SUCCEEDED"
  | "SUCCEEDED_WITH_WARNINGS"
  | "FAILED"
  | "INTERRUPTED"
  | "RETRYABLE"
  | "EXPIRED"
  | "REJECTED";
export type ProjectAnalysisJobType = "PROJECT" | "FILE" | "CAPABILITY_INTERPRET" | "WORK_SESSION_SCAN" | "CAPABILITY_CARD_ANALYSIS";

export type ProjectAnalysisJob = {
  id: string;
  projectId: string;
  jobType: ProjectAnalysisJobType;
  filePath: string | null;
  status: ProjectAnalysisJobStatus;
  projectResult: ProjectAnalysis | null;
  fileResult: ProjectFileAnalysis | null;
  capabilityInterpretResult: CapabilityInterpretResponse | null;
  workSessionScanResult: WorkSessionScanResult | null;
  errorMessage: string | null;
  warningMessage: string | null;
  failureStage: string | null;
  capabilityCardResult: {
    cardCount: number;
    needsEvidenceCount: number;
    rawResponsePresent: boolean;
    repaired: boolean;
    recognizedItems: number;
    discardedItems: number;
    invalidSourceIndexes: number;
    providerName: string;
    modelName: string;
    finishReason: string;
    promptTokens: number;
    completionTokens: number;
    totalTokens: number;
    providerMaxTokens: number;
    taskPolicyMaxTokens: number;
    effectiveMaxTokens: number;
    providerTemperature: number;
    effectiveTemperature: number;
    timeoutSeconds: number;
    requestLatencyMs: number;
    outputTruncated: boolean;
    compactRetryAttempted: boolean;
    compactRetrySucceeded: boolean;
    partialResult: boolean;
    recoveredItems: number;
    capabilityProfile: string;
    recommendedTemperature: number;
    temperatureSent: boolean;
    temperatureDecision: string;
    maxTokenDecision: string;
    retryType: string;
    reasoningBudgetExhausted: boolean;
    schemaMatched: boolean;
    failureCode: string;
    requestCount: number;
  } | null;
  recordId: string | null;
  createdAt: string;
  updatedAt: string;
  startedAt: string | null;
  completedAt: string | null;
  // V3.3.3: 分析进度可视化。stage 是当前阶段，stageMessage 是中文说明，currentStepStartedAt 用于计算已等待时间。
  stage: string;
  stageMessage: string;
  currentStepStartedAt: string | null;
  inputSummary: string | null;
  diagnosticsJson: string | null;
  modelReturned: boolean;
  failureAcknowledged: boolean;
  queuedAt: string | null;
  heartbeatAt: string | null;
  cancellationRequestedAt: string | null;
  cancelledAt: string | null;
  attemptCount: number;
  maxAttempts: number;
  requestCount: number;
  maxRequestCount: number;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  maxTotalTokens: number;
  elapsedMs: number;
  maxDurationMs: number;
  idempotencyKey: string | null;
  inputFingerprint: string | null;
  failureCode: string | null;
  restartRecoveryState: string | null;
  queuePosition: number;
  retriedFromJobId: string | null;
  retryReason: string | null;
};

export type ProjectAnalysisRecordType = "PROJECT" | "FILE";

export type ProjectAnalysisRecord = {
  id: string;
  projectId: string;
  recordType: ProjectAnalysisRecordType;
  filePath: string | null;
  summary: string;
  details: string;
  analysisSource: "LOCAL_RULE" | "MODEL_ANALYSIS" | string;
  modelUsed: boolean;
  providerName: string | null;
  confidence: string;
  createdAt: string;
};

export type ApplySuggestionsResult = {
  appliedCount: number;
  memory: ProjectMemory;
  snapshot: ProjectSnapshot;
  evolutionRecord: ProjectEvolutionRecord;
};

export type AgentBridgeWriteResult = {
  projectFlowDir: string;
  writtenFiles: string[];
  globalRule: string;
  alreadyLinked: boolean;
};

export type AgentBridgeHealth = {
  pathAccessible: boolean;
  sameGitRepository: boolean;
  protocolExists: boolean;
  resultsDirectoryExists: boolean;
  agentsFileExists: boolean;
  entryRulePresent: boolean;
  protocolVersion: string;
  detectedRuleFiles: string[];
  warnings: string[];
};

export type GitHubStatus = {
  ghInstalled: boolean;
  ghAuthenticated: boolean;
  repoDetected: boolean;
  nameWithOwner: string;
  url: string;
  defaultBranch: string;
  currentBranch: string;
  visibility: string;
  primaryLanguage: string;
  remoteUrl: string;
  commitUrlTemplate: string;
  status: "CONNECTED" | "NOT_INSTALLED" | "NOT_AUTHENTICATED" | "NO_REMOTE" | "CONNECTION_TIMEOUT" | "PERMISSION_DENIED" | "FETCH_FAILED" | "CALL_FAILED" | "JSON_PARSE_FAILED";
  remoteRelation: "synced" | "local_ahead" | "remote_ahead" | "diverged" | "no_upstream" | "github_unavailable";
  localAhead: number;
  remoteAhead: number;
  warnings: string[];
};

export type AgentResultScanResult = {
  importedResults: number;
  materials: ProjectMaterial[];
  suggestions: AiSuggestion[];
  warnings: string[];
};

export type AgentTaskBriefResult = {
  taskId: string;
  taskDir: string;
  briefPath: string;
  resultPath: string;
  statusPath: string;
  writtenFiles: string[];
};

export type WorkSessionCandidate = {
  sessionId: string;
  projectId: string;
  agentType: string;
  agentName: string;
  taskIntent: string;
  branchName: string;
  baseCommit: string;
  startTime: string;
  endTime: string;
  attributionConfidence: string;
  detectionMethod: string;
  changedFiles: number;
  addedLines: number;
  deletedLines: number;
  affectedModules: string[];
  evidence: string[];
  files: string[];
};

export type WorkSessionScanResult = {
  projectId: string;
  projectPath: string;
  branchName: string;
  scannedAt: string;
  sessions: WorkSessionCandidate[];
  warnings: string[];
  batch: ChangeBatch | null;
  segments: DevelopmentSegment[];
  firstScan: boolean;
};

export type ChangeBatch = {
  id: string;
  projectId: string;
  scanStartedAt: string;
  scanFinishedAt: string;
  baseCommitSha: string;
  headCommitSha: string;
  branchName: string;
  newCommitCount: number;
  changedFileCount: number;
  agentResultCount: number;
  segmentCount: number;
  status: "PENDING" | "PARTIAL" | "REVIEWED" | "FAILED";
  warnings: string[];
  firstScan: boolean;
  scanFingerprint: string;
  worktreeDirty: boolean;
  githubStatus: string;
  remoteRelation: string;
  segmentationMode: "MODEL" | "LOCAL_RULE";
  modelStatus: string;
  modelProvider: string;
  fallbackReason: string;
  gitScanMs: number;
  modelSegmentMs: number;
  githubInspectMs: number;
  totalScanMs: number;
  // V3.3.3: 分析口径 JSON——记录本次用了哪些来源。
  analysisScope: string | null;
};

export type DevelopmentSegment = {
  id: string;
  projectId: string;
  batchId: string;
  title: string;
  plainSummary: string;
  mainChanges: string[];
  userVisibleValue: string;
  includedCommitRefs: string[];
  includedAgentResultRefs: string[];
  affectedFiles: string[];
  evidenceRefs: string[];
  confidence: "HIGH" | "MEDIUM" | "LOW";
  status: "PENDING" | "CONFIRMED" | "IGNORED" | "NEEDS_REVIEW";
  createdAt: string;
  updatedAt: string;
  generationMode: "MODEL" | "LOCAL_RULE";
  modelProvider: string;
  fallbackReason: string;
  // V3.3.3: 质量门槛改为标记器，状态细化。
  qualityStatus: "PASS" | "NEEDS_REVIEW" | "NEEDS_CHINESE_REWRITE" | "NEEDS_EVIDENCE" | "PARTIAL_EVIDENCE" | "LOW_CONFIDENCE" | "NEEDS_MANUAL";
  qualityReason: string;
  commitUrls: string[];
  uncertainties: string[];
};

export type CapabilityCard = {
  id: string;
  projectId: string;
  name: string;
  summary: string;
  problemSolved: string;
  featureEntry: string;
  sourceRefs: string[];
  evidenceRefs: string[];
  readmeExpression: string;
  resumeExpression: string;
  interviewExpression: string;
  status: "CANDIDATE" | "CONFIRMED" | "NEEDS_EVIDENCE" | "IGNORED";
  generationMode: "MODEL" | "LOCAL_RULE";
  modelProvider: string;
  fallbackReason: string;
  analysisJobId: string | null;
  legacyResult: boolean;
  legacyTruncated: boolean;
  createdAt: string;
  updatedAt: string;
};

export type EvidenceSource = {
  sourceType: string;
  sourceRef: string;
  summary: string;
};

export type EvidenceBundle = {
  id: string;
  projectId: string;
  workSessionId: string;
  agentType: string;
  taskIntent: string;
  branchName: string;
  attributionConfidence: string;
  changedFiles: number;
  addedLines: number;
  deletedLines: number;
  files: string[];
  objectiveEvidence: string[];
  agentClaims: string[];
  sources: EvidenceSource[];
  status: "READY_FOR_CHANGE" | "CHANGE_DRAFTED" | "CHANGE_ACCEPTED" | "ARCHIVED" | string;
  nextAction: "GENERATE_CHANGE" | "REVIEW_CHANGE" | "VIEW_MEMORY" | "NO_ACTION" | string;
  changeId: string | null;
  createdAt: string;
  updatedAt: string;
};

export type AgentSignatureFeedback = {
  id: string;
  projectId: string;
  agentName: string;
  originalAgentType: string;
  correctedAgentType: string;
  correctedTaskIntent: string;
  scope: string;
  createdAt: string;
  updatedAt: string;
};

export type ChangeConflict = {
  id: string;
  projectId: string;
  conflictType: string;
  filePath: string;
  moduleName: string;
  severity: string;
  status: string;
  summary: string;
  evidenceBundleIds: string[];
};

export type ContextSyncResult = {
  projectId: string;
  contextPath: string;
  writtenFiles: string[];
  syncedAt: string;
};

export function listProjectMaterials(token: string, projectId: string): Promise<ProjectMaterial[]> {
  return requestJson<ProjectMaterial[]>(`/projects/${projectId}/materials`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

/** @deprecated Legacy material API. Current project onboarding should use importProjectZip. */
export function createTextMaterial(
  token: string,
  projectId: string,
  sourceType: MaterialSourceType,
  content: string,
): Promise<ProjectMaterial> {
  return requestJson<ProjectMaterial>(`/projects/${projectId}/materials/text`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ sourceType, content }),
  });
}

async function postFormData<T>(token: string, path: string, formData: FormData): Promise<T> {
  const response = await fetchWithFriendlyError(`${apiBaseUrl()}${path}`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
    },
    body: formData,
  });
  const payload = await readApiPayload<T>(response);
  if (!response.ok) {
    throw new Error(payload.error?.message ?? "请求失败，请稍后重试");
  }
  return payload.data;
}

/** @deprecated Legacy material API. Current project onboarding should use importProjectZip. */
export function uploadProjectMaterialFile(
  token: string,
  projectId: string,
  file: File,
  sourceType: MaterialSourceType,
): Promise<ProjectMaterial> {
  const formData = new FormData();
  formData.append("file", file);
  return postFormData<ProjectMaterial>(token, `/projects/${projectId}/materials/file?sourceType=${sourceType}`, formData);
}

/** @deprecated Legacy material API. Current project onboarding should use importProjectZip. */
export function uploadProjectZip(token: string, projectId: string, file: File): Promise<ProjectMaterial> {
  const formData = new FormData();
  formData.append("file", file);
  return postFormData<ProjectMaterial>(token, `/projects/${projectId}/materials/zip`, formData);
}

export function importProjectZip(token: string, file: File, projectId?: string): Promise<ProjectImportAnalyzeResult> {
  if (file.size > ZIP_UPLOAD_LIMIT_BYTES) {
    return Promise.reject(new Error(ZIP_UPLOAD_TOO_LARGE_MESSAGE));
  }
  const formData = new FormData();
  formData.append("file", file);
  const query = projectId ? `?projectId=${projectId}` : "";
  return postFormData<ProjectImportAnalyzeResult>(token, `/project-imports/zip${query}`, formData);
}

/** @deprecated Legacy material analysis API. Prefer project/file analysis records. */
export function analyzeProjectMaterial(token: string, materialId: string): Promise<AnalyzeMaterialResult> {
  return requestJson<AnalyzeMaterialResult>(`/project-materials/${materialId}/analyze`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

/** @deprecated Legacy V2 suggestion API. Primary review flow should use ProjectChange APIs. */
export function listAiSuggestions(token: string, projectId: string): Promise<AiSuggestion[]> {
  return requestJson<AiSuggestion[]>(`/projects/${projectId}/suggestions`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

/** @deprecated Legacy V2 suggestion API. Primary review flow should use ProjectChange APIs. */
export function updateAiSuggestion(
  token: string,
  suggestionId: string,
  title: string,
  reason: string,
  payload: Record<string, unknown>,
): Promise<AiSuggestion> {
  return requestJson<AiSuggestion>(`/ai-suggestions/${suggestionId}`, {
    method: "PATCH",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ title, reason, payload }),
  });
}

/** @deprecated Legacy V2 suggestion API. Primary review flow should use ProjectChange APIs. */
export function applyAiSuggestions(token: string, projectId: string, suggestionIds: string[]): Promise<ApplySuggestionsResult> {
  return requestJson<ApplySuggestionsResult>(`/projects/${projectId}/suggestions/apply`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ suggestionIds }),
  });
}

/** @deprecated Legacy V2 suggestion API. Primary review flow should use ProjectChange APIs. */
export function ignoreAiSuggestion(token: string, suggestionId: string): Promise<AiSuggestion> {
  return requestJson<AiSuggestion>(`/ai-suggestions/${suggestionId}/ignore`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export function listProjectChanges(token: string, projectId: string): Promise<ProjectChange[]> {
  return requestJson<ProjectChange[]>(`/projects/${projectId}/changes`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export function getProjectChange(token: string, changeId: string): Promise<ProjectChange> {
  return requestJson<ProjectChange>(`/project-changes/${changeId}`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export function updateProjectChange(token: string, changeId: string, payload: ProjectChangePayload): Promise<ProjectChange> {
  return requestJson<ProjectChange>(`/project-changes/${changeId}`, {
    method: "PATCH",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });
}

export function acceptProjectChange(token: string, changeId: string): Promise<ProjectChange> {
  return requestJson<ProjectChange>(`/project-changes/${changeId}/accept`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export function confirmProjectChange(
  token: string,
  changeId: string,
  action: SedimentAction,
  targetSedimentId: string | null,
): Promise<SedimentConfirmation> {
  return requestJson<SedimentConfirmation>(`/project-changes/${changeId}/confirm`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ action, targetSedimentId }),
  });
}

export function previewProjectChangeConfirmation(
  token: string,
  changeId: string,
  action: SedimentAction,
  targetSedimentId: string | null,
): Promise<SedimentImpactPreview> {
  return requestJson<SedimentImpactPreview>(`/project-changes/${changeId}/confirmation-preview`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ action, targetSedimentId }),
  });
}

export function acknowledgeAnalysisFailure(token: string, jobId: string): Promise<ProjectAnalysisJob> {
  return requestJson<ProjectAnalysisJob>(`/analysis-jobs/${jobId}/acknowledge`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
  });
}

export function cancelProjectAnalysisJob(token: string, jobId: string): Promise<ProjectAnalysisJob> {
  return requestJson<ProjectAnalysisJob>(`/analysis-jobs/${jobId}/cancel`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
  });
}

export function retryProjectAnalysisJob(token: string, jobId: string): Promise<ProjectAnalysisJob> {
  return requestJson<ProjectAnalysisJob>(`/analysis-jobs/${jobId}/retry`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
  });
}

export function listProjectSediments(token: string, projectId: string): Promise<ProjectSediment[]> {
  return requestJson<ProjectSediment[]>(`/projects/${projectId}/sediments`, {
    headers: { Authorization: `Bearer ${token}` },
  });
}

export function listSedimentReviewBatches(token: string, projectId: string): Promise<SedimentReviewBatch[]> {
  return requestJson<SedimentReviewBatch[]>(`/projects/${projectId}/sediment-review-batches`, {
    headers: { Authorization: `Bearer ${token}` },
  });
}

export function getSedimentReviewBatch(token: string, batchId: string): Promise<SedimentReviewBatchDetail> {
  return requestJson<SedimentReviewBatchDetail>(`/sediment-review-batches/${batchId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
}

export function getCapabilityAnalysisOverview(token: string, projectId: string): Promise<CapabilityAnalysisOverview> {
  return requestJson<CapabilityAnalysisOverview>(`/projects/${projectId}/capabilities/overview`, {
    headers: { Authorization: `Bearer ${token}` },
  });
}

export function analyzeProjectCapabilities(token: string, projectId: string): Promise<CapabilityCard[]> {
  return requestJson<CapabilityCard[]>(`/projects/${projectId}/capabilities/analyze`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
  });
}

// V3.3.4: 能力分析异步任务。刷新/离开页面不丢，完成后重新拉取 capability-cards。
export function startCapabilityCardAnalysisJob(token: string, projectId: string): Promise<ProjectAnalysisJob> {
  return requestJson<ProjectAnalysisJob>(`/projects/${projectId}/capabilities/analyze/jobs`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
  });
}

export function listProjectCapabilityCards(token: string, projectId: string): Promise<CapabilityCard[]> {
  return requestJson<CapabilityCard[]>(`/projects/${projectId}/capability-cards`, {
    headers: { Authorization: `Bearer ${token}` },
  });
}

export function updateCapabilityCard(token: string, cardId: string, action: "CONFIRM" | "IGNORE"): Promise<CapabilityCard> {
  return requestJson<CapabilityCard>(`/capability-cards/${cardId}`, {
    method: "PATCH",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ action }),
  });
}

export function getProjectSediment(token: string, sedimentId: string): Promise<ProjectSediment> {
  return requestJson<ProjectSediment>(`/project-sediments/${sedimentId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
}

export function updateProjectSedimentNotes(token: string, sedimentId: string, developerNotes: string): Promise<ProjectSediment> {
  return requestJson<ProjectSediment>(`/project-sediments/${sedimentId}`, {
    method: "PATCH",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ developerNotes }),
  });
}

export function ignoreProjectChange(token: string, changeId: string): Promise<ProjectChange> {
  return requestJson<ProjectChange>(`/project-changes/${changeId}/ignore`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export function runProjectAnalysis(token: string, projectId: string): Promise<ProjectAnalysisJob> {
  return requestJson<ProjectAnalysisJob>(`/projects/${projectId}/analysis/run`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export function analyzeProjectFile(token: string, projectId: string, path: string): Promise<ProjectAnalysisJob> {
  return requestJson<ProjectAnalysisJob>(`/projects/${projectId}/files/analyze`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ path }),
  });
}

export function getProjectAnalysisJob(token: string, jobId: string): Promise<ProjectAnalysisJob> {
  return requestJson<ProjectAnalysisJob>(`/analysis-jobs/${jobId}`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export function listProjectAnalysisJobs(token: string, projectId: string): Promise<ProjectAnalysisJob[]> {
  return requestJson<ProjectAnalysisJob[]>(`/projects/${projectId}/analysis-jobs`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export function listProjectAnalysisRecords(token: string, projectId: string): Promise<ProjectAnalysisRecord[]> {
  return requestJson<ProjectAnalysisRecord[]>(`/projects/${projectId}/analysis-records`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export function getProjectAnalysisRecord(token: string, recordId: string): Promise<ProjectAnalysisRecord> {
  return requestJson<ProjectAnalysisRecord>(`/project-analysis-records/${recordId}`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export function deleteProjectAnalysisRecord(token: string, recordId: string): Promise<void> {
  return requestJson<void>(`/project-analysis-records/${recordId}`, {
    method: "DELETE",
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export function getProjectMemory(token: string, projectId: string): Promise<ProjectMemory> {
  return requestJson<ProjectMemory>(`/projects/${projectId}/memory`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export function updateProjectMemory(token: string, projectId: string, payload: ProjectMemoryPayload): Promise<ProjectMemory> {
  return requestJson<ProjectMemory>(`/projects/${projectId}/memory`, {
    method: "PATCH",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });
}

export function saveProjectLocalPath(token: string, projectId: string, localProjectPath: string): Promise<ProjectMemory> {
  return requestJson<ProjectMemory>(`/projects/${projectId}/memory/local-path`, {
    method: "PATCH",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ localProjectPath }),
  });
}

export function interpretCapability(token: string, projectId: string, capabilityFact: string): Promise<ProjectAnalysisJob> {
  return requestJson<ProjectAnalysisJob>(`/projects/${projectId}/capabilities/interpret`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ capabilityFact }),
  });
}

export function listProjectFactSources(token: string, projectId: string): Promise<ProjectFactSource[]> {
  return requestJson<ProjectFactSource[]>(`/projects/${projectId}/fact-sources`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

/** @deprecated Snapshot UI is not part of the current primary workflow. */
export function listProjectSnapshots(token: string, projectId: string): Promise<ProjectSnapshot[]> {
  return requestJson<ProjectSnapshot[]>(`/projects/${projectId}/snapshots`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export function listProjectEvolutionRecords(token: string, projectId: string): Promise<ProjectEvolutionRecord[]> {
  return requestJson<ProjectEvolutionRecord[]>(`/projects/${projectId}/evolution-records`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export function writeProjectFlowProtocol(
  token: string,
  projectId: string,
  projectPath: string,
  requirements: string,
): Promise<AgentBridgeWriteResult> {
  return requestJson<AgentBridgeWriteResult>(`/projects/${projectId}/agent-bridge/protocol`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ projectPath, requirements }),
  });
}

export function scanProjectFlowAgentResults(
  token: string,
  projectId: string,
  projectPath: string,
): Promise<AgentResultScanResult> {
  return requestJson<AgentResultScanResult>(`/projects/${projectId}/agent-bridge/scan`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ projectPath, requirements: "" }),
  });
}

export function getAgentBridgeHealth(token: string, projectId: string): Promise<AgentBridgeHealth> {
  return requestJson<AgentBridgeHealth>(`/projects/${projectId}/agent-bridge/health`, {
    headers: { Authorization: `Bearer ${token}` },
  });
}

export function getProjectGitHubStatus(token: string, projectId: string): Promise<GitHubStatus> {
  return requestJson<GitHubStatus>(`/projects/${projectId}/github/status`, {
    headers: { Authorization: `Bearer ${token}` },
  });
}

// V3.3.3: GitHub 刷新同步状态——只读取远程提交信息，不修改本地代码。
export function refreshProjectGitHub(token: string, projectId: string): Promise<GitHubStatus> {
  return requestJson<GitHubStatus>(`/projects/${projectId}/github/refresh`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
  });
}

export type GitHubLoginGuide = {
  ghInstalled: boolean;
  status: string;
  command: string;
  instructions: string[];
  warnings: string[];
};

// V3.3.3: GitHub 登录指引。不读取、不展示、不保存 token；只提供命令让用户在终端执行。
export function getGitHubLoginGuide(token: string, projectId: string): Promise<GitHubLoginGuide> {
  return requestJson<GitHubLoginGuide>(`/projects/${projectId}/github/login-guide`, {
    headers: { Authorization: `Bearer ${token}` },
  });
}

// V3.3.4: 打开登录终端结果。opened=false 时前端回退到复制命令。
export type GitHubOpenTerminalResult = {
  opened: boolean;
  command: string;
  platform: string;
  warnings: string[];
};

// V3.3.4: 打开登录终端，执行固定白名单命令 gh auth login --web --clipboard。
// 后端只执行固定命令，不接受前端传入的任意命令。
export function openGitHubLoginTerminal(token: string, projectId: string): Promise<GitHubOpenTerminalResult> {
  return requestJson<GitHubOpenTerminalResult>(`/projects/${projectId}/github/open-login-terminal`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
  });
}

export function scanProjectWorkSessions(token: string, projectId: string): Promise<WorkSessionScanResult> {
  return requestJson<WorkSessionScanResult>(`/projects/${projectId}/scan`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export function startProjectWorkSessionScan(token: string, projectId: string): Promise<ProjectAnalysisJob> {
  return requestJson<ProjectAnalysisJob>(`/projects/${projectId}/scan/jobs`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export function listProjectWorkSessions(token: string, projectId: string): Promise<WorkSessionCandidate[]> {
  return requestJson<WorkSessionCandidate[]>(`/projects/${projectId}/work-sessions`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

/** @deprecated Agent attribution correction is not exposed in the current primary workflow. */
export function updateWorkSession(
  token: string,
  sessionId: string,
  agentType: string,
  taskIntent: string,
): Promise<WorkSessionCandidate> {
  return requestJson<WorkSessionCandidate>(`/work-sessions/${sessionId}`, {
    method: "PATCH",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ agentType, taskIntent }),
  });
}

export function createEvidenceBundle(token: string, sessionId: string): Promise<EvidenceBundle> {
  return requestJson<EvidenceBundle>(`/work-sessions/${sessionId}/evidence-bundles`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export function listProjectEvidenceBundles(token: string, projectId: string): Promise<EvidenceBundle[]> {
  return requestJson<EvidenceBundle[]>(`/projects/${projectId}/evidence-bundles`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

/** @deprecated Agent signature feedback is not exposed in the current primary workflow. */
export function listProjectAgentSignatureFeedback(token: string, projectId: string): Promise<AgentSignatureFeedback[]> {
  return requestJson<AgentSignatureFeedback[]>(`/projects/${projectId}/agent-signature-feedback`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export function listProjectChangeConflicts(token: string, projectId: string): Promise<ChangeConflict[]> {
  return requestJson<ChangeConflict[]>(`/projects/${projectId}/change-conflicts`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export function draftProjectChangeFromEvidenceBundle(token: string, bundleId: string): Promise<ProjectChange> {
  return requestJson<ProjectChange>(`/evidence-bundles/${bundleId}/draft-changes`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export function syncProjectContext(token: string, projectId: string): Promise<ContextSyncResult> {
  return requestJson<ContextSyncResult>(`/projects/${projectId}/context/sync`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

/** @deprecated Task brief writing belongs to the legacy agent bridge task flow. */
export function writeAgentTaskBrief(
  token: string,
  projectId: string,
  taskId: string,
  projectPath: string,
  requirements: string,
): Promise<AgentTaskBriefResult> {
  return requestJson<AgentTaskBriefResult>(`/projects/${projectId}/agent-bridge/tasks/${taskId}/brief`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ projectPath, requirements }),
  });
}
