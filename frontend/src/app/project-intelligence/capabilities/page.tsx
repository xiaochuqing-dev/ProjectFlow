"use client";

import { Suspense, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { ArrowLeft, Check, ChevronDown, Clipboard, RefreshCw, RotateCcw, Sparkles, X } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { Badge, Button, ProjectContextBar, Toast } from "@/components/ui";
import { useProjectSelection } from "@/hooks/useProjectSelection";
import {
  acknowledgeAnalysisFailure,
  cancelProjectAnalysisJob,
  getCapabilityAnalysisOverview,
  getProjectAnalysisJob,
  getProjectMemory,
  listProjectAnalysisJobs,
  listProjectCapabilityCards,
  retryProjectAnalysisJob,
  startCapabilityCardAnalysisJob,
  updateCapabilityCard,
  type CapabilityCard,
  type CapabilityAnalysisOverview,
  type ProjectAnalysisJob,
  type ProjectMemory,
} from "@/lib/api";
import { readSession } from "@/lib/auth";

// V3.3.4: 能力分析阶段中文映射。
const CAPABILITY_STAGE_LABELS: Record<string, string> = {
  QUEUED: "等待能力分析启动",
  LOAD_EVIDENCE: "正在读取已确认沉淀和开发推进段",
  MODEL_CAPABILITY_ANALYSIS: "正在调用模型分析项目能力",
  PERSIST_CAPABILITY_CARDS: "正在保存能力卡片",
  MODEL_REQUEST: "正在请求模型",
  MODEL_RESPONSE_RECEIVED: "模型已返回，正在解析",
  MODEL_RESPONSE_PARSE: "模型已返回，但解析失败",
  MODEL_OUTPUT_TRUNCATED: "模型输出达到长度上限",
  MODEL_EMPTY_CONTENT: "模型已响应但内容为空",
  MODEL_OUTPUT_NORMALIZE: "正在归一化模型字段",
  ITEM_VALIDATION: "正在逐项校验能力卡片",
  EVIDENCE_BINDING: "正在绑定来源证据",
  CONTENT_SANITIZE: "正在清洗用户可见内容",
  DATABASE_PERSIST: "正在保存能力卡片",
  JOB_RESULT_SERIALIZE: "正在整理任务结果",
  FRONTEND_REFRESH: "正在刷新页面状态",
  SUCCEEDED: "能力分析完成",
  SUCCEEDED_WITH_WARNINGS: "能力分析已完成，部分结果需复核",
  FAILED: "能力分析失败",
};

export default function CompletedCapabilitiesPage() {
  return (
    <Suspense fallback={<AppShell eyebrow="项目理解" title="能力与成果"><div className="h-1 bg-slate-950" /></AppShell>}>
      <CompletedCapabilitiesContent />
    </Suspense>
  );
}

function CompletedCapabilitiesContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const queryProjectId = searchParams.get("projectId") ?? "";
  const { projects, selectedProject, selectedProjectId, selectProject, loadingProjects, projectError } = useProjectSelection({ queryProjectId });
  const [cards, setCards] = useState<CapabilityCard[]>([]);
  const [memory, setMemory] = useState<ProjectMemory | null>(null);
  const [overview, setOverview] = useState<CapabilityAnalysisOverview | null>(null);
  const [loading, setLoading] = useState(false);
  const [actingId, setActingId] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  // V3.3.4: 能力分析异步任务。刷新/离开页面后回来能恢复正在运行的 job。
  const [capabilityJob, setCapabilityJob] = useState<ProjectAnalysisJob | null>(null);
  const [capabilityJobs, setCapabilityJobs] = useState<ProjectAnalysisJob[]>([]);
  const [startingJob, setStartingJob] = useState(false);
  const pollRef = useRef<number | null>(null);
  const pollFailureCount = useRef(0);

  useEffect(() => {
    const session = readSession();
    if (!session || !selectedProjectId) {
      setCards([]);
      setMemory(null);
      setOverview(null);
      setCapabilityJob(null);
      setCapabilityJobs([]);
      return;
    }
    setLoading(true);
    setError("");
    Promise.all([
      listProjectCapabilityCards(session.accessToken, selectedProjectId),
      getProjectMemory(session.accessToken, selectedProjectId),
      // V3.3.4: 进入页面时恢复正在运行的能力分析任务。
      listProjectAnalysisJobs(session.accessToken, selectedProjectId).catch(() => []),
      getCapabilityAnalysisOverview(session.accessToken, selectedProjectId),
    ])
      .then(([items, record, jobs, overviewRecord]) => {
        setCards(items);
        setMemory(record);
        setOverview(overviewRecord);
        const history = (jobs as ProjectAnalysisJob[])
          .filter((job) => job.jobType === "CAPABILITY_CARD_ANALYSIS")
          .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
        const active = history.find((job) => job.status === "QUEUED" || job.status === "RUNNING" || job.status === "CANCEL_REQUESTED");
        setCapabilityJobs(history);
        setCapabilityJob(active ?? history[0] ?? null);
      })
      .catch((exception) => setError(exception instanceof Error ? exception.message : "能力与成果加载失败"))
      .finally(() => setLoading(false));
  }, [selectedProjectId]);

  // V3.3.4: 轮询正在运行的能力分析任务，完成后重新拉取能力卡片。
  useEffect(() => {
    if (!capabilityJob || !["QUEUED", "RUNNING", "CANCEL_REQUESTED"].includes(capabilityJob.status)) {
      if (pollRef.current) {
        window.clearInterval(pollRef.current);
        pollRef.current = null;
      }
      return;
    }
    const session = readSession();
    if (!session) return;
    const jobId = capabilityJob.id;
    const accessToken = session.accessToken;
    let stopped = false;
    async function poll() {
      if (stopped) return;
      try {
        const updated = await getProjectAnalysisJob(accessToken, jobId);
        setCapabilityJob(updated);
        setCapabilityJobs((current) => [updated, ...current.filter((job) => job.id !== updated.id)].sort((a, b) => b.createdAt.localeCompare(a.createdAt)));
        pollFailureCount.current = 0;
        setError("");
        if (updated.status === "SUCCEEDED" || updated.status === "SUCCEEDED_WITH_WARNINGS") {
          // 完成后重新拉取能力卡片（已确认保留，候选被替换）。
          const items = await listProjectCapabilityCards(accessToken, updated.projectId);
          const overviewRecord = await getCapabilityAnalysisOverview(accessToken, updated.projectId);
          setCards(items);
          setOverview(overviewRecord);
          const result = updated.capabilityCardResult;
          setNotice(updated.status === "SUCCEEDED_WITH_WARNINGS"
            ? updated.warningMessage || `能力分析已完成，保存 ${result?.cardCount ?? items.length} 张卡片，其中 ${result?.needsEvidenceCount ?? 0} 张需要补充证据。`
            : `能力分析完成，已保存 ${result?.cardCount ?? items.length} 张能力卡片。`);
          stopped = true;
          if (pollRef.current) {
            window.clearInterval(pollRef.current);
            pollRef.current = null;
          }
        } else if (updated.status === "FAILED") {
          setError(updated.errorMessage || "能力分析失败，请稍后重试。");
          stopped = true;
          if (pollRef.current) {
            window.clearInterval(pollRef.current);
            pollRef.current = null;
          }
        }
      } catch (exception) {
        if (!stopped) {
          pollFailureCount.current += 1;
          setError(pollFailureCount.current < 3
            ? "状态刷新暂时失败，正在自动重试。后台任务可能仍在运行。"
            : "状态连续刷新失败，请检查本地服务连接。后台任务可能仍在运行。");
        }
      }
    }
    void poll();
    pollRef.current = window.setInterval(poll, 1500);
    return () => {
      stopped = true;
      if (pollRef.current) {
        window.clearInterval(pollRef.current);
        pollRef.current = null;
      }
    };
  }, [capabilityJob?.id, capabilityJob?.status]);

  function handleSelectProject(projectId: string) {
    selectProject(projectId);
    router.replace(`/project-intelligence/capabilities?projectId=${projectId}`);
  }

  // V3.3.4: 点击分析项目能力 -> 创建异步 job，后端异步执行，前端轮询。
  async function analyzeCapabilities() {
    const session = readSession();
    if (!session || !selectedProjectId) return;
    setStartingJob(true);
    setError("");
    setNotice("");
    try {
      const job = await startCapabilityCardAnalysisJob(session.accessToken, selectedProjectId);
      setCapabilityJob(job);
      setCapabilityJobs((current) => [job, ...current.filter((item) => item.id !== job.id)]);
      setNotice("能力分析已启动，页面可以离开，任务会继续运行。");
    } catch (exception) {
      const message = exception instanceof Error ? exception.message : "项目能力分析启动失败";
      setError(message);
    } finally {
      setStartingJob(false);
    }
  }

  async function dismissFailure(job: ProjectAnalysisJob) {
    const session = readSession();
    if (!session) return;
    try {
      const updated = await acknowledgeAnalysisFailure(session.accessToken, job.id);
      setCapabilityJobs((current) => current.map((item) => item.id === updated.id ? updated : item));
      if (capabilityJob?.id === updated.id) setCapabilityJob(updated);
      setError("");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "失败提示关闭失败");
    }
  }

  async function cancelCapabilityJob() {
    const session = readSession();
    if (!session || !capabilityJob) return;
    try {
      const updated = await cancelProjectAnalysisJob(session.accessToken, capabilityJob.id);
      setCapabilityJob(updated);
      setCapabilityJobs((current) => [updated, ...current.filter((item) => item.id !== updated.id)]);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "取消能力分析失败");
    }
  }

  async function retryCapabilityJob() {
    const session = readSession();
    if (!session || !capabilityJob) return;
    try {
      const updated = await retryProjectAnalysisJob(session.accessToken, capabilityJob.id);
      setCapabilityJob(updated);
      setCapabilityJobs((current) => [updated, ...current.filter((item) => item.id !== updated.id)]);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "重新运行能力分析失败");
    }
  }

  async function updateCard(card: CapabilityCard, action: "CONFIRM" | "IGNORE") {
    const session = readSession();
    if (!session) return;
    setActingId(card.id);
    setError("");
    try {
      const updated = await updateCapabilityCard(session.accessToken, card.id, action);
      setCards((current) => current.map((item) => item.id === updated.id ? updated : item));
      setNotice(action === "CONFIRM" ? "已确认这一张能力卡片，其他候选保持不变。" : "已忽略这一张候选能力。");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "能力卡片更新失败");
    } finally {
      setActingId("");
    }
  }

  async function copy(value: string) {
    try {
      await navigator.clipboard.writeText(value);
      setNotice("已复制可复用表达。");
    } catch {
      setError("复制失败，请手动复制。");
    }
  }

  const visibleCards = cards.filter((item) => item.status !== "IGNORED");
  const confirmedCount = visibleCards.filter((item) => item.status === "CONFIRMED").length;
  const analyzing = startingJob || (capabilityJob ? ["QUEUED", "RUNNING", "CANCEL_REQUESTED"].includes(capabilityJob.status) : false);
  const stage = capabilityJob?.stage ?? "";
  const stageMessage = capabilityJob?.stageMessage ?? "";
  const showProgress = analyzing && stage && stage !== "SUCCEEDED" && stage !== "SUCCEEDED_WITH_WARNINGS" && stage !== "FAILED";
  const elapsedMs = computeElapsedMs(capabilityJob);
  const inputSummary = parseInputSummary(capabilityJob?.inputSummary);
  const currentBatchId = visibleCards.find((card) => card.analysisJobId)?.analysisJobId ?? null;
  const currentSuccessJob = capabilityJobs.find((job) => job.id === currentBatchId)
    ?? capabilityJobs.find((job) => job.status === "SUCCEEDED" || job.status === "SUCCEEDED_WITH_WARNINGS")
    ?? null;
  const latestFailure = capabilityJobs.find((job) => job.status === "FAILED" && !job.failureAcknowledged) ?? null;
  const currentBatchCards = currentBatchId ? visibleCards.filter((card) => card.analysisJobId === currentBatchId) : [];
  const currentNeedsEvidence = currentBatchCards.filter((card) => card.status === "NEEDS_EVIDENCE").length;

  return (
    <AppShell eyebrow="项目理解" title={selectedProject ? `${selectedProject.name} · 能力与成果` : "能力与成果"}>
      <div className="min-h-[calc(100vh-4rem)] bg-surface p-6">
        <ProjectContextBar
          actions={<Link className="inline-flex items-center gap-1 rounded-md border border-line bg-white px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50" href={`/project-intelligence?projectId=${selectedProjectId}`}><ArrowLeft className="h-4 w-4" />回到项目理解</Link>}
          leadingExtras={<><Badge label={`${visibleCards.length} 张能力卡片`} tone={visibleCards.length ? "success" : "warning"} /><Badge label={`${confirmedCount} 张已确认`} /></>}
          onSelect={handleSelectProject}
          projects={projects}
          selectedProjectId={selectedProjectId}
        />

        <section className="rounded-md border border-line bg-white shadow-panel">
          <header className="flex flex-wrap items-start justify-between gap-4 border-b border-line p-5">
            <div className="max-w-2xl">
              <h2 className="text-xl font-semibold text-slate-950">整体项目能力分析</h2>
              <p className="mt-1 text-sm leading-6 text-slate-600">基于已确认项目沉淀及其证据引用，一次生成可逐条确认的结构化能力卡片。</p>
              <p className="mt-1 text-xs leading-5 text-slate-500">重新分析会替换未确认候选能力，已确认能力会保留。</p>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              {capabilityJob && (capabilityJob.status === "QUEUED" || capabilityJob.status === "RUNNING") ? (
                <Button onClick={cancelCapabilityJob} variant="secondary"><X className="h-4 w-4" />取消分析</Button>
              ) : null}
              {capabilityJob && ["CANCELLED", "FAILED", "INTERRUPTED", "RETRYABLE", "EXPIRED", "REJECTED"].includes(capabilityJob.status) ? (
                <Button onClick={retryCapabilityJob} variant="secondary"><RotateCcw className="h-4 w-4" />重新运行</Button>
              ) : null}
              <Button disabled={!selectedProjectId || analyzing} loading={analyzing} onClick={analyzeCapabilities} variant="primary">
                {analyzing ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Sparkles className="h-4 w-4" />}
                {overview?.pendingSedimentCount ? `分析 ${overview.pendingSedimentCount} 条新增沉淀` : "分析项目能力"}
              </Button>
              <span className="text-xs text-slate-500">确认后可生成能力解读</span>
            </div>
          </header>

          {overview ? (
            <div className="grid gap-3 border-b border-line bg-slate-50 p-5 sm:grid-cols-2 xl:grid-cols-5">
              <OverviewMetric label="上次成功" value={overview.lastSuccessfulAt ? new Date(overview.lastSuccessfulAt).toLocaleString("zh-CN") : "尚未分析"} />
              <OverviewMetric label="上次输入沉淀" value={`${overview.lastInputSedimentCount} 条`} />
              <OverviewMetric label="此后新增" value={`${overview.newSedimentCount} 条`} />
              <OverviewMetric label="此后更新" value={`${overview.updatedSedimentCount} 条`} />
              <OverviewMetric label="待能力分析" value={`${overview.pendingSedimentCount} 条`} />
            </div>
          ) : null}

          {currentSuccessJob ? (
            <div className="border-b border-line bg-emerald-50 px-5 py-4 text-sm leading-6 text-emerald-950">
              <div className="flex flex-wrap items-center gap-2">
                <p className="font-semibold">当前生效结果</p>
                <Badge label={currentSuccessJob.capabilityCardResult?.providerName || "模型"} tone="success" />
                <Badge label={currentSuccessJob.capabilityCardResult?.modelName || "模型名未记录"} />
              </div>
              <p className="mt-1">
                来自 {new Date(currentSuccessJob.completedAt ?? currentSuccessJob.createdAt).toLocaleString("zh-CN")} 的成功分析；
                当前批次 {currentBatchCards.length || currentSuccessJob.capabilityCardResult?.cardCount || 0} 张卡片，{currentNeedsEvidence || currentSuccessJob.capabilityCardResult?.needsEvidenceCount || 0} 张需要补证据。
              </p>
              <p className="text-xs text-emerald-800">生成方式：模型分析。每张新卡片均记录本次任务编号；旧版无批次卡片会单独标注。</p>
            </div>
          ) : visibleCards.length ? (
            <div className="border-b border-line bg-amber-50 px-5 py-3 text-sm text-amber-900">当前卡片来自旧版结果，来源时间或分析批次未知；不会把它们错误归入本次分析。</div>
          ) : null}

          {/* V3.3.4: 能力分析进度可视化。用户能看到当前阶段、已等待时间、输入规模。 */}
          {showProgress ? (
            <div className="border-b border-line bg-slate-50 px-5 py-3 text-xs leading-5">
              <div className="flex flex-wrap items-center gap-2">
                <RefreshCw className="h-3 w-3 animate-spin text-brand" />
                <span className="font-semibold text-slate-900">{CAPABILITY_STAGE_LABELS[stage] ?? stage}</span>
                {elapsedMs > 0 ? <span className="text-slate-500">已等待 {formatElapsed(elapsedMs)}</span> : null}
              </div>
              {stageMessage ? <p className="mt-1 text-slate-600">{stageMessage}</p> : null}
              {capabilityJob?.status === "QUEUED" ? <p className="mt-1 text-slate-500">前方约 {capabilityJob.queuePosition} 个任务；尚未产生模型费用。</p> : null}
              {capabilityJob ? <p className="mt-1 text-slate-500">模型请求 {capabilityJob.requestCount}/{capabilityJob.maxRequestCount} 次 · Token {capabilityJob.totalTokens}/{capabilityJob.maxTotalTokens}</p> : null}
              {inputSummary ? (
                <p className="mt-1 text-slate-500">
                  基于 {inputSummary.sedimentCount} 条已确认项目沉淀，其中 {inputSummary.pendingSedimentCount} 条此前待能力分析。
                </p>
              ) : null}
              <p className="mt-1 text-slate-500">页面可以离开，任务会继续运行，完成后能力卡片会自动刷新。</p>
            </div>
          ) : null}

          {visibleCards.length ? (
            <div className="divide-y divide-line">
              {visibleCards.map((card) => (
                <CapabilityCardRow acting={actingId === card.id} card={card} key={card.id} onCopy={copy} onUpdate={updateCard} />
              ))}
            </div>
          ) : !loading ? (
            <div className="p-8 text-center">
              <p className="font-semibold text-slate-950">还没有结构化能力卡片</p>
              <p className="mx-auto mt-2 max-w-xl text-sm leading-6 text-slate-600">先在沉淀确认中确认开发推进段，再点击“分析项目能力”。系统不会再从旧字符串字段生成模板卡片。</p>
            </div>
          ) : <div className="h-1 bg-slate-950" />}

          {memory?.completedCapabilities?.trim() ? (
            <details className="border-t border-line bg-slate-50 px-5 py-4">
              <summary className="cursor-pointer text-sm font-semibold text-slate-700">兼容档案字段</summary>
              <p className="mt-2 text-xs leading-5 text-slate-500">以下是旧版 completedCapabilities 开发者备注，不作为 V3.3.3 正式能力卡片的数据源。</p>
              <pre className="mt-3 whitespace-pre-wrap text-sm leading-6 text-slate-700">{memory.completedCapabilities}</pre>
            </details>
          ) : null}
        </section>

        {/* V3.3.3: 未配置模型时，明确提示去配置模型，不生成低质量本地模板卡片。 */}
        {error && error.includes("未配置模型") ? (
          <div className="mt-4 rounded-md border border-warning/30 bg-warning-soft p-4 text-sm leading-6 text-warning-fg">
            <p className="font-semibold">当前未配置模型，无法进行完整人话能力分析。</p>
            <p className="mt-1">ProjectFlow 不会用低质量本地模板伪装成完整模型分析。请先配置模型，再分析项目能力。</p>
            <Link className="mt-2 inline-flex items-center gap-1 font-semibold text-brand hover:text-brand-hover" href="/settings">
              去设置模型 <ArrowLeft className="h-3.5 w-3.5 rotate-180" />
            </Link>
          </div>
        ) : null}

        {/* V3.3.4: 任务失败时显示错误原因。 */}
        {latestFailure && !latestFailure.errorMessage?.includes("未配置模型") ? (
          <div className="mt-4 rounded-md border border-danger/30 bg-danger-soft p-4 text-sm leading-6 text-danger-fg">
            <p className="font-semibold">最近一次能力分析失败，当前仍展示上一次成功结果</p>
            <p className="mt-1">{latestFailure.errorMessage || "能力分析失败，请稍后重试。"}</p>
            <p className="mt-1 text-xs">分析时间：{new Date(latestFailure.completedAt ?? latestFailure.createdAt).toLocaleString("zh-CN")} · 失败阶段：{CAPABILITY_STAGE_LABELS[latestFailure.failureStage ?? ""] ?? "后端处理"} · 模型{latestFailure.modelReturned ? "已经返回" : "尚未返回"}</p>
            <p className="mt-1 text-xs">旧能力卡片没有被替换。</p>
            <div className="mt-3 flex flex-wrap gap-2">
              <Button disabled={analyzing} onClick={analyzeCapabilities} size="sm" variant="secondary">重试</Button>
              <Button onClick={() => dismissFailure(latestFailure)} size="sm" variant="secondary">关闭提示</Button>
              <details className="rounded-md border border-rose-200 bg-white px-3 py-1 text-xs">
                <summary className="cursor-pointer font-semibold">查看分析详情</summary>
                <pre className="mt-2 max-w-2xl whitespace-pre-wrap break-words text-slate-700">{formatDiagnostics(latestFailure.diagnosticsJson)}</pre>
              </details>
            </div>
          </div>
        ) : null}

        {capabilityJob?.status === "SUCCEEDED_WITH_WARNINGS" ? (
          <div className="mt-4 rounded-md border border-warning/30 bg-warning-soft p-4 text-sm leading-6 text-warning-fg">
            <p className="font-semibold">{capabilityJob.warningMessage || "能力分析已完成，部分结果需要复核。"}</p>
            <p className="mt-1">已保存 {capabilityJob.capabilityCardResult?.cardCount ?? visibleCards.length} 张有效卡片，{capabilityJob.capabilityCardResult?.needsEvidenceCount ?? 0} 张需要补充证据。</p>
            <details className="mt-2">
              <summary className="cursor-pointer font-semibold">分析详情</summary>
              <div className="mt-2 space-y-1 text-xs">
                <p>模型原始返回：{capabilityJob.capabilityCardResult?.rawResponsePresent ? "已收到" : "未收到"}</p>
                <p>JSON 自动修复：{capabilityJob.capabilityCardResult?.repaired ? "是" : "否"}</p>
                <p>识别 {capabilityJob.capabilityCardResult?.recognizedItems ?? 0} 项，丢弃 {capabilityJob.capabilityCardResult?.discardedItems ?? 0} 项。</p>
                <p>无效来源编号：{capabilityJob.capabilityCardResult?.invalidSourceIndexes ?? 0} 个。</p>
                <p>Provider / 模型：{capabilityJob.capabilityCardResult?.providerName || "未记录"} / {capabilityJob.capabilityCardResult?.modelName || "未记录"}</p>
                <p>finish reason：{capabilityJob.capabilityCardResult?.finishReason || "未返回"}</p>
                <p>Token：输入 {capabilityJob.capabilityCardResult?.promptTokens ?? 0} / 输出 {capabilityJob.capabilityCardResult?.completionTokens ?? 0} / 总计 {capabilityJob.capabilityCardResult?.totalTokens ?? 0}</p>
                <p>Max Tokens：Provider 上限 {capabilityJob.capabilityCardResult?.providerMaxTokens ?? 0} / 任务上限 {capabilityJob.capabilityCardResult?.taskPolicyMaxTokens ?? 0} / 实际生效 {capabilityJob.capabilityCardResult?.effectiveMaxTokens ?? 0}</p>
                <p>Temperature：配置 {capabilityJob.capabilityCardResult?.providerTemperature ?? 0} / 实际生效 {capabilityJob.capabilityCardResult?.effectiveTemperature ?? 0}</p>
                <p>请求超时：{capabilityJob.capabilityCardResult?.timeoutSeconds ?? 0} 秒 · 模型请求耗时 {capabilityJob.capabilityCardResult?.requestLatencyMs ?? 0} ms</p>
                <p>截断：{capabilityJob.capabilityCardResult?.outputTruncated ? "检测到" : "未检测到"} · 紧凑重试：{capabilityJob.capabilityCardResult?.compactRetryAttempted ? capabilityJob.capabilityCardResult.compactRetrySucceeded ? "已成功" : "已执行但仍为部分结果" : "未执行"}</p>
              </div>
            </details>
          </div>
        ) : null}

        {capabilityJobs.length ? (
          <details className="mt-4 rounded-md border border-line bg-white p-4 text-sm shadow-panel">
            <summary className="cursor-pointer font-semibold text-slate-800">查看历史能力分析（{capabilityJobs.length} 次）</summary>
            <div className="mt-3 divide-y divide-line">
              {capabilityJobs.map((job) => (
                <div className="grid gap-1 py-3 text-xs text-slate-600 md:grid-cols-[180px_130px_1fr]" key={job.id}>
                  <span>{new Date(job.createdAt).toLocaleString("zh-CN")}</span>
                  <span>{job.status === "FAILED" ? "失败" : job.status === "RUNNING" ? "运行中" : job.status === "QUEUED" ? "等待中" : "成功"}</span>
                  <span>{job.id === currentSuccessJob?.id ? "当前生效批次" : job.errorMessage || `${job.capabilityCardResult?.cardCount ?? 0} 张卡片 · ${job.capabilityCardResult?.providerName || "Provider 未记录"}`}</span>
                </div>
              ))}
            </div>
          </details>
        ) : null}

        <Toast error={(capabilityJob?.status === "FAILED" || error.includes("未配置模型")) ? projectError : error || projectError} notice={notice} />
        {loadingProjects ? <div className="fixed inset-x-0 bottom-0 h-1 bg-slate-950" /> : null}
      </div>
    </AppShell>
  );
}

