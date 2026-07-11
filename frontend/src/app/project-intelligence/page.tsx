"use client";

import { FormEvent, Suspense, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { DatabaseZap, Pencil, RefreshCw, Save, ShieldCheck } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { Badge, ProjectContextBar, Toast } from "@/components/ui";
import { useAutoDismissNotice } from "@/hooks/useAutoDismissNotice";
import { useProjectSelection } from "@/hooks/useProjectSelection";
import {
  getProjectMemory,
  listAiSuggestions,
  listProjectAnalysisRecords,
  listProjectEvolutionRecords,
  listProjectFactSources,
  listProjectSediments,
  updateProjectMemory,
  type AiSuggestion,
  type ProjectAnalysisRecord,
  type ProjectEvolutionRecord,
  type ProjectFactSource,
  type ProjectMemory,
  type ProjectMemoryPayload,
  type ProjectSediment,
} from "@/lib/api";
import { readSession } from "@/lib/auth";
import { capabilityBulletItems } from "@/lib/project-memory-display";
import { useProjectAnalysisJobs } from "@/lib/use-project-analysis-jobs";
import {
  ArchiveEntryCard,
  ArchiveFieldReview,
  SmallEntryLink,
  fieldConfig,
} from "@/components/project-intelligence/ProjectAssetPanels";

export default function ProjectIntelligencePage() {
  return (
    <Suspense fallback={<AppShell eyebrow="已确认且可追溯" title="项目沉淀"><div className="min-h-[calc(100vh-4rem)] bg-surface p-8"><div className="h-1 bg-slate-950" /></div></AppShell>}>
      <ProjectIntelligencePageContent />
    </Suspense>
  );
}

function ProjectIntelligencePageContent() {
  const searchParams = useSearchParams();
  const queryProjectId = searchParams.get("projectId") ?? "";
  const { projects, selectedProject, selectedProjectId, selectProject, loadingProjects, projectError } = useProjectSelection({ queryProjectId });
  const [memory, setMemory] = useState<ProjectMemory | null>(null);
  const [formValue, setFormValue] = useState<ProjectMemoryPayload>(emptyPayload());
  const [suggestions, setSuggestions] = useState<AiSuggestion[]>([]);
  const [evolutionRecords, setEvolutionRecords] = useState<ProjectEvolutionRecord[]>([]);
  const [factSources, setFactSources] = useState<ProjectFactSource[]>([]);
  const [analysisRecords, setAnalysisRecords] = useState<ProjectAnalysisRecord[]>([]);
  const [sediments, setSediments] = useState<ProjectSediment[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const { jobs, jobError, enqueueProjectAnalysis } = useProjectAnalysisJobs(selectedProjectId);
  const latestProjectJob = jobs.find((job) => job.jobType === "PROJECT") ?? null;
  const analysis = latestProjectJob?.status === "SUCCEEDED" ? latestProjectJob.projectResult : null;
  const analyzing = latestProjectJob?.status === "QUEUED" || latestProjectJob?.status === "RUNNING";
  const pendingFacts = suggestions.filter((suggestion) => suggestion.status === "PENDING" && suggestion.type === "UPDATE_PROJECT_MEMORY");
  const latestSourceByField = useMemo(() => {
    const entries = new Map<string, ProjectFactSource>();
    for (const source of factSources) {
      const existing = entries.get(source.fieldKey);
      if (!existing || new Date(source.updatedAt).getTime() > new Date(existing.updatedAt).getTime()) {
        entries.set(source.fieldKey, source);
      }
    }
    return entries;
  }, [factSources]);
  const pendingFactsByField = useMemo(() => {
    const entries = new Map<string, AiSuggestion[]>();
    for (const suggestion of pendingFacts) {
      const fieldKey = suggestionFieldKey(suggestion);
      if (!fieldKey) continue;
      entries.set(fieldKey, [...(entries.get(fieldKey) ?? []), suggestion]);
    }
    return entries;
  }, [pendingFacts]);
  const completedCapabilityItems = useMemo(() => capabilityBulletItems(formValue.completedCapabilities), [formValue.completedCapabilities]);

  useEffect(() => {
    refreshProjectContext(selectedProjectId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedProjectId]);

  useEffect(() => {
    const session = readSession();
    if (!session || !selectedProjectId || latestProjectJob?.status !== "SUCCEEDED") {
      return;
    }
    listProjectAnalysisRecords(session.accessToken, selectedProjectId)
      .then(setAnalysisRecords)
      .catch(() => undefined);
  }, [latestProjectJob?.id, latestProjectJob?.status, selectedProjectId]);

  useAutoDismissNotice(error, notice, () => {
    setNotice("");
    setError("");
  });

  async function refreshProjectContext(projectId: string) {
    const session = readSession();
    if (!session || !projectId) {
      setMemory(null);
      setFormValue(emptyPayload());
      setSuggestions([]);
      setEvolutionRecords([]);
      setFactSources([]);
      setAnalysisRecords([]);
      setSediments([]);
      return;
    }

    setLoading(true);
    setError("");
    try {
      const [memoryRecord, suggestionItems, evolutionItems, sourceItems, analysisItems, sedimentItems] = await Promise.all([
        getProjectMemory(session.accessToken, projectId),
        listAiSuggestions(session.accessToken, projectId),
        listProjectEvolutionRecords(session.accessToken, projectId),
        listProjectFactSources(session.accessToken, projectId),
        listProjectAnalysisRecords(session.accessToken, projectId),
        listProjectSediments(session.accessToken, projectId),
      ]);
      setMemory(memoryRecord);
      setFormValue(toPayload(memoryRecord));
      setSuggestions(suggestionItems);
      setEvolutionRecords(evolutionItems);
      setFactSources(sourceItems);
      setAnalysisRecords(analysisItems);
      setSediments(sedimentItems);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "项目理解加载失败");
    } finally {
      setLoading(false);
    }
  }

  async function handleSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const session = readSession();
    if (!session || !selectedProjectId) {
      return;
    }

    setSaving(true);
    setError("");
    setNotice("");
    try {
      const updated = await updateProjectMemory(session.accessToken, selectedProjectId, formValue);
      setMemory(updated);
      setFormValue(toPayload(updated));
      setFactSources(await listProjectFactSources(session.accessToken, selectedProjectId));
      setNotice("兼容档案字段已保存。后续输出仍优先使用已确认项目沉淀。");
      setEditing(false);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "兼容档案字段保存失败");
    } finally {
      setSaving(false);
    }
  }

  function updateField(key: keyof ProjectMemoryPayload, value: string) {
    setFormValue((current) => ({ ...current, [key]: value }));
  }

  async function handleRunAnalysis() {
    const session = readSession();
    if (!session || !selectedProjectId) {
      return;
    }

    setError("");
    setNotice("");
    try {
      await enqueueProjectAnalysis();
      setNotice("分析任务已提交。刷新页面不会中断任务。");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "项目理解分析失败。请先导入完整项目 zip。");
    }
  }

  return (
    <AppShell eyebrow="已确认且可追溯" title="项目沉淀">
      <div className="min-h-[calc(100vh-4rem)] bg-surface p-8">
        <ProjectContextBar
          actions={(
            <div className="flex items-center gap-2 text-sm text-muted">
              <ShieldCheck className="h-4 w-4" />
              {selectedProject?.name ?? "暂无项目"}
            </div>
          )}
          leadingExtras={(
            <>
              <Badge label={`版本 ${memory?.version ?? "-"}`} />
              <Badge label="用户确认内容优先" tone="success" />
              <button
                className="inline-flex h-9 items-center gap-2 rounded-md bg-slate-950 px-3 text-sm font-semibold text-white disabled:opacity-60"
                disabled={analyzing || !selectedProjectId}
                onClick={handleRunAnalysis}
                type="button"
              >
                {analyzing ? <RefreshCw className="h-4 w-4 animate-spin" /> : null}
                {latestProjectJob?.status === "QUEUED" ? "等待分析" : analyzing ? "模型分析中" : analysis ? "重新分析" : "运行项目分析"}
              </button>
            </>
          )}
          onSelect={selectProject}
          projects={projects}
          selectedProjectId={selectedProjectId}
        />

        <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
          <div className="rounded-md border border-line bg-white shadow-panel">
            {analysis ? (
              <div className="border-b border-line bg-slate-50 p-5">
                <div className="mb-3 flex flex-wrap items-center gap-2">
                  <span className={`rounded-md px-2.5 py-1 text-xs ${analysis.modelUsed ? "bg-emerald-50 text-emerald-700" : "bg-amber-50 text-amber-800"}`}>
                    {analysis.modelUsed ? "模型分析" : "本地规则"}
                  </span>
                  <span className="text-xs text-muted">{analysis.message}</span>
                </div>
                <p className="text-base leading-7 text-slate-800">{analysis.summary}</p>
                <p className="mt-2 text-sm leading-6 text-slate-600">{analysis.architecture}</p>
                {analysis.risks.length ? <p className="mt-2 text-sm text-amber-800">风险：{analysis.risks.join("；")}</p> : null}
                {analysis.evidence.length ? (
                  <div className="mt-4 rounded-md border border-line bg-white p-4">
                    <p className="text-sm font-semibold text-slate-950">判断依据</p>
                    <ul className="mt-2 space-y-1 text-sm leading-6 text-slate-600">
                      {analysis.evidence.map((item) => <li key={item}>- {item}</li>)}
                    </ul>
                  </div>
                ) : null}
                {analysis.limitations.length ? <p className="mt-3 text-xs leading-5 text-muted">分析局限：{analysis.limitations.join("；")}</p> : null}
              </div>
            ) : null}
            {latestProjectJob?.status === "FAILED" ? (
              <div className="border-b border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">
                分析任务失败：{latestProjectJob.errorMessage ?? "未知错误"}
              </div>
            ) : null}
            {jobError ? <div className="border-b border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">{jobError}</div> : null}
            <div className="flex items-center justify-between border-b border-line px-5 py-4">
              <div className="flex items-center gap-2">
                <DatabaseZap className="h-4 w-4 text-slate-700" />
                <div>
                  <h2 className="font-semibold">已确认沉淀</h2>
                  <p className="mt-1 text-xs text-muted">只展示有真实来源且经过确认的内容，主观备注进入独立详情。</p>
                </div>
              </div>
              {editing ? (
                <button
                  className="inline-flex items-center gap-2 rounded-md border border-line px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50"
                  onClick={() => setEditing(false)}
                  type="button"
                >
                  收起编辑
                </button>
              ) : (
                <button
                  className="inline-flex items-center gap-2 rounded-md bg-slate-950 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-800"
                  onClick={() => setEditing(true)}
                  type="button"
                >
                  <Pencil className="h-4 w-4" />
                  兼容档案字段
                </button>
              )}
            </div>

            {editing ? (
              <form className="grid gap-0 md:grid-cols-2" onSubmit={handleSave}>
                {fieldConfig.map((field) => {
                  const latestSource = latestSourceByField.get(field.key);
                  return (
                    <ArchiveFieldReview
                      candidateCount={pendingFactsByField.get(field.key)?.length ?? 0}
                      field={field}
                      key={field.key}
                      latestSource={latestSource}
                      onChange={(value) => updateField(field.key, value)}
                      projectId={selectedProjectId}
                      value={formValue[field.key]}
                    />
                  );
                })}
                <div className="col-span-full flex items-center justify-end gap-3 border-t border-line p-4">
                  <button className="rounded-md border border-line px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50" onClick={() => setEditing(false)} type="button">取消</button>
                  <button className="inline-flex items-center gap-2 rounded-md bg-slate-950 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60" disabled={saving || !selectedProjectId} type="submit">
                    {saving ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
                    {saving ? "保存中..." : "保存手动修正"}
                  </button>
                </div>
              </form>
            ) : (
              <SedimentOverview sediments={sediments} projectId={selectedProjectId} />
            )}
            {loading || loadingProjects ? <div className="h-1 bg-slate-950" /> : null}
          </div>

          <aside className="space-y-5">
            <section className="rounded-md border border-line bg-white shadow-panel">
              <div className="border-b border-line px-5 py-4">
                <h2 className="font-semibold">项目沉淀入口</h2>
              </div>
              <div className="space-y-4 p-5">
                <div className="space-y-3">
                  <ArchiveEntryCard
                    count={completedCapabilityItems.length}
                    href={`/project-intelligence/capabilities?projectId=${selectedProjectId}`}
                    label="能力沉淀"
                    latestAt={memory?.updatedAt ?? undefined}
                    latestLabel={completedCapabilityItems[0] || "暂无确认能力"}
                    text="查看已确认能力、真实证据和可复用素材。"
                    tone="emerald"
                  />
                  <ArchiveEntryCard
                    count={pendingFacts.length}
                    href={`/tasks?projectId=${selectedProjectId}&type=project-memory`}
                    label="建议沉淀"
                    latestLabel={pendingFacts.length ? "需要审查" : "无待确认"}
                    text="确认开发推进段应新建、合并、补证据或忽略。"
                    tone="amber"
                  />
                </div>
                <div className="rounded-md border border-line bg-slate-50 p-3">
                  <p className="text-xs font-semibold text-slate-700">辅助查看</p>
                  <div className="mt-3 grid gap-2">
                    <SmallEntryLink href={`/project-intelligence/timeline?projectId=${selectedProjectId}`} label="项目时间线" text={`${evolutionRecords.length} 条 · 能力、决策、风险和资产更新`} />
                    <SmallEntryLink href={`/project-intelligence/analysis-records?projectId=${selectedProjectId}`} label="分析记录" text={`${analysisRecords.length} 条 · 项目分析和文件分析历史`} />
                  </div>
                </div>
              </div>
            </section>

            <section className="rounded-md border border-line bg-white p-5 shadow-panel">
              <p className="font-semibold text-slate-950">确认原则</p>
              <p className="mt-2 text-sm leading-6 text-slate-600">
                AI、zip 分析和 Agent result 都只是候选。只有用户确认后，内容才进入项目沉淀和成果输出来源。
              </p>
            </section>
          </aside>
        </div>

        <Toast error={error || projectError} notice={notice} />
      </div>
    </AppShell>
  );
}

