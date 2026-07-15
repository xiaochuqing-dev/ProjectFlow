"use client";

import Link from "next/link";
import { Suspense, useEffect, useMemo, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Archive, ChevronRight, RefreshCw } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { Badge } from "@/components/ui";
import { useProjectSelection } from "@/hooks/useProjectSelection";
import {
  getProjectFactHistoryState,
  listProjectRecordBatches,
  type ProjectFactHistoryState,
  type ProjectRecordBatch,
} from "@/lib/api";
import { readSession } from "@/lib/auth";
import { formatProjectRecordRange, groupProjectRecordBatches } from "@/lib/project-fact-memory";
import { factHistoryStatusLabel, modelStatusLabel, projectRecordBatchStatusLabel } from "@/lib/status-labels";

const PAGE_SIZE = 40;

export default function SedimentReviewPage() {
  return (
    <Suspense fallback={<AppShell title="项目记录"><div className="p-8 text-sm text-muted">正在加载项目记录…</div></AppShell>}>
      <ProjectRecordsContent />
    </Suspense>
  );
}

function ProjectRecordsContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const queryProjectId = searchParams.get("projectId") ?? "";
  const { projects, selectedProjectId, selectProject, loadingProjects, projectError } = useProjectSelection({ queryProjectId });
  const [batches, setBatches] = useState<ProjectRecordBatch[]>([]);
  const [historyState, setHistoryState] = useState<ProjectFactHistoryState | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState("");
  const requestVersion = useRef(0);
  const loadedProjectId = useRef("");

  useEffect(() => {
    void refresh(selectedProjectId);
  }, [selectedProjectId]);

  const grouped = useMemo(() => groupProjectRecordBatches(batches), [batches]);

  async function refresh(projectId: string) {
    const session = readSession();
    const version = ++requestVersion.current;
    if (!session || !projectId) {
      loadedProjectId.current = "";
      setBatches([]);
      setHistoryState(null);
      setLoading(false);
      return;
    }
    if (loadedProjectId.current !== projectId) {
      loadedProjectId.current = projectId;
      setBatches([]);
      setHistoryState(null);
      setPage(0);
      setTotalPages(0);
    }
    setLoading(true);
    setLoadingMore(false);
    setError("");
    const [batchResult, historyResult] = await Promise.allSettled([
      listProjectRecordBatches(session.accessToken, projectId, 0, PAGE_SIZE),
      getProjectFactHistoryState(session.accessToken, projectId),
    ]);
    if (version !== requestVersion.current) return;
    if (batchResult.status === "fulfilled") {
      setBatches(batchResult.value.items ?? []);
      setPage(batchResult.value.page ?? 0);
      setTotalPages(batchResult.value.totalPages ?? 0);
    } else {
      setError(batchResult.reason instanceof Error ? batchResult.reason.message : "项目记录加载失败");
    }
    if (historyResult.status === "fulfilled") {
      setHistoryState(historyResult.value);
    } else if (batchResult.status === "fulfilled") {
      setError("项目记录已加载，历史记忆补齐状态暂时不可用。");
    }
    setLoading(false);
  }

  async function loadMore() {
    const session = readSession();
    if (!session || !selectedProjectId || loadingMore || page + 1 >= totalPages) return;
    const version = requestVersion.current;
    setLoadingMore(true);
    setError("");
    try {
      const result = await listProjectRecordBatches(session.accessToken, selectedProjectId, page + 1, PAGE_SIZE);
      if (version !== requestVersion.current) return;
      setBatches((current) => [...current, ...(result.items ?? [])]);
      setPage(result.page ?? page + 1);
      setTotalPages(result.totalPages ?? totalPages);
    } catch (exception) {
      if (version === requestVersion.current) setError(exception instanceof Error ? exception.message : "更多项目记录加载失败");
    } finally {
      if (version === requestVersion.current) setLoadingMore(false);
    }
  }

  function changeProject(projectId: string) {
    selectProject(projectId);
    router.replace(`/sediment-review?projectId=${projectId}`);
  }

  return (
    <AppShell
      eyebrow="按事实发生时间和分析批次浏览"
      title="项目记录"
      actions={<button className="inline-flex items-center gap-2 rounded-md border border-line bg-white px-3 py-2 text-sm font-semibold" disabled={loading || !selectedProjectId} onClick={() => void refresh(selectedProjectId)} type="button"><RefreshCw className={`h-4 w-4 ${loading ? "animate-spin" : ""}`} />刷新</button>}
    >
      <div className="space-y-6 p-8">
        <section className="rounded-md border border-line bg-white p-5 shadow-panel">
          <div className="flex flex-wrap items-end justify-between gap-4">
            <div>
              <h2 className="font-semibold text-slate-950">选择项目</h2>
              <p className="mt-1 max-w-3xl text-sm leading-6 text-muted">ProjectFlow 会在分析新变化后自动归并并保存项目事实。这里按分析批次查看项目从过去到现在发生过什么。</p>
            </div>
            <select className="min-w-64 rounded-md border border-line bg-white px-3 py-2 text-sm" disabled={loadingProjects} onChange={(event) => changeProject(event.target.value)} value={selectedProjectId}>
              <option value="">请选择项目</option>
              {projects.map((project) => <option key={project.id} value={project.id}>{project.name}</option>)}
            </select>
          </div>
        </section>

        {historyState ? <HistoryProgress state={historyState} /> : null}

        {error || projectError ? (
          <p className="rounded-md border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
            {error || projectError}{batches.length > 0 ? " 已保留当前可用记录。" : ""}
          </p>
        ) : null}

        {grouped.map((group) => (
          <section className="space-y-3" key={group.key}>
            <div className="flex items-center gap-2"><Archive className="h-4 w-4 text-slate-500" /><h2 className="font-semibold">{group.label}</h2><Badge label={`${group.items.length} 个批次`} tone="slate" /></div>
            <div className="grid gap-4 xl:grid-cols-2">
              {group.items.map((batch) => <BatchCard batch={batch} projectId={selectedProjectId} key={batch.batchId} />)}
            </div>
          </section>
        ))}

        {page + 1 < totalPages ? (
          <div className="flex justify-center"><button className="rounded-md border border-line bg-white px-4 py-2 text-sm font-semibold disabled:opacity-60" disabled={loadingMore} onClick={() => void loadMore()} type="button">{loadingMore ? "加载中…" : "加载更早记录"}</button></div>
        ) : null}

        {!loading && !error && selectedProjectId && batches.length === 0 ? (
          <section className="rounded-md border border-dashed border-line bg-white p-10 text-center">
            <p className="font-semibold">暂无项目记录</p>
            <p className="mt-2 text-sm text-muted">先回到工作台执行“分析新变化”，完成后项目事实会自动出现在这里，无需逐条确认。</p>
            <Link className="mt-4 inline-flex rounded-md bg-slate-950 px-4 py-2 text-sm font-semibold text-white" href={`/dashboard?projectId=${selectedProjectId}`}>去分析新变化</Link>
          </section>
        ) : null}
      </div>
    </AppShell>
  );
}

