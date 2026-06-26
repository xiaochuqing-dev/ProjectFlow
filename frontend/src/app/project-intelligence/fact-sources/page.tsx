"use client";

import { Suspense, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { ArrowLeft } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { ResourceTimeline, type ResourceTimelineItem } from "@/components/ResourceTimeline";
import { listProjectFactSources, listProjects, type Project, type ProjectFactSource } from "@/lib/api";
import { readSession } from "@/lib/auth";
import { resolveSelectedProjectId } from "@/lib/project-selection";

const fieldLabels: Record<string, string> = {
  positioning: "项目定位",
  currentStage: "当前阶段",
  completedCapabilities: "已完成能力",
  inProgressCapabilities: "进行中能力",
  currentRisks: "当前风险",
  technicalDecisions: "技术决策",
  developerLearnings: "经验沉淀",
  showcaseAssets: "可展示成果",
  nextStepSuggestions: "下一步目标",
};

export default function FactSourcesPage() {
  return (
    <Suspense fallback={<AppShell eyebrow="项目资产可信来源" title="可信依据"><div className="min-h-[calc(100vh-4rem)] bg-surface p-6"><div className="h-1 bg-slate-950" /></div></AppShell>}>
      <FactSourcesPageContent />
    </Suspense>
  );
}

function FactSourcesPageContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const queryProjectId = searchParams.get("projectId") ?? "";
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState(queryProjectId);
  const [sources, setSources] = useState<ProjectFactSource[]>([]);
  const [activeField, setActiveField] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const project = useMemo(() => projects.find((item) => item.id === selectedProjectId), [projects, selectedProjectId]);
  const fields = useMemo(() => Array.from(new Set(sources.map((source) => source.fieldKey))), [sources]);
  const selectedField = activeField || fields[0] || "";
  const selectedSources = sources.filter((source) => source.fieldKey === selectedField);
  const sourceItems = useMemo(() => selectedSources.map(toFactSourceItem), [selectedSources]);

  useEffect(() => {
    const session = readSession();
    if (!session) {
      setError("请先登录后再查看可信依据。");
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
    listProjectFactSources(session.accessToken, selectedProjectId)
      .then((items) => {
        setSources(items);
        setActiveField(items[0]?.fieldKey ?? "");
      })
      .catch((exception) => setError(exception instanceof Error ? exception.message : "可信依据加载失败"))
      .finally(() => setLoading(false));
  }, [selectedProjectId]);

  return (
    <AppShell eyebrow="项目资产可信来源" title={project ? `${project.name} · 可信依据` : "可信依据"}>
      <div className="min-h-[calc(100vh-4rem)] bg-surface p-6">
        <section className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-md border border-line bg-white p-4 shadow-panel">
          <div>
            <button className="mb-2 inline-flex items-center gap-1 text-sm font-semibold text-slate-600 hover:text-slate-950" onClick={() => router.back()} type="button">
              <ArrowLeft className="h-4 w-4" />
              返回上一步
            </button>
            <h2 className="text-xl font-semibold text-slate-950">可信依据</h2>
            <p className="mt-1 text-sm text-muted">查看项目资产从哪里来，哪些内容经过用户确认。</p>
          </div>
          <Link className="rounded-md border border-line px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50" href={`/project-intelligence?projectId=${selectedProjectId}`}>
            回到项目理解
          </Link>
        </section>

        {error ? <div className="mb-5 rounded-md border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">{error}</div> : null}
        {loading ? <div className="h-1 bg-slate-950" /> : null}

        <section className="grid gap-5 lg:grid-cols-[260px_minmax(0,1fr)]">
          <aside className="rounded-md border border-line bg-white shadow-panel">
            <div className="border-b border-line px-5 py-4">
              <h3 className="font-semibold text-slate-950">字段 · {fields.length} 项</h3>
            </div>
            <div className="p-3">
              {fields.length ? fields.map((field) => (
                <button
                  className={`mb-2 flex w-full items-center justify-between rounded-md px-3 py-2 text-left text-sm transition ${selectedField === field ? "bg-slate-950 text-white" : "bg-slate-50 text-slate-700 hover:bg-slate-100"}`}
                  key={field}
                  onClick={() => setActiveField(field)}
                  type="button"
                >
                  <span>{fieldLabels[field] ?? field}</span>
                  <span>{sources.filter((source) => source.fieldKey === field).length}</span>
                </button>
              )) : <p className="p-2 text-sm text-muted">暂无可信依据字段。</p>}
            </div>
          </aside>

          <div className="rounded-md border border-line bg-white shadow-panel">
            <ResourceTimeline
              emptyText="保存项目资产或采纳变更后会生成可信依据。"
              items={sourceItems}
              title={`${fieldLabels[selectedField] ?? (selectedField || "字段来源")} · ${selectedSources.length} 条`}
            />
          </div>
        </section>
      </div>
    </AppShell>
  );
}

function toFactSourceItem(source: ProjectFactSource): ResourceTimelineItem {
  return {
    id: source.id,
    title: fieldLabels[source.fieldKey] ?? source.fieldKey,
    summary: source.value || "暂无字段内容。",
    date: source.updatedAt || source.createdAt,
    type: source.sourceType,
    status: source.confirmedByUser ? "已确认" : "待确认",
    source: source.sourceType,
    detail: [
      `字段：${fieldLabels[source.fieldKey] ?? source.fieldKey}`,
      `置信度：${source.confidence}`,
      "",
      source.value,
    ].join("\n"),
  };
}
