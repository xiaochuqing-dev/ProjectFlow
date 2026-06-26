"use client";

import { FormEvent, Suspense, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ArrowRight, DatabaseZap, ListChecks, RefreshCw, Save, ShieldCheck } from "lucide-react";
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
  updateProjectMemory,
  type AiSuggestion,
  type ProjectAnalysisRecord,
  type ProjectEvolutionRecord,
  type ProjectFactSource,
  type ProjectMemory,
  type ProjectMemoryPayload,
} from "@/lib/api";
import { readSession } from "@/lib/auth";
import { capabilityBulletItems } from "@/lib/project-memory-display";
import { useProjectAnalysisJobs } from "@/lib/use-project-analysis-jobs";

const fieldConfig: Array<{
  key: keyof ProjectMemoryPayload;
  label: string;
  source: string;
  rows: number;
}> = [
  { key: "positioning", label: "项目定位", source: "用户确认 / zip 分析 / 建议采纳", rows: 4 },
  { key: "currentStage", label: "当前阶段", source: "用户确认优先", rows: 2 },
  { key: "completedCapabilities", label: "已完成能力", source: "采纳记录 / 每日回顾", rows: 5 },
  { key: "inProgressCapabilities", label: "进行中能力", source: "任务变化 / agent result", rows: 5 },
  { key: "currentRisks", label: "当前风险", source: "风险建议 / 用户手动", rows: 5 },
  { key: "technicalDecisions", label: "技术决策", source: "开发成果审查采纳", rows: 5 },
  { key: "developerLearnings", label: "经验沉淀", source: "每日回顾 / 模型总结", rows: 5 },
  { key: "showcaseAssets", label: "可展示成果", source: "成果素材采纳", rows: 5 },
  { key: "nextStepSuggestions", label: "下一步目标", source: "用户确认 / agent result", rows: 5 },
];

