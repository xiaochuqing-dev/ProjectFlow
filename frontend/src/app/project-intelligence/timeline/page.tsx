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

export default function ProjectTimelinePage() {
  return (
    <Suspense fallback={<AppShell eyebrow="项目成长记录" title="成长时间线"><div className="min-h-[calc(100vh-4rem)] bg-surface p-6"><div className="h-1 bg-slate-950" /></div></AppShell>}>
      <ProjectTimelinePageContent />
    </Suspense>
  );
}

function ProjectTimelinePageContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const queryProjectId = searchParams.get("projectId") ?? "";
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState(queryProjectId);
  const [records, setRecords] = useState<ProjectEvolutionRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const project = useMemo(() => projects.find((item) => item.id === selectedProjectId), [projects, selectedProjectId]);
  const timelineItems = useMemo(() => records.map(toTimelineItem), [records]);

  useEffect(() => {
    const session = readSession();
    if (!session) {
      setError("请先登录后再查看成长时间线。");
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
      .catch((exception) => setError(exception instanceof Error ? exception.message : "成长时间线加载失败"))
      .finally(() => setLoading(false));
  }, [selectedProjectId]);

  return (
    <AppShell eyebrow="项目成长记录" title={project ? `${project.name} · 成长时间线` : "成长时间线"}>
      <div className="min-h-[calc(100vh-4rem)] bg-surface p-6">
        <Header onBack={() => router.back()} projectId={selectedProjectId} title="成长时间线" />
        {error ? <div className="mb-5 rounded-md border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">{error}</div> : null}
        {loading ? <div className="h-1 bg-slate-950" /> : null}
        <ResourceTimeline emptyText="暂无成长记录。采纳结构化变更后会在这里形成时间线。" items={timelineItems} title={`按时间沉淀的项目变化 · ${records.length} 条`} />
      </div>
    </AppShell>
  );
}

function Header({ onBack, projectId, title }: { onBack: () => void; projectId: string; title: string }) {
  return (
    <section className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-md border border-line bg-white p-4 shadow-panel">
      <div>
        <button className="mb-2 inline-flex items-center gap-1 text-sm font-semibold text-slate-600 hover:text-slate-950" onClick={onBack} type="button">
          <ArrowLeft className="h-4 w-4" />
          返回上一步
        </button>
        <h2 className="text-xl font-semibold text-slate-950">{title}</h2>
      </div>
      <Link className="rounded-md border border-line px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50" href={`/project-intelligence?projectId=${projectId}`}>
        回到项目画像
      </Link>
    </section>
  );
}

function toTimelineItem(record: ProjectEvolutionRecord): ResourceTimelineItem {
  const detail = [
    sectionLine("变化内容", record.detectedChanges),
    sectionLine("关键成果", record.keyAchievements),
    sectionLine("问题风险", record.keyIssues),
    sectionLine("技术决策", record.technicalDecisions),
    sectionLine("经验沉淀", record.developerLearnings),
    sectionLine("下一步", record.nextSteps),
  ].filter(Boolean).join("\n\n");
  return {
    id: record.id,
    title: record.summary || "项目成长记录",
    summary: record.detectedChanges || record.keyAchievements || "已记录项目变化。",
    date: record.createdAt,
    type: "成长记录",
    status: "已入档",
    source: record.materialId ? "项目材料" : "项目档案",
    meta: record.materialId ? `materialId: ${record.materialId}` : undefined,
    detail,
  };
}

function sectionLine(label: string, value: string) {
  return value ? `${label}\n${value}` : "";
}
