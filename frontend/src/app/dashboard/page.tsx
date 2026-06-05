"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import {
  ArrowRight,
  BookOpenText,
  CheckCircle2,
  Clock3,
  FolderKanban,
  LayoutDashboard,
  ListChecks,
  Plus,
  Sparkles,
  SquareKanban,
} from "lucide-react";
import { AppShell } from "@/components/AppShell";
import {
  listDevLogs,
  listProjects,
  listTasks,
  type DevLog,
  type Project,
  type TaskItem,
  type TaskStatus,
} from "@/lib/api";
import { readSession } from "@/lib/auth";

const statusLabels: Record<TaskStatus, string> = {
  BACKLOG: "待梳理",
  TODO: "待执行",
  IN_PROGRESS: "进行中",
  REVIEW: "待验收",
  DONE: "已完成",
};

const statusOrder: TaskStatus[] = ["BACKLOG", "TODO", "IN_PROGRESS", "REVIEW", "DONE"];

const actionCards = [
  {
    title: "创建项目空间",
    description: "记录项目简介、技术栈和仓库链接，先把真实工程放进系统。",
    href: "/projects",
    cta: "管理项目",
    icon: FolderKanban,
    tone: "bg-blue-50 text-blue-700",
  },
  {
    title: "拆解任务看板",
    description: "把需求拆成待执行、进行中、待验收，让推进状态一眼清楚。",
    href: "/tasks",
    cta: "进入看板",
    icon: SquareKanban,
    tone: "bg-emerald-50 text-emerald-700",
  },
  {
    title: "沉淀开发日志",
    description: "记录每天的实现、取舍、阻塞和验证，形成可复盘的过程证据。",
    href: "/dev-logs",
    cta: "写日志",
    icon: BookOpenText,
    tone: "bg-amber-50 text-amber-700",
  },
  {
    title: "整理 AI 复盘",
    description: "汇总项目、任务和日志内容，后续生成阶段总结与展示材料。",
    href: "/ai-review",
    cta: "查看准备项",
    icon: Sparkles,
    tone: "bg-slate-900 text-white",
  },
];