export default function ProjectIntelligencePage() {
  return (
    <Suspense fallback={<AppShell eyebrow="项目理解与项目资产" title="项目理解"><div className="min-h-[calc(100vh-4rem)] bg-surface p-8"><div className="h-1 bg-slate-950" /></div></AppShell>}>
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
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
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
      return;
    }

    setLoading(true);
    setError("");
    try {
      const [memoryRecord, suggestionItems, evolutionItems, sourceItems, analysisItems] = await Promise.all([
        getProjectMemory(session.accessToken, projectId),
        listAiSuggestions(session.accessToken, projectId),
        listProjectEvolutionRecords(session.accessToken, projectId),
        listProjectFactSources(session.accessToken, projectId),
        listProjectAnalysisRecords(session.accessToken, projectId),
      ]);
      setMemory(memoryRecord);
      setFormValue(toPayload(memoryRecord));
      setSuggestions(suggestionItems);
      setEvolutionRecords(evolutionItems);
      setFactSources(sourceItems);
      setAnalysisRecords(analysisItems);
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
      setNotice("项目资产已保存。后续成果输出会优先使用这些已确认内容。");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "项目资产保存失败");
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
    <AppShell eyebrow="项目理解与项目资产" title="项目理解">
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
          <form className="rounded-md border border-line bg-white shadow-panel" onSubmit={handleSave}>
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
                  <h2 className="font-semibold">项目资产工作台</h2>
                  <p className="mt-1 text-xs text-muted">默认展示已确认资产和可信依据。只有需要修正时才展开编辑框。</p>
                </div>
              </div>
              <button
                className="inline-flex items-center gap-2 rounded-md bg-slate-950 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
                disabled={saving || !selectedProjectId}
                type="submit"
              >
                {saving ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
                {saving ? "保存中..." : "保存手动修正"}
              </button>
            </div>
            <div className="grid gap-0 md:grid-cols-2">
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
            </div>
            {loading || loadingProjects ? <div className="h-1 bg-slate-950" /> : null}
          </form>

          <aside className="space-y-5">
            <section className="rounded-md border border-line bg-white shadow-panel">
              <div className="border-b border-line px-5 py-4">
                <h2 className="font-semibold">项目资产入口</h2>
              </div>
              <div className="space-y-4 p-5">
                <div className="space-y-3">
                  <ArchiveEntryCard
                    count={completedCapabilityItems.length}
                    href={`/project-intelligence/capabilities?projectId=${selectedProjectId}`}
                    label="能力与成果"
                    latestAt={memory?.updatedAt ?? undefined}
                    latestLabel={completedCapabilityItems[0] || "暂无确认能力"}
                    text="查看已沉淀的项目能力、成果表达和可复用素材。"
                    tone="emerald"
                  />
                  <ArchiveEntryCard
                    count={pendingFacts.length}
                    href={`/tasks?projectId=${selectedProjectId}&type=project-memory`}
                    label="待确认成果"
                    latestLabel={pendingFacts.length ? "需要审查" : "无待确认"}
                    text="确认哪些今日开发内容可以进入项目资产。"
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
                AI、zip 分析和 agent result 都只是候选。用户确认后，才进入正式项目资产和成果输出来源。
              </p>
            </section>
          </aside>
        </div>

        <Toast error={error || projectError} notice={notice} />
      </div>
    </AppShell>
  );
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

function sourceLabel(source: ProjectFactSource) {
  return `${source.sourceType} · ${source.confirmedByUser ? "已确认" : source.confidence}`;
}

function ArchiveFieldReview({
  candidateCount,
  field,
  latestSource,
  onChange,
  projectId,
  value,
}: {
  candidateCount: number;
  field: { key: keyof ProjectMemoryPayload; label: string; source: string; rows: number };
  latestSource?: ProjectFactSource;
  onChange: (value: string) => void;
  projectId: string;
  value: string;
}) {
  const isCompletedCapabilities = field.key === "completedCapabilities";
  return (
    <section className="border-b border-line p-5 odd:md:border-r">
      <div className="mb-3 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="font-semibold text-slate-950">{field.label}</h3>
          <p className="mt-1 text-xs text-muted">
            {latestSource ? `更新于 ${new Date(latestSource.updatedAt).toLocaleString()}` : field.source}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <span className={`rounded-md px-2 py-1 text-xs ${latestSource?.confirmedByUser ? "bg-emerald-50 text-emerald-700" : "bg-slate-100 text-muted"}`}>
            {latestSource ? sourceLabel(latestSource) : "暂无来源"}
          </span>
          {candidateCount ? <span className="rounded-md bg-amber-50 px-2 py-1 text-xs text-amber-800">候选 {candidateCount}</span> : null}
        </div>
      </div>
      {isCompletedCapabilities ? (
        <CompletedCapabilitiesCard projectId={projectId} value={value} />
      ) : (
        <p className="min-h-20 whitespace-pre-line rounded-md border border-line bg-slate-50 p-3 text-sm leading-6 text-slate-700">
          {value || "暂无已确认内容。采纳结构化变更或运行项目分析后，会形成可审查候选。"}
        </p>
      )}
      {latestSource?.sourceId ? (
        <details className="mt-2 rounded-md border border-line bg-white">
          <summary className="cursor-pointer px-3 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50">
            为什么可信？
          </summary>
          <p className="break-all border-t border-line p-3 font-mono text-xs text-muted">高级信息：{latestSource.sourceId}</p>
        </details>
      ) : null}
      <details className="mt-3 rounded-md border border-line bg-white">
        <summary className="cursor-pointer px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50">
          手动修正字段
        </summary>
        <div className="border-t border-line p-3">
          <textarea
            className="w-full resize-y rounded-md border border-line bg-white px-3 py-2 text-sm leading-6 outline-none focus:border-slate-950"
            onChange={(event) => onChange(event.target.value)}
            rows={field.rows}
            value={value}
          />
        </div>
      </details>
    </section>
  );
}

function CompletedCapabilitiesCard({ projectId, value }: { projectId: string; value: string }) {
  const items = capabilityBulletItems(value, 3);
  return (
    <div className="rounded-md border border-emerald-200 bg-emerald-50 p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 gap-3">
          <span className="mt-1 flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-emerald-700 text-white">
            <ListChecks className="h-4 w-4" />
          </span>
          <div className="min-w-0">
            <p className="font-semibold text-emerald-950">能力与成果</p>
            <p className="mt-1 text-sm leading-5 text-emerald-900">已完成能力会整理成可解释、可复用的能力资产。</p>
          </div>
        </div>
        <span className="rounded-full bg-emerald-800 px-2.5 py-1 text-xs font-semibold text-white">{items.length} 项</span>
      </div>
      <ul className="mt-3 space-y-1 text-sm leading-6 text-emerald-950">
        {(items.length ? items : ["暂无已确认能力。"]).slice(0, 3).map((item) => (
          <li className="line-clamp-1" key={item}>- {item}</li>
        ))}
      </ul>
      <Link className="mt-3 inline-flex items-center gap-1 text-sm font-semibold text-emerald-900 hover:text-emerald-700" href={`/project-intelligence/capabilities?projectId=${projectId}`}>
        查看能力详情
        <ArrowRight className="h-4 w-4" />
      </Link>
    </div>
  );
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

function SmallEntryLink({ href, label, text }: { href: string; label: string; text: string }) {
  return (
    <Link className="block rounded-md bg-white px-3 py-2 text-sm transition hover:-translate-y-0.5 hover:shadow-sm" href={href}>
      <span className="font-semibold text-slate-950">{label}</span>
      <span className="ml-2 text-xs text-muted">{text}</span>
    </Link>
  );
}

function ArchiveEntryCard({
  count,
  href,
  label,
  latestAt,
  latestLabel,
  text,
  tone = "slate",
}: {
  count: number;
  href: string;
  label: string;
  latestAt?: string;
  latestLabel?: string;
  text: string;
  tone?: ArchiveEntryTone;
}) {
  const styles = archiveEntryToneStyles[tone];
  return (
    <Link className={`relative block overflow-hidden rounded-md border p-4 pl-5 transition hover:-translate-y-0.5 hover:bg-white hover:shadow-sm ${styles.card}`} href={href}>
      <span className={`absolute inset-y-0 left-0 w-1 ${styles.marker}`} />
      <div className="flex items-center justify-between gap-3">
        <p className="font-semibold text-slate-950">{label}</p>
        <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${styles.count}`}>{count} 条</span>
      </div>
      <div className="mt-3 flex flex-wrap items-center gap-2 text-xs text-muted">
        <span className={`rounded-md px-2 py-1 ${styles.chip}`}>{latestAt ? `最新 ${formatShortDate(latestAt)}` : "暂无更新"}</span>
        {latestLabel ? <span className={`max-w-full truncate rounded-md px-2 py-1 ${styles.chip}`}>{latestLabel}</span> : null}
      </div>
      <p className="mt-2 text-sm leading-5 text-slate-600">{text}</p>
    </Link>
  );
}

type ArchiveEntryTone = "emerald" | "sky" | "indigo" | "amber" | "rose" | "slate";

const archiveEntryToneStyles: Record<ArchiveEntryTone, { card: string; marker: string; count: string; chip: string }> = {
  emerald: {
    card: "border-emerald-200 bg-emerald-50",
    marker: "bg-emerald-700",
    count: "bg-emerald-800 text-white",
    chip: "bg-white text-emerald-900",
  },
  sky: {
    card: "border-sky-200 bg-sky-50",
    marker: "bg-sky-700",
    count: "bg-sky-800 text-white",
    chip: "bg-white text-sky-900",
  },
  indigo: {
    card: "border-indigo-200 bg-indigo-50",
    marker: "bg-indigo-700",
    count: "bg-indigo-800 text-white",
    chip: "bg-white text-indigo-900",
  },
  amber: {
    card: "border-amber-200 bg-amber-50",
    marker: "bg-amber-700",
    count: "bg-amber-800 text-white",
    chip: "bg-white text-amber-900",
  },
  rose: {
    card: "border-rose-200 bg-rose-50",
    marker: "bg-rose-700",
    count: "bg-rose-800 text-white",
    chip: "bg-white text-rose-900",
  },
  slate: {
    card: "border-slate-200 bg-slate-50",
    marker: "bg-slate-700",
    count: "bg-slate-800 text-white",
    chip: "bg-white text-slate-700",
  },
};

function latestAt(items: Array<{ createdAt?: string; updatedAt?: string }>) {
  return items
    .map((item) => item.updatedAt || item.createdAt || "")
    .filter(Boolean)
    .sort((left, right) => new Date(right).getTime() - new Date(left).getTime())[0];
}

function formatShortDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString(undefined, { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" });
}

function confirmedCountLabel(items: ProjectFactSource[]) {
  const confirmed = items.filter((item) => item.confirmedByUser).length;
  return confirmed ? `已确认 ${confirmed} 项` : "暂无确认来源";
}

function analysisTypeLabel(record: ProjectAnalysisRecord) {
  return record.recordType === "FILE" ? "最新文件分析" : "最新项目分析";
}