function CapabilityCardRow({
  acting,
  card,
  onCopy,
  onUpdate,
}: {
  acting: boolean;
  card: CapabilityCard;
  onCopy: (value: string) => void;
  onUpdate: (card: CapabilityCard, action: "CONFIRM" | "IGNORE") => void;
}) {
  return (
    <article className="p-5">
      <details className="group">
        <summary className="flex cursor-pointer list-none items-start gap-3">
          <ChevronDown className="mt-1 h-4 w-4 shrink-0 text-slate-500 transition-transform group-open:rotate-180" />
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <h3 className="font-semibold text-slate-950 break-words">{card.name}</h3>
              <Badge label={statusLabel(card.status)} tone={card.status === "CONFIRMED" ? "success" : card.status === "NEEDS_EVIDENCE" ? "warning" : "slate"} />
              <Badge label={card.generationMode === "MODEL" ? `模型 · ${card.modelProvider}` : "本地事实摘要"} />
              <Badge label={card.analysisJobId ? `批次 ${card.analysisJobId.slice(0, 8)}` : "旧版结果 · 来源时间未知"} tone={card.analysisJobId ? "slate" : "warning"} />
              <span className="text-xs text-slate-400 group-open:hidden">查看详情</span>
            </div>
            <p className="mt-1 max-w-3xl text-sm leading-6 text-slate-600 break-words line-clamp-3">{card.summary}</p>
          </div>
        </summary>

        <div className="ml-7 mt-4 grid gap-4 lg:grid-cols-[minmax(0,1fr)_280px]">
          <dl className="space-y-3 text-sm leading-6">
            <Info label="解决什么问题" value={card.problemSolved} />
            <Info label="为什么重要" value={card.featureEntry} />
            <div>
              <dt className="text-xs font-semibold text-slate-700">可复用表达</dt>
              <dd className="mt-1">
                <dl className="space-y-2">
                  <Info label="README 表达" value={card.readmeExpression} copy={() => onCopy(card.readmeExpression)} />
                  <Info label="简历表达" value={card.resumeExpression} copy={() => onCopy(card.resumeExpression)} />
                  <Info label="面试表达" value={card.interviewExpression} />
                </dl>
              </dd>
            </div>
          </dl>
          <aside className="rounded-md bg-slate-50 p-4 text-xs leading-5 text-slate-600">
            <p className="font-semibold text-slate-800">来源证据</p>
            <p className="mt-2">{card.sourceRefs.length} 个来源，{card.evidenceRefs.length} 条证据。</p>
            {card.fallbackReason ? <p className="mt-2 text-amber-800">{card.fallbackReason}</p> : null}
            {card.legacyTruncated ? <p className="mt-2 text-amber-800">旧版内容可能已被永久截断，建议重新分析生成完整内容。</p> : null}
            {card.status !== "CONFIRMED" ? (
              <div className="mt-4 flex gap-2">
                <Button disabled={acting || card.status === "NEEDS_EVIDENCE"} onClick={() => onUpdate(card, "CONFIRM")} size="sm" variant="primary"><Check className="h-3.5 w-3.5" />确认此项</Button>
                <Button disabled={acting} onClick={() => onUpdate(card, "IGNORE")} size="sm" variant="secondary"><X className="h-3.5 w-3.5" />忽略</Button>
              </div>
            ) : null}
          </aside>
        </div>
      </details>
    </article>
  );
}

