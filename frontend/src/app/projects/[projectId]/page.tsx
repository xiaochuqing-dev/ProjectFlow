"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { AppShell } from "@/components/AppShell";
import { getProject, type Project } from "@/lib/api";
import { readSession } from "@/lib/auth";

export default function ProjectDetailPage() {
  const params = useParams<{ projectId: string }>();
  const [project, setProject] = useState<Project | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    const session = readSession();
    if (!session) {
      return;
    }
    getProject(session.accessToken, params.projectId)
      .then(setProject)
      .catch((exception) => setError(exception instanceof Error ? exception.message : "项目加载失败"));
  }, [params.projectId]);

  return (
    <AppShell eyebrow="项目详情" title={project?.name ?? "项目详情"}>
      <div className="p-8">
        {error ? <div className="rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">{error}</div> : null}
        {project ? (
          <section className="rounded-lg border border-line bg-white p-6 shadow-panel">
            <div className="mb-6 flex items-start justify-between gap-6">
              <div>
                <h2 className="text-xl font-semibold">{project.name}</h2>
                <p className="mt-2 max-w-3xl text-sm leading-6 text-muted">{project.description}</p>
              </div>
              <span className="rounded-full bg-blue-50 px-3 py-1 text-sm font-medium text-brand">{project.status}</span>
            </div>
            <div className="grid gap-4 md:grid-cols-3">
              <div className="rounded-lg border border-line bg-slate-50 p-4">
                <p className="text-sm text-muted">仓库</p>
                <p className="mt-2 truncate text-sm font-medium">{project.repoUrl || "未填写"}</p>
              </div>
              <div className="rounded-lg border border-line bg-slate-50 p-4">
                <p className="text-sm text-muted">开始日期</p>
                <p className="mt-2 text-sm font-medium">{project.startDate || "未填写"}</p>
              </div>
              <div className="rounded-lg border border-line bg-slate-50 p-4">
                <p className="text-sm text-muted">技术栈</p>
                <p className="mt-2 text-sm font-medium">{project.techStack.join(", ") || "未填写"}</p>
              </div>
            </div>
          </section>
        ) : null}
      </div>
    </AppShell>
  );
}
