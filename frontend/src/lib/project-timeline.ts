import type { TimelineGranularity, TimelineSummaryStatus } from "./api";

export const timelineGranularityLabels: Record<TimelineGranularity, string> = {
  DAY: "按日",
  WEEK: "按周",
  MONTH: "按月",
  LIFECYCLE: "全部",
};

export const timelineStatusLabels: Record<TimelineSummaryStatus, string> = {
  DIRTY: "自动摘要等待更新",
  QUEUED: "自动摘要已排队",
  GENERATING: "自动摘要正在更新",
  READY: "自动摘要已更新",
  FAILED: "自动摘要更新失败",
  WAITING_FOR_MODEL: "等待模型配置",
  NOT_REQUIRED: "事实时间视图",
  NOT_GENERATED: "尚未生成自动摘要",
};

export function timelinePeriodLabel(granularity: TimelineGranularity, periodKey: string) {
  if (granularity === "MONTH" && /^\d{4}-\d{2}$/.test(periodKey)) {
    const [year, month] = periodKey.split("-");
    return `${year} 年 ${Number(month)} 月`;
  }
  if (granularity === "DAY" && /^\d{4}-\d{2}-\d{2}$/.test(periodKey)) {
    const [year, month, day] = periodKey.split("-");
    return `${year} 年 ${Number(month)} 月 ${Number(day)} 日`;
  }
  if (granularity === "WEEK") return `ISO 周 ${periodKey}`;
  return periodKey === "ALL" ? "项目完整历程" : periodKey;
}

export function timelineRangeLabel(start: string | null, end: string | null, zone: string) {
  if (!start || !end) return "";
  const formatter = new Intl.DateTimeFormat("zh-CN", {
    timeZone: zone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
  return `${formatter.format(new Date(start))} 至 ${formatter.format(new Date(new Date(end).getTime() - 1))}`;
}

export function timelineHistoryLabel(status: string, covered: number, total: number) {
  if (status === "COMPLETED") return "Git 历史覆盖已完成";
  if (status === "FAILED") return `历史补齐局部失败：已覆盖 ${covered} / ${total} commits`;
  if (status === "PAUSED") return `历史补齐已暂停：已覆盖 ${covered} / ${total} commits`;
  return `项目历史记忆仍在补齐：已覆盖 ${covered} / ${total} commits`;
}
