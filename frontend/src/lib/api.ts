import type { AuthResult } from "./auth";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api";

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

function isNetworkError(error: unknown) {
  return error instanceof TypeError && error.message.toLowerCase().includes("fetch");
}

async function readApiPayload<T>(response: Response): Promise<ApiResponse<T> & ApiErrorResponse> {
  try {
    return (await response.json()) as ApiResponse<T> & ApiErrorResponse;
  } catch {
    throw new Error(RESPONSE_ERROR_MESSAGE);
  }
}

async function fetchWithFriendlyError(url: string, options: RequestInit): Promise<Response> {
  try {
    return await fetch(url, options);
  } catch (error) {
    if (isNetworkError(error)) {
      throw new Error(NETWORK_ERROR_MESSAGE);
    }
    throw error instanceof Error ? error : new Error("请求失败，请稍后重试");
  }
}

async function postJson<T>(path: string, body: unknown, retryNetwork = false): Promise<T> {
  const request = () => fetchWithFriendlyError(`${API_BASE_URL}${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });

  let response: Response;
  try {
    response = await request();
  } catch (error) {
    if (!retryNetwork || !(error instanceof Error) || error.message !== NETWORK_ERROR_MESSAGE) {
      throw error;
    }
    await new Promise((resolve) => window.setTimeout(resolve, 350));
    response = await request();
  }

  const payload = await readApiPayload<T>(response);
  if (!response.ok) {
    throw new Error(payload.error?.message ?? "请求失败，请稍后重试");
  }
  return payload.data;
}

async function requestJson<T>(path: string, options: RequestInit): Promise<T> {
  const response = await fetchWithFriendlyError(`${API_BASE_URL}${path}`, options);
  const payload = await readApiPayload<T>(response);
  if (!response.ok) {
    throw new Error(payload.error?.message ?? "请求失败，请稍后重试");
  }
  return payload.data;
}

export function login(email: string, password: string): Promise<AuthResult> {
  return postJson<AuthResult>("/auth/login", { email, password }, true);
}

export function register(username: string, email: string, password: string): Promise<AuthResult> {
  return postJson<AuthResult>("/auth/register", { username, email, password });
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
};

export type ProviderTestResult = {
  ok: boolean;
  provider: string;
  message: string;
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

export function listProjectMaterials(token: string, projectId: string): Promise<ProjectMaterial[]> {
  return requestJson<ProjectMaterial[]>(`/projects/${projectId}/materials`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

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
  const response = await fetchWithFriendlyError(`${API_BASE_URL}${path}`, {
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

export function uploadProjectZip(token: string, projectId: string, file: File): Promise<ProjectMaterial> {
  const formData = new FormData();
  formData.append("file", file);
  return postFormData<ProjectMaterial>(token, `/projects/${projectId}/materials/zip`, formData);
}

export function importProjectZip(token: string, file: File, projectId?: string): Promise<ProjectImportAnalyzeResult> {
  const formData = new FormData();
  formData.append("file", file);
  const query = projectId ? `?projectId=${projectId}` : "";
  return postFormData<ProjectImportAnalyzeResult>(token, `/project-imports/zip${query}`, formData);
}

export function analyzeProjectMaterial(token: string, materialId: string): Promise<AnalyzeMaterialResult> {
  return requestJson<AnalyzeMaterialResult>(`/project-materials/${materialId}/analyze`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

export function listAiSuggestions(token: string, projectId: string): Promise<AiSuggestion[]> {
  return requestJson<AiSuggestion[]>(`/projects/${projectId}/suggestions`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
}

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

export function ignoreAiSuggestion(token: string, suggestionId: string): Promise<AiSuggestion> {
  return requestJson<AiSuggestion>(`/ai-suggestions/${suggestionId}/ignore`, {
    method: "POST",
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
