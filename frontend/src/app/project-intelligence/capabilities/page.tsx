"use client";

import { Suspense, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Activity, AlertTriangle, ArrowLeft, Clock3, GitCommit, RefreshCw, Search, ShieldCheck } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { Badge, Button, ProjectContextBar } from "@/components/ui";
import { useProjectSelection } from "@/hooks/useProjectSelection";
import {
  getCapabilityMapOverview,
  listCapabilityMapAttention,
  listCapabilityMapChanges,
  listProjectCapabilities,
  listProjectCapabilityCards,
  retryCapabilityMap,
  type CapabilityAttentionPage,
  type CapabilityCard,
  type CapabilityEvolutionPage,
  type CapabilityMapOverview,
  type ProjectCapabilityPage,
} from "@/lib/api";
import { readSession } from "@/lib/auth";
import { capabilityEvolutionLabel, capabilityMapStatusLabel, capabilityMaturityLabel } from "@/lib/project-capabilities";

export default function CapabilityMapPage() {
  return (
    <Suspense fallback={<AppShell eyebrow="项目理解" title="能力地图"><div className="h-1 bg-slate-950" /></AppShell>}>
      <CapabilityMapContent />
    </Suspense>
  );
}

function CapabilityMapContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const queryProjectId = searchParams.get("projectId") ?? "";
  const { projects, selectedProject, selectedProjectId, selectProject, loadingProjects, projectError } = useProjectSelection({ queryProjectId });
  const [overview, setOverview] = useState<CapabilityMapOverview | null>(null);
  const [capabilities, setCapabilities] = useState<ProjectCapabilityPage | null>(null);
  const [changes, setChanges] = useState<CapabilityEvolutionPage | null>(null);
  const [attention, setAttention] = useState<CapabilityAttentionPage | null>(null);
  const [legacyCards, setLegacyCards] = useState<CapabilityCard[]>([]);
  const [loadedProjectId, setLoadedProjectId] = useState("");
  const [search, setSearch] = useState("");
  const [maturity, setMaturity] = useState("");
  const [loading, setLoading] = useState(false);
  const [retrying, setRetrying] = useState(false);
  const [error, setError] = useState("");
  const requestId = useRef(0);

  useEffect(() => {
    const session = readSession();
    const currentRequest = ++requestId.current;
    if (!session || !selectedProjectId) {
      setLoadedProjectId("");
      setOverview(null);
      setCapabilities(null);
      setChanges(null);
      setAttention(null);
      setLegacyCards([]);
      return;
    }
    setLoadedProjectId("");
    setLoading(true);
    setError("");
    const token = session.accessToken;
    Promise.allSettled([
      getCapabilityMapOverview(token, selectedProjectId),
      listProjectCapabilities(token, selectedProjectId, { status: "ACTIVE", maturity, search, sort: "lastEnhancedAt", size: 50 }),
      listCapabilityMapChanges(token, selectedProjectId, 0, 12),
      listCapabilityMapAttention(token, selectedProjectId, 0, 12),
      listProjectCapabilityCards(token, selectedProjectId),
    ]).then((results) => {
      if (requestId.current !== currentRequest) return;
      const [overviewResult, capabilityResult, changesResult, attentionResult, legacyResult] = results;
      if (overviewResult.status === "fulfilled") setOverview(overviewResult.value);
      if (capabilityResult.status === "fulfilled") setCapabilities(capabilityResult.value);
      if (changesResult.status === "fulfilled") setChanges(changesResult.value);
      if (attentionResult.status === "fulfilled") setAttention(attentionResult.value);
      if (legacyResult.status === "fulfilled") setLegacyCards(legacyResult.value);
      const failed = results.filter((result) => result.status === "rejected");
      if (failed.length) setError("部分能力数据暂时无法读取，已保留成功加载的内容。");
      setLoadedProjectId(selectedProjectId);
    }).finally(() => {
      if (requestId.current === currentRequest) setLoading(false);
    });
  }, [selectedProjectId, search, maturity]);

  function handleSelectProject(projectId: string) {
    selectProject(projectId);
    router.replace(`/project-intelligence/capabilities?projectId=${projectId}`);
  }

  async function retry() {
    const session = readSession();
    if (!session || !selectedProjectId) return;
    setRetrying(true);
    setError("");
    try {
      await retryCapabilityMap(session.accessToken, selectedProjectId);
      const next = await getCapabilityMapOverview(session.accessToken, selectedProjectId);
      if (selectedProjectId === next.projectId) setOverview(next);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "能力地图重试启动失败");
    } finally {
      setRetrying(false);
    }
  }

  const readyForProject = loadedProjectId === selectedProjectId;
  const visibleOverview = readyForProject ? overview : null;
  const visibleCapabilities = readyForProject ? capabilities : null;
  const visibleChanges = readyForProject ? changes : null;
  const visibleAttention = readyForProject ? attention : null;
  const visibleLegacy = readyForProject ? legacyCards : [];

  return (
    <AppShell eyebrow="项目理解" title={selectedProject ? `${selectedProject.name} · 能力地图` : "能力地图"}>
      <div className="min-h-[calc(100vh-4rem)] bg-surface p-6">
        <ProjectContextBar
          actions={<Link className="inline-flex items-center gap-1 rounded-md border border-line bg-white px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50" href={`/project-intelligence?projectId=${selectedProjectId}`}><ArrowLeft className="h-4 w-4" />回到项目记忆</Link>}
          leadingExtras={<><Badge label={`${visibleOverview?.activeCount ?? 0} 项长期能力`} tone={visibleOverview?.activeCount ? "success" : "warning"} /><Badge label={capabilityMapStatusLabel(visibleOverview?.mapStatus ?? "NOT_INITIALIZED")} /></>}
          onSelect={handleSelectProject}
          projects={projects}
          selectedProjectId={selectedProjectId}
        />

        <section className="rounded-md border border-line bg-white shadow-panel">
          <header className="flex flex-wrap items-start justify-between gap-4 border-b border-line p-5">
            <div className="max-w-3xl">
              <h2 className="text-xl font-semibold text-slate-950">能力地图</h2>
              <p className="mt-1 text-sm leading-6 text-slate-600">基于项目从创建至今的全部事实，自动维护长期能力及其演进。</p>
              <p className="mt-1 text-xs text-slate-500">项目事实是事实来源；项目历程组织时间，长期能力解释完整历史证明项目能做什么。</p>
            </div>
            {visibleOverview && visibleOverview.mapStatus !== "READY" ? (
              <Button disabled={retrying || ["QUEUED", "GENERATING"].includes(visibleOverview.mapStatus)} loading={retrying} onClick={retry} variant="secondary">
                <RefreshCw className="h-4 w-4" />恢复自动更新
              </Button>
            ) : null}
          </header>

          {visibleOverview ? (
            <div className="grid gap-3 border-b border-line bg-slate-50 p-5 sm:grid-cols-2 xl:grid-cols-6">
              <Metric label="长期能力" value={`${visibleOverview.activeCount} 项`} />
              <Metric label="事实覆盖" value={`${visibleOverview.coveredFactCount}/${visibleOverview.sourceFactCount}`} />
              <Metric label="形成能力支撑" value={`${visibleOverview.assignedFactCount} 条`} />
              <Metric label="无能力变化" value={`${visibleOverview.noCapabilityChangeFactCount} 条`} />
              <Metric label="能力层关注" value={`${visibleOverview.attentionCount} 项`} />
              <Metric label="历史覆盖" value={`${visibleOverview.historyCoverage.coveredCount}/${visibleOverview.historyCoverage.totalCount}`} />
            </div>
          ) : null}

          {visibleOverview?.stale || visibleOverview?.errorSummary ? (
            <div className="border-b border-amber-200 bg-amber-50 px-5 py-3 text-sm text-amber-950">
              <p className="font-semibold">已有能力仍可读取，新事实正在等待恢复</p>
              <p className="mt-1">{visibleOverview.errorSummary || "后台会合并新事实并自动更新；刷新不会删除上次成功结果。"}</p>
            </div>
          ) : null}

          <div className="flex flex-wrap items-center gap-3 border-b border-line p-5">
            <label className="relative min-w-64 flex-1">
              <Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
              <input className="w-full rounded-md border border-line py-2 pl-9 pr-3 text-sm" onChange={(event) => setSearch(event.target.value)} placeholder="搜索能力名称、摘要或解决的问题" value={search} />
            </label>
            <select className="rounded-md border border-line bg-white px-3 py-2 text-sm" onChange={(event) => setMaturity(event.target.value)} value={maturity}>
              <option value="">全部成熟阶段</option>
              <option value="FORMING">形成中</option>
              <option value="FORMED">已形成</option>
              <option value="CONTINUOUSLY_ENHANCED">持续增强</option>
              <option value="LONG_TERM_STABLE">长期稳定</option>
            </select>
          </div>

          {visibleCapabilities?.items.length ? (
            <div className="divide-y divide-line">
              {visibleCapabilities.items.map((capability) => (
                <Link className="block p-5 transition hover:bg-slate-50" href={`/project-intelligence/capabilities/${capability.id}?projectId=${selectedProjectId}`} key={capability.id}>
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div className="max-w-3xl">
                      <div className="flex flex-wrap items-center gap-2"><h3 className="font-semibold text-slate-950">{capability.name}</h3><Badge label={capabilityMaturityLabel(capability.maturity)} tone={capability.maturity === "FORMING" ? "warning" : "success"} /></div>
                      <p className="mt-2 text-sm leading-6 text-slate-600">{capability.summary}</p>
                      <p className="mt-2 text-xs leading-5 text-slate-500">{capability.maturityReason}</p>
                    </div>
                    <div className="grid grid-cols-2 gap-x-5 gap-y-1 text-xs text-slate-600">
                      <span>{capability.factCount} 条事实</span><span>{capability.batchCount} 个批次</span>
                      <span>{capability.commitCount} 个提交</span><span>{capability.evolutionCount} 次演进</span>
                    </div>
                  </div>
                  <div className="mt-3 flex flex-wrap gap-4 text-xs text-slate-500"><span>首次形成：{formatDate(capability.firstFormedAt)}</span><span>最近增强：{formatDate(capability.lastEnhancedAt)}</span>{capability.attentionCount ? <span className="text-amber-700">{capability.attentionCount} 条关联事实需关注</span> : null}</div>
                </Link>
              ))}
            </div>
          ) : readyForProject && !loading ? (
            <div className="p-10 text-center"><ShieldCheck className="mx-auto h-8 w-8 text-slate-400" /><p className="mt-3 font-semibold text-slate-950">能力地图正在等待完整初始化</p><p className="mx-auto mt-2 max-w-xl text-sm leading-6 text-slate-600">系统会在事实和历史覆盖就绪后自动建立长期能力；普通维护事实也会被明确计入覆盖，不会被静默丢弃。</p></div>
          ) : <div className="p-8 text-sm text-slate-500">正在读取能力地图…</div>}
        </section>

        <div className="mt-5 grid gap-5 xl:grid-cols-2">
          <section className="rounded-md border border-line bg-white shadow-panel">
            <header className="border-b border-line p-4"><h3 className="flex items-center gap-2 font-semibold text-slate-950"><Activity className="h-4 w-4" />最近能力变化</h3></header>
            <div className="divide-y divide-line">
              {visibleChanges?.items.length ? visibleChanges.items.map((change) => (
                <div className="p-4" key={change.id}><div className="flex flex-wrap items-center gap-2"><Badge label={capabilityEvolutionLabel(change.type)} /><span className="text-xs text-slate-500">V{change.versionAfter}</span></div><p className="mt-2 font-medium text-slate-900">{change.title}</p><p className="mt-1 text-sm leading-6 text-slate-600">{change.summary}</p><p className="mt-2 text-xs text-slate-500">{change.sourceFactCount} 条事实 · {formatDate(change.occurredAt)}</p></div>
              )) : <p className="p-5 text-sm text-slate-500">暂无自动能力变化。</p>}
            </div>
          </section>

          <section className="rounded-md border border-line bg-white shadow-panel">
            <header className="border-b border-line p-4"><h3 className="flex items-center gap-2 font-semibold text-slate-950"><AlertTriangle className="h-4 w-4" />能力层需要关注</h3></header>
            <div className="divide-y divide-line">
              {visibleAttention?.items.length ? visibleAttention.items.map((item) => (
                <div className="p-4" key={item.id}><Badge label={item.type} tone="warning" /><p className="mt-2 text-sm leading-6 text-slate-700">{item.reason}</p><p className="mt-2 text-xs text-slate-500">{formatDate(item.createdAt)}</p></div>
              )) : <p className="p-5 text-sm text-slate-500">没有需要人工介入的能力冲突。</p>}
            </div>
          </section>
        </div>

        <details className="mt-5 rounded-md border border-line bg-white p-5 shadow-panel">
          <summary className="cursor-pointer font-semibold text-slate-950">旧版能力卡片（{visibleLegacy.length}）</summary>
          <p className="mt-2 text-sm leading-6 text-slate-600">V3.3.x 基于人工沉淀生成的历史卡片已保留用于兼容。V3.4.2 的能力地图以 ProjectFact 为事实基础，未确认和已忽略卡片不会自动成为长期能力。</p>
          {visibleLegacy.length ? <div className="mt-4 grid gap-3 md:grid-cols-2">{visibleLegacy.map((card) => <div className="rounded-md border border-line p-3" key={card.id}><div className="flex items-center gap-2"><p className="font-medium text-slate-900">{card.name}</p><Badge label={legacyStatus(card.status)} /></div><p className="mt-1 text-sm text-slate-600">{card.summary}</p></div>)}</div> : null}
        </details>

        {(error || projectError) ? <div className="mt-4 rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800">{error || projectError}</div> : null}
        {loadingProjects ? <p className="mt-4 text-xs text-slate-500">正在加载项目…</p> : null}
      </div>
    </AppShell>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return <div><p className="text-xs text-slate-500">{label}</p><p className="mt-1 text-lg font-semibold text-slate-950">{value}</p></div>;
}

function formatDate(value: string | null) {
  return value ? new Date(value).toLocaleDateString("zh-CN") : "尚无";
}

function legacyStatus(value: CapabilityCard["status"]) {
  return { CANDIDATE: "历史候选", CONFIRMED: "历史已采纳", NEEDS_EVIDENCE: "历史证据不足", IGNORED: "历史已忽略" }[value];
}
