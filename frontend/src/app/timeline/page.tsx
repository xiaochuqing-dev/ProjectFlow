"use client";

import { Suspense, useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { CalendarDays, ChevronRight, Clock3, GitCommitHorizontal, Layers3, RefreshCw } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { Badge, Button, Card, PageContainer, ProjectContextBar, SectionHeader, Stat } from "@/components/ui";
import { useProjectSelection } from "@/hooks/useProjectSelection";
import {
  getTimelineLifecycle,
  getTimelineOverview,
  getTimelinePeriod,
  getTimelineThemeFacts,
  listTimelinePeriods,
  retryTimelineSummary,
  type TimelineGranularity,
  type TimelineLifecycle,
  type TimelineOverview,
  type TimelinePeriod,
  type TimelinePeriodDetail,
  type TimelinePeriodPage,
  type TimelineThemeFacts,
} from "@/lib/api";
import {
  timelineGranularityLabels,
  timelineHistoryLabel,
  timelinePeriodLabel,
  timelineRangeLabel,
  timelineStatusLabels,
} from "@/lib/project-timeline";

type ViewGranularity = "DAY" | "WEEK" | "MONTH" | "LIFECYCLE";

export default function TimelinePage() {
  return (
    <Suspense fallback={<AppShell eyebrow="自动项目历程" title="项目历程"><PageContainer>正在读取项目事实…</PageContainer></AppShell>}>
      <TimelinePageContent />
    </Suspense>
  );
}

function TimelinePageContent() {
  const searchParams = useSearchParams();
  const selection = useProjectSelection({ queryProjectId: searchParams.get("projectId") ?? "" });
  const [granularity, setGranularity] = useState<ViewGranularity>("MONTH");
  const [overview, setOverview] = useState<TimelineOverview | null>(null);
  const [periodPage, setPeriodPage] = useState<TimelinePeriodPage | null>(null);
  const [lifecycle, setLifecycle] = useState<TimelineLifecycle | null>(null);
  const [detail, setDetail] = useState<TimelinePeriodDetail | null>(null);
  const [themeFacts, setThemeFacts] = useState<TimelineThemeFacts | null>(null);
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState("");
  const [secondaryError, setSecondaryError] = useState("");
  const requestGeneration = useRef(0);
  const detailGeneration = useRef(0);
  const selectedProjectId = selection.selectedProjectId;
  const token = selection.session?.accessToken ?? "";

  const loadDetail = useCallback(async (
    projectId: string,
    selectedGranularity: Exclude<TimelineGranularity, "LIFECYCLE">,
    periodKey: string,
  ) => {
    if (!token || !projectId) return;
    const generation = ++detailGeneration.current;
    setDetailLoading(true);
    setSecondaryError("");
    setThemeFacts(null);
    try {
      const response = await getTimelinePeriod(token, projectId, selectedGranularity, periodKey, 0, 20);
      if (generation === detailGeneration.current && projectId === selection.selectedProjectId) setDetail(response);
    } catch (exception) {
      if (generation === detailGeneration.current) {
        setSecondaryError(exception instanceof Error ? exception.message : "时间段详情读取失败");
      }
    } finally {
      if (generation === detailGeneration.current) setDetailLoading(false);
    }
  }, [selection.selectedProjectId, token]);

  useEffect(() => {
    if (!token || !selectedProjectId) return;
    const generation = ++requestGeneration.current;
    const projectId = selectedProjectId;
    setLoading(true);
    setError("");
    setSecondaryError("");
    setDetail(null);
    setThemeFacts(null);
    const contentRequest = granularity === "LIFECYCLE"
      ? getTimelineLifecycle(token, projectId)
      : listTimelinePeriods(token, projectId, granularity, 0, 24);
    Promise.allSettled([getTimelineOverview(token, projectId), contentRequest]).then(([overviewResult, contentResult]) => {
      if (generation !== requestGeneration.current || projectId !== selection.selectedProjectId) return;
      if (overviewResult.status === "fulfilled") setOverview(overviewResult.value);
      else setError(overviewResult.reason instanceof Error ? overviewResult.reason.message : "项目历程概览读取失败");
      if (contentResult.status === "fulfilled") {
        if (granularity === "LIFECYCLE") {
          const value = contentResult.value as TimelineLifecycle;
          setLifecycle(value);
          setPeriodPage(null);
        } else {
          const value = contentResult.value as TimelinePeriodPage;
          setPeriodPage(value);
          setLifecycle(null);
          if (value.items[0]) void loadDetail(projectId, granularity, value.items[0].periodKey);
        }
      } else {
        setSecondaryError(contentResult.reason instanceof Error ? contentResult.reason.message : "项目历程时间段读取失败");
      }
    }).finally(() => {
      if (generation === requestGeneration.current) setLoading(false);
    });
  }, [granularity, loadDetail, selectedProjectId, selection.selectedProjectId, token]);

  function changeProject(projectId: string) {
    requestGeneration.current++;
    detailGeneration.current++;
    setOverview(null);
    setPeriodPage(null);
    setLifecycle(null);
    setDetail(null);
    setThemeFacts(null);
    setError("");
    setSecondaryError("");
    selection.selectProject(projectId);
  }

  async function openTheme(themeId: string) {
    if (!token || !selectedProjectId) return;
    const generation = ++detailGeneration.current;
    setDetailLoading(true);
    setSecondaryError("");
    try {
      const response = await getTimelineThemeFacts(token, selectedProjectId, themeId, 0, 30);
      if (generation === detailGeneration.current) setThemeFacts(response);
    } catch (exception) {
      if (generation === detailGeneration.current) {
        setSecondaryError(exception instanceof Error ? exception.message : "主题事实读取失败");
      }
    } finally {
      if (generation === detailGeneration.current) setDetailLoading(false);
    }
  }

  async function retrySummary() {
    if (!token || !selectedProjectId) return;
    const summary = granularity === "LIFECYCLE" ? lifecycle?.currentSummary : detail?.currentSummary;
    if (!summary || (summary.status !== "FAILED" && summary.status !== "WAITING_FOR_MODEL")) return;
    setDetailLoading(true);
    setSecondaryError("");
    try {
      await retryTimelineSummary(token, selectedProjectId, summary.granularity as Exclude<TimelineGranularity, "DAY">, summary.periodKey);
      setSecondaryError("自动摘要已进入后台队列，事实与统计可继续查看。");
    } catch (exception) {
      setSecondaryError(exception instanceof Error ? exception.message : "自动摘要重试失败");
    } finally {
      setDetailLoading(false);
    }
  }

  const periods = granularity === "LIFECYCLE" ? lifecycle?.months ?? [] : periodPage?.items ?? [];
  const currentSummary = granularity === "LIFECYCLE" ? lifecycle?.currentSummary : detail?.currentSummary;
  const themes = granularity === "LIFECYCLE" ? lifecycle?.stages ?? [] : detail?.themes ?? [];

  return (
    <AppShell eyebrow="自动项目历程" title={selection.selectedProject ? `${selection.selectedProject.name} · 项目历程` : "项目历程"}>
      <PageContainer>
        <ProjectContextBar
          projects={selection.projects}
          selectedProjectId={selectedProjectId}
          onSelect={changeProject}
          leadingExtras={overview ? <Badge label={`时区 ${overview.timelineZone}`} tone="slate" /> : null}
          actions={<Link className="text-sm font-medium text-brand hover:underline" href="/dev-logs">旧每日回顾兼容入口</Link>}
        />

        {selection.projectError || error ? (
          <div className="mb-5 rounded-card border border-danger/30 bg-danger-soft p-4 text-sm text-danger-fg">
            {selection.projectError || error}
          </div>
        ) : null}
        {secondaryError ? (
          <div className="mb-5 rounded-card border border-warning/30 bg-warning-soft p-4 text-sm text-warning-fg">{secondaryError}</div>
        ) : null}

        {overview ? (
          <>
            <div className="mb-5 grid gap-3 md:grid-cols-4">
              <Stat label="项目事实" value={overview.factCount} icon={<Layers3 className="h-4 w-4" />} />
              <Stat label="分析批次" value={overview.batchCount} icon={<CalendarDays className="h-4 w-4" />} />
              <Stat label="已覆盖 commits" value={overview.commitCoverage.coveredCommitCount} icon={<GitCommitHorizontal className="h-4 w-4" />} />
              <Stat label="时间范围" value={overview.earliestFactAt ? formatDate(overview.earliestFactAt, overview.timelineZone) : "暂无"} hint={overview.latestFactAt ? `至 ${formatDate(overview.latestFactAt, overview.timelineZone)}` : undefined} icon={<Clock3 className="h-4 w-4" />} />
            </div>
            <div className="mb-5 rounded-card border border-line bg-surfaceAlt px-4 py-3 text-sm text-body">
              {timelineHistoryLabel(overview.history.status, overview.history.coveredCommitCount, overview.history.totalCommitCount)}。当前历程基于已经记录的项目事实，会随历史补齐自动扩展。
            </div>
          </>
        ) : null}

        <div className="mb-5 flex flex-wrap gap-2" aria-label="项目历程粒度">
          {(["DAY", "WEEK", "MONTH", "LIFECYCLE"] as ViewGranularity[]).map((item) => (
            <Button key={item} variant={granularity === item ? "primary" : "secondary"} onClick={() => setGranularity(item)}>
              {timelineGranularityLabels[item]}
            </Button>
          ))}
        </div>

        {loading ? <div className="mb-5 h-1 animate-pulse rounded-full bg-brand" /> : null}
        {!loading && selectedProjectId && periods.length === 0 ? (
          <Card padding="lg"><p className="text-sm text-muted">暂无可展示的项目事实。完成一次“分析新变化”后，这里会自动形成项目历程。</p></Card>
        ) : null}

        {granularity === "LIFECYCLE" && lifecycle ? (
          <LifecycleSummary lifecycle={lifecycle} />
        ) : null}

        <div className="grid gap-5 xl:grid-cols-[minmax(280px,0.78fr)_minmax(0,1.5fr)]">
          <Card padding="none">
            <SectionHeader title={granularity === "LIFECYCLE" ? "按月回看" : `${timelineGranularityLabels[granularity]}时间段`} subtitle={`${periods.length} 个时间段`} />
            <div className="max-h-[720px] divide-y divide-line overflow-auto">
              {periods.map((period) => (
                <PeriodButton
                  active={detail?.periodKey === period.periodKey}
                  granularity={granularity === "LIFECYCLE" ? "MONTH" : granularity}
                  key={period.periodKey}
                  onClick={() => void loadDetail(selectedProjectId, granularity === "LIFECYCLE" ? "MONTH" : granularity, period.periodKey)}
                  period={period}
                  zone={overview?.timelineZone ?? "Asia/Shanghai"}
                />
              ))}
            </div>
          </Card>

          <Card padding="none">
            <SectionHeader
              title={granularity === "LIFECYCLE" && !detail ? "完整历程摘要" : detail ? timelinePeriodLabel(detail.granularity, detail.periodKey) : "选择时间段"}
              subtitle={detail ? timelineRangeLabel(detail.periodStart, detail.periodEnd, detail.timelineZone) : "查看自动摘要、演进主题与事实来源"}
              actions={currentSummary && (currentSummary.status === "FAILED" || currentSummary.status === "WAITING_FOR_MODEL") ? (
                <Button loading={detailLoading} onClick={() => void retrySummary()} size="sm"><RefreshCw className="h-3.5 w-3.5" />重新尝试自动摘要</Button>
              ) : null}
            />
            <div className="space-y-5 p-5">
              <SummaryBlock summary={currentSummary} sourceCount={granularity === "LIFECYCLE" ? lifecycle?.sourceFactCount ?? 0 : detail?.sourceFactCount ?? 0} />
              {themes.length > 0 ? (
                <div>
                  <h3 className="mb-2 text-sm font-semibold text-ink">主要演进主题</h3>
                  <div className="grid gap-2 md:grid-cols-2">
                    {themes.map((theme) => (
                      <button className="rounded-card border border-line p-4 text-left transition hover:border-brand/40 hover:bg-brand-soft/30" key={theme.id} onClick={() => void openTheme(theme.id)} type="button">
                        <div className="flex items-center justify-between gap-2"><span className="font-medium text-ink">{theme.title}</span><Badge label={`${theme.factCount} 条事实`} tone="brand" /></div>
                        <p className="mt-2 text-sm leading-6 text-body">{theme.summary}</p>
                      </button>
                    ))}
                  </div>
                </div>
              ) : null}
              {themeFacts ? <FactList facts={themeFacts.facts.items} title={`${themeFacts.title} · 事实来源`} /> : detail ? <FactList facts={detail.facts.items} title="本时间段项目事实" /> : null}
              {detailLoading ? <p className="text-sm text-muted">正在读取可追溯事实…</p> : null}
            </div>
          </Card>
        </div>
      </PageContainer>
    </AppShell>
  );
}

function PeriodButton({ active, granularity, onClick, period, zone }: {
  active: boolean;
  granularity: Exclude<TimelineGranularity, "LIFECYCLE">;
  onClick: () => void;
  period: TimelinePeriod;
  zone: string;
}) {
  return (
    <button className={`block w-full p-4 text-left transition ${active ? "bg-brand-soft" : "hover:bg-surfaceAlt"}`} onClick={onClick} type="button">
      <div className="flex items-center justify-between gap-2"><span className="font-medium text-ink">{timelinePeriodLabel(granularity, period.periodKey)}</span><ChevronRight className="h-4 w-4 text-muted" /></div>
      <p className="mt-1 text-xs text-muted">{timelineRangeLabel(period.periodStart, period.periodEnd, zone)}</p>
      <div className="mt-2 flex flex-wrap gap-2 text-xs text-body"><span>{period.stats.factCount} 条事实</span><span>{period.stats.commitCount} commits</span><span>{period.stats.batchCount} 批次</span></div>
      <p className="mt-2 text-xs text-muted">{period.summaryStale ? "摘要基于旧版本事实，正在更新" : timelineStatusLabels[period.summaryStatus]}</p>
    </button>
  );
}

function SummaryBlock({ summary, sourceCount }: { summary: TimelinePeriodDetail["currentSummary"] | TimelineLifecycle["currentSummary"] | undefined; sourceCount: number }) {
  if (!summary) return <p className="text-sm text-muted">事实与统计已可查看，自动摘要尚未生成。</p>;
  return (
    <div className="rounded-card bg-surfaceAlt p-4">
      <div className="flex flex-wrap items-center gap-2"><Badge label={summary.stale ? "摘要基于旧版本事实，正在更新" : timelineStatusLabels[summary.status]} tone={summary.status === "FAILED" ? "danger" : summary.status === "READY" ? "success" : "warning"} /><span className="text-xs text-muted">覆盖 {summary.coveredFactCount} / {sourceCount}</span></div>
      {summary.summary ? <p className="mt-3 leading-7 text-body">{summary.summary}</p> : null}
      {summary.errorSummary ? <p className="mt-2 text-sm text-danger-fg">{summary.errorSummary}</p> : null}
    </div>
  );
}

function LifecycleSummary({ lifecycle }: { lifecycle: TimelineLifecycle }) {
  return (
    <Card className="mb-5" padding="lg">
      <h2 className="text-lg font-semibold text-ink">项目完整历程</h2>
      <p className="mt-2 text-sm leading-6 text-body">从 {lifecycle.earliestFactAt ? formatDate(lifecycle.earliestFactAt, lifecycle.timelineZone) : "最早事实"} 到 {lifecycle.latestFactAt ? formatDate(lifecycle.latestFactAt, lifecycle.timelineZone) : "当前"}，共 {lifecycle.stats.factCount} 条唯一项目事实、{lifecycle.stats.batchCount} 个分析批次、{lifecycle.stats.commitCount} 个 commits。</p>
    </Card>
  );
}

function FactList({ facts, title }: { facts: TimelinePeriodDetail["facts"]["items"]; title: string }) {
  return (
    <div>
      <h3 className="mb-2 text-sm font-semibold text-ink">{title}</h3>
      <div className="space-y-2">
        {facts.map((fact) => (
          <article className="rounded-card border border-line p-4" key={fact.id}>
            <div className="flex flex-wrap items-start justify-between gap-2"><div><h4 className="font-medium text-ink">{fact.title}</h4><p className="mt-1 text-sm leading-6 text-body">{fact.summary}</p></div><Badge label={fact.recordStatus === "NEEDS_ATTENTION" ? "需要关注" : "已记录"} tone={fact.recordStatus === "NEEDS_ATTENTION" ? "warning" : "success"} /></div>
            <div className="mt-3 flex flex-wrap gap-3 text-xs text-muted"><span>{fact.commitCount} commits</span><span>{fact.affectedFileCount} files</span>{fact.batchId ? <Link className="text-brand hover:underline" href={`/sediment-review/${fact.batchId}`}>追溯批次与证据</Link> : null}</div>
          </article>
        ))}
      </div>
    </div>
  );
}

function formatDate(value: string, zone: string) {
  return new Intl.DateTimeFormat("zh-CN", { timeZone: zone, year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date(value));
}
