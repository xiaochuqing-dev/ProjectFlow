"use client";

import { Suspense, useCallback, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { AlertTriangle, CheckCircle2, GitBranch, RefreshCw, ScanSearch } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { Badge, PageContainer, ProjectContextBar } from "@/components/ui";
import { useProjectSelection } from "@/hooks/useProjectSelection";
import {
  cancelProjectAnalysisJob,
  getProjectEvolutionBridges,
  getProjectAnalysisJob,
  getProjectUnderstanding,
  listProjectAnalysisJobs,
  refreshProjectUnderstanding,
  retryProjectAnalysisJob,
  type ProjectAnalysisJob,
  type ProjectEvolutionBridge,
  type ProjectUnderstandingSnapshot,
  type UnderstandingSection,
} from "@/lib/api";

export default function ProjectUnderstandingPage() {
  return (
    <Suspense fallback={<AppShell eyebrow="V3.6 当前结构与演进" title="项目理解"><PageContainer><div className="h-1 bg-slate-950" /></PageContainer></AppShell>}>
      <ProjectUnderstandingContent />
    </Suspense>
  );
}

function ProjectUnderstandingContent() {
  const searchParams = useSearchParams();
  const selection = useProjectSelection({ queryProjectId: searchParams.get("projectId") ?? "" });
  const [snapshot, setSnapshot] = useState<ProjectUnderstandingSnapshot | null>(null);
  const [bridges, setBridges] = useState<ProjectEvolutionBridge[]>([]);
  const [job, setJob] = useState<ProjectAnalysisJob | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const active = job?.status === "QUEUED" || job?.status === "RUNNING" || job?.status === "CANCEL_REQUESTED";

  const loadSnapshot = useCallback(async (projectId: string, quiet = false) => {
    if (!selection.session || !projectId) {
      setSnapshot(null);
      setBridges([]);
      return;
    }
    if (!quiet) setLoading(true);
    try {
      const current = await getProjectUnderstanding(selection.session.accessToken, projectId);
      setSnapshot(current);
      setBridges(
        await getProjectEvolutionBridges(selection.session.accessToken, projectId, 0, 10)
          .then((page) => page.items)
          .catch(() => []),
      );
      setError("");
    } catch (exception) {
      setSnapshot(null);
      setBridges([]);
      if (!quiet) setError(exception instanceof Error ? exception.message : "项目理解读取失败");
    } finally {
      if (!quiet) setLoading(false);
    }
  }, [selection.session]);

  useEffect(() => {
    setJob(null);
    void loadSnapshot(selection.selectedProjectId);
    if (selection.session && selection.selectedProjectId) {
      void listProjectAnalysisJobs(selection.session.accessToken, selection.selectedProjectId)
        .then((jobs) => setJob(jobs.find((item) => item.jobType === "PROJECT_UNDERSTANDING_REFRESH") ?? null))
        .catch(() => undefined);
    }
  }, [loadSnapshot, selection.selectedProjectId, selection.session]);

  useEffect(() => {
    if (!active || !job || !selection.session) return;
    const timer = window.setTimeout(async () => {
      try {
        const latest = await getProjectAnalysisJob(selection.session!.accessToken, job.id);
        setJob(latest);
        if (latest.status === "SUCCEEDED" || latest.status === "SUCCEEDED_WITH_WARNINGS") {
          await loadSnapshot(latest.projectId, true);
        } else if (latest.status === "FAILED" || latest.status === "EXPIRED" || latest.status === "REJECTED") {
          setError(latest.errorMessage ?? "项目理解任务未完成");
          await loadSnapshot(latest.projectId, true);
        }
      } catch (exception) {
        setError(exception instanceof Error ? exception.message : "项目理解任务状态读取失败");
      }
    }, 1200);
    return () => window.clearTimeout(timer);
  }, [active, job, loadSnapshot, selection.session]);

  async function startRefresh() {
    if (!selection.session || !selection.selectedProjectId) return;
    setError("");
    try {
      setJob(await refreshProjectUnderstanding(selection.session.accessToken, selection.selectedProjectId));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "项目理解任务提交失败");
    }
  }

  async function cancelRefresh() {
    if (!selection.session || !job) return;
    try {
      setJob(await cancelProjectAnalysisJob(selection.session.accessToken, job.id));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "项目理解任务取消失败");
    }
  }

  async function retryRefresh() {
    if (!selection.session || !job) return;
    setError("");
    try {
      setJob(await retryProjectAnalysisJob(selection.session.accessToken, job.id));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "项目理解任务重试失败");
    }
  }

  return (
    <AppShell eyebrow="V3.6 当前结构与演进" title={selection.selectedProject ? `${selection.selectedProject.name} · 项目理解` : "项目理解"}>
      <PageContainer>
        <ProjectContextBar
          actions={(
            <>
              {active ? <button className="h-10 rounded-field border border-line bg-white px-4 text-sm font-semibold" onClick={() => void cancelRefresh()} type="button">取消任务</button> : null}
              {!active && job && ["FAILED", "INTERRUPTED", "RETRYABLE", "EXPIRED"].includes(job.status) ? <button className="h-10 rounded-field border border-line bg-white px-4 text-sm font-semibold" onClick={() => void retryRefresh()} type="button">重试上次任务</button> : null}
              <button
                className="inline-flex h-10 items-center gap-2 rounded-field bg-brand px-4 text-sm font-semibold text-white disabled:opacity-60"
                disabled={!selection.selectedProjectId || active}
                onClick={() => void startRefresh()}
                type="button"
              >
                <RefreshCw className={`h-4 w-4 ${active ? "animate-spin" : ""}`} />
                {active ? "理解中" : snapshot ? "刷新理解" : "开始理解"}
              </button>
            </>
          )}
          leadingExtras={snapshot ? (
            <>
              <Badge label={classificationLabel(snapshot.classification)} tone="slate" />
              <Badge label={snapshot.currentStatus === "CURRENT" ? "当前" : "已过期"} tone={snapshot.currentStatus === "CURRENT" ? "success" : "warning"} />
              <Badge label={`证据覆盖 ${percent(snapshot.evidenceCoverage.structureCoverage)}`} tone="slate" />
            </>
          ) : null}
          onSelect={selection.selectProject}
          projects={selection.projects}
          selectedProjectId={selection.selectedProjectId}
        />

        {job && active ? (
          <div className="mb-5 rounded-card border border-blue-200 bg-blue-50 p-4 text-sm text-blue-950">
            <p className="font-semibold">{job.stageMessage || "正在建立项目理解"}</p>
            <p className="mt-1 text-xs">阶段 {job.stage || "QUEUED"} · 请求 {job.requestCount}/{job.maxRequestCount} · 刷新页面不会中断</p>
          </div>
        ) : null}
        {error ? <div className="mb-5 flex gap-2 rounded-card border border-amber-200 bg-amber-50 p-4 text-sm text-amber-950"><AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" /><span>{error}</span></div> : null}

        {snapshot ? <UnderstandingView bridges={bridges} snapshot={snapshot} /> : (
          <section className="grid min-h-80 place-items-center rounded-card border border-line bg-white p-8 text-center shadow-card">
            <div>
              <ScanSearch className="mx-auto h-10 w-10 text-brand" />
              <h2 className="mt-4 font-semibold text-slate-950">{loading ? "正在读取项目理解…" : "还没有当前项目理解"}</h2>
              <p className="mt-2 max-w-xl text-sm leading-6 text-muted">绑定本地目录后运行。系统会先扫描结构和证据，再按规模决定是否调用模型；没有 Git 或模型也能得到诚实的确定性结果。</p>
            </div>
          </section>
        )}
      </PageContainer>
    </AppShell>
  );
}

