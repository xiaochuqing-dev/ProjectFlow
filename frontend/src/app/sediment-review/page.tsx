"use client";

import Link from "next/link";
import { Suspense, useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Archive, ChevronRight, RefreshCw } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { Badge } from "@/components/ui";
import { useProjectSelection } from "@/hooks/useProjectSelection";
import { listSedimentReviewBatches, type SedimentReviewBatch } from "@/lib/api";
import { readSession } from "@/lib/auth";

const groupLabels: Record<SedimentReviewBatch["timeGroup"], string> = {
  TODAY: "今天",
  YESTERDAY: "昨天",
  THIS_WEEK: "本周",
  EARLIER: "更早",
};

export default function SedimentReviewPage() {
  return (
    <Suspense fallback={<AppShell title="沉淀处理中心"><div className="p-8 text-sm text-muted">正在加载分析批次…</div></AppShell>}>
      <SedimentReviewContent />
    </Suspense>
  );
}

function SedimentReviewContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const queryProjectId = searchParams.get("projectId") ?? "";
  const { projects, selectedProjectId, selectProject, loadingProjects, projectError } = useProjectSelection({ queryProjectId });
  const [batches, setBatches] = useState<SedimentReviewBatch[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    void refresh(selectedProjectId);
  }, [selectedProjectId]);

  const grouped = useMemo(() => {
    return (["TODAY", "YESTERDAY", "THIS_WEEK", "EARLIER"] as const).map((group) => ({
      group,
      items: batches.filter((batch) => batch.timeGroup === group),
    })).filter((entry) => entry.items.length > 0);
  }, [batches]);

  async function refresh(projectId: string) {
    const session = readSession();
    if (!session || !projectId) {
      setBatches([]);
      return;
    }
    setLoading(true);
    setError("");
    try {
      setBatches(await listSedimentReviewBatches(session.accessToken, projectId));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "沉淀批次加载失败");
    } finally {
      setLoading(false);
    }
  }

  function changeProject(projectId: string) {
    selectProject(projectId);
    router.replace(`/sediment-review?projectId=${projectId}`);
  }

  return (
    <AppShell
      eyebrow="按时间和扫描批次逐步处理"
      title="沉淀处理中心"
      actions={<button className="inline-flex items-center gap-2 rounded-md border border-line bg-white px-3 py-2 text-sm font-semibold" disabled={loading || !selectedProjectId} onClick={() => void refresh(selectedProjectId)} type="button"><RefreshCw className={`h-4 w-4 ${loading ? "animate-spin" : ""}`} />刷新</button>}
    >
      <div className="space-y-6 p-8">
        <section className="rounded-md border border-line bg-white p-5 shadow-panel">
          <div className="flex flex-wrap items-end justify-between gap-4">
            <div>
              <h2 className="font-semibold text-slate-950">选择项目</h2>
              <p className="mt-1 text-sm text-muted">首页只展示批次摘要，具体建议进入批次后逐条处理。</p>
            </div>
            <select className="min-w-64 rounded-md border border-line bg-white px-3 py-2 text-sm" disabled={loadingProjects} onChange={(event) => changeProject(event.target.value)} value={selectedProjectId}>
              <option value="">请选择项目</option>
              {projects.map((project) => <option key={project.id} value={project.id}>{project.name}</option>)}
            </select>
          </div>
        </section>

        {error || projectError ? (
          <p className="rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-800">
            {error || projectError}{error && batches.length > 0 ? " 已保留当前可用批次。" : ""}
          </p>
        ) : null}

        {grouped.map(({ group, items }) => (
          <section className="space-y-3" key={group}>
            <div className="flex items-center gap-2"><Archive className="h-4 w-4 text-slate-500" /><h2 className="font-semibold">{groupLabels[group]}</h2><Badge label={`${items.length} 个批次`} tone="slate" /></div>
            <div className="grid gap-4 xl:grid-cols-2">
              {items.map((batch) => <BatchCard batch={batch} projectId={selectedProjectId} key={batch.batchId} />)}
            </div>
          </section>
        ))}

        {!loading && !error && selectedProjectId && batches.length === 0 ? (
          <section className="rounded-md border border-dashed border-line bg-white p-10 text-center">
            <p className="font-semibold">暂无分析批次</p>
            <p className="mt-2 text-sm text-muted">先回到工作台执行“分析新变化”，结果会按批次出现在这里。</p>
            <Link className="mt-4 inline-flex rounded-md bg-slate-950 px-4 py-2 text-sm font-semibold text-white" href={`/dashboard?projectId=${selectedProjectId}`}>去分析新变化</Link>
          </section>
        ) : null}
      </div>
    </AppShell>
  );
}

function BatchCard({ batch, projectId }: { batch: SedimentReviewBatch; projectId: string }) {
  const sourceLabel = batch.resultSource === "MODEL_RESULT" ? "完整模型分析" : batch.resultSource === "MODEL_PARTIAL_RESULT" ? "部分模型结果" : batch.resultSource === "LOCAL_FACT_DRAFT" ? "本地事实草稿" : batch.resultSource === "LEGACY_INCOMPLETE" ? "历史数据不完整" : "需要重新分析";
  return (
    <article className="rounded-md border border-line bg-white p-5 shadow-panel">
      <div className="flex flex-wrap items-center gap-2">
        <Badge label={sourceLabel} tone={batch.resultSource === "MODEL_RESULT" ? "success" : "warning"} />
        <span className="text-xs text-muted">{new Date(batch.scanStartedAt).toLocaleString("zh-CN")}</span>
      </div>
      <h3 className="mt-3 font-semibold text-slate-950">{batch.branchName || "当前分支"} · {batch.commitCount} 个提交</h3>
      <p className="mt-2 text-sm text-slate-600">涉及 {batch.changedFileCount} 个文件，读取 {batch.agentResultCount} 个 Agent result。</p>
      <div className="mt-4 grid grid-cols-2 gap-2 text-sm sm:grid-cols-4">
        <Metric label="正式建议" value={batch.formalSuggestionCount} />
        <Metric label="本地草稿" value={batch.localDraftCount} />
        <Metric label="已处理" value={batch.processedCount} />
        <Metric label="待处理" value={batch.pendingCount} />
      </div>
      {batch.needsReanalysis ? <p className="mt-3 rounded-md bg-amber-50 px-3 py-2 text-xs text-amber-800">本批次包含未完成模型分析的事实草稿，可查看事实或重新分析。</p> : null}
      <div className="mt-4 flex gap-2">
        <Link className="inline-flex flex-1 items-center justify-center gap-1 rounded-md bg-slate-950 px-3 py-2 text-sm font-semibold text-white" href={`/sediment-review/${batch.batchId}?projectId=${projectId}`}>
          {batch.pendingCount > 0 ? `继续处理 ${batch.pendingCount} 条` : "查看批次详情"}<ChevronRight className="h-4 w-4" />
        </Link>
        {batch.needsReanalysis ? <Link className="rounded-md border border-line px-3 py-2 text-sm font-semibold" href={`/dashboard?projectId=${projectId}`}>重新分析</Link> : null}
      </div>
    </article>
  );
}

function Metric({ label, value }: { label: string; value: number }) {
  return <div className="rounded-md bg-slate-50 p-2"><p className="text-xs text-muted">{label}</p><p className="mt-1 font-semibold">{value}</p></div>;
}
