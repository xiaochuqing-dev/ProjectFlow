"use client";

import { FormEvent, Suspense, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ArrowRight, DatabaseZap, Pencil, RefreshCw, Save, ShieldCheck } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { Badge, ProjectContextBar, Toast } from "@/components/ui";
import { ArchiveFieldReview, fieldConfig } from "@/components/project-intelligence/ProjectAssetPanels";
import { useAutoDismissNotice } from "@/hooks/useAutoDismissNotice";
import { useProjectSelection } from "@/hooks/useProjectSelection";
import {
  getFactMemoryOverview,
  getProjectFactHistoryState,
  getProjectMemory,
  listProjectFacts,
  listProjectSediments,
  updateProjectMemory,
  type FactMemoryOverview,
  type ProjectFactHistoryState,
  type ProjectFactSummary,
  type ProjectMemory,
  type ProjectMemoryPayload,
  type ProjectSediment,
} from "@/lib/api";
import { readSession } from "@/lib/auth";
import { formatFactOccurredRange } from "@/lib/project-fact-memory";
import { factHistoryStatusLabel, factRecordStatusLabel, factSourceModeLabel } from "@/lib/status-labels";
import { useProjectAnalysisJobs } from "@/lib/use-project-analysis-jobs";

export default function ProjectIntelligencePage() {
  return (
    <Suspense fallback={<AppShell eyebrow="自动项目事实与长期记忆" title="项目记忆"><div className="min-h-[calc(100vh-4rem)] bg-surface p-8"><div className="h-1 bg-slate-950" /></div></AppShell>}>
      <ProjectMemoryPageContent />
    </Suspense>
  );
}

