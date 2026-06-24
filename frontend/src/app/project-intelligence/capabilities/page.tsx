"use client";

import { Suspense, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ArrowLeft, CheckCircle2, ListChecks } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { Badge, ProjectContextBar, Toast } from "@/components/ui";
import { useProjectSelection } from "@/hooks/useProjectSelection";
import { getProjectMemory, type ProjectMemory } from "@/lib/api";
import { readSession } from "@/lib/auth";
import { capabilityBulletItems } from "@/lib/project-memory-display";

export default function CompletedCapabilitiesPage() {
  return (
    <Suspense fallback={<AppShell eyebrow="项目画像" title="能力清单"><div className="min-h-[calc(100vh-4rem)] bg-surface p-6"><div className="h-1 bg-slate-950" /></div></AppShell>}>
      <CompletedCapabilitiesContent />
    </Suspense>
  );
}

function CompletedCapabilitiesContent() {
  const searchParams = useSearchParams();
  const queryProjectId = searchParams.get("projectId") ?? "";
  const { projects, selectedProject, selectedProjectId, selectProject, loadingProjects, projectError } = useProjectSelection({ queryProjectId });
  const [memory, setMemory] = useState<ProjectMemory | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const capabilities = useMemo(() => capabilityBulletItems(memory?.completedCapabilities ?? ""), [memory?.completedCapabilities]);

  useEffect(() => {
    const session = readSession();
    if (!session || !selectedProjectId) {
      setMemory(null);
      setLoading(false);
      return;
    }

    setLoading(true);
    setError("");
    getProjectMemory(session.accessToken, selectedProjectId)
      .then(setMemory)
      .catch((exception) => setError(exception instanceof Error ? exception.message : "能力清单加载失败"))
      .finally(() => setLoading(false));
  }, [selectedProjectId]);

  return (
    <AppShell eyebrow="项目画像" title={selectedProject ? `${selectedProject.name} · 能力清单` : "能力清单"}>
      <div className="min-h-[calc(100vh-4rem)] bg-surface p-6">
        <ProjectContextBar
          actions={(
            <Link className="inline-flex items-center gap-1 rounded-md border border-line bg-white px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50" href={`/project-intelligence?projectId=${selectedProjectId}`}>
              <ArrowLeft className="h-4 w-4" />
              回到项目画像
            </Link>
          )}
          leadingExtras={(
            <>
              <Badge label={`${capabilities.length} 项能力`} tone={capabilities.length ? "success" : "warning"} />
              <Badge label={`版本 ${memory?.version ?? "-"}`} />
            </>
          )}
          onSelect={selectProject}
          projects={projects}
          selectedProjectId={selectedProjectId}
        />

        <section className="rounded-md border border-line bg-white shadow-panel">
          <div className="flex flex-wrap items-start justify-between gap-3 border-b border-line p-5">
            <div className="flex gap-3">
              <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-emerald-700 text-white">
                <ListChecks className="h-5 w-5" />
              </span>
              <div>
                <h2 className="text-xl font-semibold text-slate-950">已完成能力</h2>
                <p className="mt-1 text-sm leading-6 text-slate-600">这里展示可复用能力点，不展示 zip 或 Git 识别出的原始文件路径。</p>
              </div>
            </div>
            <span className="rounded-full bg-emerald-800 px-3 py-1 text-sm font-semibold text-white">{capabilities.length} 项</span>
          </div>

          <div className="grid gap-3 p-5 md:grid-cols-2">
            {capabilities.map((item, index) => (
              <article className="rounded-md border border-emerald-100 bg-emerald-50 p-4" key={item}>
                <div className="mb-2 flex items-center gap-2">
                  <CheckCircle2 className="h-4 w-4 text-emerald-700" />
                  <span className="text-xs font-semibold text-emerald-800">能力 {index + 1}</span>
                </div>
                <p className="text-sm leading-6 text-emerald-950">{item}</p>
              </article>
            ))}
            {capabilities.length === 0 ? (
              <p className="rounded-md border border-line bg-slate-50 p-4 text-sm text-muted">暂无已确认能力。采纳结构化变更或运行项目分析后，会先形成候选，再经用户确认进入这里。</p>
            ) : null}
          </div>
        </section>

        {error || projectError ? <Toast error={error || projectError} notice="" /> : null}
        {loading || loadingProjects ? <div className="fixed inset-x-0 bottom-0 h-1 bg-slate-950" /> : null}
      </div>
    </AppShell>
  );
}
