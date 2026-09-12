export type ProjectHistoryEntityType = "overview" | "chapter" | "story" | "thread";

export function safeHistoryDeepLink(value: string | null | undefined) {
  const candidate = value?.trim() ?? "";
  return /^(https:\/\/|\/projects\/|obsidian:\/\/)/i.test(candidate) ? candidate : "";
}

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

export function projectHistoryRoleLabel(value: string | null | undefined) {
  return value === "SUPPORTING" ? "支撑工作" : "主要变化";
}

export function projectHistoryPresentationLabel(value: string | null | undefined) {
  return value === "USER_DECLARED_PRESENTATION" ? "经过你的修改" : "自动整理";
}

export function projectHistoryStatusLabel(value: string | null | undefined) {
  const labels: Record<string, string> = {
    READY: "可阅读",
    DEGRADED: "部分内容待核对",
    FAILED: "最近刷新失败",
    NOT_INITIALIZED: "尚未生成",
  };
  return labels[value ?? ""] ?? "状态待核对";
}

export function projectHistorySourceTypeLabel(value: string | null | undefined) {
  const labels: Record<string, string> = {
    GIT: "Git 记录",
    GITHUB: "GitHub 记录",
    FILESYSTEM: "本地文件",
    PROJECT_FACT: "项目事实",
    AGENT_RESULT: "Agent 工作结果",
    DOCUMENT: "项目文档",
    USER: "用户说明",
    EXTERNAL: "外部来源",
  };
  return labels[value ?? ""] ?? "其他来源";
}

export function projectHistoryRewriteStateLabel(value: string | null | undefined) {
  const labels: Record<string, string> = {
    CURRENT: "在本次读取的历史中",
    STALE: "来源已变化",
    INVALIDATED: "已失效",
  };
  return labels[value ?? ""] ?? "有效性待核对";
}

export function projectHistoryEvidenceValidationLabel(value: string | null | undefined) {
  const labels: Record<string, string> = {
    OBSERVED: "已观察到来源变化，未独立验证运行结果", SOURCE_BACKED: "可追溯到直接来源",
    VERIFIED: "存在验证记录，范围以来源为准", DECLARED: "来源作者的陈述",
    PROCESS_EVIDENCE: "开发过程记录", INFERRED: "系统归纳", EXPLAINED: "基于来源的解释",
    UNKNOWN: "尚未确认", CONFLICTED: "来源有冲突",
  };
  return labels[value ?? ""] ?? "验证边界见来源记录";
}
