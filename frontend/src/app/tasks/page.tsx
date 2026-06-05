"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { ArrowLeft, ArrowRight, CalendarDays, Flag, Layers3, Plus, RefreshCw } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import {
  createTask,
  listProjects,
  listTasks,
  updateTaskStatus,
  type Project,
  type TaskItem,
  type TaskPriority,
  type TaskStatus,
} from "@/lib/api";
import { readSession } from "@/lib/auth";

const columns: TaskStatus[] = ["BACKLOG", "TODO", "IN_PROGRESS", "REVIEW", "DONE"];

const statusLabels: Record<TaskStatus, string> = {
  BACKLOG: "待梳理",
  TODO: "待执行",
  IN_PROGRESS: "进行中",
  REVIEW: "待验收",
  DONE: "已完成",
};

const priorityLabels: Record<TaskPriority, string> = {
  LOW: "低",
  MEDIUM: "中",
  HIGH: "高",
};

const priorityStyles: Record<TaskPriority, string> = {
  LOW: "bg-emerald-50 text-emerald-700",
  MEDIUM: "bg-amber-50 text-amber-700",
  HIGH: "bg-rose-50 text-rose-700",
};

const nextStatus: Partial<Record<TaskStatus, TaskStatus>> = {
  BACKLOG: "TODO",
  TODO: "IN_PROGRESS",
  IN_PROGRESS: "REVIEW",
  REVIEW: "DONE",
};

const previousStatus: Partial<Record<TaskStatus, TaskStatus>> = {
  IN_PROGRESS: "TODO",
  REVIEW: "IN_PROGRESS",
  DONE: "REVIEW",
};

