"use client";

import { Suspense, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useParams, useSearchParams } from "next/navigation";
import { ArrowLeft, CalendarDays, FileText } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { Badge, Toast } from "@/components/ui";
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
import { buildDailyReviewSourceItems } from "@/lib/daily-review-sources";
import { resolveSelectedProjectId } from "@/lib/project-selection";

export default function DailySourceDetailPage() {
  return (
    <Suspense fallback={<AppShell eyebrow="每日回顾来源" title="来源详情"><div className="min-h-[calc(100vh-4rem)] bg-surface p-6"><div className="h-1 bg-slate-950" /></div></AppShell>}>
      <DailySourceDetailContent />
    </Suspense>
  );
}

function DailySourceDetailContent() {
  const params = useParams<{ sourceId: string }>();
  const searchParams = useSearchParams();
  const sourceId = decodeURIComponent(Array.isArray(params.sourceId) ? params.sourceId[0] : params.sourceId);
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
  const sourceItems = useMemo(() => buildDailyReviewSourceItems({
    completedTasks,
    date,
    dayEvolution,
    dayLogs,
    memory,
  }), [completedTasks, date, dayEvolution, dayLogs, memory]);
  const source = sourceItems.find((item) => item.id === sourceId);

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
      .catch((exception) => setError(exception instanceof Error ? exception.message : "来源详情加载失败"))
      .finally(() => setLoading(false));
  }, [selectedProjectId]);

  return (
    <AppShell eyebrow="每日回顾来源" title={source?.title ?? "来源详情"}>
      <div className="min-h-[calc(100vh-4rem)] bg-surface p-6">
        <section className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-md border border-line bg-white p-4 shadow-panel">
          <div>
            <Link className="mb-2 inline-flex items-center gap-1 text-sm font-semibold text-slate-600 hover:text-slate-950" href={`/dev-logs/sources?projectId=${selectedProjectId}&date=${date}`}>
              <ArrowLeft className="h-4 w-4" />
              回到来源列表
            </Link>
            <h2 className="text-xl font-semibold text-slate-950">{source?.title ?? "未找到来源"}</h2>
            <p className="mt-1 flex items-center gap-2 text-sm text-muted">
              <CalendarDays className="h-4 w-4" />
              {project?.name ?? "当前项目"} · {date}
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            {source ? <Badge label={source.type} /> : null}
            {source ? <Badge label={source.status} tone="success" /> : null}
          </div>
        </section>

        {error ? <Toast error={error} notice="" /> : null}
        {loading ? <div className="h-1 bg-slate-950" /> : null}

        <article className="rounded-md border border-line bg-white shadow-panel">
          {source ? (
            <>
              <div className="border-b border-line p-5">
                <div className="mb-3 flex items-center gap-2 text-sm font-semibold text-slate-700">
                  <FileText className="h-4 w-4" />
                  {source.source}
                </div>
                <p className="text-base leading-7 text-slate-800">{source.summary}</p>
              </div>
              <div className="whitespace-pre-line p-5 text-sm leading-7 text-slate-700">
                {source.detail || "暂无详情。"}
              </div>
            </>
          ) : (
            <p className="p-5 text-sm text-muted">没有找到这条来源。可能是日期、项目或来源类型已经变化。</p>
          )}
        </article>
      </div>
    </AppShell>
  );
}