function ProjectMemoryPageContent() {
  const searchParams = useSearchParams();
  const queryProjectId = searchParams.get("projectId") ?? "";
  const { projects, selectedProject, selectedProjectId, selectProject, loadingProjects, projectError } = useProjectSelection({ queryProjectId });
  const [overview, setOverview] = useState<FactMemoryOverview | null>(null);
  const [recentFacts, setRecentFacts] = useState<ProjectFactSummary[]>([]);
  const [historyState, setHistoryState] = useState<ProjectFactHistoryState | null>(null);
  const [memory, setMemory] = useState<ProjectMemory | null>(null);
  const [formValue, setFormValue] = useState<ProjectMemoryPayload>(emptyPayload());
  const [sediments, setSediments] = useState<ProjectSediment[]>([]);
  const [factLoading, setFactLoading] = useState(true);
  const [compatibilityLoading, setCompatibilityLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState(false);
  const [factError, setFactError] = useState("");
  const [compatibilityError, setCompatibilityError] = useState("");
  const [notice, setNotice] = useState("");
  const factRequestVersion = useRef(0);
  const compatibilityRequestVersion = useRef(0);
  const factProjectId = useRef("");
  const compatibilityProjectId = useRef("");

  const { jobs, jobError, enqueueProjectAnalysis } = useProjectAnalysisJobs(selectedProjectId);
  const latestProjectJob = jobs.find((job) => job.jobType === "PROJECT") ?? null;
  const analysis = latestProjectJob?.status === "SUCCEEDED" ? latestProjectJob.projectResult : null;
  const analyzing = latestProjectJob?.status === "QUEUED" || latestProjectJob?.status === "RUNNING";

  useEffect(() => {
    void refreshFactMemory(selectedProjectId);
    void refreshCompatibility(selectedProjectId);
  }, [selectedProjectId]);

  useAutoDismissNotice(factError || compatibilityError, notice, () => {
    setNotice("");
    setFactError("");
    setCompatibilityError("");
  });

  async function refreshFactMemory(projectId: string) {
    const session = readSession();
    const version = ++factRequestVersion.current;
    if (!session || !projectId) {
      factProjectId.current = "";
      setOverview(null);
      setRecentFacts([]);
      setHistoryState(null);
      setFactLoading(false);
      return;
    }
    if (factProjectId.current !== projectId) {
      factProjectId.current = projectId;
      setOverview(null);
      setRecentFacts([]);
      setHistoryState(null);
    }
    setFactLoading(true);
    setFactError("");
    const [overviewResult, factsResult, historyResult] = await Promise.allSettled([
      getFactMemoryOverview(session.accessToken, projectId),
      listProjectFacts(session.accessToken, projectId, { page: 0, size: 8 }),
      getProjectFactHistoryState(session.accessToken, projectId),
    ]);
    if (version !== factRequestVersion.current) return;
    const failures: string[] = [];
    if (overviewResult.status === "fulfilled") setOverview(overviewResult.value); else failures.push("事实概览");
    if (factsResult.status === "fulfilled") setRecentFacts(factsResult.value.items ?? []); else failures.push("最近事实");
    if (historyResult.status === "fulfilled") setHistoryState(historyResult.value); else failures.push("历史补齐状态");
    if (failures.length) setFactError(`${failures.join("、")}读取失败，已保留其他可用项目事实。`);
    setFactLoading(false);
  }

  async function refreshCompatibility(projectId: string) {
    const session = readSession();
    const version = ++compatibilityRequestVersion.current;
    if (!session || !projectId) {
      compatibilityProjectId.current = "";
      setMemory(null);
      setFormValue(emptyPayload());
      setSediments([]);
      setCompatibilityLoading(false);
      return;
    }
    if (compatibilityProjectId.current !== projectId) {
      compatibilityProjectId.current = projectId;
      setMemory(null);
      setFormValue(emptyPayload());
      setSediments([]);
      setEditing(false);
    }
    setCompatibilityLoading(true);
    setCompatibilityError("");
    const [memoryResult, sedimentResult] = await Promise.allSettled([
      getProjectMemory(session.accessToken, projectId),
      listProjectSediments(session.accessToken, projectId),
    ]);
    if (version !== compatibilityRequestVersion.current) return;
    const failures: string[] = [];
    if (memoryResult.status === "fulfilled") {
      setMemory(memoryResult.value);
      setFormValue(toPayload(memoryResult.value));
    } else failures.push("兼容项目档案");
    if (sedimentResult.status === "fulfilled") setSediments(sedimentResult.value); else failures.push("旧版已确认沉淀");
    if (failures.length) setCompatibilityError(`${failures.join("、")}暂时不可用，不影响项目事实。`);
    setCompatibilityLoading(false);
  }

  async function handleSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const session = readSession();
    if (!session || !selectedProjectId) return;
    setSaving(true);
    setCompatibilityError("");
    setNotice("");
    try {
      const updated = await updateProjectMemory(session.accessToken, selectedProjectId, formValue);
      setMemory(updated);
      setFormValue(toPayload(updated));
      setNotice("兼容项目档案已保存，不会改写自动记录的项目事实。");
      setEditing(false);
    } catch (exception) {
      setCompatibilityError(exception instanceof Error ? exception.message : "兼容项目档案保存失败");
    } finally {
      setSaving(false);
    }
  }

  async function handleRunAnalysis() {
    if (!selectedProjectId) return;
    setFactError("");
    setNotice("");
    try {
      await enqueueProjectAnalysis();
      setNotice("辅助项目分析任务已提交，刷新页面不会中断任务。");
    } catch (exception) {
      setFactError(exception instanceof Error ? exception.message : "项目分析提交失败");
    }
  }

  function updateField(key: keyof ProjectMemoryPayload, value: string) {
    setFormValue((current) => ({ ...current, [key]: value }));
  }

  return (
    <AppShell eyebrow="自动项目事实与长期记忆" title="项目记忆">
      <div className="min-h-[calc(100vh-4rem)] bg-surface p-8">
        <ProjectContextBar
          actions={<div className="flex items-center gap-2 text-sm text-muted"><ShieldCheck className="h-4 w-4" />{selectedProject?.name ?? "暂无项目"}</div>}
          leadingExtras={(
            <>
              <Badge label={`项目事实 ${overview?.totalFactCount ?? 0}`} tone="success" />
              <Badge label={`需要关注 ${overview?.attentionFactCount ?? 0}`} tone={overview?.attentionFactCount ? "warning" : "slate"} />
              <button className="inline-flex h-9 items-center gap-2 rounded-md border border-line bg-white px-3 text-sm font-semibold disabled:opacity-60" disabled={analyzing || !selectedProjectId} onClick={() => void handleRunAnalysis()} type="button">
                {analyzing ? <RefreshCw className="h-4 w-4 animate-spin" /> : null}{latestProjectJob?.status === "QUEUED" ? "等待辅助分析" : analyzing ? "辅助分析中" : "运行辅助项目分析"}
              </button>
            </>
          )}
          onSelect={selectProject}
          projects={projects}
          selectedProjectId={selectedProjectId}
        />

        {factError || projectError ? <p className="mb-5 rounded-md border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">{factError || projectError}</p> : null}

        <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_340px]">
          <FactMemoryOverviewPanel historyState={historyState} loading={factLoading} overview={overview} projectId={selectedProjectId} recentFacts={recentFacts} />
          <MemoryNavigation projectId={selectedProjectId} />
        </div>

        {analysis || latestProjectJob?.status === "FAILED" || jobError ? (
          <details className="mt-6 rounded-md border border-line bg-white shadow-panel">
            <summary className="cursor-pointer px-5 py-4 font-semibold text-slate-950">辅助项目分析（不作为项目事实）</summary>
            <div className="border-t border-line p-5 text-sm leading-6 text-slate-700">
              {analysis ? <><p>{analysis.summary}</p><p className="mt-2 text-muted">{analysis.architecture}</p></> : null}
              {latestProjectJob?.status === "FAILED" ? <p className="text-red-700">最近一次辅助分析失败：{latestProjectJob.errorMessage ?? "未知错误"}</p> : null}
              {jobError ? <p className="text-amber-800">{jobError}</p> : null}
            </div>
          </details>
        ) : null}

        <section className="mt-6 space-y-4">
          <details className="rounded-md border border-line bg-white shadow-panel">
            <summary className="cursor-pointer px-5 py-4"><p className="font-semibold text-slate-950">旧版已确认沉淀 · {sediments.length}</p><p className="mt-1 text-xs leading-5 text-muted">V3.3.x 人工确认记录，已保留用于历史兼容。V3.4.0 新变化会直接自动记录为项目事实。</p></summary>
            <LegacySediments projectId={selectedProjectId} sediments={sediments} />
          </details>

          <section className="rounded-md border border-line bg-white shadow-panel">
            <div className="flex flex-wrap items-center justify-between gap-3 px-5 py-4">
              <div><p className="font-semibold text-slate-950">兼容项目档案</p><p className="mt-1 text-xs text-muted">保留旧定位、阶段、能力和建议字段；下一步建议不会自动写入项目事实。</p></div>
              <button className="inline-flex items-center gap-2 rounded-md border border-line px-3 py-2 text-sm font-semibold" onClick={() => setEditing((current) => !current)} type="button"><Pencil className="h-4 w-4" />{editing ? "收起编辑" : "编辑兼容档案"}</button>
            </div>
            {editing ? (
              <form className="grid gap-0 border-t border-line md:grid-cols-2" onSubmit={handleSave}>
                {fieldConfig.map((field) => <ArchiveFieldReview candidateCount={0} field={field} key={field.key} onChange={(value) => updateField(field.key, value)} projectId={selectedProjectId} value={formValue[field.key]} />)}
                <div className="col-span-full flex items-center justify-end gap-3 border-t border-line p-4"><button className="rounded-md border border-line px-4 py-2 text-sm font-semibold" onClick={() => setEditing(false)} type="button">取消</button><button className="inline-flex items-center gap-2 rounded-md bg-slate-950 px-4 py-2 text-sm font-semibold text-white disabled:opacity-60" disabled={saving || !selectedProjectId} type="submit">{saving ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}{saving ? "保存中…" : "保存兼容档案"}</button></div>
              </form>
            ) : <p className="border-t border-line px-5 py-4 text-sm text-muted">档案版本 {memory?.version ?? "-"}。这些字段仅作历史兼容，不是新的项目记忆事实源。</p>}
          </section>
        </section>

        {compatibilityError ? <p className="mt-5 rounded-md border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">{compatibilityError}</p> : null}
        {factLoading || compatibilityLoading || loadingProjects ? <div className="fixed inset-x-0 bottom-0 h-1 bg-slate-950" /> : null}
        <Toast error="" notice={notice} />
      </div>
    </AppShell>
  );
}

