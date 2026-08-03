"use client";

import Link from "next/link";
import { useParams, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { AppShell } from "@/components/AppShell";
import {
  getProject,
  getProjectHistoryChapter,
  getProjectHistoryOverview,
  getProjectHistoryStory,
  getProjectHistoryThread,
  type Project,
  type ProjectHistoryChapterDetail,
  type ProjectHistoryOverview,
  type ProjectHistoryStory,
  type ProjectHistoryStoryDetail,
  type ProjectHistoryThreadDetail,
} from "@/lib/api";
import { readSession } from "@/lib/auth";
import {
  projectHistoryEntityType,
  projectHistoryHref,
  projectHistoryTransitionLabel,
  type ProjectHistoryEntityType,
} from "@/lib/project-history";

type LoadedHistory =
  | { type: "overview"; value: ProjectHistoryOverview }
  | { type: "chapter"; value: ProjectHistoryChapterDetail }
  | { type: "story"; value: ProjectHistoryStoryDetail }
  | { type: "thread"; value: ProjectHistoryThreadDetail };

export default function ProjectHistoryPreviewPage() {
  const params = useParams<{ projectId: string }>();
  const searchParams = useSearchParams();
  const entityType = projectHistoryEntityType(searchParams.get("type"));
  const entityId = searchParams.get("id") ?? "";
  const [project, setProject] = useState<Project | null>(null);
  const [history, setHistory] = useState<LoadedHistory | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    setHistory(null);
    setError("");
    const token = readSession().accessToken;
    Promise.all([
      getProject(token, params.projectId),
      loadHistory(token, params.projectId, entityType, entityId),
    ]).then(([nextProject, nextHistory]) => {
      if (!active) return;
      setProject(nextProject);
      setHistory(nextHistory);
    }).catch((exception) => {
      if (!active) return;
      setError(exception instanceof Error ? exception.message : "项目历程加载失败");
    });
    return () => {
      active = false;
    };
  }, [entityId, entityType, params.projectId]);

  return (
    <AppShell eyebrow="只读开发者预览" title={`${project?.name ?? "项目"} · 项目历程`}>
      <div className="space-y-5 p-6 md:p-8">
        <div className="flex flex-wrap items-center gap-3 text-sm">
          <Link className="rounded-field border border-line bg-white px-3 py-2 text-body hover:bg-surfaceAlt" href={`/projects/${params.projectId}`}>
            返回项目
          </Link>
          <Link className="rounded-field border border-line bg-white px-3 py-2 text-body hover:bg-surfaceAlt" href={projectHistoryHref(params.projectId)}>
            历程总览
          </Link>
          <span className="text-muted">此页面提供稳定深链接和信息层次验收，不代表最终 GUI。</span>
        </div>

        {error ? (
          <section className="rounded-card border border-rose-200 bg-rose-50 p-5 text-sm text-rose-800">
            <h2 className="font-semibold">无法读取该历程详情</h2>
            <p className="mt-2">{error}</p>
          </section>
        ) : null}

        {!error && !history ? <p className="rounded-card border border-line bg-white p-5 text-sm text-muted">正在读取持久化历程……</p> : null}
        {history?.type === "overview" ? <OverviewView projectId={params.projectId} value={history.value} /> : null}
        {history?.type === "chapter" ? <ChapterView projectId={params.projectId} value={history.value} /> : null}
        {history?.type === "story" ? <StoryView projectId={params.projectId} value={history.value} /> : null}
        {history?.type === "thread" ? <ThreadView projectId={params.projectId} value={history.value} /> : null}
      </div>
    </AppShell>
  );
}

async function loadHistory(
  token: string,
  projectId: string,
  type: ProjectHistoryEntityType,
  entityId: string,
): Promise<LoadedHistory> {
  if (type === "overview") return { type, value: await getProjectHistoryOverview(token, projectId) };
  if (!entityId) throw new Error("深链接缺少历程实体 ID。");
  if (type === "chapter") return { type, value: await getProjectHistoryChapter(token, projectId, entityId) };
  if (type === "story") return { type, value: await getProjectHistoryStory(token, projectId, entityId) };
  return { type, value: await getProjectHistoryThread(token, projectId, entityId) };
}