function SedimentOverview({ sediments, projectId }: { sediments: ProjectSediment[]; projectId: string }) {
  if (sediments.length === 0) {
    return (
      <div className="p-8 text-center">
        <p className="font-semibold text-slate-950">还没有已确认沉淀</p>
        <p className="mx-auto mt-2 max-w-lg text-sm leading-6 text-muted">先在工作台分析新变化，再到沉淀确认选择新建、合并、补证据或忽略。</p>
        <Link className="mt-4 inline-flex rounded-md bg-slate-950 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-800" href={`/sediment-review?projectId=${projectId}`}>进入沉淀处理中心</Link>
      </div>
    );
  }
  const statusOrder = ["PENDING_ANALYSIS", "CAPABILITY_FORMED", "ANALYZED_NO_CAPABILITY", "LEGACY"];
  const groups = statusOrder.map((status) => ({
    status,
    items: sediments.filter((item) => status === "LEGACY" ? !item.capabilityStatus : item.capabilityStatus === status),
  })).filter((group) => group.items.length > 0);
  return <div className="divide-y divide-line">{groups.map((group) => (
    <details className="group" key={group.status} open={group.status === "PENDING_ANALYSIS"}>
      <summary className="flex cursor-pointer list-none items-center justify-between gap-4 px-5 py-4 hover:bg-slate-50">
        <div><p className="font-semibold text-slate-950">{sedimentCapabilityStatusLabel(group.status)}</p><p className="mt-1 text-xs text-muted">{group.items.length} 条 · 最近更新 {new Date(group.items[0].updatedAt).toLocaleString("zh-CN")}</p></div>
        <span className="text-xs font-semibold text-slate-600 group-open:hidden">展开时间档案</span>
      </summary>
      <div className="border-t border-line bg-slate-50 p-4">{sedimentTimeGroups(group.items).map((timeGroup) => (
        <details className="mb-3 rounded-md border border-line bg-white last:mb-0" key={timeGroup.label}>
          <summary className="cursor-pointer px-4 py-3 text-sm font-semibold">{timeGroup.label} · {timeGroup.items.length} 条</summary>
          <div className="divide-y divide-line">{timeGroup.items.map((sediment) => (
            <Link className="block px-4 py-4 hover:bg-slate-50" href={`/project-sediments/${sediment.id}`} key={sediment.id}>
              <div className="flex flex-wrap items-center justify-between gap-3"><h3 className="font-semibold text-slate-950 break-words">{sediment.title}</h3><span className="rounded-md bg-emerald-50 px-2 py-1 text-xs font-medium text-emerald-700">{sedimentCapabilityStatusLabel(sediment.capabilityStatus)}</span></div>
              <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600 break-words line-clamp-2">{sediment.summary}</p>
              <div className="mt-3 flex flex-wrap gap-3 text-xs text-muted"><span>{sediment.sourceSegmentIds.length} 个推进段</span><span>{sediment.evidenceRefs.length} 条证据</span><span>涉及 {sediment.affectedFiles.length} 个文件</span><span>{new Date(sediment.updatedAt).toLocaleString("zh-CN")}</span></div>
            </Link>
          ))}</div>
        </details>
      ))}</div>
    </details>
  ))}</div>;
}