function FactMemoryOverviewPanel({ historyState, loading, overview, projectId, recentFacts }: { historyState: ProjectFactHistoryState | null; loading: boolean; overview: FactMemoryOverview | null; projectId: string; recentFacts: ProjectFactSummary[] }) {
  return (
    <section className="rounded-md border border-line bg-white shadow-panel">
      <div className="border-b border-line px-5 py-4"><div className="flex items-center gap-2"><DatabaseZap className="h-4 w-4 text-brand" /><h2 className="font-semibold text-slate-950">项目事实概览</h2></div><p className="mt-1 text-xs text-muted">数据库中的项目事实是长期记忆来源，页面缓存只用于加快显示。</p></div>
      <div className="grid grid-cols-2 gap-3 p-5 lg:grid-cols-5">
        <OverviewMetric label="已记录事实" value={overview?.totalFactCount ?? 0} />
        <OverviewMetric label="覆盖 commits" value={`${overview?.coveredCommitCount ?? 0} / ${overview?.totalCommitCount ?? 0}`} />
        <OverviewMetric label="需要关注" value={overview?.attentionFactCount ?? 0} />
        <OverviewMetric label="最早事实" value={formatOverviewDate(overview?.earliestOccurredAt)} />
        <OverviewMetric label="最近事实" value={formatOverviewDate(overview?.latestOccurredAt)} />
      </div>
      {historyState ? <div className="border-y border-line bg-blue-50 px-5 py-4 text-sm text-blue-950"><div className="flex flex-wrap items-center justify-between gap-3"><p className="font-semibold">{factHistoryStatusLabel(historyState.status)}</p><span>已覆盖 {historyState.coveredCommitCount ?? 0} / {historyState.totalCommitCount ?? 0} commits</span></div>{historyState.status === "RUNNING" ? <p className="mt-2 text-xs">后台会按有界批次从旧到新继续补齐，离开页面不会中断。</p> : null}{historyState.errorSummary ? <p className="mt-2 text-xs text-amber-900">{historyState.errorSummary}</p> : null}</div> : null}
      <div className="flex items-center justify-between px-5 py-4"><h3 className="font-semibold text-slate-950">最近项目事实</h3>{projectId ? <Link className="inline-flex items-center gap-1 text-sm font-semibold text-brand" href={`/sediment-review?projectId=${projectId}`}>查看全部项目记录<ArrowRight className="h-4 w-4" /></Link> : null}</div>
      {recentFacts.length ? <div className="divide-y divide-line border-t border-line">{recentFacts.map((fact) => <Link className="block px-5 py-4 hover:bg-slate-50" href={`/sediment-review/${fact.batchId}?projectId=${projectId}`} key={fact.id}><div className="flex flex-wrap items-center gap-2"><Badge label={factRecordStatusLabel(fact.recordStatus)} tone={fact.recordStatus === "NEEDS_ATTENTION" ? "warning" : "success"} /><Badge label={factSourceModeLabel(fact.sourceMode)} tone="slate" /><span className="text-xs text-muted">{formatFactOccurredRange(fact.occurredFrom, fact.occurredTo)}</span></div><h4 className="mt-2 font-semibold text-slate-950">{fact.title}</h4><p className="mt-1 line-clamp-2 text-sm leading-6 text-slate-600">{fact.summary}</p></Link>)}</div> : <p className="border-t border-line p-8 text-center text-sm text-muted">{loading ? "正在读取项目事实…" : "暂无项目事实。完成一次“分析新变化”后会自动记录。"}</p>}
    </section>
  );
}