export default function TasksPage() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState("");
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [movingTaskId, setMovingTaskId] = useState("");

  const selectedProject = useMemo(
    () => projects.find((project) => project.id === selectedProjectId),
    [projects, selectedProjectId],
  );

  const groupedTasks = useMemo(() => {
    return columns.reduce<Record<TaskStatus, TaskItem[]>>(
      (groups, status) => ({
        ...groups,
        [status]: tasks.filter((task) => task.status === status),
      }),
      {
        BACKLOG: [],
        TODO: [],
        IN_PROGRESS: [],
        REVIEW: [],
        DONE: [],
      },
    );
  }, [tasks]);

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
      setTasks([]);
      return;
    }

    const session = readSession();
    if (!session) {
      return;
    }

    setLoading(true);
    setError("");
    listTasks(session.accessToken, selectedProjectId)
      .then(setTasks)
      .catch((exception) => setError(exception instanceof Error ? exception.message : "任务加载失败"))
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
      const task = await createTask(session.accessToken, selectedProjectId, {
        title: String(formData.get("title")),
        description: String(formData.get("description")),
        status: "TODO",
        priority: String(formData.get("priority")) as TaskPriority,
        dueDate: String(formData.get("dueDate")) || null,
        tags: String(formData.get("tags"))
          .split(",")
          .map((item) => item.trim())
          .filter(Boolean),
      });
      setTasks((current) => [task, ...current]);
      form.reset();
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "任务创建失败");
    } finally {
      setCreating(false);
    }
  }

  async function moveTask(task: TaskItem, status: TaskStatus) {
    const session = readSession();
    if (!session) {
      return;
    }

    setMovingTaskId(task.id);
    setError("");
    try {
      const updatedTask = await updateTaskStatus(session.accessToken, task.id, status);
      setTasks((current) => current.map((item) => (item.id === updatedTask.id ? updatedTask : item)));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "状态更新失败");
    } finally {
      setMovingTaskId("");
    }
  }

  return (
    <AppShell eyebrow="任务工作台" title="任务看板">
      <div className="grid min-h-[calc(100vh-4rem)] gap-6 p-8 xl:grid-cols-[360px_1fr]">
        <section className="space-y-4">
          <div className="rounded-lg border border-line bg-white p-5 shadow-panel">
            <div className="mb-4 flex items-center gap-3">
              <div className="grid h-10 w-10 place-items-center rounded-xl bg-slate-900 text-white">
                <Layers3 className="h-5 w-5" />
              </div>
              <div>
                <h2 className="font-semibold">项目上下文</h2>
                <p className="text-sm text-muted">任务始终绑定到一个真实项目。</p>
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

            <div className="mt-4 rounded-lg bg-slate-50 p-4">
              <p className="text-sm font-medium">{selectedProject?.name ?? "暂无项目"}</p>
              <p className="mt-1 line-clamp-3 text-sm text-muted">
                {selectedProject?.description ?? "先在项目管理中创建一个项目，再拆解任务。"}
              </p>
            </div>
          </div>

          <div className="rounded-lg border border-line bg-white p-5 shadow-panel">
            <div className="mb-4 flex items-center gap-3">
              <div className="grid h-10 w-10 place-items-center rounded-xl bg-blue-50 text-brand">
                <Plus className="h-5 w-5" />
              </div>
              <div>
                <h2 className="font-semibold">快速加任务</h2>
                <p className="text-sm text-muted">只写标题即可，其他信息可以后补。</p>
              </div>
            </div>

            <form className="space-y-3" onSubmit={handleCreate}>
              <input
                className="w-full rounded-lg border border-line px-3 py-2 text-sm outline-none focus:border-brand"
                disabled={!selectedProjectId}
                name="title"
                placeholder="任务标题，例如：完成登录页布局"
                required
              />
              <textarea
                className="min-h-20 w-full rounded-lg border border-line px-3 py-2 text-sm outline-none focus:border-brand"
                disabled={!selectedProjectId}
                name="description"
                placeholder="可选：补充执行说明"
              />
              <details className="rounded-lg border border-line bg-slate-50 p-3">
                <summary className="cursor-pointer text-sm font-medium text-slate-700">优先级、日期、标签</summary>
                <div className="mt-3 space-y-3">
                  <div className="grid grid-cols-2 gap-3">
                    <select className="rounded-lg border border-line bg-white px-3 py-2 text-sm outline-none focus:border-brand" defaultValue="MEDIUM" disabled={!selectedProjectId} name="priority">
                      <option value="LOW">低优先级</option>
                      <option value="MEDIUM">中优先级</option>
                      <option value="HIGH">高优先级</option>
                    </select>
                    <input className="rounded-lg border border-line bg-white px-3 py-2 text-sm outline-none focus:border-brand" disabled={!selectedProjectId} name="dueDate" type="date" />
                  </div>
                  <input
                    className="w-full rounded-lg border border-line bg-white px-3 py-2 text-sm outline-none focus:border-brand"
                    disabled={!selectedProjectId}
                    name="tags"
                    placeholder="标签，可选，用英文逗号分隔"
                  />
                </div>
              </details>
              {error ? <p className="text-sm text-rose-600">{error}</p> : null}
              <button
                className="flex w-full items-center justify-center gap-2 rounded-lg bg-brand px-4 py-2.5 text-sm font-semibold text-white hover:bg-blue-600 disabled:opacity-60"
                disabled={creating || !selectedProjectId}
                type="submit"
              >
                {creating ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />}
                {creating ? "创建中..." : "添加到待执行"}
              </button>
            </form>
          </div>
        </section>

        <section className="min-w-0 rounded-lg border border-line bg-white p-5 shadow-panel">
          <div className="mb-5 flex items-center justify-between gap-4">
            <div>
              <h2 className="font-semibold">执行流转</h2>
              <p className="text-sm text-muted">从拆解、执行、验收到完成，记录每一步工程推进。</p>
            </div>
            <span className="rounded-full bg-slate-100 px-3 py-1 text-sm text-slate-600">{tasks.length} 个任务</span>
          </div>

          {loading ? (
            <div className="grid min-h-80 place-items-center rounded-lg border border-dashed border-line text-sm text-muted">
              加载任务中...
            </div>
          ) : projects.length === 0 ? (
            <div className="grid min-h-80 place-items-center rounded-lg border border-dashed border-line text-sm text-muted">
              暂无项目。请先进入项目管理创建项目。
            </div>
          ) : (
            <div className="grid gap-4 overflow-x-auto pb-2 xl:grid-cols-5">
              {columns.map((status) => (
                <div className="min-w-64 rounded-lg border border-line bg-slate-50/70 p-3" key={status}>
                  <div className="mb-3 flex items-center justify-between">
                    <h3 className="text-sm font-semibold">{statusLabels[status]}</h3>
                    <span className="rounded-full bg-white px-2 py-1 text-xs text-muted">{groupedTasks[status].length}</span>
                  </div>

                  <div className="space-y-3">
                    {groupedTasks[status].map((task) => {
                      const backStatus = previousStatus[task.status];
                      const forwardStatus = nextStatus[task.status];

                      return (
                        <article className="rounded-lg border border-line bg-white p-4 shadow-sm" key={task.id}>
                          <div className="mb-3 flex items-start justify-between gap-3">
                            <h4 className="text-sm font-semibold leading-5">{task.title}</h4>
                            <span className={`shrink-0 rounded-full px-2 py-1 text-xs ${priorityStyles[task.priority]}`}>
                              {priorityLabels[task.priority]}
                            </span>
                          </div>
                          {task.description ? <p className="line-clamp-3 text-sm text-muted">{task.description}</p> : null}
                          <div className="mt-3 flex flex-wrap gap-2">
                            {task.tags.map((tag) => (
                              <span className="rounded-md bg-slate-100 px-2 py-1 text-xs text-slate-600" key={tag}>
                                {tag}
                              </span>
                            ))}
                          </div>
                          <div className="mt-4 flex items-center justify-between gap-3 text-xs text-muted">
                            <span className="flex items-center gap-1">
                              <CalendarDays className="h-3.5 w-3.5" />
                              {task.dueDate ?? "未设截止"}
                            </span>
                            <span className="flex items-center gap-1">
                              <Flag className="h-3.5 w-3.5" />
                              {statusLabels[task.status]}
                            </span>
                          </div>
                          <div className="mt-4 flex items-center justify-between gap-2">
                            <button
                              className="grid h-8 w-8 place-items-center rounded-lg border border-line text-slate-500 hover:border-blue-200 hover:bg-blue-50 disabled:cursor-not-allowed disabled:opacity-40"
                              disabled={!backStatus || movingTaskId === task.id}
                              onClick={() => backStatus && moveTask(task, backStatus)}
                              title="退回上一阶段"
                              type="button"
                            >
                              <ArrowLeft className="h-4 w-4" />
                            </button>
                            <button
                              className="grid h-8 w-8 place-items-center rounded-lg border border-line text-slate-500 hover:border-blue-200 hover:bg-blue-50 disabled:cursor-not-allowed disabled:opacity-40"
                              disabled={!forwardStatus || movingTaskId === task.id}
                              onClick={() => forwardStatus && moveTask(task, forwardStatus)}
                              title="推进到下一阶段"
                              type="button"
                            >
                              {movingTaskId === task.id ? <RefreshCw className="h-4 w-4 animate-spin" /> : <ArrowRight className="h-4 w-4" />}
                            </button>
                          </div>
                        </article>
                      );
                    })}

                    {groupedTasks[status].length === 0 ? (
                      <div className="rounded-lg border border-dashed border-line bg-white p-5 text-center text-sm text-muted">
                        暂无任务
                      </div>
                    ) : null}
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>
      </div>
    </AppShell>
  );
}
