"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { BookOpenText, CalendarDays, CheckCircle2, Clock3, FilePenLine, History, RefreshCw, Save, ShieldAlert } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import {
  createDevLog,
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
import { rememberSelectedProjectId, resolveSelectedProjectId } from "@/lib/project-selection";

export default function DevLogsPage() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [logs, setLogs] = useState<DevLog[]>([]);
  const [evolutionRecords, setEvolutionRecords] = useState<ProjectEvolutionRecord[]>([]);
  const [memory, setMemory] = useState<ProjectMemory | null>(null);
  const [selectedProjectId, setSelectedProjectId] = useState("");
  const [reviewDate, setReviewDate] = useState(new Date().toISOString().slice(0, 10));
  const [draftTitle, setDraftTitle] = useState("");
  const [draftContent, setDraftContent] = useState("");
  const [minutesSpent, setMinutesSpent] = useState(30);
  const [blocked, setBlocked] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const selectedProject = useMemo(
    () => projects.find((project) => project.id === selectedProjectId),
    [projects, selectedProjectId],
  );
  const dayLogs = logs.filter((log) => log.logDate === reviewDate);
  const dayEvolution = evolutionRecords.filter((record) => record.createdAt.slice(0, 10) === reviewDate);
  const activeTasks = tasks.filter((task) => task.status !== "DONE");
  const completedTasks = tasks.filter((task) => task.status === "DONE");
  const totalMinutes = logs.reduce((total, log) => total + log.minutesSpent, 0);

  useEffect(() => {
    const session = readSession();
    if (!session) {
      return;
    }

    setLoading(true);
    listProjects(session.accessToken)
      .then((items) => {
        setProjects(items);
        setSelectedProjectId(resolveSelectedProjectId(items));
      })
      .catch((exception) => setError(exception instanceof Error ? exception.message : "项目加载失败"))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    refreshProjectContext(selectedProjectId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedProjectId]);

  useEffect(() => {
    const title = `${reviewDate} 每日回顾`;
    setDraftTitle(title);
    setDraftContent(buildDailyDraft(selectedProject, memory, dayLogs, dayEvolution, activeTasks, completedTasks));
    setBlocked(dayLogs.some((log) => log.blocked) || Boolean(memory?.currentRisks && !memory.currentRisks.includes("暂无")));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reviewDate, selectedProjectId, logs, evolutionRecords, tasks, memory]);

  useEffect(() => {
    if (!notice && !error) {
      return;
    }
    const timeout = window.setTimeout(() => {
      setNotice("");
      setError("");
    }, 4200);
    return () => window.clearTimeout(timeout);
  }, [error, notice]);

  async function refreshProjectContext(projectId: string) {
    const session = readSession();
    if (!session || !projectId) {
      setTasks([]);
      setLogs([]);
      setEvolutionRecords([]);
      setMemory(null);
      return;
    }

    setLoading(true);
    setError("");
    try {
      const [devLogs, projectTasks, evolutionItems, memoryRecord] = await Promise.all([
        listDevLogs(session.accessToken, projectId),
        listTasks(session.accessToken, projectId),
        listProjectEvolutionRecords(session.accessToken, projectId),
        getProjectMemory(session.accessToken, projectId),
      ]);
      setLogs(devLogs);
      setTasks(projectTasks);
      setEvolutionRecords(evolutionItems);
      setMemory(memoryRecord);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "每日回顾数据加载失败");
    } finally {
      setLoading(false);
    }
  }

  async function handleSaveReview(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const session = readSession();
    if (!session || !selectedProjectId) {
      return;
    }

    setSaving(true);
    setError("");
    setNotice("");
    try {
      const log = await createDevLog(session.accessToken, selectedProjectId, {
        taskId: null,
        title: draftTitle,
        content: draftContent,
        category: "REVIEW",
        logDate: reviewDate,
        minutesSpent,
        blocked,
        tags: ["daily-review", "confirmed-assets"],
      });
      setLogs((current) => [log, ...current]);
      setNotice("每日回顾已保存，可用于成果输出。");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "每日回顾保存失败");
    } finally {
      setSaving(false);
    }
  }

  return (
    <AppShell eyebrow="按日期沉淀真实开发过程" title="每日回顾">
      <div className="min-h-[calc(100vh-4rem)] bg-surface p-8">
        <section className="mb-6 rounded-md border border-line bg-white p-4 shadow-panel">
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div className="flex flex-wrap items-center gap-3">
              <select
                className="h-10 min-w-72 rounded-md border border-line bg-white px-3 text-sm outline-none focus:border-slate-950"
                disabled={projects.length === 0}
                onChange={(event) => {
                  rememberSelectedProjectId(event.target.value);
                  setSelectedProjectId(event.target.value);
                }}
                value={selectedProjectId}
              >
                {projects.map((project) => (
                  <option key={project.id} value={project.id}>
                    {project.name}
                  </option>
                ))}
              </select>
              <input
                className="h-10 rounded-md border border-line bg-white px-3 text-sm outline-none focus:border-slate-950"
                onChange={(event) => setReviewDate(event.target.value)}
                type="date"
                value={reviewDate}
              />
            </div>
            <div className="flex flex-wrap items-center gap-2 text-xs">
              <StatusPill label="当日日志" value={dayLogs.length} />
              <StatusPill label="当日演进" value={dayEvolution.length} />
              <StatusPill label="累计小时" value={Math.round((totalMinutes / 60) * 10) / 10} />
            </div>
          </div>
        </section>

        <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_380px]">
          <form className="rounded-md border border-line bg-white shadow-panel" onSubmit={handleSaveReview}>
            <div className="flex items-center justify-between border-b border-line px-5 py-4">
              <div className="flex items-center gap-2">
                <FilePenLine className="h-4 w-4 text-slate-700" />
                <h2 className="font-semibold">可编辑回顾草稿</h2>
              </div>
              <button
                className="inline-flex items-center gap-2 rounded-md bg-slate-950 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
                disabled={saving || !selectedProjectId}
                type="submit"
              >
                {saving ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
                保存为当天记录
              </button>
            </div>
            <div className="space-y-4 p-5">
              <label className="block">
                <span className="mb-1 block text-sm font-medium text-slate-700">标题</span>
                <input
                  className="w-full rounded-md border border-line px-3 py-2 text-sm outline-none focus:border-slate-950"
                  onChange={(event) => setDraftTitle(event.target.value)}
                  value={draftTitle}
                />
              </label>
              <textarea
                className="min-h-[620px] w-full rounded-md border border-line bg-slate-50 px-4 py-3 text-sm leading-7 outline-none focus:border-slate-950"
                onChange={(event) => setDraftContent(event.target.value)}
                value={draftContent}
              />
              <div className="grid gap-3 md:grid-cols-[180px_1fr]">
                <label className="block">
                  <span className="mb-1 block text-sm font-medium text-slate-700">整理耗时（分钟）</span>
                  <input
                    className="w-full rounded-md border border-line px-3 py-2 text-sm outline-none focus:border-slate-950"
                    min="0"
                    onChange={(event) => setMinutesSpent(Number(event.target.value))}
                    type="number"
                    value={minutesSpent}
                  />
                </label>
                <label className="flex items-end gap-2 rounded-md border border-line bg-slate-50 px-3 py-2 text-sm text-slate-700">
                  <input checked={blocked} className="h-4 w-4 accent-slate-950" onChange={(event) => setBlocked(event.target.checked)} type="checkbox" />
                  今日存在风险或阻塞
                </label>
              </div>
            </div>
          </form>

          <aside className="space-y-5">
            <SourcePanel
              icon={<History className="h-4 w-4 text-slate-700" />}
              title="大变化"
              empty="暂无当日演进记录。"
              items={dayEvolution.map((record) => record.detectedChanges || record.summary)}
            />
            <SourcePanel
              icon={<CheckCircle2 className="h-4 w-4 text-emerald-600" />}
              title="任务证据"
              empty="暂无任务证据。"
              items={[
                ...completedTasks.slice(0, 4).map((task) => `已完成：${task.title}`),
                ...activeTasks.slice(0, 4).map((task) => `进行中：${task.title}`),
              ]}
            />
            <SourcePanel
              icon={<ShieldAlert className="h-4 w-4 text-amber-700" />}
              title="风险与决策"
              empty="暂无已确认风险或决策。"
              items={[memory?.currentRisks, memory?.technicalDecisions].filter(Boolean) as string[]}
            />
            <section className="rounded-md border border-line bg-white shadow-panel">
              <div className="flex items-center gap-2 border-b border-line px-5 py-4">
                <BookOpenText className="h-4 w-4 text-slate-700" />
                <h2 className="font-semibold">已保存回顾</h2>
              </div>
              <div className="divide-y divide-line">
                {logs.slice(0, 6).map((log) => (
                  <article className="p-4 text-sm" key={log.id}>
                    <div className="mb-2 flex items-center justify-between gap-3">
                      <p className="font-medium text-slate-950">{log.title}</p>
                      <span className="flex items-center gap-1 text-xs text-muted">
                        <CalendarDays className="h-3.5 w-3.5" />
                        {log.logDate}
                      </span>
                    </div>
                    <p className="line-clamp-3 whitespace-pre-line leading-5 text-slate-600">{log.content}</p>
                    <p className="mt-2 flex items-center gap-1 text-xs text-muted">
                      <Clock3 className="h-3.5 w-3.5" />
                      {log.minutesSpent} 分钟
                    </p>
                  </article>
                ))}
                {logs.length === 0 ? <p className="p-5 text-sm text-muted">暂无已保存回顾。</p> : null}
              </div>
            </section>
          </aside>
        </div>

        {error ? <div className="fixed bottom-5 left-1/2 z-50 -translate-x-1/2 rounded-md border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 shadow-panel">{error}</div> : null}
        {notice ? <div className="fixed bottom-5 left-1/2 z-50 -translate-x-1/2 rounded-md border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700 shadow-panel">{notice}</div> : null}
        {loading ? <div className="fixed inset-x-0 bottom-0 h-1 bg-slate-950" /> : null}
      </div>
    </AppShell>
  );
}

