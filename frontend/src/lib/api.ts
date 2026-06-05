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

async function postJson<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });

  const payload = (await response.json()) as ApiResponse<T> & ApiErrorResponse;
  if (!response.ok) {
    throw new Error(payload.error?.message ?? "请求失败，请稍后重试");
  }
  return payload.data;
}

async function requestJson<T>(path: string, options: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, options);
  const payload = (await response.json()) as ApiResponse<T> & ApiErrorResponse;
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
