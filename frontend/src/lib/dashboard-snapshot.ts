"use client";

import type {
  AiOutput,
  AiSuggestion,
  ChangeConflict,
  EvidenceBundle,
  GitHubStatus,
  Project,
  ProjectChange,
  ProjectEvolutionRecord,
  ProjectMaterial,
  ProjectMemory,
  TaskItem,
  WorkSessionScanResult,
} from "./api";

const LEGACY_SNAPSHOT_KEY = "projectflow:dashboardSnapshot";
const SNAPSHOT_KEY_PREFIX = "projectflow:dashboardSnapshot:";
const ACTIVE_PROJECT_KEY = "projectflow:dashboardSnapshot:activeProject";
export const DASHBOARD_SNAPSHOT_SCHEMA_VERSION = 2;

export type DashboardSnapshot = {
  schemaVersion: number;
  projectId: string;
  capturedAt: string;
  selectedProjectId: string;
  latestScanJobId: string | null;
  latestBatchId: string | null;
  latestBatchUpdatedAt: string | null;
  pendingSedimentReviewCount: number;
  projects: Project[];
  materials: ProjectMaterial[];
  suggestions: AiSuggestion[];
  evolutionRecords: ProjectEvolutionRecord[];
  evidenceBundles: EvidenceBundle[];
  changeConflicts: ChangeConflict[];
  changes: ProjectChange[];
  outputs: AiOutput[];
  tasks: TaskItem[];
  memory: ProjectMemory | null;
  workSessionScan: WorkSessionScanResult | null;
  githubStatus: GitHubStatus | null;
};

export function mergeWorkSessionScanResult(
  current: WorkSessionScanResult | null,
  incoming: WorkSessionScanResult | null,
  authoritative = false,
): WorkSessionScanResult | null {
  if (!incoming) {
    return current;
  }
  if (!current || current.projectId !== incoming.projectId) {
    return incoming;
  }
  if (authoritative) {
    return incoming;
  }
  return {
    ...incoming,
    warnings: incoming.warnings.length > 0 ? incoming.warnings : current.warnings,
    batch: incoming.batch ?? current.batch,
    segments: incoming.segments.length > 0 ? incoming.segments : current.segments,
  };
}

export function readDashboardSnapshot(projectId?: string): DashboardSnapshot | null {
  if (typeof window === "undefined") {
    return null;
  }
  try {
    const requestedProjectId = projectId || window.sessionStorage.getItem(ACTIVE_PROJECT_KEY) || "";
    if (requestedProjectId) {
      const current = parseSnapshot(window.sessionStorage.getItem(snapshotKey(requestedProjectId)), requestedProjectId);
      if (current) {
        return current;
      }
    }

    const legacy = parseSnapshot(window.sessionStorage.getItem(LEGACY_SNAPSHOT_KEY), requestedProjectId);
    if (!legacy || (requestedProjectId && legacy.projectId !== requestedProjectId)) {
      return null;
    }
    writeDashboardSnapshot(legacy);
    window.sessionStorage.removeItem(LEGACY_SNAPSHOT_KEY);
    return legacy;
  } catch {
    return null;
  }
}

export function writeDashboardSnapshot(snapshot: DashboardSnapshot) {
  if (typeof window === "undefined") {
    return;
  }
  try {
    const projectId = snapshot.projectId || snapshot.selectedProjectId;
    if (!projectId) {
      return;
    }
    const workSessionScan = snapshot.workSessionScan?.projectId === projectId ? snapshot.workSessionScan : null;
    window.sessionStorage.setItem(snapshotKey(projectId), JSON.stringify({ ...snapshot, projectId, selectedProjectId: projectId, workSessionScan }));
    window.sessionStorage.setItem(ACTIVE_PROJECT_KEY, projectId);
  } catch {
    // 快照写失败不应影响工作台主流程。
  }
}

