"use client";

import { Suspense, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { ArrowLeft, CalendarDays } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { ResourceTimeline, type ResourceTimelineItem } from "@/components/ResourceTimeline";
import {
  getProjectMemory,
  listDevLogs,
  listProjectEvolutionRecords,
  listProjects,
  listTasks,
  type DevLog,
  type Project,
  type ProjectEvolutionRecord,
  type ProjectMemory,
  type TaskItem,
} from "@/lib/api";
import { readSession } from "@/lib/auth";
import { resolveSelectedProjectId } from "@/lib/project-selection";

export default function DailySourcesPage() {
  return (
    <Suspense fallback={<AppShell eyebrow="每日回顾来源明细" title="每日回顾来源"><div className="min-h-[calc(100vh-4rem)] bg-surface p-6"><div className="h-1 bg-slate-950" /></div></AppShell>}>
      <DailySourcesPageContent />
    </Suspense>
  );
}

function DailySourcesPageContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const queryProjectId = searchParams.get("projectId") ?? "";
  const date = searchParams.get("date") ?? new Date().toISOString().slice(0, 10);
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState(queryProjectId);
  const [logs, setLogs] = useState<DevLog[]>([]);
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [evolutionRecords, setEvolutionRecords] = useState<ProjectEvolutionRecord[]>([]);
  const [memory, setMemory] = useState<ProjectMemory | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const project = useMemo(() => projects.find((item) => item.id === selectedProjectId), [projects, selectedProjectId]);
  const dayLogs = logs.filter((log) => log.logDate === date);
  const dayEvolution = evolutionRecords.filter((record) => record.createdAt.slice(0, 10) === date);
  const completedTasks = tasks.filter((task) => task.status === "DONE");
  const riskItems = [memory?.currentRisks, memory?.technicalDecisions].filter((item): item is string => Boolean(item && !item.includes("暂无")));
  const sourceItems = useMemo<ResourceTimelineItem[]>(() => [
    ...dayLogs.map((log) => ({
      id: `log-${log.id}`,
      title: log.title,
      summary: log.content || "当天保存的开发回顾。",
      date: log.updatedAt || `${log.logDate}T00:00:00`,
      type: "开发日志",
      status: log.blocked ? "有阻塞" : "已记录",
      source: `${log.category} · ${log.minutesSpent} 分钟`,
      detail: log.content,
    })),
    ...dayEvolution.map((record) => ({
      id: `evo-${record.id}`,
      title: record.summary || "项目演进记录",
      summary: record.detectedChanges || record.keyAchievements || "当天项目演进记录。",
      date: record.createdAt,
      type: "项目演进",
      status: "已入档",
      source: record.materialId ? "项目材料" : "项目档案",
      detail: [
        record.detectedChanges ? `变化内容\n${record.detectedChanges}` : "",
        record.keyAchievements ? `关键成果\n${record.keyAchievements}` : "",
        record.nextSteps ? `下一步\n${record.nextSteps}` : "",
      ].filter(Boolean).join("\n\n"),
    })),
    ...completedTasks.map((task) => ({
      id: `task-${task.id}`,
      title: task.title,
      summary: task.description || "当天可作为证据的已完成任务。",
      date: task.updatedAt || task.createdAt,
      type: "任务证据",
      status: task.status,
      source: task.priority,
      detail: task.description || "暂无任务说明。",
    })),
    ...riskItems.map((item, index) => ({
      id: `memory-${index}`,
      title: index === 0 ? "当前风险" : "技术决策",
      summary: item,
      date: memory?.updatedAt || `${date}T00:00:00`,
      type: "项目档案",
      status: "已确认",
      source: "项目档案",
      detail: item,
    })),
  ], [completedTasks, date, dayEvolution, dayLogs, memory?.updatedAt, riskItems]);

  useEffect(() => {
    const session = readSession();
    if (!session) {
      setError("请先登录后再查看每日回顾来源。");
      setLoading(false);
      return;
    }

    listProjects(session.accessToken)
      .then((items) => {
        setProjects(items);
        setSelectedProjectId(queryProjectId || resolveSelectedProjectId(items));
      })
      .catch((exception) => setError(exception instanceof Error ? exception.message : "项目加载失败"))
      .finally(() => setLoading(false));
  }, [queryProjectId]);

  useEffect(() => {
    const session = readSession();
    if (!session || !selectedProjectId) {
      return;
    }

    setLoading(true);
    setError("");
    Promise.all([
      listDevLogs(session.accessToken, selectedProjectId),
      listTasks(session.accessToken, selectedProjectId),
      listProjectEvolutionRecords(session.accessToken, selectedProjectId),
      getProjectMemory(session.accessToken, selectedProjectId),
    ])
      .then(([devLogs, projectTasks, evolutionItems, memoryRecord]) => {
        setLogs(devLogs);
        setTasks(projectTasks);
        setEvolutionRecords(evolutionItems);
        setMemory(memoryRecord);
      })
      .catch((exception) => setError(exception instanceof Error ? exception.message : "每日来源加载失败"))
      .finally(() => setLoading(false));
  }, [selectedProjectId]);

  return (
    <AppShell eyebrow="每日回顾来源明细" title={project ? `${project.name} · ${date}` : "每日回顾来源"}>
      <div className="min-h-[calc(100vh-4rem)] bg-surface p-6">
        <section className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-md border border-line bg-white p-4 shadow-panel">
          <div>
            <button className="mb-2 inline-flex items-center gap-1 text-sm font-semibold text-slate-600 hover:text-slate-950" onClick={() => router.back()} type="button">
              <ArrowLeft className="h-4 w-4" />
              返回上一步
            </button>
            <h2 className="text-xl font-semibold text-slate-950">每日回顾来源</h2>
            <p className="mt-1 flex items-center gap-2 text-sm text-muted">
              <CalendarDays className="h-4 w-4" />
              {date}
            </p>
          </div>
          <Link className="rounded-md border border-line px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50" href="/dev-logs">
            回到每日回顾
          </Link>
        </section>

        {error ? <div className="mb-5 rounded-md border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">{error}</div> : null}
        {loading ? <div className="h-1 bg-slate-950" /> : null}

        <ResourceTimeline
          emptyText="当天还没有可用于每日回顾的来源。保存开发日志、采纳变更或确认项目档案后会出现在这里。"
          items={sourceItems}
          title={`每日回顾来源 · ${sourceItems.length} 条`}
        />
      </div>
    </AppShell>
  );
}