function UnderstandingView({
  snapshot,
  bridges,
}: {
  snapshot: ProjectUnderstandingSnapshot;
  bridges: ProjectEvolutionBridge[];
}) {
  const sections: Array<[string, UnderstandingSection]> = [
    ["项目身份", snapshot.identity],
    ["技术组成", snapshot.technology],
    ["结构边界", snapshot.structure],
    ["架构理解", snapshot.architecture],
    ["能力理解", snapshot.capabilities],
    ["工程状态", snapshot.engineeringState],
  ];
  return (
    <div className="space-y-5">
      <section className="grid gap-4 rounded-card border border-line bg-white p-5 shadow-card md:grid-cols-2 xl:grid-cols-4">
        <Metric label="文件 / 源码" value={`${snapshot.intake.fileCount} / ${snapshot.intake.sourceFileCount}`} />
        <Metric label="估算代码行" value={snapshot.intake.estimatedLoc.toLocaleString("zh-CN")} />
        <Metric label="结构来源" value={snapshot.analysisPlan.structureProvider} />
        <Metric label="语义模式" value={semanticLabel(snapshot.analysisPlan.semanticMode)} />
      </section>

      <section className="grid gap-4 xl:grid-cols-2">
        {sections.map(([title, section]) => <SectionCard key={title} section={section} title={title} />)}
      </section>

      <section className="grid gap-4 xl:grid-cols-2">
        <div className="rounded-card border border-line bg-white p-5 shadow-card">
          <h2 className="font-semibold text-slate-950">证据与可信度</h2>
          <div className="mt-4 grid grid-cols-2 gap-3 text-sm">
            <Metric label="已观察" value={snapshot.evidenceCoverage.observedClaims} />
            <Metric label="模型推断" value={snapshot.evidenceCoverage.inferredClaims} />
            <Metric label="有证据判断" value={snapshot.evidenceCoverage.evidenceBoundClaims} />
            <Metric label="整体置信度" value={confidenceLabel(snapshot.quality.confidence)} />
          </div>
          <p className="mt-4 text-xs leading-5 text-muted">结构索引 {snapshot.structureIndexVersion} · 语义规则 {snapshot.modelAnalysisVersion} · {snapshot.quality.modelUsed ? "模型参与" : "未调用模型"}{snapshot.quality.cacheHit ? " · 本次命中缓存" : ""}</p>
        </div>
        <div className="rounded-card border border-line bg-white p-5 shadow-card">
          <h2 className="font-semibold text-slate-950">仍然未知</h2>
          {snapshot.unknowns.length ? <ul className="mt-4 space-y-2 text-sm leading-6 text-slate-700">{snapshot.unknowns.map((item) => <li className="flex gap-2" key={item}><AlertTriangle className="mt-1 h-4 w-4 shrink-0 text-amber-600" />{item}</li>)}</ul> : <p className="mt-4 flex items-center gap-2 text-sm text-emerald-800"><CheckCircle2 className="h-4 w-4" />当前没有额外未知项。</p>}
        </div>
      </section>

      <section className="rounded-card border border-line bg-white p-5 shadow-card">
        <h2 className="flex items-center gap-2 font-semibold text-slate-950"><GitBranch className="h-4 w-4" />仓库接入状态</h2>
        <p className="mt-3 text-sm text-slate-700">{snapshot.intake.git.available ? `Git ${snapshot.intake.git.branch}，${snapshot.intake.git.commitCount} 次提交，工作区 ${snapshot.intake.git.worktreeState}` : "没有 Git：当前结构仍可理解，但历史演进明确标记为不可用。"}</p>
        <p className="mt-2 text-xs text-muted">分析时间 {new Date(snapshot.analyzedAt).toLocaleString("zh-CN")} · 指标来源 {snapshot.intake.metricsSource}</p>
      </section>

      <section className="rounded-card border border-line bg-white p-5 shadow-card">
        <h2 className="font-semibold text-slate-950">证据支持的项目演进</h2>
        {bridges.length ? (
          <div className="mt-4 space-y-4">
            {bridges.slice(0, 5).map((bridge) => (
              <article className="rounded-field border border-line bg-slate-50 p-4" key={bridge.id}>
                <div className="flex flex-wrap items-center gap-2">
                  <Badge label={bridge.epistemicStatus === "OBSERVED" ? "已观察" : "证据推断"} tone={bridge.epistemicStatus === "OBSERVED" ? "success" : "slate"} />
                  <span className="text-xs text-muted">{bridge.affectedAreaLabel} · {new Date(bridge.occurredAt).toLocaleDateString("zh-CN")}</span>
                </div>
                <p className="mt-2 text-sm font-semibold text-slate-950">{bridge.meaningfulChange}</p>
                <div className="mt-3 grid gap-2 text-xs leading-5 text-slate-700 md:grid-cols-3">
                  <p><span className="font-semibold">之前：</span>{bridge.beforeState}</p>
                  <p><span className="font-semibold">变化：</span>{bridge.changedPaths.slice(0, 3).join("、")}</p>
                  <p><span className="font-semibold">之后：</span>{bridge.afterState}</p>
                </div>
              </article>
            ))}
          </div>
        ) : <p className="mt-3 text-sm text-muted">尚无同时具备真实 Git 提交、ProjectFact 和结构区域证据的演进桥；系统不会为填充页面编造历史。</p>}
      </section>
    </div>
  );
}

