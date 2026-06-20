"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import { Plus, Sparkles, Trash2 } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { createProject, deleteProject, listProjects, type Project } from "@/lib/api";
import { readSession } from "@/lib/auth";

export default function ProjectsPage() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [error, setError] = useState("");
  const [creating, setCreating] = useState(false);
  const [deletingProjectId, setDeletingProjectId] = useState("");

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

    const form = event.currentTarget;
    const formData = new FormData(form);
    setCreating(true);
    setError("");
    try {
      const project = await createProject(session.accessToken, {
        name: String(formData.get("name")),
        description: String(formData.get("description")) || "暂未填写项目简介，可以后续补充。",
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
      form.reset();
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "项目创建失败");
    } finally {
      setCreating(false);
    }
  }

  async function handleDeleteProject(project: Project) {
    const session = readSession();
    if (!session) {
      return;
    }
    const confirmed = window.confirm(`删除 ProjectFlow 中的项目“${project.name}”？这只会删除 ProjectFlow 保存的数据，不会删除你磁盘上的真实源码文件夹。`);
    if (!confirmed) {
      return;
    }

    setDeletingProjectId(project.id);
    setError("");
    try {
      await deleteProject(session.accessToken, project.id);
      setProjects((current) => current.filter((item) => item.id !== project.id));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "项目删除失败");
    } finally {
      setDeletingProjectId("");
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
              <h2 className="font-semibold">快速开始</h2>
              <p className="text-sm text-muted">只填项目名也可以先开始。</p>
            </div>
          </div>
          <form className="space-y-4" onSubmit={handleCreate}>
            <label className="block">
              <span className="mb-2 block text-sm font-medium text-slate-700">项目名称</span>
              <input
                className="w-full rounded-lg border border-line px-3 py-2 text-sm outline-none focus:border-brand"
                name="name"
                placeholder="例如：英语写作训练工具"
                required
              />
            </label>
            <textarea
              className="min-h-20 w-full rounded-lg border border-line px-3 py-2 text-sm outline-none focus:border-brand"
              name="description"
              placeholder="可选：一句话说明这个项目想解决什么问题"
            />
            <details className="rounded-lg border border-line bg-slate-50 p-4">
              <summary className="cursor-pointer text-sm font-medium text-slate-700">补充信息，可之后再填</summary>
              <div className="mt-4 space-y-3">
                <input className="w-full rounded-lg border border-line bg-white px-3 py-2 text-sm outline-none focus:border-brand" name="techStack" placeholder="技术栈，可选，例如 Next.js, Spring Boot" />
                <input className="w-full rounded-lg border border-line bg-white px-3 py-2 text-sm outline-none focus:border-brand" name="repoUrl" placeholder="仓库链接，可选，没有 GitHub 也可以留空" />
              </div>
            </details>
            {error ? <p className="text-sm text-rose-600">{error}</p> : null}
            <button className="w-full rounded-lg bg-brand px-4 py-2.5 text-sm font-semibold text-white hover:bg-blue-600 disabled:opacity-60" disabled={creating} type="submit">
              {creating ? "创建中..." : "先创建，后面再完善"}
            </button>
          </form>
          <div className="mt-5 rounded-lg bg-blue-50 p-4 text-sm leading-6 text-blue-800">
            <div className="mb-1 flex items-center gap-2 font-semibold">
              <Sparkles className="h-4 w-4" />
              不确定技术栈也没关系
            </div>
            先把项目建起来，后续在任务、日志和复盘里慢慢补充，系统会根据真实记录生成材料。
          </div>
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
              <article className="rounded-lg border border-line p-4 transition hover:border-blue-200 hover:bg-blue-50/40" key={project.id}>
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <Link className="font-semibold hover:text-brand" href={`/projects/${project.id}`}>{project.name}</Link>
                    <p className="mt-1 line-clamp-2 text-sm text-muted">{project.description || "暂未填写项目简介"}</p>
                  </div>
                  <div className="flex shrink-0 items-center gap-2">
                    <span className="rounded-full bg-blue-50 px-3 py-1 text-xs font-medium text-brand">{project.status}</span>
                    <button
                      className="inline-flex items-center gap-1 rounded-md border border-rose-200 px-2.5 py-1 text-xs font-semibold text-rose-700 hover:bg-rose-50 disabled:opacity-50"
                      disabled={deletingProjectId === project.id}
                      onClick={() => handleDeleteProject(project)}
                      title="删除当前 ProjectFlow 项目记录，不删除本地真实源码文件夹。"
                      type="button"
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                      {deletingProjectId === project.id ? "删除中" : "删除"}
                    </button>
                  </div>
                </div>
                {project.techStack.length > 0 ? (
                  <div className="mt-4 flex flex-wrap gap-2">
                    {project.techStack.map((item) => (
                      <span className="rounded-md bg-slate-100 px-2 py-1 text-xs text-slate-600" key={item}>{item}</span>
                    ))}
                  </div>
                ) : null}
              </article>
            ))}
            {projects.length === 0 ? (
              <div className="rounded-lg border border-dashed border-line p-8 text-center text-sm text-muted">
                暂无项目。输入一个项目名就可以开始，不需要一次填完所有信息。
              </div>
            ) : null}
          </div>
        </section>
      </div>
    </AppShell>
  );
}