function BatchCard({ batch, projectId }: { batch: ProjectRecordBatch; projectId: string }) {
  const attention = batch.attentionCount ?? 0;
  const failed = batch.batchStatus === "FAILED";
  return (
    <article className="rounded-md border border-line bg-white p-5 shadow-panel">
      <div className="flex flex-wrap items-center gap-2">
        <Badge label={projectRecordBatchStatusLabel(batch.batchStatus, attention)} tone={failed ? "warning" : attention ? "warning" : "success"} />
        <Badge label={batchSourceLabel(batch)} tone="slate" />
      </div>
      <p className="mt-3 text-xs text-muted">{formatProjectRecordRange(batch)}</p>
      <h3 className="mt-2 font-semibold text-slate-950">{batch.branchName || "当前分支"} · {batch.commitCount ?? 0} 个提交</h3>
      <p className="mt-2 text-sm text-slate-600">涉及 {batch.changedFileCount ?? 0} 个文件，读取 {batch.agentResultCount ?? 0} 个 Agent result。</p>
      <div className="mt-4 grid grid-cols-2 gap-2 text-sm sm:grid-cols-4">
        <Metric label="项目事实" value={batch.factCount ?? 0} />
        <Metric label="需要关注" value={attention} />
        <Metric label="提交" value={batch.commitCount ?? 0} />
        <Metric label="文件" value={batch.changedFileCount ?? 0} />
      </div>
      {attention > 0 ? <p className="mt-3 rounded-md bg-amber-50 px-3 py-2 text-xs text-amber-900">异常项不会阻塞批次完成或下一次分析，可进入批次查看原因和证据。</p> : null}
      <div className="mt-4 flex gap-2">
        <Link className="inline-flex flex-1 items-center justify-center gap-1 rounded-md bg-slate-950 px-3 py-2 text-sm font-semibold text-white" href={`/sediment-review/${batch.batchId}?projectId=${projectId}`}>
          查看批次记录<ChevronRight className="h-4 w-4" />
        </Link>
        {attention > 0 || batch.needsReanalysis ? <Link className="rounded-md border border-line px-3 py-2 text-sm font-semibold" href={`/dashboard?projectId=${projectId}`}>重新分析</Link> : null}
      </div>
    </article>
  );
}

function HistoryProgress({ state }: { state: ProjectFactHistoryState }) {
  const total = Math.max(0, state.totalCommitCount ?? 0);
  const covered = Math.max(0, state.coveredCommitCount ?? 0);
  const percent = total > 0 ? Math.min(100, Math.round(covered / total * 100)) : 0;
  return (
    <section className="rounded-md border border-blue-200 bg-blue-50 p-5 text-sm text-blue-950">
      <div className="flex flex-wrap items-center justify-between gap-3"><p className="font-semibold">{factHistoryStatusLabel(state.status)}</p><span>{covered} / {total} commits</span></div>
      <div className="mt-3 h-2 overflow-hidden rounded-full bg-white"><div className="h-full bg-blue-600" style={{ width: `${percent}%` }} /></div>
      {state.status === "RUNNING" ? <p className="mt-2 text-xs leading-5">后台正在分批补齐项目历史，离开页面后任务仍会继续。</p> : null}
      {state.errorSummary ? <p className="mt-2 text-xs leading-5 text-amber-900">{state.errorSummary}</p> : null}
    </section>
  );
}

function batchSourceLabel(batch: ProjectRecordBatch) {
  if (batch.scanType === "HISTORY_BACKFILL") return "历史记忆补齐";
  if (batch.scanType?.startsWith("LEGACY")) return "历史数据兼容";
  return modelStatusLabel(batch.modelStatus, batch.modelProvider);
}

function Metric({ label, value }: { label: string; value: number }) {
  return <div className="rounded-md bg-slate-50 p-2"><p className="text-xs text-muted">{label}</p><p className="mt-1 font-semibold">{value}</p></div>;
}