function Info({ label, value, copy }: { label: string; value: string; copy?: () => void }) {
  return (
    <div>
      <dt className="text-xs font-semibold text-slate-700">{label}</dt>
      <dd className="mt-1 flex max-w-3xl items-start gap-2 text-slate-800">
        <span className="min-w-0 break-words whitespace-pre-wrap">{value}</span>
        {copy ? <button aria-label={`复制${label}`} className="shrink-0 rounded-md p-1 text-slate-500 hover:bg-slate-100 hover:text-slate-900" onClick={copy} type="button"><Clipboard className="h-3.5 w-3.5" /></button> : null}
      </dd>
    </div>
  );
}

function OverviewMetric({ label, value }: { label: string; value: string }) {
  return <div className="rounded-md border border-line bg-white p-3"><p className="text-xs text-muted">{label}</p><p className="mt-1 text-sm font-semibold text-slate-950">{value}</p></div>;
}

function statusLabel(status: CapabilityCard["status"]) {
  if (status === "CONFIRMED") return "已确认";
  if (status === "NEEDS_EVIDENCE") return "需补证据";
  return "候选";
}

// V3.3.4: 计算已等待时间。
function computeElapsedMs(job: ProjectAnalysisJob | null | undefined): number {
  if (!job || !job.currentStepStartedAt) return 0;
  const start = new Date(job.currentStepStartedAt).getTime();
  if (!Number.isFinite(start)) return 0;
  const end = job.completedAt ? new Date(job.completedAt).getTime() : Date.now();
  return Math.max(0, end - start);
}

