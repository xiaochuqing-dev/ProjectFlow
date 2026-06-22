"use client";

import { Suspense, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { ArrowLeft } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { ResourceTimeline, type ResourceTimelineItem } from "@/components/ResourceTimeline";
import {
  listProjectAnalysisRecords,
  listProjects,
  type Project,
  type ProjectAnalysisRecord,
} from "@/lib/api";
import { readSession } from "@/lib/auth";
import { resolveSelectedProjectId } from "@/lib/project-selection";

export default function AnalysisRecordsPage() {
  return (
    <Suspense fallback={<AppShell eyebrow="项目分析历史" title="分析记录"><div className="min-h-[calc(100vh-4rem)] bg-surface p-6"><div className="h-1 bg-slate-950" /></div></AppShell>}>
      <AnalysisRecordsPageContent />
    </Suspense>
  );
}

function AnalysisRecordsPageContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const queryProjectId = searchParams.get("projectId") ?? "";
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState(queryProjectId);
  const [records, setRecords] = useState<ProjectAnalysisRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const project = useMemo(() => projects.find((item) => item.id === selectedProjectId), [projects, selectedProjectId]);
  const recordItems = useMemo(() => records.map(toAnalysisItem), [records]);

  useEffect(() => {
    const session = readSession();
    if (!session) {
      setError("请先登录后再查看分析记录。");
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
    listProjectAnalysisRecords(session.accessToken, selectedProjectId)
      .then(setRecords)
      .catch((exception) => setError(exception instanceof Error ? exception.message : "分析记录加载失败"))
      .finally(() => setLoading(false));
  }, [selectedProjectId]);

  return (
    <AppShell eyebrow="项目分析历史" title={project ? `${project.name} · 分析记录` : "分析记录"}>
      <div className="min-h-[calc(100vh-4rem)] bg-surface p-6">
        <section className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-md border border-line bg-white p-4 shadow-panel">
          <div>
            <button className="mb-2 inline-flex items-center gap-1 text-sm font-semibold text-slate-600 hover:text-slate-950" onClick={() => router.back()} type="button">
              <ArrowLeft className="h-4 w-4" />
              返回上一步
            </button>
            <h2 className="text-xl font-semibold text-slate-950">分析记录</h2>
            <p className="mt-1 text-sm text-muted">项目分析和文件分析的历史结果。</p>
          </div>
          <Link className="rounded-md border border-line px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50" href={`/project-intelligence?projectId=${selectedProjectId}`}>
            回到项目画像
          </Link>
        </section>

        {error ? <div className="mb-5 rounded-md border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">{error}</div> : null}
        {loading ? <div className="h-1 bg-slate-950" /> : null}

        <ResourceTimeline emptyText="暂无分析记录。运行项目分析或文件分析后会出现在这里。" items={recordItems} title={`记录 · ${records.length} 条`} />
        {records.length ? <p className="mt-4 text-sm text-muted">删除仍在详情页执行，避免在长期列表误删历史分析。</p> : null}
      </div>
    </AppShell>
  );
}

function toAnalysisItem(record: ProjectAnalysisRecord): ResourceTimelineItem {
  return {
    id: record.id,
    title: record.filePath ?? (record.recordType === "FILE" ? "文件分析" : "项目分析"),
    summary: record.summary || "暂无分析摘要。",
    date: record.createdAt,
    type: record.recordType === "FILE" ? "文件分析" : "项目分析",
    status: record.modelUsed ? "模型分析" : "本地规则",
    source: record.providerName ?? record.analysisSource,
    meta: record.filePath ?? undefined,
    detail: record.details || record.summary,
    href: `/project-analysis-records/${record.id}`,
  };
}
