"use client";

import Link from "next/link";
import { useParams, useSearchParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { ArrowLeft, ArrowRight, Check, RefreshCw } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { Badge } from "@/components/ui";
import {
  confirmProjectChange,
  getSedimentReviewBatch,
  listProjectSediments,
  type ProjectSediment,
  type SedimentAction,
  type SedimentConfirmation,
  type SedimentReviewBatchDetail,
} from "@/lib/api";
import { readSession } from "@/lib/auth";

export default function SedimentReviewBatchPage() {
  const params = useParams<{ batchId: string }>();
  const searchParams = useSearchParams();
  const projectId = searchParams.get("projectId") ?? "";
  const [detail, setDetail] = useState<SedimentReviewBatchDetail | null>(null);
  const [sediments, setSediments] = useState<ProjectSediment[]>([]);
  const [index, setIndex] = useState(0);
  const [action, setAction] = useState<SedimentAction>("NEW_SEDIMENT");
  const [targetId, setTargetId] = useState("");
  const [feedback, setFeedback] = useState<SedimentConfirmation | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const pendingItems = useMemo(() => detail?.formalSuggestions.filter((item) => item.status === "PENDING" || item.status === "EDITED") ?? [], [detail]);
  const current = pendingItems[Math.min(index, Math.max(0, pendingItems.length - 1))];

  useEffect(() => {
    void refresh();
  }, [params.batchId]);

  useEffect(() => {
    if (!current) return;
    setAction(current.suggestedAction || "NEW_SEDIMENT");
    setTargetId(current.targetSedimentId ?? "");
  }, [current?.changeId]);

  async function refresh() {
    const session = readSession();
    if (!session || !params.batchId) return;
    setLoading(true);
    setError("");
    try {
      const [batch, sedimentItems] = await Promise.all([
        getSedimentReviewBatch(session.accessToken, params.batchId),
        projectId ? listProjectSediments(session.accessToken, projectId) : Promise.resolve([]),
      ]);
      setDetail(batch);
      setSediments(sedimentItems);
      setIndex((value) => Math.min(value, Math.max(0, batch.batch.pendingCount - 1)));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "批次详情加载失败");
    } finally {
      setLoading(false);
    }
  }

  async function confirmCurrent() {
    const session = readSession();
    if (!session || !current) return;
    const needsTarget = action === "MERGE_EXISTING" || action === "EVIDENCE_ONLY";
    if (needsTarget && !targetId) {
      setError("请选择要更新的项目沉淀");
      return;
    }
    setSaving(true);
    setError("");
    try {
      const result = await confirmProjectChange(session.accessToken, current.changeId, action, needsTarget ? targetId : null);
      setFeedback(result);
      await refresh();
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "沉淀确认失败");
    } finally {
      setSaving(false);
    }
  }

  const centerPath = projectId ? `/sediment-review?projectId=${projectId}` : "/sediment-review";
  return (
    <AppShell eyebrow="逐条决策并实时更新批次进度" title="处理分析批次">
      <div className="space-y-5 p-8">
        <Link className="inline-flex items-center gap-1 text-sm font-semibold text-slate-700" href={centerPath}><ArrowLeft className="h-4 w-4" />返回沉淀处理中心</Link>
        {error ? <p className="rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-800">{error}</p> : null}
        {feedback ? <ConfirmationFeedback feedback={feedback} onClose={() => setFeedback(null)} /> : null}

        {detail ? (
          <section className="rounded-md border border-line bg-white p-5 shadow-panel">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <p className="text-xs text-muted">{new Date(detail.batch.scanStartedAt).toLocaleString("zh-CN")} · {detail.batch.branchName}</p>
                <h2 className="mt-1 font-semibold">批次进度 {detail.batch.processedCount} / {detail.batch.formalSuggestionCount}</h2>
              </div>
              <div className="flex gap-2"><Badge label={`正式建议 ${detail.batch.formalSuggestionCount}`} tone="brand" /><Badge label={`本地草稿 ${detail.batch.localDraftCount}`} tone={detail.batch.localDraftCount ? "warning" : "slate"} /></div>
            </div>
            <div className="mt-4 h-2 overflow-hidden rounded-full bg-slate-100"><div className="h-full bg-slate-950" style={{ width: `${detail.batch.formalSuggestionCount ? Math.round(detail.batch.processedCount / detail.batch.formalSuggestionCount * 100) : 100}%` }} /></div>
          </section>
        ) : null}

        {current ? (
          <section className="rounded-md border border-line bg-white shadow-panel">
            <div className="border-b border-line p-5">
              <div className="flex flex-wrap items-center gap-2">
                <Badge label={`${index + 1} / ${pendingItems.length}`} tone="slate" />
                <Badge label={sourceLabel(current.contentSource)} tone={current.contentSource === "MODEL_RESULT" ? "success" : "warning"} />
                <Badge label={qualityLabel(current.qualityStatus)} tone={current.qualityStatus === "PASS" ? "success" : "warning"} />
              </div>
              <h2 className="mt-4 text-xl font-semibold text-slate-950">{current.title}</h2>
              <p className="mt-2 leading-7 text-slate-600">{current.summary}</p>
              <p className="mt-3 text-sm text-muted">{current.evidenceCount} 条证据 · 本次涉及 {current.affectedFileCount} 个文件</p>
            </div>
            <div className="grid gap-5 p-5 lg:grid-cols-[minmax(0,1fr)_320px]">
              <div className="space-y-4">
                <div className={`rounded-md p-4 text-sm ${current.recommendationStrength === "HIGH" ? "bg-blue-50 text-blue-950" : "bg-slate-50 text-slate-700"}`}>
                  <p className="font-semibold">{strengthLabel(current.recommendationStrength)} · {actionLabel(current.suggestedAction || "NEW_SEDIMENT")}</p>
                  <p className="mt-1 text-xs leading-5">推荐只作为决策辅助；你可以在右侧调整处理方式。</p>
                </div>
                <details className="rounded-md border border-line p-4 text-sm"><summary className="cursor-pointer font-semibold">查看证据与诊断入口</summary><div className="mt-3 flex gap-3"><Link className="font-semibold text-blue-700" href={`/project-changes/${current.changeId}`}>查看完整建议与证据</Link>{detail?.batch.needsReanalysis ? <Link className="font-semibold text-amber-700" href={projectId ? `/dashboard?projectId=${projectId}` : "/dashboard"}>重新模型分析</Link> : null}</div></details>
              </div>
              <div className="space-y-3 rounded-md border border-line p-4">
                <label className="block text-sm font-semibold">处理方式<select className="mt-2 w-full rounded-md border border-line bg-white px-3 py-2 font-normal" onChange={(event) => setAction(event.target.value as SedimentAction)} value={action}><option value="NEW_SEDIMENT">新建项目沉淀</option><option value="MERGE_EXISTING">合并到已有沉淀</option><option value="EVIDENCE_ONLY">只补充证据</option><option value="IGNORE">暂不沉淀</option></select></label>
                {action === "MERGE_EXISTING" || action === "EVIDENCE_ONLY" ? <label className="block text-sm font-semibold">目标沉淀<select className="mt-2 w-full rounded-md border border-line bg-white px-3 py-2 font-normal" onChange={(event) => setTargetId(event.target.value)} value={targetId}><option value="">请选择</option>{sediments.map((sediment) => <option value={sediment.id} key={sediment.id}>{sediment.title}</option>)}</select></label> : null}
                <button className="inline-flex w-full items-center justify-center gap-2 rounded-md bg-slate-950 px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-60" disabled={saving} onClick={() => void confirmCurrent()} type="button">{saving ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />}{actionLabel(action)}</button>
                <button className="w-full rounded-md border border-line px-4 py-2 text-sm font-semibold" onClick={() => setIndex((value) => Math.min(pendingItems.length - 1, value + 1))} type="button">跳过，处理下一条</button>
                <Link className="block text-center text-xs font-semibold text-muted" href={centerPath}>稍后处理并返回</Link>
              </div>
            </div>
            <div className="flex justify-between border-t border-line p-4"><button className="inline-flex items-center gap-1 text-sm font-semibold disabled:opacity-40" disabled={index === 0} onClick={() => setIndex((value) => Math.max(0, value - 1))} type="button"><ArrowLeft className="h-4 w-4" />上一条</button><button className="inline-flex items-center gap-1 text-sm font-semibold disabled:opacity-40" disabled={index >= pendingItems.length - 1} onClick={() => setIndex((value) => Math.min(pendingItems.length - 1, value + 1))} type="button">下一条<ArrowRight className="h-4 w-4" /></button></div>
          </section>
        ) : !loading ? <section className="rounded-md border border-line bg-white p-10 text-center"><p className="font-semibold">本批次正式建议已处理完</p><p className="mt-2 text-sm text-muted">本地事实草稿仍保留在下方，不会被自动写入项目沉淀。</p></section> : null}

        {detail?.localDrafts.length ? <section className="rounded-md border border-amber-200 bg-amber-50 p-5"><h2 className="font-semibold text-amber-950">本地事实草稿 {detail.localDrafts.length} 条</h2><p className="mt-1 text-sm text-amber-800">这些内容未经过完整模型语义归并，可能重复、模板化或不完整，不会自动成为正式成果。</p><div className="mt-4 space-y-2">{detail.localDrafts.map((draft) => <article className="rounded-md bg-white p-4" key={draft.id}><p className="font-semibold">{draft.title}</p><p className="mt-1 line-clamp-2 text-sm text-slate-600">{draft.plainSummary}</p></article>)}</div></section> : null}
      </div>
    </AppShell>
  );
}