function MemoryNavigation({ projectId }: { projectId: string }) {
  const suffix = projectId ? `?projectId=${projectId}` : "";
  return <aside className="space-y-3"><NavigationCard href={`/sediment-review${suffix}`} label="项目记录" text="按批次和事实发生月份浏览完整项目历史。" /><NavigationCard href={`/project-intelligence/capabilities${suffix}`} label="能力与成果" text="查看已有能力卡片；后续将逐步改为读取项目事实。" /><NavigationCard href={`/project-intelligence/timeline${suffix}`} label="兼容项目时间线" text="查看旧版成长记录。" /><NavigationCard href={`/project-intelligence/analysis-records${suffix}`} label="辅助分析记录" text="查看项目分析和文件分析历史。" /></aside>;
}

function NavigationCard({ href, label, text }: { href: string; label: string; text: string }) {
  return <Link className="block rounded-md border border-line bg-white p-5 shadow-panel hover:border-slate-300" href={href}><p className="font-semibold text-slate-950">{label}</p><p className="mt-2 text-sm leading-6 text-muted">{text}</p><span className="mt-3 inline-flex items-center gap-1 text-sm font-semibold text-brand">查看<ArrowRight className="h-4 w-4" /></span></Link>;
}

function LegacySediments({ projectId, sediments }: { projectId: string; sediments: ProjectSediment[] }) {
  if (!sediments.length) return <p className="border-t border-line p-6 text-sm text-muted">没有旧版已确认沉淀。</p>;
  return <div className="divide-y divide-line border-t border-line">{sediments.map((sediment) => <Link className="block px-5 py-4 hover:bg-slate-50" href={`/project-sediments/${sediment.id}?projectId=${projectId}`} key={sediment.id}><h3 className="font-semibold text-slate-950">{sediment.title}</h3><p className="mt-1 line-clamp-2 text-sm leading-6 text-slate-600">{sediment.summary}</p><p className="mt-2 text-xs text-muted">V3.3.x 人工确认记录 · {new Date(sediment.updatedAt).toLocaleString("zh-CN")}</p></Link>)}</div>;
}

function OverviewMetric({ label, value }: { label: string; value: number | string }) {
  return <div className="rounded-md bg-slate-50 p-3"><p className="text-xs text-muted">{label}</p><p className="mt-1 break-words font-semibold text-slate-950">{value}</p></div>;
}

function formatOverviewDate(value: string | null | undefined) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isFinite(date.getTime()) ? date.toLocaleDateString("zh-CN") : "—";
}

function emptyPayload(): ProjectMemoryPayload {
  return { positioning: "", currentStage: "", completedCapabilities: "", inProgressCapabilities: "", currentRisks: "", technicalDecisions: "", developerLearnings: "", showcaseAssets: "", nextStepSuggestions: "" };
}

function toPayload(memory: ProjectMemory): ProjectMemoryPayload {
  return { positioning: memory.positioning, currentStage: memory.currentStage, completedCapabilities: memory.completedCapabilities, inProgressCapabilities: memory.inProgressCapabilities, currentRisks: memory.currentRisks, technicalDecisions: memory.technicalDecisions, developerLearnings: memory.developerLearnings, showcaseAssets: memory.showcaseAssets, nextStepSuggestions: memory.nextStepSuggestions };
}
