"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useSearchParams } from "next/navigation";
import { ArrowLeft, GitBranch, Layers3, Link2, ShieldCheck } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { Badge } from "@/components/ui";
import { getProjectCapability, type ProjectCapabilityDetail } from "@/lib/api";
import { readSession } from "@/lib/auth";
import { capabilityEvolutionLabel, capabilityMaturityLabel } from "@/lib/project-capabilities";

export default function CapabilityDetailPage() {
  const params = useParams<{ capabilityId: string }>();
  const searchParams = useSearchParams();
  const projectId = searchParams.get("projectId") ?? "";
  const [capability, setCapability] = useState<ProjectCapabilityDetail | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    const session = readSession();
    if (!session || !params.capabilityId) return;
    let active = true;
    setCapability(null);
    setError("");
    getProjectCapability(session.accessToken, params.capabilityId)
      .then((result) => { if (active) setCapability(result); })
      .catch((exception) => { if (active) setError(exception instanceof Error ? exception.message : "能力详情加载失败"); });
    return () => { active = false; };
  }, [params.capabilityId]);

  return (
    <AppShell eyebrow="能力地图" title={capability?.name ?? "能力详情"}>
      <div className="min-h-[calc(100vh-4rem)] bg-surface p-6">
        <Link className="inline-flex items-center gap-1 text-sm font-semibold text-slate-700 hover:text-slate-950" href={`/project-intelligence/capabilities?projectId=${projectId}`}><ArrowLeft className="h-4 w-4" />返回能力地图</Link>
        {error ? <div className="mt-4 rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800">{error}</div> : null}
        {!capability && !error ? <p className="mt-5 text-sm text-slate-500">正在读取能力详情…</p> : null}
        {capability ? <CapabilityContent capability={capability} projectId={projectId || capability.projectId} /> : null}
      </div>
    </AppShell>
  );
}

