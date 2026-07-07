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

// 工作台快照缓存于 sessionStorage：离开工作台后组件卸载、state 销毁，
// 回到工作台时先用快照瞬时渲染旧数据，再在后台静默刷新，避免出现
// "刚登录进来什么都没加载" 的空白与约 10 秒的等待。
const SNAPSHOT_KEY = "projectflow:dashboardSnapshot";

export type DashboardSnapshot = {
  capturedAt: string;
  selectedProjectId: string;
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

export function readDashboardSnapshot(): DashboardSnapshot | null {
  if (typeof window === "undefined") {
    return null;
  }
  try {
    const raw = window.sessionStorage.getItem(SNAPSHOT_KEY);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as DashboardSnapshot;
    if (!parsed || typeof parsed !== "object" || !Array.isArray(parsed.projects)) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export function writeDashboardSnapshot(snapshot: DashboardSnapshot) {
  if (typeof window === "undefined") {
    return;
  }
  try {
    window.sessionStorage.setItem(SNAPSHOT_KEY, JSON.stringify(snapshot));
  } catch {
    // 快照写失败不应影响工作台主流程。
  }
}

export function patchDashboardSnapshot(patch: Partial<DashboardSnapshot>) {
  if (typeof window === "undefined") {
    return;
  }
  const current = readDashboardSnapshot();
  if (!current && patch.projects === undefined) {
    // 没有现成快照、又没提供 projects 基础字段，先不写，等首次完整刷新。
    return;
  }
  writeDashboardSnapshot({
    capturedAt: new Date().toISOString(),
    selectedProjectId: patch.selectedProjectId ?? current?.selectedProjectId ?? "",
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
    workSessionScan: patch.workSessionScan !== undefined ? patch.workSessionScan : current?.workSessionScan ?? null,
    githubStatus: patch.githubStatus !== undefined ? patch.githubStatus : current?.githubStatus ?? null,
  });
}

export function clearDashboardSnapshot() {
  if (typeof window === "undefined") {
    return;
  }
  try {
    window.sessionStorage.removeItem(SNAPSHOT_KEY);
  } catch {
    // 忽略清理失败。
  }
}
