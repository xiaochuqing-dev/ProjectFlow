"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { BookOpenText, CalendarDays, Clock3, FilePenLine, Layers3, Link2, Plus, RefreshCw, ShieldAlert } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import {
  createDevLog,
  listDevLogs,
  listProjects,
  listTasks,
  type DevLog,
  type DevLogCategory,
  type Project,
  type TaskItem,
} from "@/lib/api";
import { readSession } from "@/lib/auth";

const categories: DevLogCategory[] = ["FEATURE", "BUGFIX", "REFACTOR", "RESEARCH", "REVIEW", "DEPLOYMENT"];

const categoryLabels: Record<DevLogCategory, string> = {
  FEATURE: "功能开发",
  BUGFIX: "缺陷修复",
  REFACTOR: "结构调整",
  RESEARCH: "技术调研",
  REVIEW: "评审验收",
  DEPLOYMENT: "部署发布",
};

const categoryStyles: Record<DevLogCategory, string> = {
  FEATURE: "bg-blue-50 text-blue-700",
  BUGFIX: "bg-rose-50 text-rose-700",
  REFACTOR: "bg-violet-50 text-violet-700",
  RESEARCH: "bg-cyan-50 text-cyan-700",
  REVIEW: "bg-amber-50 text-amber-700",
  DEPLOYMENT: "bg-emerald-50 text-emerald-700",
};