function ConfirmationFeedback({ feedback, onClose }: { feedback: SedimentConfirmation; onClose: () => void }) {
  return <section className="rounded-md border border-emerald-200 bg-emerald-50 p-5 text-sm text-emerald-950"><div className="flex justify-between gap-4"><div><p className="font-semibold">{feedback.actionLabel}完成</p><p className="mt-1">{feedback.resultMessage}</p><p className="mt-2">新增证据 {feedback.evidenceAdded} 条 · 本次涉及文件 {feedback.filesAdded} 个 · {feedback.summaryUpdated ? "摘要已更新" : "摘要保持不变"}</p><p className="mt-1">{feedback.usedByNextCapabilityAnalysis ? "已进入待能力分析" : "未进入能力分析"}</p></div><button className="text-xs font-semibold" onClick={onClose} type="button">关闭</button></div>{feedback.sedimentPath ? <Link className="mt-3 inline-flex font-semibold text-emerald-800" href={feedback.sedimentPath}>查看项目沉淀</Link> : null}</section>;
}

function sourceLabel(value: string) { if (value === "MODEL_RESULT") return "完整模型分析"; if (value === "MODEL_PARTIAL_RESULT") return "部分模型恢复"; if (value === "AGENT_RESULT_DRAFT") return "Agent result 草稿"; if (value === "LEGACY_INCOMPLETE") return "历史数据不完整"; return "本地事实草稿"; }
function qualityLabel(value: string) { if (value === "PASS") return "可确认"; if (value === "NEEDS_EVIDENCE") return "缺证据"; if (value === "NEEDS_CHINESE_REWRITE") return "需中文整理"; return "需复核"; }
function strengthLabel(value: string) { if (value === "HIGH") return "高可信推荐"; if (value === "MEDIUM") return "中等可信推荐"; if (value === "NOT_RECOMMENDED") return "不建议自动推荐"; return "仅供参考"; }
function actionLabel(value: SedimentAction | "") { if (value === "MERGE_EXISTING") return "合并并确认"; if (value === "EVIDENCE_ONLY") return "补充证据并确认"; if (value === "IGNORE") return "暂不沉淀"; return "新建并确认"; }
