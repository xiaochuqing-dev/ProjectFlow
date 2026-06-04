"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import { Plus } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { createProject, listProjects, type Project } from "@/lib/api";
import { readSession } from "@/lib/auth";

export default function ProjectsPage() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [error, setError] = useState("");
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    const session = readSession();
    if (!session) {
      return;
    }
    listProjects(session.accessToken)
      .then(setProjects)
      .catch((exception) => setError(exception instanceof Error ? exception.message : "项目加载失败"));
  }, []);

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const session = readSession();
    if (!session) {
      return;
    }

    const formData = new FormData(event.currentTarget);
    setCreating(true);
    setError("");
    try {
      const project = await createProject(session.accessToken, {
        name: String(formData.get("name")),
        description: String(formData.get("description")),
        status: "BUILDING",
        techStack: String(formData.get("techStack"))
          .split(",")
          .map((item) => item.trim())
          .filter(Boolean),
        repoUrl: String(formData.get("repoUrl")),
        startDate: new Date().toISOString().slice(0, 10),
        endDate: null,
      });
      setProjects((current) => [project, ...current]);
      event.currentTarget.reset();
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "项目创建失败");
    } finally {
      setCreating(false);
    }
  }

  return (
    <AppShell eyebrow="项目空间" title="项目管理">
      <div className="grid gap-6 p-8 xl:grid-cols-[360px_1fr]">
        <section className="rounded-lg border border-line bg-white p-6 shadow-panel">
          <div className="mb-5 flex items-center gap-3">
            <div className="grid h-10 w-10 place-items-center rounded-xl bg-blue-50 text-brand">
              <Plus className="h-5 w-5" />
            </div>
            <div>
              <h2 className="font-semibold">创建项目</h2>
              <p className="text-sm text-muted">先沉淀真实项目，再生成复盘材料。</p>
            </div>
          </div>
          <form className="space-y-4" onSubmit={handleCreate}>
            <input className="w-full rounded-lg border border-line px-3 py-2 text-sm outline-none focus:border-brand" name="name" placeholder="项目名称" required />
            <textarea className="min-h-24 w-full rounded-lg border border-line px-3 py-2 text-sm outline-none focus:border-brand" name="description" placeholder="项目简介" required />
            <input className="w-full rounded-lg border border-line px-3 py-2 text-sm outline-none focus:border-brand" name="techStack" placeholder="技术栈，用英文逗号分隔" />
            <input className="w-full rounded-lg border border-line px-3 py-2 text-sm outline-none focus:border-brand" name="repoUrl" placeholder="GitHub 仓库链接" />
            {error ? <p className="text-sm text-rose-600">{error}</p> : null}
            <button className="w-full rounded-lg bg-brand px-4 py-2.5 text-sm font-semibold text-white hover:bg-blue-600 disabled:opacity-60" disabled={creating} type="submit">
              {creating ? "创建中..." : "创建项目"}
            </button>
          </form>
        </section>

        <section className="rounded-lg border border-line bg-white p-6 shadow-panel">
          <div className="mb-5 flex items-center justify-between">
            <div>
              <h2 className="font-semibold">项目列表</h2>
              <p className="text-sm text-muted">当前账号下的项目空间。</p>
            </div>
            <span className="rounded-full bg-slate-100 px-3 py-1 text-sm text-slate-600">{projects.length} 个项目</span>
          </div>

          <div className="space-y-3">
            {projects.map((project) => (
              <Link className="block rounded-lg border border-line p-4 transition hover:border-blue-200 hover:bg-blue-50/40" href={`/projects/${project.id}`} key={project.id}>
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <h3 className="font-semibold">{project.name}</h3>
                    <p className="mt-1 line-clamp-2 text-sm text-muted">{project.description}</p>
                  </div>
                  <span className="rounded-full bg-blue-50 px-3 py-1 text-xs font-medium text-brand">{project.status}</span>
                </div>
                <div className="mt-4 flex flex-wrap gap-2">
                  {project.techStack.map((item) => (
                    <span className="rounded-md bg-slate-100 px-2 py-1 text-xs text-slate-600" key={item}>{item}</span>
                  ))}
                </div>
              </Link>
            ))}
            {projects.length === 0 ? (
              <div className="rounded-lg border border-dashed border-line p-8 text-center text-sm text-muted">
                暂无项目。先创建 InsightWrite 2.0 作为第一个真实示例。
              </div>
            ) : null}
          </div>
        </section>
      </div>
    </AppShell>
  );
}
