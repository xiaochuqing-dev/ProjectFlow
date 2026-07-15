"use client";

import Link from "next/link";
import { useParams, useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { ArrowLeft, ChevronDown, RefreshCw } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { Badge } from "@/components/ui";
import {
  getProjectFact,
  getProjectRecordBatch,
  listProjectFacts,
  type ProjectFact,
  type ProjectFactSummary,
  type ProjectRecordBatch,
} from "@/lib/api";
import { readSession } from "@/lib/auth";
import { compactProjectPath } from "@/lib/project-insights";
import { formatFactOccurredRange, formatProjectRecordRange } from "@/lib/project-fact-memory";
import {
  factOriginLabel,
  factRecordStatusLabel,
  factSourceModeLabel,
  projectRecordBatchStatusLabel,
  qualityStatusLabel,
} from "@/lib/status-labels";

export default function ProjectRecordBatchPage() {
  const params = useParams<{ batchId: string }>();
  const searchParams = useSearchParams();
  const queryProjectId = searchParams.get("projectId") ?? "";
  const [batch, setBatch] = useState<ProjectRecordBatch | null>(null);
  const [facts, setFacts] = useState<ProjectFactSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const requestVersion = useRef(0);
  const loadedBatchId = useRef("");

  useEffect(() => {
    void refresh();
  }, [params.batchId]);

  async function refresh() {
    const session = readSession();
    if (!session || !params.batchId) return;
    const version = ++requestVersion.current;
    if (loadedBatchId.current !== params.batchId) {
      loadedBatchId.current = params.batchId;
      setBatch(null);
      setFacts([]);
    }
    setLoading(true);
    setError("");
    try {
      const result = await getProjectRecordBatch(session.accessToken, params.batchId);
      if (version !== requestVersion.current) return;
      const firstPage = result.facts;
      let allFacts = firstPage?.items ?? [];
      const projectId = result.batch.projectId || queryProjectId;
      const totalPages = firstPage?.totalPages ?? 1;
      if (projectId && totalPages > 1) {
        const remaining = await Promise.all(Array.from({ length: totalPages - 1 }, (_, index) =>
          listProjectFacts(session.accessToken, projectId, {
            batchId: result.batch.batchId,
            page: index + 1,
            size: firstPage.size || 20,
          })));
        allFacts = [...allFacts, ...remaining.flatMap((page) => page.items ?? [])];
      }
      if (version !== requestVersion.current) return;
      setBatch(result.batch);
      setFacts(allFacts);
    } catch (exception) {
      if (version === requestVersion.current) setError(exception instanceof Error ? exception.message : "批次记录加载失败");
    } finally {
      if (version === requestVersion.current) setLoading(false);
    }
  }

  const projectId = batch?.projectId || queryProjectId;
  const centerPath = projectId ? `/sediment-review?projectId=${projectId}` : "/sediment-review";
  return (
    <AppShell eyebrow="一次查看本批次全部项目事实" title="批次记录">
      <div className="space-y-5 p-8">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <Link className="inline-flex items-center gap-1 text-sm font-semibold text-slate-700" href={centerPath}><ArrowLeft className="h-4 w-4" />返回项目记录</Link>
          <button className="inline-flex items-center gap-2 rounded-md border border-line bg-white px-3 py-2 text-sm font-semibold disabled:opacity-60" disabled={loading} onClick={() => void refresh()} type="button"><RefreshCw className={`h-4 w-4 ${loading ? "animate-spin" : ""}`} />刷新</button>
        </div>
        {error ? <p className="rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-800">{error}{facts.length ? " 已保留当前可用事实。" : ""}</p> : null}

        {batch ? <BatchSummary batch={batch} /> : null}

        {facts.length ? (
          <section className="space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-3"><h2 className="text-lg font-semibold text-slate-950">本批次项目事实</h2><Badge label={`${facts.length} 条`} tone="brand" /></div>
            {facts.map((fact) => <FactCard fact={fact} key={fact.id} projectId={projectId} />)}
          </section>
        ) : !loading && batch ? (
          <section className="rounded-md border border-dashed border-line bg-white p-10 text-center"><p className="font-semibold">本批次暂无可展示事实</p><p className="mt-2 text-sm text-muted">历史迁移或事实写入可能尚未完成，可返回工作台重新分析。</p></section>
        ) : null}
      </div>
    </AppShell>
  );
}

function BatchSummary({ batch }: { batch: ProjectRecordBatch }) {
  return (
    <section className="rounded-md border border-line bg-white p-5 shadow-panel">
      <div className="flex flex-wrap items-center gap-2">
        <Badge label={projectRecordBatchStatusLabel(batch.batchStatus, batch.attentionCount ?? 0)} tone={batch.attentionCount ? "warning" : "success"} />
        <span className="text-xs text-muted">{formatProjectRecordRange(batch)}</span>
      </div>
      <h2 className="mt-3 text-xl font-semibold text-slate-950">{batch.branchName || "当前分支"}</h2>
      <div className="mt-4 grid grid-cols-2 gap-3 text-sm sm:grid-cols-5">
        <Metric label="项目事实" value={batch.factCount ?? 0} />
        <Metric label="需要关注" value={batch.attentionCount ?? 0} />
        <Metric label="提交" value={batch.commitCount ?? 0} />
        <Metric label="文件" value={batch.changedFileCount ?? 0} />
        <Metric label="Agent result" value={batch.agentResultCount ?? 0} />
      </div>
    </section>
  );
}

function FactCard({ fact, projectId }: { fact: ProjectFactSummary; projectId: string }) {
  const [detail, setDetail] = useState<ProjectFact | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const needsAttention = fact.recordStatus === "NEEDS_ATTENTION";

  async function loadDetail() {
    const session = readSession();
    if (!session || detail || loading) return;
    setLoading(true);
    setError("");
    try {
      setDetail(await getProjectFact(session.accessToken, fact.id));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "事实详情加载失败");
    } finally {
      setLoading(false);
    }
  }

  return (
    <details className={`group rounded-md border bg-white shadow-panel ${needsAttention ? "border-amber-300" : "border-line"}`} onToggle={(event) => { if (event.currentTarget.open) void loadDetail(); }}>
      <summary className="cursor-pointer list-none p-5">
        <div className="flex flex-wrap items-center gap-2">
          <Badge label={factRecordStatusLabel(fact.recordStatus)} tone={needsAttention ? "warning" : "success"} />
          <Badge label={qualityStatusLabel(fact.qualityStatus)} tone={fact.qualityStatus === "PASS" ? "success" : "warning"} />
          <Badge label={factSourceModeLabel(fact.sourceMode)} tone="slate" />
          <span className="ml-auto inline-flex items-center gap-1 text-xs font-semibold text-slate-600">展开事实与证据<ChevronDown className="h-4 w-4 transition group-open:rotate-180" /></span>
        </div>
        <h3 className="mt-3 text-lg font-semibold text-slate-950 break-words">{fact.title}</h3>
        <p className="mt-2 text-sm leading-6 text-slate-600 break-words">{fact.summary}</p>
        <p className="mt-3 text-xs text-muted">{formatFactOccurredRange(fact.occurredFrom, fact.occurredTo)}</p>
        <div className="mt-3 flex flex-wrap gap-3 text-xs text-muted"><span>{fact.commitCount ?? 0} 个提交</span><span>{fact.agentResultCount ?? 0} 个 Agent result</span><span>{fact.evidenceCount ?? 0} 条证据</span><span>{fact.affectedFileCount ?? 0} 个文件</span></div>
        {needsAttention ? <p className="mt-3 rounded-md bg-amber-50 px-3 py-2 text-sm leading-6 text-amber-900">{fact.attentionReason || "这条事实的证据或时间信息需要关注，但不会阻塞后续分析。"}</p> : null}
      </summary>
      <div className="border-t border-line p-5">
        {loading ? <p className="text-sm text-muted">正在读取完整事实与证据…</p> : null}
        {error ? <p className="rounded-md bg-red-50 p-3 text-sm text-red-800">{error}</p> : null}
        {detail ? <FactDetailContent fact={detail} /> : null}
        {needsAttention && projectId ? <Link className="mt-4 inline-flex rounded-md border border-amber-300 px-3 py-2 text-sm font-semibold text-amber-900" href={`/dashboard?projectId=${projectId}`}>重新分析当前批次</Link> : null}
      </div>
    </details>
  );
}

