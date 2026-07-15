import type { ProjectRecordBatch } from "./api";

export type ProjectRecordGroup = {
  key: string;
  label: string;
  items: ProjectRecordBatch[];
};

const EXPLICIT_HISTORY_MONTHS = 12;

export function groupProjectRecordBatches(items: ProjectRecordBatch[], now = new Date()): ProjectRecordGroup[] {
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const mondayOffset = (startOfToday.getDay() + 6) % 7;
  const startOfWeek = new Date(startOfToday);
  startOfWeek.setDate(startOfWeek.getDate() - mondayOffset);
  const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1);
  const oldestExplicitMonth = new Date(now.getFullYear(), now.getMonth() - EXPLICIT_HISTORY_MONTHS, 1);
  const groups = new Map<string, ProjectRecordGroup>();

  for (const item of [...items].sort((left, right) => batchTime(right) - batchTime(left))) {
    const occurredAt = projectRecordOccurredAt(item);
    let key = "earlier";
    let label = "更早";
    if (occurredAt && occurredAt >= startOfWeek) {
      key = "this-week";
      label = "本周";
    } else if (occurredAt && occurredAt >= startOfMonth) {
      key = "this-month";
      label = "本月";
    } else if (occurredAt && occurredAt >= oldestExplicitMonth) {
      key = `${occurredAt.getFullYear()}-${String(occurredAt.getMonth() + 1).padStart(2, "0")}`;
      label = `${occurredAt.getFullYear()} 年 ${occurredAt.getMonth() + 1} 月`;
    }
    const group = groups.get(key) ?? { key, label, items: [] };
    group.items.push(item);
    groups.set(key, group);
  }

  const fixedOrder = ["this-week", "this-month"];
  const historical = [...groups.values()]
    .filter((group) => !fixedOrder.includes(group.key) && group.key !== "earlier")
    .sort((left, right) => right.key.localeCompare(left.key));
  return [
    ...fixedOrder.map((key) => groups.get(key)).filter((group): group is ProjectRecordGroup => Boolean(group)),
    ...historical,
    ...(groups.has("earlier") ? [groups.get("earlier")!] : []),
  ];
}

export function projectRecordOccurredAt(batch: ProjectRecordBatch): Date | null {
  return safeDate(batch.factOccurredTo)
    ?? safeDate(batch.factOccurredFrom)
    ?? safeDate(batch.scanFinishedAt)
    ?? safeDate(batch.scanStartedAt);
}

export function formatProjectRecordRange(batch: ProjectRecordBatch): string {
  const from = safeDate(batch.factOccurredFrom);
  const to = safeDate(batch.factOccurredTo);
  if (!from && !to) {
    const scannedAt = safeDate(batch.scanFinishedAt) ?? safeDate(batch.scanStartedAt);
    return scannedAt ? `分析于 ${formatDateTime(scannedAt)}，事实时间待补充` : "事实时间待补充";
  }
  if (!from || !to || from.toDateString() === to.toDateString()) return formatDateTime(to ?? from!);
  return `${formatDateTime(from)} 至 ${formatDateTime(to)}`;
}

export function formatFactOccurredRange(fromValue: string | null, toValue: string | null): string {
  const from = safeDate(fromValue);
  const to = safeDate(toValue);
  if (!from && !to) return "发生时间待补充";
  if (!from || !to || from.toDateString() === to.toDateString()) return formatDateTime(to ?? from!);
  return `${formatDateTime(from)} 至 ${formatDateTime(to)}`;
}

export function safeDate(value: string | null | undefined): Date | null {
  if (!value) return null;
  const date = new Date(value);
  return Number.isFinite(date.getTime()) && date.getTime() > 0 ? date : null;
}

function batchTime(batch: ProjectRecordBatch): number {
  return projectRecordOccurredAt(batch)?.getTime() ?? 0;
}

function formatDateTime(value: Date): string {
  return value.toLocaleString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}
