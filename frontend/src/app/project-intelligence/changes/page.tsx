"use client";

import { Suspense, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { ArrowLeft } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { ResourceTimeline, type ResourceTimelineItem } from "@/components/ResourceTimeline";
import { listProjectEvolutionRecords, listProjects, type Project, type ProjectEvolutionRecord } from "@/lib/api";
import { readSession } from "@/lib/auth";
import { resolveSelectedProjectId } from "@/lib/project-selection";

export default function ArchiveChangesPage() {
  return (
    <Suspense fallback={<AppShell eyebrow="项目沉淀更新记录" title="项目沉淀更新"><div className="min-h-[calc(100vh-4rem)] bg-surface p-6"><div className="h-1 bg-slate-950" /></div></AppShell>}>
      <ArchiveChangesPageContent />
    </Suspense>
  );
}

function ArchiveChangesPageContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const queryProjectId = searchParams.get("projectId") ?? "";
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState(queryProjectId);
  const [records, setRecords] = useState<ProjectEvolutionRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const project = useMemo(() => projects.find((item) => item.id === selectedProjectId), [projects, selectedProjectId]);
  const archiveItems = useMemo(() => records.map(toArchiveChangeItem), [records]);

  useEffect(() => {
    const session = readSession();
    if (!session) {
      setError("请先登录后再查看项目沉淀更新。");
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
    listProjectEvolutionRecords(session.accessToken, selectedProjectId)
      .then(setRecords)
      .catch((exception) => setError(exception instanceof Error ? exception.message : "项目沉淀更新加载失败"))
      .finally(() => setLoading(false));
  }, [selectedProjectId]);

  return (
    <AppShell eyebrow="项目沉淀更新记录" title={project ? `${project.name} · 项目沉淀更新` : "项目沉淀更新"}>
      <div className="min-h-[calc(100vh-4rem)] bg-surface p-6">
        <section className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-md border border-line bg-white p-4 shadow-panel">
          <div>
            <button className="mb-2 inline-flex items-center gap-1 text-sm font-semibold text-slate-600 hover:text-slate-950" onClick={() => router.back()} type="button">
              <ArrowLeft className="h-4 w-4" />
              返回上一步
            </button>
            <h2 className="text-xl font-semibold text-slate-950">项目沉淀更新</h2>
            <p className="mt-1 text-sm text-muted">展示项目沉淀每次更新改了哪些内容。</p>
          </div>
          <Link className="rounded-md border border-line px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50" href={`/project-intelligence?projectId=${selectedProjectId}`}>
            回到项目理解
          </Link>
        </section>

        {error ? <div className="mb-5 rounded-md border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">{error}</div> : null}
        {loading ? <div className="h-1 bg-slate-950" /> : null}

        <ResourceTimeline emptyText="暂无项目沉淀更新。确认建议沉淀后会出现记录。" items={archiveItems} title={`变化记录 · ${records.length} 条`} />
      </div>
    </AppShell>
  );
}

function toArchiveChangeItem(record: ProjectEvolutionRecord): ResourceTimelineItem {
  const detail = [
    block("变化内容", record.detectedChanges),
    block("关键成果", record.keyAchievements),
    block("问题风险", record.keyIssues),
    block("技术决策", record.technicalDecisions),
    block("经验沉淀", record.developerLearnings),
    block("下一步", record.nextSteps),
  ].filter(Boolean).join("\n\n");
  return {
    id: record.id,
    title: record.summary || "项目沉淀更新",
    summary: record.detectedChanges || record.keyAchievements || "本次更新已进入项目沉淀。",
    date: record.createdAt,
    type: record.technicalDecisions ? "技术决策" : record.keyIssues ? "风险变化" : "项目沉淀更新",
    status: "已入档",
    source: record.materialId ? "项目材料" : "用户确认",
    meta: record.materialId ? `materialId: ${record.materialId}` : undefined,
    detail,
  };
}

function block(label: string, value: string) {
  return value ? `${label}\n${value}` : "";
}