function OverviewView({ projectId, value }: { projectId: string; value: ProjectHistoryOverview }) {
  const warnings = [...value.coverage.gaps, ...value.coverage.limitations, ...value.overview.conflicts, ...value.overview.unknowns];
  return (
    <>
      <section className="rounded-card border border-line bg-white p-6 shadow-card">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-xs text-muted">状态 {value.status} · {value.sourceEventCount} 个来源事件</p>
            <h2 className="mt-1 text-xl font-semibold">项目从哪里来，现在是什么状态</h2>
          </div>
          <span className={`rounded-full px-3 py-1 text-xs font-medium ${value.coverage.complete ? "bg-emerald-50 text-emerald-700" : "bg-amber-50 text-amber-800"}`}>
            {value.coverage.complete ? "历史覆盖完整" : value.coverage.currentness}
          </span>
        </div>
        <div className="mt-5 grid gap-4 md:grid-cols-2">
          <StateCard label="最早可确认状态" value={value.overview.earliestConfirmedState} />
          <StateCard label="当前状态" value={value.overview.currentState} />
        </div>
        <p className="mt-4 text-xs text-muted">覆盖时间：{formatMoment(value.earliestEventAt)} → {formatMoment(value.latestEventAt)}</p>
      </section>

      <section className="rounded-card border border-line bg-white p-6 shadow-card">
        <h2 className="text-lg font-semibold">时间篇章</h2>
        <div className="mt-4 space-y-3">
          {value.overview.chapters.length ? value.overview.chapters.map((chapter) => (
            <Link className="block rounded-field border border-line p-4 hover:border-lineStrong hover:bg-surfaceAlt" href={projectHistoryHref(projectId, "chapter", chapter.id)} key={chapter.id}>
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h3 className="font-semibold">{chapter.title}</h3>
                <span className="text-xs text-muted">{chapter.storyCount} 个故事 · {chapter.rawEventCount} 个事件</span>
              </div>
              <p className="mt-2 text-sm leading-6 text-body">{chapter.summary}</p>
              <p className="mt-2 text-xs text-muted">{formatMoment(chapter.from)} → {formatMoment(chapter.to)}</p>
            </Link>
          )) : <p className="text-sm text-muted">当前没有可展示的时间篇章。</p>}
        </div>
      </section>

      <ListSection title="最近变化" items={value.overview.recentChanges} />
      {warnings.length ? <ListSection title="覆盖缺口、冲突与未知" items={warnings} tone="warning" /> : null}
      {value.errorSummary ? <ListSection title="最近一次刷新异常" items={[value.errorSummary]} tone="warning" /> : null}
    </>
  );
}

function ChapterView({ projectId, value }: { projectId: string; value: ProjectHistoryChapterDetail }) {
  return (
    <>
      <section className="rounded-card border border-line bg-white p-6 shadow-card">
        <p className="text-xs text-muted">时间篇章 · {formatMoment(value.chapter.from)} → {formatMoment(value.chapter.to)}</p>
        <h2 className="mt-1 text-xl font-semibold">{value.chapter.title}</h2>
        <p className="mt-3 max-w-4xl text-sm leading-6 text-body">{value.chapter.summary}</p>
        <p className="mt-3 text-xs text-muted">{value.chapter.storyCount} 个故事 · {value.chapter.rawEventCount} 个来源事件 · {value.chapter.authority}</p>
      </section>
      <section className="space-y-4">
        {value.stories.map((story) => <StoryCard key={story.id} projectId={projectId} story={story} />)}
      </section>
      {value.chapter.limitations.length ? <ListSection title="篇章限制" items={value.chapter.limitations} tone="warning" /> : null}
    </>
  );
}

function StoryView({ projectId, value }: { projectId: string; value: ProjectHistoryStoryDetail }) {
  const story = value.story;
  return (
    <>
      <section className="rounded-card border border-line bg-white p-6 shadow-card">
        <p className="text-xs text-muted">变化故事 · {formatMoment(story.occurredFrom)} → {formatMoment(story.occurredTo)}</p>
        <h2 className="mt-1 text-xl font-semibold">{story.humanTitle}</h2>
        <p className="mt-3 text-sm leading-6 text-body">{story.oneSentenceSummary}</p>
        <div className="mt-5 grid gap-4 lg:grid-cols-3">
          <StateCard label="Before" value={story.beforeState} />
          <StateCard label="Change" value={story.change} />
          <StateCard label="After" value={story.afterState} />
        </div>
        {story.reason ? <p className="mt-4 text-sm"><span className="font-semibold">有证据支持的原因：</span>{story.reason}</p> : null}
        {story.laterOutcome ? <p className="mt-3 text-sm"><span className="font-semibold">后续结果：</span>{story.laterOutcome}</p> : null}
        <p className="mt-4 text-xs text-muted">{story.rawEventCount} 个来源事件 · {story.evidenceCount} 条证据 · {story.authority} · {story.summaryStatus}</p>
      </section>

      {value.threads.length ? (
        <section className="rounded-card border border-line bg-white p-6 shadow-card">
          <h2 className="text-lg font-semibold">所属演变链</h2>
          <div className="mt-3 flex flex-wrap gap-2">
            {value.threads.map((thread) => (
              <Link className="rounded-full bg-brand-soft px-3 py-1.5 text-sm text-brand" href={projectHistoryHref(projectId, "thread", thread.id)} key={thread.id}>
                {thread.subjectLabel}
              </Link>
            ))}
          </div>
        </section>
      ) : null}

      <section className="rounded-card border border-line bg-white p-6 shadow-card">
        <h2 className="text-lg font-semibold">来源事件下钻</h2>
        <div className="mt-4 space-y-3">
          {value.events.map((event) => (
            <article className="rounded-field border border-line bg-surfaceAlt p-4" key={event.id}>
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h3 className="font-medium">{event.safeSourceLabel}</h3>
                <span className="text-xs text-muted">{projectHistoryTransitionLabel(event.transition)} · {event.sourceType}</span>
              </div>
              <p className="mt-2 text-xs text-muted">{formatMoment(event.occurredAt)} · {event.evidenceRefs.length} 条 Evidence · {event.rewriteState}</p>
            </article>
          ))}
        </div>
      </section>

      {story.conflicts.length ? <ListSection title="冲突" items={story.conflicts} tone="warning" /> : null}
      {story.unknowns.length ? <ListSection title="未知" items={story.unknowns} tone="warning" /> : null}
      {story.limitations.length ? <ListSection title="覆盖限制" items={story.limitations} tone="warning" /> : null}
    </>
  );
}