function CapabilityContent({ capability, projectId }: { capability: ProjectCapabilityDetail; projectId: string }) {
  return (
    <div className="mt-4 space-y-5">
      {capability.status === "MERGED" && capability.mergedIntoCapabilityId ? (
        <div className="rounded-md border border-blue-200 bg-blue-50 p-4 text-sm text-blue-950">这项旧能力已非破坏性合并，历史和事实仍保留。<Link className="ml-2 font-semibold underline" href={`/project-intelligence/capabilities/${capability.mergedIntoCapabilityId}?projectId=${projectId}`}>查看当前目标能力</Link></div>
      ) : null}
      {capability.stale ? <div className="rounded-md border border-amber-200 bg-amber-50 p-4 text-sm text-amber-950">新事实正在等待自动更新；这里继续展示上次成功的能力内容。</div> : null}

      <section className="rounded-md border border-line bg-white p-5 shadow-panel">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="max-w-3xl"><div className="flex flex-wrap items-center gap-2"><h2 className="text-xl font-semibold text-slate-950">{capability.name}</h2><Badge label={capabilityMaturityLabel(capability.maturity)} tone="success" /><Badge label={`V${capability.currentVersion}`} /></div><p className="mt-3 text-sm leading-7 text-slate-700">{capability.summary}</p></div>
          <div className="grid grid-cols-2 gap-x-6 gap-y-2 text-sm text-slate-600"><span>{capability.factCount} 条事实</span><span>{capability.batchCount} 个批次</span><span>{capability.commitCount} 个提交</span><span>{capability.evolutionCount} 次演进</span></div>
        </div>
        <div className="mt-5 grid gap-4 md:grid-cols-2">
          <Info label="解决的问题" value={capability.problemSolved} />
          <Info label="长期价值" value={capability.longTermValue} />
          <Info label="首次形成" value={formatDate(capability.firstFormedAt)} />
          <Info label="最近增强" value={formatDate(capability.lastEnhancedAt)} />
        </div>
        <div className="mt-4 rounded-md bg-slate-50 p-4"><p className="flex items-center gap-2 text-sm font-semibold text-slate-900"><ShieldCheck className="h-4 w-4" />成熟度依据</p><p className="mt-2 text-sm leading-6 text-slate-600">{capability.maturityReason}</p></div>
        {capability.aliases.length ? <p className="mt-4 text-xs text-slate-500">历史别名：{capability.aliases.join("、")}</p> : null}
      </section>

      <section className="rounded-md border border-line bg-white shadow-panel">
        <header className="border-b border-line p-4"><h3 className="flex items-center gap-2 font-semibold text-slate-950"><GitBranch className="h-4 w-4" />能力演进</h3></header>
        <div className="divide-y divide-line">
          {capability.evolutions.items.map((evolution) => (
            <article className="p-5" key={evolution.id}><div className="flex flex-wrap items-center gap-2"><Badge label={capabilityEvolutionLabel(evolution.type)} /><span className="text-sm font-semibold text-slate-900">V{evolution.versionAfter}</span><span className="text-xs text-slate-500">{formatDate(evolution.occurredAt)}</span></div><h4 className="mt-2 font-medium text-slate-950">{evolution.title}</h4><p className="mt-1 text-sm leading-6 text-slate-600">{evolution.summary}</p><p className="mt-2 text-xs text-slate-500">{evolution.sourceFactCount} 条事实 · {evolution.sourceBatchCount} 个批次 · {evolution.sourceTimelinePeriods.join("、") || "无时间段标签"}</p></article>
          ))}
          {!capability.evolutions.items.length ? <p className="p-5 text-sm text-slate-500">暂无可追溯演进。</p> : null}
        </div>
      </section>

      <section className="rounded-md border border-line bg-white shadow-panel">
        <header className="border-b border-line p-4"><h3 className="flex items-center gap-2 font-semibold text-slate-950"><Layers3 className="h-4 w-4" />来源事实与证据</h3><p className="mt-1 text-xs text-slate-500">能力 → ProjectFact → 分析批次 → commit、文件、Agent result 和 evidence。</p></header>
        <div className="divide-y divide-line">
          {capability.recentFacts.items.map((fact) => (
            <article className="p-5" key={fact.factId}><div className="flex flex-wrap items-center gap-2"><h4 className="font-medium text-slate-950">{fact.title}</h4><Badge label={fact.relationRole} /><Badge label={fact.recordStatus} tone={fact.recordStatus === "NEEDS_ATTENTION" ? "warning" : "success"} /></div><p className="mt-1 text-sm leading-6 text-slate-600">{fact.summary}</p><div className="mt-2 flex flex-wrap gap-4 text-xs text-slate-500"><span>{fact.commitCount} 个提交</span><span>{fact.affectedFileCount} 个文件</span><span>{fact.evidenceCount} 条证据</span><span>{formatDate(fact.occurredTo ?? fact.occurredFrom)}</span></div>{fact.batchId ? <Link className="mt-3 inline-flex items-center gap-1 text-xs font-semibold text-brand hover:underline" href={`/sediment-review/${fact.batchId}?projectId=${projectId}`}><Link2 className="h-3 w-3" />查看来源批次和证据</Link> : null}</article>
          ))}
          {!capability.recentFacts.items.length ? <p className="p-5 text-sm text-slate-500">这项兼容能力尚未追溯到 ProjectFact。</p> : null}
        </div>
      </section>

      <section className="rounded-md border border-line bg-white p-5 shadow-panel">
        <h3 className="font-semibold text-slate-950">可复用表达</h3>
        <div className="mt-4 grid gap-4 xl:grid-cols-3"><Info label="README" value={capability.readmeExpression || "尚未生成"} /><Info label="简历" value={capability.resumeExpression || "尚未生成"} /><Info label="面试" value={capability.interviewExpression || "尚未生成"} /></div>
      </section>
    </div>
  );
}

function Info({ label, value }: { label: string; value: string }) {
  return <div><p className="text-xs font-medium text-slate-500">{label}</p><p className="mt-1 text-sm leading-6 text-slate-800">{value || "尚无"}</p></div>;
}

function formatDate(value: string | null) {
  return value ? new Date(value).toLocaleDateString("zh-CN") : "尚无";
}