export default function DashboardPage() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [logs, setLogs] = useState<DevLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    const session = readSession();
    if (!session) {
      return;
    }

    let cancelled = false;
    setLoading(true);
    setError("");

    listProjects(session.accessToken)
      .then(async (projectItems) => {
        const [taskGroups, logGroups] = await Promise.all([
          Promise.all(projectItems.map((project) => listTasks(session.accessToken, project.id).catch(() => []))),
          Promise.all(projectItems.map((project) => listDevLogs(session.accessToken, project.id).catch(() => []))),
        ]);

        if (cancelled) {
          return;
        }

        setProjects(projectItems);
        setTasks(taskGroups.flat());
        setLogs(logGroups.flat());
      })
      .catch((exception) => setError(exception instanceof Error ? exception.message : "工作台数据加载失败"))
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const taskStats = useMemo(() => {
    return statusOrder.reduce<Record<TaskStatus, number>>(
      (stats, status) => ({
        ...stats,
        [status]: tasks.filter((task) => task.status === status).length,
      }),
      {
        BACKLOG: 0,
        TODO: 0,
        IN_PROGRESS: 0,
        REVIEW: 0,
        DONE: 0,
      },
    );
  }, [tasks]);

  const activeTasks = taskStats.TODO + taskStats.IN_PROGRESS + taskStats.REVIEW;
  const doneRate = tasks.length === 0 ? 0 : Math.round((taskStats.DONE / tasks.length) * 100);
  const blockedLogs = logs.filter((log) => log.blocked).length;
  const recentLogs = [...logs]
    .sort((first, second) => `${second.logDate}${second.createdAt}`.localeCompare(`${first.logDate}${first.createdAt}`))
    .slice(0, 3);
  const latestProject = [...projects].sort((first, second) => second.updatedAt.localeCompare(first.updatedAt))[0];

  return (
    <AppShell
      actions={
        <Link
          className="flex items-center gap-2 rounded-full bg-brand px-4 py-2 text-sm font-semibold text-white hover:bg-blue-600"
          href="/projects"
        >
          <Plus className="h-4 w-4" />
          新建项目
        </Link>
      }
      eyebrow="项目工作台"
      title="总览"
    >
      <div className="space-y-6 p-8">
        <section className="rounded-lg border border-line bg-white p-6 shadow-panel">
          <div className="grid gap-6 xl:grid-cols-[1.1fr_0.9fr]">
            <div>
              <div className="mb-5 inline-flex items-center gap-2 rounded-full bg-blue-50 px-3 py-1 text-sm font-medium text-brand">
                <LayoutDashboard className="h-4 w-4" />
                登录后从这里开始
              </div>
              <h2 className="text-3xl font-semibold tracking-normal text-slate-950">
                把项目推进、任务执行和开发记录放到同一个工作流里。
              </h2>
              <p className="mt-4 max-w-3xl text-base leading-7 text-slate-600">
                先创建项目，再拆解任务，用开发日志记录每天的推进和风险。等数据沉淀起来后，AI
                复盘就能基于真实过程生成阶段总结和展示材料。
              </p>
              <div className="mt-6 flex flex-wrap gap-3">
                <Link
                  className="flex items-center gap-2 rounded-lg bg-slate-950 px-4 py-2.5 text-sm font-semibold text-white hover:bg-slate-800"
                  href={projects.length === 0 ? "/projects" : "/tasks"}
                >
                  {projects.length === 0 ? "先创建项目" : "继续推进任务"}
                  <ArrowRight className="h-4 w-4" />
                </Link>
                <Link
                  className="flex items-center gap-2 rounded-lg border border-line bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50"
                  href="/dev-logs"
                >
                  记录开发日志
                </Link>
              </div>
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              {[
                ["项目空间", projects.length, "个项目"],
                ["推进任务", activeTasks, "个待推进"],
                ["完成率", `${doneRate}%`, "任务完成"],
                ["风险记录", blockedLogs, "条阻塞"],
              ].map(([label, value, helper]) => (
                <div className="rounded-lg border border-line bg-slate-50 p-4" key={label as string}>
                  <p className="text-sm text-muted">{label as string}</p>
                  <p className="mt-2 text-2xl font-semibold text-slate-950">{value as string | number}</p>
                  <p className="mt-1 text-xs text-muted">{helper as string}</p>
                </div>
              ))}
            </div>
          </div>
        </section>

        {error ? (
          <div className="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
            {error}
          </div>
        ) : null}

        <section className="grid gap-4 xl:grid-cols-4">
          {actionCards.map((item) => {
            const Icon = item.icon;
            return (
              <Link
                className="group rounded-lg border border-line bg-white p-5 shadow-panel transition hover:-translate-y-0.5 hover:border-blue-200 hover:shadow-lg"
                href={item.href}
                key={item.title}
              >
                <div className={`mb-5 grid h-11 w-11 place-items-center rounded-xl ${item.tone}`}>
                  <Icon className="h-5 w-5" />
                </div>
                <h3 className="font-semibold text-slate-950">{item.title}</h3>
                <p className="mt-2 min-h-16 text-sm leading-6 text-slate-600">{item.description}</p>
                <span className="mt-5 flex items-center gap-2 text-sm font-semibold text-brand">
                  {item.cta}
                  <ArrowRight className="h-4 w-4 transition group-hover:translate-x-0.5" />
                </span>
              </Link>
            );
          })}
        </section>

        <section className="grid gap-6 xl:grid-cols-[0.9fr_1.1fr]">
          <div className="rounded-lg border border-line bg-white p-6 shadow-panel">
            <div className="mb-5 flex items-center justify-between gap-4">
              <div>
                <h2 className="font-semibold text-slate-950">任务分布</h2>
                <p className="text-sm text-muted">看清当前卡在哪个阶段。</p>
              </div>
              <ListChecks className="h-5 w-5 text-brand" />
            </div>

            <div className="space-y-4">
              {statusOrder.map((status) => {
                const count = taskStats[status];
                const width = tasks.length === 0 ? 0 : Math.max(8, Math.round((count / tasks.length) * 100));
                return (
                  <div key={status}>
                    <div className="mb-2 flex items-center justify-between text-sm">
                      <span className="font-medium text-slate-700">{statusLabels[status]}</span>
                      <span className="text-muted">{count}</span>
                    </div>
                    <div className="h-2 rounded-full bg-slate-100">
                      <div className="h-2 rounded-full bg-brand" style={{ width: `${width}%` }} />
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          <div className="rounded-lg border border-line bg-white p-6 shadow-panel">
            <div className="mb-5 flex items-center justify-between gap-4">
              <div>
                <h2 className="font-semibold text-slate-950">最近过程记录</h2>
                <p className="text-sm text-muted">用于后续复盘的最新证据。</p>
              </div>
              <Clock3 className="h-5 w-5 text-brand" />
            </div>

            {loading ? (
              <div className="grid min-h-48 place-items-center rounded-lg border border-dashed border-line text-sm text-muted">
                加载工作台数据中...
              </div>
            ) : projects.length === 0 ? (
              <div className="rounded-lg border border-dashed border-line p-6">
                <h3 className="font-semibold text-slate-950">还没有项目空间</h3>
                <p className="mt-2 text-sm leading-6 text-slate-600">
                  先创建一个真实项目，之后就可以在这里看到任务推进、日志沉淀和复盘准备情况。
                </p>
                <Link className="mt-4 inline-flex items-center gap-2 text-sm font-semibold text-brand" href="/projects">
                  创建第一个项目
                  <ArrowRight className="h-4 w-4" />
                </Link>
              </div>
            ) : recentLogs.length === 0 ? (
              <div className="rounded-lg border border-dashed border-line p-6">
                <h3 className="font-semibold text-slate-950">{latestProject?.name ?? "当前项目"} 还没有日志</h3>
                <p className="mt-2 text-sm leading-6 text-slate-600">
                  建议从今天完成的功能、遇到的阻塞、做出的技术取舍开始记录。
                </p>
                <Link className="mt-4 inline-flex items-center gap-2 text-sm font-semibold text-brand" href="/dev-logs">
                  写第一条日志
                  <ArrowRight className="h-4 w-4" />
                </Link>
              </div>
            ) : (
              <div className="space-y-3">
                {recentLogs.map((log) => (
                  <article className="rounded-lg border border-line p-4" key={log.id}>
                    <div className="mb-2 flex items-center justify-between gap-4">
                      <h3 className="truncate font-semibold text-slate-950">{log.title}</h3>
                      <span className="shrink-0 text-xs text-muted">{log.logDate}</span>
                    </div>
                    <p className="line-clamp-2 text-sm leading-6 text-slate-600">{log.content}</p>
                  </article>
                ))}
              </div>
            )}
          </div>
        </section>

        <section className="rounded-lg border border-line bg-slate-950 p-6 text-white" id="ai-review">
          <div className="flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <div className="mb-3 inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-1 text-sm text-blue-100">
                <Sparkles className="h-4 w-4" />
                AI 复盘准备
              </div>
              <h2 className="text-xl font-semibold">复盘不是单独写总结，而是从真实过程里提炼。</h2>
              <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-300">
                项目简介、任务流转、开发日志和阻塞记录越完整，后续生成的阶段复盘、作品集描述和面试讲述就越可靠。
              </p>
            </div>
            <div className="grid min-w-72 grid-cols-3 gap-3 text-center">
              {[
                ["项目", projects.length > 0],
                ["任务", tasks.length > 0],
                ["日志", logs.length > 0],
              ].map(([label, ready]) => (
                <div className="rounded-lg bg-white/8 p-3" key={label as string}>
                  <CheckCircle2 className={`mx-auto mb-2 h-5 w-5 ${ready ? "text-emerald-300" : "text-slate-500"}`} />
                  <p className="text-sm">{label as string}</p>
                </div>
              ))}
            </div>
          </div>
        </section>
      </div>
    </AppShell>
  );
}