function ThreadView({ projectId, value }: { projectId: string; value: ProjectHistoryThreadDetail }) {
  return (
    <>
      <section className="rounded-card border border-line bg-white p-6 shadow-card">
        <p className="text-xs text-muted">演变链 · {value.thread.subjectType}</p>
        <h2 className="mt-1 text-xl font-semibold">{value.thread.subjectLabel}</h2>
        <div className="mt-4 flex flex-wrap items-center gap-2 text-sm">
          {value.thread.transitions.map((transition, index) => (
            <span className="contents" key={`${transition}-${index}`}>
              {index ? <span className="text-muted">→</span> : null}
              <span className="rounded-full bg-surfaceAlt px-3 py-1.5">{projectHistoryTransitionLabel(transition)}</span>
            </span>
          ))}
        </div>
        <p className="mt-4 text-sm leading-6 text-body">{value.thread.currentOutcome}</p>
        <p className="mt-3 text-xs text-muted">{value.thread.evidenceCount} 条证据</p>
      </section>
      <section className="space-y-4">
        {value.stories.map((story) => <StoryCard key={story.id} projectId={projectId} story={story} />)}
      </section>
      {value.thread.gaps.length ? <ListSection title="演变链缺口" items={value.thread.gaps} tone="warning" /> : null}
      {value.thread.conflicts.length ? <ListSection title="演变链冲突" items={value.thread.conflicts} tone="warning" /> : null}
      {value.thread.unknowns.length ? <ListSection title="演变链未知" items={value.thread.unknowns} tone="warning" /> : null}
    </>
  );
}

function StoryCard({ projectId, story }: { projectId: string; story: ProjectHistoryStory }) {
  return (
    <Link className="block rounded-card border border-line bg-white p-5 shadow-card hover:border-lineStrong hover:bg-surfaceAlt" href={projectHistoryHref(projectId, "story", story.id)}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="font-semibold">{story.humanTitle}</h3>
        <span className="text-xs text-muted">{story.rawEventCount} 个事件 · {story.evidenceCount} 条证据</span>
      </div>
      <p className="mt-2 text-sm leading-6 text-body">{story.oneSentenceSummary}</p>
      <p className="mt-2 text-xs text-muted">{formatMoment(story.occurredFrom)} → {formatMoment(story.occurredTo)}</p>
    </Link>
  );
}

function StateCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-field border border-line bg-surfaceAlt p-4">
      <p className="text-xs font-semibold uppercase tracking-wide text-muted">{label}</p>
      <p className="mt-2 text-sm leading-6 text-body">{value || "证据不足，当前未知。"}</p>
    </div>
  );
}

function ListSection({ title, items, tone = "default" }: { title: string; items: string[]; tone?: "default" | "warning" }) {
  return (
    <section className={`rounded-card border p-6 shadow-card ${tone === "warning" ? "border-amber-200 bg-amber-50" : "border-line bg-white"}`}>
      <h2 className="text-lg font-semibold">{title}</h2>
      <ul className="mt-3 space-y-2 text-sm leading-6 text-body">
        {items.map((item, index) => <li key={`${item}-${index}`}>• {item}</li>)}
      </ul>
    </section>
  );
}

function formatMoment(value: string | null | undefined) {
  if (!value) return "未知时间";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString("zh-CN", { hour12: false });
}
