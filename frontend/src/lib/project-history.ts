export type ProjectHistoryEntityType = "overview" | "chapter" | "story" | "thread";

export function projectHistoryEntityType(value: string | null | undefined): ProjectHistoryEntityType {
  return value === "chapter" || value === "story" || value === "thread" ? value : "overview";
}

export function projectHistoryHref(
  projectId: string,
  type: ProjectHistoryEntityType = "overview",
  entityId = "",
) {
  const base = `/projects/${encodeURIComponent(projectId)}/history`;
  if (type === "overview") return base;
  const params = new URLSearchParams({ type, id: entityId });
  return `${base}?${params.toString()}`;
}

export const projectHistoryTransitionLabels: Record<string, string> = {
  CREATED: "新增",
  MODIFIED: "修改",
  REMOVED: "删除",
  RESTORED: "恢复",
  RENAMED: "重命名",
  MOVED: "移动",
  REPLACED: "替换",
  SPLIT: "拆分",
  MERGED: "合并",
  REVERTED: "撤销",
  REAPPLIED: "重新实现",
  UNKNOWN_TRANSITION: "转换未知",
};

export function projectHistoryTransitionLabel(value: string) {
  return projectHistoryTransitionLabels[value] ?? value ?? "未知";
}