function SectionCard({ title, section }: { title: string; section: UnderstandingSection }) {
  return (
    <article className="rounded-card border border-line bg-white p-5 shadow-card">
      <h2 className="font-semibold text-slate-950">{title}</h2>
      <p className="mt-2 text-sm leading-6 text-slate-700">{section.summary || "证据不足，保持未知。"}</p>
      {section.claims.length ? <div className="mt-4 space-y-3 border-t border-line pt-4">{section.claims.map((claim) => (
        <div key={claim.id}>
          <div className="flex flex-wrap items-center gap-2"><Badge label={statusLabel(claim.epistemicStatus)} tone={claim.epistemicStatus === "OBSERVED" ? "success" : "slate"} /><span className="text-xs text-muted">{confidenceLabel(claim.confidence)} · {claim.evidenceRefs.length} 条证据</span></div>
          <p className="mt-1 text-sm leading-6 text-slate-700">{claim.text}</p>
        </div>
      ))}</div> : null}
    </article>
  );
}

function Metric({ label, value }: { label: string; value: string | number }) {
  return <div className="rounded-field bg-slate-50 p-3"><p className="text-xs text-muted">{label}</p><p className="mt-1 break-words font-semibold text-slate-950">{value}</p></div>;
}

function percent(value: number) { return `${Math.round(value * 100)}%`; }
function confidenceLabel(value: string) { return value === "HIGH" ? "高" : value === "LOW" ? "低" : "中"; }
function statusLabel(value: string) { return value === "OBSERVED" ? "已观察" : value === "EXPLAINED" ? "用户说明" : "模型推断"; }
function semanticLabel(value: string) {
  const labels: Record<string, string> = { ONE_PASS_BOUNDED: "有界单次归纳", UNAVAILABLE: "模型不可用", SKIPPED_EMPTY: "空目录跳过", SKIPPED_NON_CODE: "非代码跳过" };
  return labels[value] ?? value;
}
function classificationLabel(value: string) {
  const labels: Record<string, string> = { EMPTY: "空目录", UNKNOWN_NON_CODE: "未识别代码", CODE_NO_GIT: "无 Git 代码", SMALL: "小型项目", MEDIUM: "中型项目", LARGE: "大型项目", HUGE_MONOREPO: "超大 / 多工作区" };
  return labels[value] ?? value;
}