function sedimentCapabilityStatusLabel(status: string) {
  if (status === "PENDING_ANALYSIS") return "待能力分析";
  if (status === "CAPABILITY_FORMED") return "已形成能力卡片";
  if (status === "ANALYZED_NO_CAPABILITY") return "已分析，未形成能力卡片";
  return "旧版来源未知";
}

function sedimentTimeGroups(items: ProjectSediment[]) {
  const now = new Date();
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const startOfWeek = startOfToday - 6 * 24 * 60 * 60 * 1000;
  const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1).getTime();
  const definitions = [
    { label: "今天", match: (time: number) => time >= startOfToday },
    { label: "本周", match: (time: number) => time >= startOfWeek && time < startOfToday },
    { label: "本月", match: (time: number) => time >= startOfMonth && time < startOfWeek },
    { label: "更早", match: (time: number) => time < startOfMonth },
  ];
  return definitions.map((definition) => ({ label: definition.label, items: items.filter((item) => definition.match(new Date(item.updatedAt).getTime())) })).filter((group) => group.items.length > 0);
}

function emptyPayload(): ProjectMemoryPayload {
  return {
    positioning: "",
    currentStage: "",
    completedCapabilities: "",
    inProgressCapabilities: "",
    currentRisks: "",
    technicalDecisions: "",
    developerLearnings: "",
    showcaseAssets: "",
    nextStepSuggestions: "",
  };
}

function toPayload(memory: ProjectMemory): ProjectMemoryPayload {
  return {
    positioning: memory.positioning,
    currentStage: memory.currentStage,
    completedCapabilities: memory.completedCapabilities,
    inProgressCapabilities: memory.inProgressCapabilities,
    currentRisks: memory.currentRisks,
    technicalDecisions: memory.technicalDecisions,
    developerLearnings: memory.developerLearnings,
    showcaseAssets: memory.showcaseAssets,
    nextStepSuggestions: memory.nextStepSuggestions,
  };
}

function suggestionFieldKey(suggestion: AiSuggestion) {
  const payloadField = suggestion.payload.fieldKey;
  if (typeof payloadField === "string") {
    return payloadField;
  }
  const title = suggestion.title.toLowerCase();
  const match = fieldConfig.find((field) => title.includes(field.label.toLowerCase()) || title.includes(String(field.key).toLowerCase()));
  return match?.key ?? "";
}