export function patchDashboardSnapshot(patch: Partial<DashboardSnapshot>) {
  if (typeof window === "undefined") {
    return;
  }
  const projectId = patch.projectId || patch.selectedProjectId || window.sessionStorage.getItem(ACTIVE_PROJECT_KEY) || "";
  if (!projectId) {
    return;
  }
  const current = readDashboardSnapshot(projectId);
  const currentScan = current?.workSessionScan?.projectId === projectId ? current.workSessionScan : null;
  const workSessionScan = patch.workSessionScan === null
    ? null
    : patch.workSessionScan?.projectId === projectId
      ? patch.workSessionScan
      : currentScan;
  writeDashboardSnapshot({
    schemaVersion: DASHBOARD_SNAPSHOT_SCHEMA_VERSION,
    projectId,
    capturedAt: new Date().toISOString(),
    selectedProjectId: projectId,
    latestScanJobId: patch.latestScanJobId !== undefined ? patch.latestScanJobId : current?.latestScanJobId ?? null,
    latestBatchId: patch.latestBatchId !== undefined ? patch.latestBatchId : workSessionScan?.batch?.id ?? current?.latestBatchId ?? null,
    latestBatchUpdatedAt: patch.latestBatchUpdatedAt !== undefined
      ? patch.latestBatchUpdatedAt
      : workSessionScan?.batch?.scanFinishedAt ?? workSessionScan?.batch?.scanStartedAt ?? current?.latestBatchUpdatedAt ?? null,
    pendingSedimentReviewCount: patch.pendingSedimentReviewCount ?? current?.pendingSedimentReviewCount ?? 0,
    projects: patch.projects ?? current?.projects ?? [],
    materials: patch.materials ?? current?.materials ?? [],
    suggestions: patch.suggestions ?? current?.suggestions ?? [],
    evolutionRecords: patch.evolutionRecords ?? current?.evolutionRecords ?? [],
    evidenceBundles: patch.evidenceBundles ?? current?.evidenceBundles ?? [],
    changeConflicts: patch.changeConflicts ?? current?.changeConflicts ?? [],
    changes: patch.changes ?? current?.changes ?? [],
    outputs: patch.outputs ?? current?.outputs ?? [],
    tasks: patch.tasks ?? current?.tasks ?? [],
    memory: patch.memory !== undefined ? patch.memory : current?.memory ?? null,
    workSessionScan,
    githubStatus: patch.githubStatus !== undefined ? patch.githubStatus : current?.githubStatus ?? null,
  });
}

export function isDashboardSnapshotStale(snapshot: DashboardSnapshot, maxAgeMs = 5 * 60 * 1000) {
  const capturedAt = Date.parse(snapshot.capturedAt);
  return !Number.isFinite(capturedAt) || Date.now() - capturedAt > maxAgeMs;
}

export function clearDashboardSnapshot(projectId?: string) {
  if (typeof window === "undefined") {
    return;
  }
  try {
    if (projectId) {
      window.sessionStorage.removeItem(snapshotKey(projectId));
      if (window.sessionStorage.getItem(ACTIVE_PROJECT_KEY) === projectId) {
        window.sessionStorage.removeItem(ACTIVE_PROJECT_KEY);
      }
      return;
    }
    for (let index = window.sessionStorage.length - 1; index >= 0; index -= 1) {
      const key = window.sessionStorage.key(index);
      if (key === LEGACY_SNAPSHOT_KEY || key === ACTIVE_PROJECT_KEY || key?.startsWith(SNAPSHOT_KEY_PREFIX)) {
        window.sessionStorage.removeItem(key);
      }
    }
  } catch {
    // 忽略清理失败。
  }
}

function snapshotKey(projectId: string) {
  return `${SNAPSHOT_KEY_PREFIX}${projectId}`;
}

function parseSnapshot(raw: string | null, requestedProjectId: string): DashboardSnapshot | null {
  if (!raw) {
    return null;
  }
  const parsed = JSON.parse(raw) as Partial<DashboardSnapshot>;
  if (!parsed || typeof parsed !== "object" || !Array.isArray(parsed.projects)) {
    return null;
  }
  const projectId = parsed.projectId || parsed.selectedProjectId || requestedProjectId;
  if (!projectId) {
    return null;
  }
  const scan = parsed.workSessionScan?.projectId === projectId ? parsed.workSessionScan : null;
  return {
    schemaVersion: DASHBOARD_SNAPSHOT_SCHEMA_VERSION,
    projectId,
    capturedAt: parsed.capturedAt || new Date(0).toISOString(),
    selectedProjectId: projectId,
    latestScanJobId: parsed.latestScanJobId ?? null,
    latestBatchId: parsed.latestBatchId ?? scan?.batch?.id ?? null,
    latestBatchUpdatedAt: parsed.latestBatchUpdatedAt ?? scan?.batch?.scanFinishedAt ?? scan?.batch?.scanStartedAt ?? null,
    pendingSedimentReviewCount: parsed.pendingSedimentReviewCount ?? 0,
    projects: parsed.projects,
    materials: parsed.materials ?? [],
    suggestions: parsed.suggestions ?? [],
    evolutionRecords: parsed.evolutionRecords ?? [],
    evidenceBundles: parsed.evidenceBundles ?? [],
    changeConflicts: parsed.changeConflicts ?? [],
    changes: parsed.changes ?? [],
    outputs: parsed.outputs ?? [],
    tasks: parsed.tasks ?? [],
    memory: parsed.memory ?? null,
    workSessionScan: scan,
    githubStatus: parsed.githubStatus ?? null,
  };
}