function FactDetailContent({ fact }: { fact: ProjectFact }) {
  const mainChanges = fact.mainChanges ?? [];
  const commitRefs = fact.commitRefs ?? [];
  const commitUrls = fact.commitUrls ?? [];
  const agentResultRefs = fact.agentResultRefs ?? [];
  const affectedFiles = fact.affectedFiles ?? [];
  const evidenceRefs = fact.evidenceRefs ?? [];
  return (
    <div className="space-y-5 text-sm">
      {mainChanges.length ? <section><h4 className="font-semibold text-slate-950">主要变化</h4><ul className="mt-2 space-y-1 leading-6 text-slate-700">{mainChanges.map((item, index) => <li key={`${item}-${index}`}>• {item}</li>)}</ul></section> : null}
      {fact.userVisibleValue ? <section><h4 className="font-semibold text-slate-950">用户或开发者可感知价值</h4><p className="mt-2 leading-6 text-slate-700">{fact.userVisibleValue}</p></section> : null}
      <section className="grid gap-4 md:grid-cols-2">
        <ReferenceList title="提交引用" items={commitRefs} links={commitUrls} />
        <ReferenceList title="Agent result 引用" items={agentResultRefs} />
        <ReferenceList title="涉及文件" items={affectedFiles.map(compactProjectPath)} />
        <ReferenceList title="证据引用" items={evidenceRefs} />
      </section>
      <dl className="grid gap-3 rounded-md bg-slate-50 p-4 text-xs sm:grid-cols-2 lg:grid-cols-4">
        <Diagnostic label="事实来源" value={factOriginLabel(fact.origin)} />
        <Diagnostic label="整理方式" value={factSourceModeLabel(fact.sourceMode)} />
        <Diagnostic label="来源开发推进段" value={fact.sourceSegmentId || "历史来源未知"} />
        <Diagnostic label="来源分析批次" value={fact.batchId || "旧版来源未关联批次"} />
      </dl>
    </div>
  );
}

function ReferenceList({ title, items, links = [] }: { title: string; items: string[]; links?: string[] }) {
  return (
    <div className="rounded-md border border-line p-4"><h4 className="font-semibold text-slate-950">{title} · {items.length}</h4>{items.length ? <div className="mt-2 space-y-1 text-xs leading-5 text-slate-600">{items.map((item, index) => links[index] ? <a className="block break-all font-medium text-blue-700 hover:underline" href={links[index]} key={`${item}-${index}`} rel="noreferrer" target="_blank">{item}</a> : <p className="break-all" key={`${item}-${index}`}>{item}</p>)}</div> : <p className="mt-2 text-xs text-muted">无</p>}</div>
  );
}

function Diagnostic({ label, value }: { label: string; value: string }) {
  return <div><dt className="text-muted">{label}</dt><dd className="mt-1 break-all font-medium text-slate-800">{value}</dd></div>;
}

function Metric({ label, value }: { label: string; value: number }) {
  return <div className="rounded-md bg-slate-50 p-3"><p className="text-xs text-muted">{label}</p><p className="mt-1 font-semibold">{value}</p></div>;
}
