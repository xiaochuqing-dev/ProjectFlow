import type { ResourceTimelineItem } from "@/components/ResourceTimeline";
import type { DevLog, ProjectEvolutionRecord, ProjectMemory, TaskItem } from "@/lib/api";

type DailyReviewSourceInput = {
  completedTasks: TaskItem[];
  date: string;
  dayEvolution: ProjectEvolutionRecord[];
  dayLogs: DevLog[];
  memory: ProjectMemory | null;
};

export function buildDailyReviewSourceItems({
  completedTasks,
  date,
  dayEvolution,
  dayLogs,
  memory,
}: DailyReviewSourceInput): ResourceTimelineItem[] {
  return [
    ...dayLogs.map((log) => ({
      id: `log-${log.id}`,
      title: log.title,
      summary: log.content || "当天保存的开发回顾。",
      date: log.updatedAt || `${log.logDate}T00:00:00`,
      type: "开发日志",
      status: log.blocked ? "有阻塞" : "已记录",
      source: `${log.category} · ${log.minutesSpent} 分钟`,
      detail: log.content || "暂无日志正文。",
    })),
    ...dayEvolution.map((record) => ({
      id: `evo-${record.id}`,
      title: record.summary || "项目演进记录",
      summary: record.detectedChanges || record.keyAchievements || "当天项目演进记录。",
      date: record.createdAt,
      type: "项目演进",
      status: "已入档",
      source: record.materialId ? "项目材料" : "项目沉淀",
      detail: [
        record.detectedChanges ? `变化内容\n${record.detectedChanges}` : "",
        record.keyAchievements ? `关键成果\n${record.keyAchievements}` : "",
        record.nextSteps ? `下一步\n${record.nextSteps}` : "",
      ].filter(Boolean).join("\n\n") || "暂无演进详情。",
    })),
    ...completedTasks.map((task) => ({
      id: `task-${task.id}`,
      title: task.title,
      summary: task.description || "当天可作为证据的已完成任务。",
      date: task.updatedAt || task.createdAt,
      type: "开发证据",
      status: task.status,
      source: task.priority,
      detail: task.description || "暂无任务说明。",
    })),
    ...memorySourceItems(memory, date),
  ];
}

function memorySourceItems(memory: ProjectMemory | null, date: string): ResourceTimelineItem[] {
  const values = [
    { title: "当前风险", value: memory?.currentRisks },
    { title: "技术决策", value: memory?.technicalDecisions },
  ].filter((item): item is { title: string; value: string } => Boolean(item.value && !item.value.includes("暂无")));

  return values.map((item, index) => ({
    id: `memory-${index}`,
    title: item.title,
    summary: item.value,
    date: memory?.updatedAt || `${date}T00:00:00`,
    type: "项目沉淀",
    status: "已确认",
    source: "项目沉淀",
    detail: item.value,
  }));
}