export default function DevLogsPage() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [logs, setLogs] = useState<DevLog[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);

  const selectedProject = useMemo(
    () => projects.find((project) => project.id === selectedProjectId),
    [projects, selectedProjectId],
  );

  const taskNames = useMemo(() => {
    return tasks.reduce<Record<string, string>>((names, task) => ({ ...names, [task.id]: task.title }), {});
  }, [tasks]);

  const totalMinutes = useMemo(() => logs.reduce((total, log) => total + log.minutesSpent, 0), [logs]);
  const blockedCount = useMemo(() => logs.filter((log) => log.blocked).length, [logs]);
  const today = new Date().toISOString().slice(0, 10);

  useEffect(() => {
    const session = readSession();
    if (!session) {
      return;
    }

    setLoading(true);
    listProjects(session.accessToken)
      .then((items) => {
        setProjects(items);
        setSelectedProjectId((current) => current || items[0]?.id || "");
      })
      .catch((exception) => setError(exception instanceof Error ? exception.message : "项目加载失败"))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (!selectedProjectId) {
      setLogs([]);
      setTasks([]);
      return;
    }

    const session = readSession();
    if (!session) {
      return;
    }

    setLoading(true);
    setError("");
    Promise.all([
      listDevLogs(session.accessToken, selectedProjectId),
      listTasks(session.accessToken, selectedProjectId),
    ])
      .then(([devLogs, projectTasks]) => {
        setLogs(devLogs);
        setTasks(projectTasks);
      })
      .catch((exception) => setError(exception instanceof Error ? exception.message : "开发日志加载失败"))
      .finally(() => setLoading(false));
  }, [selectedProjectId]);

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const session = readSession();
    if (!session || !selectedProjectId) {
      return;
    }

    const form = event.currentTarget;
    const formData = new FormData(form);
    setCreating(true);
    setError("");
    try {
      const log = await createDevLog(session.accessToken, selectedProjectId, {
        taskId: String(formData.get("taskId")) || null,
        title: String(formData.get("title")),
        content: String(formData.get("content")),
        category: String(formData.get("category")) as DevLogCategory,
        logDate: String(formData.get("logDate")) || today,
        minutesSpent: Number(formData.get("minutesSpent") || 0),
        blocked: formData.get("blocked") === "on",
        tags: String(formData.get("tags"))
          .split(",")
          .map((item) => item.trim())
          .filter(Boolean),
      });
      setLogs((current) => [log, ...current]);
      form.reset();
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "开发日志创建失败");
    } finally {
      setCreating(false);
    }
  }

  return (
    <AppShell eyebrow="过程沉淀" title="开发日志">
      <div className="grid min-h-[calc(100vh-4rem)] gap-6 p-8 xl:grid-cols-[380px_1fr]">
        <section className="space-y-4">
          <div className="rounded-lg border border-line bg-white p-5 shadow-panel">
            <div className="mb-4 flex items-center gap-3">
              <div className="grid h-10 w-10 place-items-center rounded-xl bg-slate-900 text-white">
                <Layers3 className="h-5 w-5" />
              </div>
              <div>
                <h2 className="font-semibold">记录范围</h2>
                <p className="text-sm text-muted">按项目沉淀开发过程。</p>
              </div>
            </div>
            <select
              className="w-full rounded-lg border border-line bg-white px-3 py-2 text-sm outline-none focus:border-brand"
              disabled={projects.length === 0}
              onChange={(event) => setSelectedProjectId(event.target.value)}
              value={selectedProjectId}
            >
              {projects.map((project) => (
                <option key={project.id} value={project.id}>
                  {project.name}
                </option>
              ))}
            </select>
            <div className="mt-4 grid grid-cols-3 gap-2 text-center">
              <div className="rounded-lg bg-slate-50 p-3">
                <p className="text-lg font-semibold">{logs.length}</p>
                <p className="text-xs text-muted">日志</p>
              </div>
              <div className="rounded-lg bg-slate-50 p-3">
                <p className="text-lg font-semibold">{Math.round(totalMinutes / 60 * 10) / 10}</p>
                <p className="text-xs text-muted">小时</p>
              </div>
              <div className="rounded-lg bg-slate-50 p-3">
                <p className="text-lg font-semibold">{blockedCount}</p>
                <p className="text-xs text-muted">阻塞</p>
              </div>
            </div>
          </div>

          <div className="rounded-lg border border-line bg-white p-5 shadow-panel">
            <div className="mb-4 flex items-center gap-3">
              <div className="grid h-10 w-10 place-items-center rounded-xl bg-blue-50 text-brand">
                <FilePenLine className="h-5 w-5" />
              </div>
              <div>
                <h2 className="font-semibold">快速写日志</h2>
                <p className="text-sm text-muted">标题和正文足够开始，分类信息可选。</p>
              </div>
            </div>

            <form className="space-y-3" onSubmit={handleCreate}>
              <input
                className="w-full rounded-lg border border-line px-3 py-2 text-sm outline-none focus:border-brand"
                disabled={!selectedProjectId}
                name="title"
                placeholder="日志标题，例如：完成任务看板第一版"
                required
              />
              <textarea
                className="min-h-28 w-full rounded-lg border border-line px-3 py-2 text-sm outline-none focus:border-brand"
                disabled={!selectedProjectId}
                name="content"
                placeholder="写今天完成了什么、遇到什么问题、下一步准备做什么"
                required
              />
              <details className="rounded-lg border border-line bg-slate-50 p-3">
                <summary className="cursor-pointer text-sm font-medium text-slate-700">分类、关联任务、日期和标签</summary>
                <div className="mt-3 space-y-3">
                  <div className="grid grid-cols-2 gap-3">
                    <select className="rounded-lg border border-line bg-white px-3 py-2 text-sm outline-none focus:border-brand" defaultValue="FEATURE" disabled={!selectedProjectId} name="category">
                      {categories.map((category) => (
                        <option key={category} value={category}>
                          {categoryLabels[category]}
                        </option>
                      ))}
                    </select>
                    <select className="rounded-lg border border-line bg-white px-3 py-2 text-sm outline-none focus:border-brand" disabled={!selectedProjectId} name="taskId">
                      <option value="">项目级日志</option>
                      {tasks.map((task) => (
                        <option key={task.id} value={task.id}>
                          {task.title}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <input className="rounded-lg border border-line bg-white px-3 py-2 text-sm outline-none focus:border-brand" defaultValue={today} disabled={!selectedProjectId} name="logDate" type="date" />
                    <input className="rounded-lg border border-line bg-white px-3 py-2 text-sm outline-none focus:border-brand" defaultValue="60" disabled={!selectedProjectId} min="0" name="minutesSpent" type="number" />
                  </div>
                  <input
                    className="w-full rounded-lg border border-line bg-white px-3 py-2 text-sm outline-none focus:border-brand"
                    disabled={!selectedProjectId}
                    name="tags"
                    placeholder="标签，可选，用英文逗号分隔"
                  />
                  <label className="flex items-center gap-2 rounded-lg border border-line bg-white px-3 py-2 text-sm text-slate-600">
                    <input className="h-4 w-4 accent-blue-600" disabled={!selectedProjectId} name="blocked" type="checkbox" />
                    存在阻塞或风险
                  </label>
                </div>
              </details>
              {error ? <p className="text-sm text-rose-600">{error}</p> : null}
              <button
                className="flex w-full items-center justify-center gap-2 rounded-lg bg-brand px-4 py-2.5 text-sm font-semibold text-white hover:bg-blue-600 disabled:opacity-60"
                disabled={creating || !selectedProjectId}
                type="submit"
              >
                {creating ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />}
                {creating ? "记录中..." : "保存这条日志"}
              </button>
            </form>
          </div>
        </section>

        <section className="min-w-0 rounded-lg border border-line bg-white p-5 shadow-panel">
          <div className="mb-5 flex items-center justify-between gap-4">
            <div>
              <h2 className="font-semibold">过程时间线</h2>
              <p className="text-sm text-muted">
                {selectedProject?.name ?? "暂无项目"} 的开发过程、任务关联和阶段性证据。
              </p>
            </div>
            <span className="rounded-full bg-slate-100 px-3 py-1 text-sm text-slate-600">{logs.length} 条记录</span>
          </div>

          {loading ? (
            <div className="grid min-h-96 place-items-center rounded-lg border border-dashed border-line text-sm text-muted">
              加载日志中...
            </div>
          ) : projects.length === 0 ? (
            <div className="grid min-h-96 place-items-center rounded-lg border border-dashed border-line text-sm text-muted">
              暂无项目。请先进入项目管理创建项目。
            </div>
          ) : logs.length === 0 ? (
            <div className="grid min-h-96 place-items-center rounded-lg border border-dashed border-line text-sm text-muted">
              暂无开发日志。可以先记录今天完成的第一项工程推进。
            </div>
          ) : (
            <div className="space-y-4">
              {logs.map((log) => (
                <article className="rounded-lg border border-line p-5 transition hover:border-blue-200 hover:bg-blue-50/30" key={log.id}>
                  <div className="flex flex-wrap items-start justify-between gap-4">
                    <div className="min-w-0">
                      <div className="mb-2 flex flex-wrap items-center gap-2">
                        <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${categoryStyles[log.category]}`}>
                          {categoryLabels[log.category]}
                        </span>
                        {log.blocked ? (
                          <span className="flex items-center gap-1 rounded-full bg-rose-50 px-2.5 py-1 text-xs font-medium text-rose-700">
                            <ShieldAlert className="h-3.5 w-3.5" />
                            有阻塞
                          </span>
                        ) : null}
                      </div>
                      <h3 className="text-base font-semibold">{log.title}</h3>
                      <p className="mt-2 whitespace-pre-line text-sm leading-6 text-slate-600">{log.content}</p>
                    </div>
                    <div className="grid min-w-32 gap-2 text-sm text-muted">
                      <span className="flex items-center gap-2">
                        <CalendarDays className="h-4 w-4" />
                        {log.logDate}
                      </span>
                      <span className="flex items-center gap-2">
                        <Clock3 className="h-4 w-4" />
                        {log.minutesSpent} 分钟
                      </span>
                    </div>
                  </div>

                  <div className="mt-4 flex flex-wrap items-center gap-2">
                    <span className="flex items-center gap-1 rounded-md bg-slate-100 px-2 py-1 text-xs text-slate-600">
                      <Link2 className="h-3.5 w-3.5" />
                      {log.taskId ? taskNames[log.taskId] ?? "已关联任务" : "项目级日志"}
                    </span>
                    {log.tags.map((tag) => (
                      <span className="rounded-md bg-slate-100 px-2 py-1 text-xs text-slate-600" key={tag}>
                        {tag}
                      </span>
                    ))}
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>
      </div>
    </AppShell>
  );
}