function formatElapsed(ms: number): string {
  if (ms < 1000) return `${ms} ms`;
  const seconds = Math.floor(ms / 1000);
  if (seconds < 60) return `${seconds} 秒`;
  const minutes = Math.floor(seconds / 60);
  const rem = seconds % 60;
  return `${minutes} 分 ${rem} 秒`;
}

function parseInputSummary(raw: string | undefined | null): { sedimentCount: number; pendingSedimentCount: number } | null {
  if (!raw || !raw.trim()) return null;
  try {
    const parsed = JSON.parse(raw);
    if (parsed && typeof parsed === "object" && typeof parsed.sedimentCount === "number") {
      return {
        sedimentCount: parsed.sedimentCount,
        pendingSedimentCount: typeof parsed.pendingSedimentCount === "number" ? parsed.pendingSedimentCount : 0,
      };
    }
    return null;
  } catch {
    return null;
  }
}

function formatDiagnostics(raw: string | null) {
  if (!raw) return "本次失败没有可用的模型响应诊断；请查看失败阶段和用户提示。";
  try {
    const value = JSON.parse(raw) as Record<string, unknown>;
    return [
      `Provider / 模型：${value.providerName || "未记录"} / ${value.modelName || "未记录"}`,
      `finish reason：${value.finishReason || "未返回"}`,
      `模型是否返回内容：${value.contentPresent ? "是" : "否"}`,
      `Token：输入 ${value.promptTokens || 0} / 输出 ${value.completionTokens || 0} / 总计 ${value.totalTokens || 0}`,
      `Max Tokens：Provider ${value.providerMaxTokens || 0} / 任务 ${value.taskPolicyMaxTokens || 0} / 实际 ${value.effectiveMaxTokens || 0}`,
      `Temperature：配置 ${value.providerTemperature ?? 0} / 实际 ${value.effectiveTemperature ?? 0}`,
      `输出截断：${value.truncated ? "是" : "否"}；紧凑重试：${value.compactRetryAttempted ? value.compactRetrySucceeded ? "成功" : "未恢复完整结果" : "未执行"}`,
    ].join("\n");
  } catch {
    return "诊断记录无法读取，但旧能力卡片仍保持不变。";
  }
}