function SourcePanel({ icon, title, items, empty }: { icon: ReactNode; title: string; items: string[]; empty: string }) {
  return (
    <section className="rounded-md border border-line bg-white shadow-panel">
      <div className="flex items-center gap-2 border-b border-line px-5 py-4">
        {icon}
        <h2 className="font-semibold">{title}</h2>
      </div>
      <div className="space-y-3 p-5 text-sm leading-6 text-slate-600">
        {items.length ? items.slice(0, 6).map((item, index) => <p className="whitespace-pre-line" key={`${title}-${index}`}>{item}</p>) : <p className="text-muted">{empty}</p>}
      </div>
    </section>
  );
}

function StatusPill({ label, value }: { label: string; value: string | number }) {
  return <span className="rounded-md bg-slate-100 px-2.5 py-1 text-slate-600">{label} {value}</span>;
}

function buildDailyDraft(
  project: Project | undefined,
  memory: ProjectMemory | null,
  logs: DevLog[],
  evolutionRecords: ProjectEvolutionRecord[],
  activeTasks: TaskItem[],
  completedTasks: TaskItem[],
) {
  const bigChanges = evolutionRecords.map((record) => record.detectedChanges || record.summary).filter(Boolean);
  const logSummaries = logs.map((log) => `${log.logDate} ${log.title}${log.blocked ? "（阻塞）" : ""}\n${log.content}`).filter(Boolean);
  const blockedLogs = logs.filter((log) => log.blocked);
  const sourceLine = `来源：当天日志 ${logs.length} 条，项目演进记录 ${evolutionRecords.length} 条，进行中任务 ${activeTasks.length} 个，已完成任务 ${completedTasks.length} 个。`;
  return `# ${project?.name ?? "项目"} 每日回顾

> ${sourceLine}

## 今天确认的变化
${listOrFallback([...bigChanges, ...logSummaries], "暂无自动识别记录。")}

## 证据来源
${listOrFallback(bigChanges, "暂无大变化。")}

## 任务状态
进行中：
${listOrFallback(activeTasks.slice(0, 6).map((task) => `${task.title}：${task.description || task.status}`), "暂无进行中任务。")}

已完成：
${listOrFallback(completedTasks.slice(0, 6).map((task) => task.title), "暂无当天可复核的完成任务。")}

## 经验沉淀
${memory?.developerLearnings || "暂无已确认经验。"}

## 风险、阻塞与决策
阻塞：
${listOrFallback(blockedLogs.map((log) => `${log.title}：${log.content}`), "暂无当天阻塞。")}

已确认风险：
${memory?.currentRisks || "暂无已确认风险。"}

技术决策：
${memory?.technicalDecisions || "暂无技术决策。"}

## 项目档案更新
已完成能力：
${memory?.completedCapabilities || "暂无已确认能力。"}

进行中能力：
${memory?.inProgressCapabilities || "暂无进行中能力。"}

## 明天继续
${listOrFallback([
    memory?.nextStepSuggestions || "",
    ...activeTasks.slice(0, 5).map((task) => task.title),
    ...(completedTasks.length ? [`复核 ${completedTasks.length} 个已完成任务是否可沉淀为成果素材。`] : []),
  ].filter(Boolean), "暂无下一步建议。")}`;
}

function listOrFallback(items: string[], fallback: string) {
  const cleanItems = items.map((item) => item.trim()).filter(Boolean);
  if (cleanItems.length === 0) {
    return `- ${fallback}`;
  }
  return cleanItems.map((item) => `- ${item.replace(/\n/g, "\n  ")}`).join("\n");
}
