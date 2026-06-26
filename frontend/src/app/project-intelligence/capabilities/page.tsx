"use client";

import { Suspense, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ArrowLeft, CheckCircle2, Clipboard, ListChecks, RefreshCw, ShieldCheck, Sparkles } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { Badge, ProjectContextBar, Toast } from "@/components/ui";
import { useProjectSelection } from "@/hooks/useProjectSelection";
import { getProjectMemory, updateProjectMemory, type CapabilityInterpretResponse, type ProjectAnalysisJob, type ProjectMemory, type ProjectMemoryPayload } from "@/lib/api";
import { readSession } from "@/lib/auth";
import { buildCapabilityAssets, type CapabilityAsset, type CapabilityAssetStatus } from "@/lib/capability-assets";
import { useProjectAnalysisJobs } from "@/lib/use-project-analysis-jobs";

export default function CompletedCapabilitiesPage() {
  return (
    <Suspense fallback={<AppShell eyebrow="项目理解" title="能力与成果"><div className="min-h-[calc(100vh-4rem)] bg-surface p-6"><div className="h-1 bg-slate-950" /></div></AppShell>}>
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
  const [notice, setNotice] = useState("");
  const [activeName, setActiveName] = useState<string | null>(null);
  const capabilityAssets = useMemo(() => buildCapabilityAssets(memory), [memory]);
  const activeAsset = capabilityAssets.find((item) => item.name === activeName) ?? null;
  const { jobs, enqueueCapabilityInterpret } = useProjectAnalysisJobs(selectedProjectId);
  const interpretJobs = useMemo(
    () => jobs.filter((job) => job.jobType === "CAPABILITY_INTERPRET"),
    [jobs],
  );
  const [adoptedSummary, setAdoptedSummary] = useState("");

  function interpretJobFor(asset: CapabilityAsset): ProjectAnalysisJob | null {
    return interpretJobs.find((job) => (job.filePath ?? "") === asset.rawFact) ?? null;
  }

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
      .catch((exception) => setError(exception instanceof Error ? exception.message : "能力与成果加载失败"))
      .finally(() => setLoading(false));
  }, [selectedProjectId]);

  async function copyExpression(value: string) {
    try {
      await navigator.clipboard.writeText(value);
      setNotice("已复制可复用表达。");
    } catch {
      setError("复制失败，请手动复制表达内容。");
    }
  }

  async function generateInterpret(asset: CapabilityAsset) {
    const session = readSession();
    if (!session || !selectedProjectId) {
      return;
    }
    setError("");
    try {
      await enqueueCapabilityInterpret(asset.rawFact);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "提交能力解读任务失败。");
    }
  }

  async function adoptCandidate(asset: CapabilityAsset, candidate: CapabilityInterpretResponse) {
    const session = readSession();
    if (!session || !selectedProjectId || !memory) {
      return;
    }
    setError("");
    try {
      const existing = memory.completedCapabilities || "";
      const line = `- ${candidate.candidate.summary}`;
      const next = existing ? `${existing.replace(/暂无已确认能力。?/, "").trim()}\n${line}` : line;
      const payload: ProjectMemoryPayload = {
        positioning: memory.positioning,
        currentStage: memory.currentStage,
        completedCapabilities: next,
        inProgressCapabilities: memory.inProgressCapabilities,
        currentRisks: memory.currentRisks,
        technicalDecisions: memory.technicalDecisions,
        developerLearnings: memory.developerLearnings,
        showcaseAssets: memory.showcaseAssets,
        nextStepSuggestions: memory.nextStepSuggestions,
      };
      const updated = await updateProjectMemory(session.accessToken, selectedProjectId, payload);
      setMemory(updated);
      setAdoptedSummary(candidate.candidate.summary);
      setNotice("候选解读已采纳为正式能力说明。");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "采纳失败。");
    }
  }

  return (
    <AppShell eyebrow="项目理解" title={selectedProject ? `${selectedProject.name} · 能力与成果` : "能力与成果"}>
      <div className="min-h-[calc(100vh-4rem)] bg-surface p-6">
        <ProjectContextBar
          actions={(
            <Link className="inline-flex items-center gap-1 rounded-md border border-line bg-white px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50" href={`/project-intelligence?projectId=${selectedProjectId}`}>
              <ArrowLeft className="h-4 w-4" />
              回到项目理解
            </Link>
          )}
          leadingExtras={(
            <>
              <Badge label={`${capabilityAssets.length} 项能力`} tone={capabilityAssets.length ? "success" : "warning"} />
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
                <h2 className="text-xl font-semibold text-slate-950">能力与成果</h2>
                <p className="mt-1 text-sm leading-6 text-slate-600">把已确认能力整理成可解释、有证据、可用于 README / 简历 / 面试的能力资产。</p>
              </div>
            </div>
            <span className="rounded-full bg-emerald-800 px-3 py-1 text-sm font-semibold text-white">{capabilityAssets.length} 项</span>
          </div>

          <div className="grid gap-4 p-5 md:grid-cols-2 xl:grid-cols-3">
            {capabilityAssets.map((asset) => {
              const job = interpretJobFor(asset);
              return (
                <CapabilityAssetCard
                  adopted={adoptedSummary && job?.status === "SUCCEEDED" && job.capabilityInterpretResult?.candidate.summary === adoptedSummary ? null : adoptedSummary}
                  asset={asset}
                  job={job}
                  key={asset.name}
                  onAdopt={(result) => adoptCandidate(asset, result)}
                  onCopy={copyExpression}
                  onGenerate={() => generateInterpret(asset)}
                  onView={() => setActiveName(asset.name)}
                />
              );
            })}
            {capabilityAssets.length === 0 ? (
              <p className="rounded-md border border-line bg-slate-50 p-4 text-sm text-muted">暂无已确认能力。采纳开发成果或保存项目资产后，会先形成能力候选，再经用户确认进入这里。</p>
            ) : null}
          </div>
        </section>

        {activeAsset ? (
          <CapabilityAssetDetailDrawer asset={activeAsset} onClose={() => setActiveName(null)} onCopy={copyExpression} />
        ) : null}

        <Toast error={error || projectError} notice={notice} />
        {loading || loadingProjects ? <div className="fixed inset-x-0 bottom-0 h-1 bg-slate-950" /> : null}
      </div>
    </AppShell>
  );
}

function CapabilityAssetCard({
  asset,
  adopted,
  job,
  onAdopt,
  onCopy,
  onGenerate,
  onView,
}: {
  asset: CapabilityAsset;
  adopted: string | null;
  job: ProjectAnalysisJob | null;
  onAdopt: (result: CapabilityInterpretResponse) => void;
  onCopy: (value: string) => void;
  onGenerate: () => void;
  onView: () => void;
}) {
  const interpreting = job != null && (job.status === "QUEUED" || job.status === "RUNNING");
  return (
    <article className="flex flex-col rounded-md border border-emerald-100 bg-emerald-50 p-4">
      <div className="mb-3 flex items-start justify-between gap-3">
        <div className="flex items-center gap-2">
          <CheckCircle2 className="h-4 w-4 shrink-0 text-emerald-700" />
          <h3 className="text-base font-semibold text-emerald-950">{asset.name}</h3>
        </div>
        <StatusBadge status={asset.status} />
      </div>
      <p className="text-sm leading-6 text-emerald-950">{asset.oneLine}</p>
      <div className="mt-3 flex flex-wrap gap-1.5">
        {asset.scenes.map((scene) => (
          <span key={scene} className="rounded-full bg-white px-2 py-0.5 text-xs font-medium text-emerald-800">{scene}</span>
        ))}
      </div>
      <div className="mt-3 flex items-center gap-2 text-xs text-emerald-700">
        <ShieldCheck className="h-3.5 w-3.5" />
        <span>{asset.evidenceCount} 条来源证据</span>
      </div>
      <div className="mt-4 flex flex-wrap items-center gap-2">
        <button className="inline-flex items-center gap-1 rounded-md bg-emerald-800 px-3 py-1.5 text-xs font-semibold text-white hover:bg-emerald-900" onClick={onView} type="button">
          查看详情
        </button>
        <button className="inline-flex items-center gap-1 rounded-md bg-white px-2 py-1 text-xs font-semibold text-emerald-900 hover:bg-emerald-100" onClick={() => onCopy(asset.reusableExpression)} type="button">
          <Clipboard className="h-3.5 w-3.5" />
          复制表达
        </button>
        <button className="ml-auto inline-flex items-center gap-1 rounded-md border border-emerald-300 px-2 py-1 text-xs font-semibold text-emerald-800 hover:bg-white disabled:opacity-60" disabled={interpreting} onClick={onGenerate} type="button" title="生成候选解读，采纳后才进入正式项目资产">
          {interpreting ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <Sparkles className="h-3.5 w-3.5" />}
          生成能力解读
        </button>
      </div>
      {adopted ? (
        <p className="mt-3 rounded-md border border-emerald-200 bg-white px-3 py-2 text-xs text-emerald-800">已采纳候选：{adopted}</p>
      ) : null}
      {job?.status === "QUEUED" || job?.status === "RUNNING" ? (
        <p className="mt-3 inline-flex items-center gap-1 rounded-md border border-sky-200 bg-sky-50 px-3 py-2 text-xs text-sky-800">
          <RefreshCw className="h-3.5 w-3.5 animate-spin" />
          正在生成候选解读，刷新或离开页面不会丢失。
        </p>
      ) : null}
      {job?.status === "FAILED" ? (
        <p className="mt-3 rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">生成失败：{job.errorMessage ?? "请重新生成。"}</p>
      ) : null}
      {job?.status === "SUCCEEDED" && job.capabilityInterpretResult ? (
        <CapabilityCandidatePanel result={job.capabilityInterpretResult} onAdopt={() => onAdopt(job.capabilityInterpretResult!)} onRetry={onGenerate} />
      ) : null}
    </article>
  );
}

function CapabilityCandidatePanel({ result, onAdopt, onRetry }: { result: CapabilityInterpretResponse; onAdopt: () => void; onRetry: () => void }) {
  const candidate = result.candidate;
  return (
    <div className="mt-3 rounded-md border border-sky-200 bg-sky-50 p-3">
      <div className="mb-2 flex items-center gap-2">
        <Sparkles className="h-4 w-4 text-sky-700" />
        <span className="text-xs font-semibold text-sky-900">候选解读</span>
        <span className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${result.degraded ? "bg-amber-100 text-amber-800" : "bg-sky-100 text-sky-800"}`}>
          {result.source === "MODEL" ? "模型生成" : "本地规则"}
        </span>
      </div>
      <p className="text-xs leading-5 text-sky-800">{result.message}</p>
      <dl className="mt-2 space-y-1 text-xs leading-5 text-sky-950">
        <div><dt className="font-semibold">说明</dt><dd>{candidate.summary}</dd></div>
        <div><dt className="font-semibold">解决的问题</dt><dd>{candidate.problem}</dd></div>
        <div><dt className="font-semibold">简历表达</dt><dd>{candidate.resume}</dd></div>
      </dl>
      <div className="mt-2 flex gap-2">
        <button className="rounded-md bg-sky-700 px-3 py-1 text-xs font-semibold text-white hover:bg-sky-800" onClick={onAdopt} type="button">采纳为正式能力说明</button>
        <button className="rounded-md border border-sky-300 px-3 py-1 text-xs font-semibold text-sky-800 hover:bg-white" onClick={onRetry} type="button">重新生成</button>
      </div>
    </div>
  );
}

function CapabilityAssetDetailDrawer({ asset, onClose, onCopy }: { asset: CapabilityAsset; onClose: () => void; onCopy: (value: string) => void }) {
  return (
    <div className="fixed inset-0 z-40 flex justify-end" role="dialog" aria-modal="true">
      <button className="absolute inset-0 bg-slate-950/40" onClick={onClose} type="button" aria-label="关闭详情" />
      <div className="relative flex h-full w-full max-w-xl flex-col overflow-y-auto bg-white shadow-xl">
        <div className="sticky top-0 flex items-center justify-between border-b border-line bg-white px-6 py-4">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="h-5 w-5 text-emerald-700" />
            <h2 className="text-lg font-semibold text-slate-950">{asset.name}</h2>
          </div>
          <button className="rounded-md px-3 py-1.5 text-sm font-semibold text-slate-600 hover:bg-slate-100" onClick={onClose} type="button">关闭</button>
        </div>
        <div className="space-y-5 p-6">
          <DetailBlock title="能力说明" value={asset.oneLine} />
          <DetailBlock title="解决什么问题" value={asset.detail.problem} />
          <DetailBlock title="为什么重要" value={asset.detail.importance} />
          <div>
            <p className="text-xs font-semibold text-slate-700">已识别内容</p>
            <ul className="mt-1 space-y-1 text-sm leading-6 text-slate-800">
              {asset.detail.recognized.map((item) => <li key={item}>- {item}</li>)}
            </ul>
          </div>
          <details className="rounded-md border border-line bg-slate-50">
            <summary className="flex cursor-pointer items-center gap-2 px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100">
              <ShieldCheck className="h-4 w-4" />
              来源证据
            </summary>
            <ul className="space-y-1 border-t border-line p-3 text-sm leading-6 text-slate-800">
              {asset.detail.evidence.map((item) => <li key={item}>- {item}</li>)}
            </ul>
          </details>
          <DetailBlock title="可复用表达 · README" value={asset.detail.readme} action={(
            <button className="mt-2 inline-flex items-center gap-1 rounded-md border border-line px-2 py-1 text-xs font-semibold text-slate-700 hover:bg-slate-50" onClick={() => onCopy(asset.detail.readme)} type="button">
              <Clipboard className="h-3.5 w-3.5" /> 复制
            </button>
          )} />
          <DetailBlock title="可复用表达 · 简历" value={asset.detail.resume} action={(
            <button className="mt-2 inline-flex items-center gap-1 rounded-md border border-line px-2 py-1 text-xs font-semibold text-slate-700 hover:bg-slate-50" onClick={() => onCopy(asset.detail.resume)} type="button">
              <Clipboard className="h-3.5 w-3.5" /> 复制
            </button>
          )} />
          <DetailBlock title="面试讲解点" value={asset.detail.interview} />
          <div className="flex items-center justify-between rounded-md border border-line bg-slate-50 px-4 py-3">
            <div className="flex items-center gap-2">
              <StatusBadge status={asset.status} />
              <span className="text-xs text-muted">{asset.evidenceCount} 条来源证据</span>
            </div>
            <Link className="text-sm font-semibold text-emerald-800 hover:text-emerald-700" href={`/project-intelligence?projectId=`}>手动修正入口</Link>
          </div>
        </div>
      </div>
    </div>
  );
}

function StatusBadge({ status }: { status: CapabilityAssetStatus }) {
  const styles: Record<CapabilityAssetStatus, string> = {
    "已确认": "bg-emerald-100 text-emerald-800",
    "可补充": "bg-amber-100 text-amber-800",
    "待补证据": "bg-slate-200 text-slate-700",
  };
  return <span className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${styles[status]}`}>{status}</span>;
}

function DetailBlock({ title, value, action }: { title: string; value: string; action?: React.ReactNode }) {
  return (
    <div>
      <p className="text-xs font-semibold text-slate-700">{title}</p>
      <p className="mt-1 text-sm leading-6 text-slate-800">{value}</p>
      {action}
    </div>
  );
}
