"use client";

import Link from "next/link";
import { ExternalLink, EyeOff, FileSearch, Pin, RotateCcw, Save } from "lucide-react";
import { useParams, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { AppShell } from "@/components/AppShell";
import {
  getProject,
  getProjectHistoryChapter,
  getProjectHistoryCorrections,
  createProjectHistoryCorrection,
  getProjectHistoryEvidence,
  getProjectHistoryOverview,
  getProjectHistoryStory,
  getProjectHistoryThread,
  type Project,
  type ProjectHistoryChapterDetail,
  type ProjectHistoryEvidence,
  type ProjectHistoryEvent,
  type ProjectHistoryOverview,
  type ProjectHistoryStory,
  type ProjectHistoryStoryDetail,
  type ProjectHistoryThreadDetail,
} from "@/lib/api";
import { readSession } from "@/lib/auth";
import {
  projectHistoryEntityType,
  projectHistoryHref,
  projectHistoryPresentationLabel,
  projectHistoryRewriteStateLabel,
  projectHistoryRoleLabel,
  projectHistorySourceTypeLabel,
  projectHistoryStatusLabel,
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
  const [presentationRevision, setPresentationRevision] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    setHistory(null);
    setError("");
    const token = readSession().accessToken;
    Promise.all([
      getProject(token, params.projectId),
      loadHistory(token, params.projectId, entityType, entityId),
      getProjectHistoryCorrections(token, params.projectId),
    ]).then(([nextProject, nextHistory, corrections]) => {
      if (!active) return;
      const historyRevision = loadedPresentationRevision(nextHistory);
      if (historyRevision && corrections.presentationRevision && historyRevision !== corrections.presentationRevision) {
        throw new Error("项目历程展示在读取期间发生变化，请重新加载后再操作。");
      }
      setProject(nextProject);
      setHistory(nextHistory);
      setPresentationRevision(historyRevision || corrections.presentationRevision || "");
    }).catch((exception) => {
      if (!active) return;
      setError(exception instanceof Error ? exception.message : "项目历程加载失败");
    });
    return () => {
      active = false;
    };
  }, [entityId, entityType, params.projectId]);

  return (
    <AppShell eyebrow="项目历程预览" title={`${project?.name ?? "项目"} · 项目历程`}>
      <div className="space-y-5 p-6 md:p-8">
        <div className="flex flex-wrap items-center gap-3 text-sm">
          <Link className="rounded-field border border-line bg-white px-3 py-2 text-body hover:bg-surfaceAlt" href={`/projects/${params.projectId}`}>
            返回项目
          </Link>
          <Link className="rounded-field border border-line bg-white px-3 py-2 text-body hover:bg-surfaceAlt" href={projectHistoryHref(params.projectId)}>
            历程总览
          </Link>
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
        {history?.type === "story" ? (
          <StoryView
            projectId={params.projectId}
            value={history.value}
            presentationRevision={presentationRevision}
            onChanged={async () => {
              const token = readSession().accessToken;
              const next = await loadHistory(token, params.projectId, entityType, entityId);
              const corrections = await getProjectHistoryCorrections(token, params.projectId);
              const historyRevision = loadedPresentationRevision(next);
              if (historyRevision && corrections.presentationRevision && historyRevision !== corrections.presentationRevision) {
                throw new Error("项目历程展示在读取期间发生变化，请重新加载后再操作。");
              }
              setHistory(next);
              setPresentationRevision(historyRevision || corrections.presentationRevision || "");
            }}
          />
        ) : null}
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

function loadedPresentationRevision(history: LoadedHistory): string {
  return history.value.presentationRevision || "";
}

function OverviewView({ projectId, value }: { projectId: string; value: ProjectHistoryOverview }) {
  const warnings = [...value.coverage.gaps, ...value.coverage.limitations, ...value.overview.conflicts, ...value.overview.unknowns];
  return (
    <>
      <section className="rounded-card border border-line bg-white p-6 shadow-card">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-xs text-muted">{projectHistoryStatusLabel(value.status)} · {value.sourceEventCount} 个来源事件</p>
            <h2 className="mt-1 text-xl font-semibold">项目从哪里来，现在是什么状态</h2>
          </div>
          <span className={`rounded-full px-3 py-1 text-xs font-medium ${value.coverage.complete ? "bg-emerald-50 text-emerald-700" : "bg-amber-50 text-amber-800"}`}>
            {value.coverage.complete ? "历史覆盖完整" : "覆盖范围有限"}
          </span>
        </div>
        <div className="mt-5 grid gap-4 md:grid-cols-2">
          <StateCard label="最早可确认状态" value={value.overview.earliestConfirmedState} />
          <StateCard label="当前状态" value={value.overview.currentState} />
        </div>
        <p className="mt-4 text-xs text-muted">覆盖时间：{formatMoment(value.earliestEventAt)} → {formatMoment(value.latestEventAt)}</p>
        <details className="mt-4 border-t border-line pt-4 text-xs text-muted">
          <summary className="cursor-pointer font-medium text-body">查看工程详情与审计信息</summary>
          <p className="mt-3">状态：{value.status} · 当前性：{value.coverage.currentness} · 来源事件：{value.sourceEventCount}</p>
        </details>
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
        <p className="mt-3 text-xs text-muted">{value.chapter.storyCount} 个故事 · {value.chapter.rawEventCount} 个来源事件</p>
        <details className="mt-4 border-t border-line pt-4 text-xs text-muted">
          <summary className="cursor-pointer font-medium text-body">查看工程详情与审计信息</summary>
          <p className="mt-3">归纳权威：{value.chapter.authority} · 覆盖：{value.chapter.coverage}</p>
        </details>
      </section>
      <section className="space-y-4">
        {value.stories.map((story) => <StoryCard key={story.id} projectId={projectId} story={story} />)}
      </section>
      {value.chapter.limitations.length ? <ListSection title="篇章限制" items={value.chapter.limitations} tone="warning" /> : null}
    </>
  );
}

function StoryView({
  projectId,
  value,
  presentationRevision,
  onChanged,
}: {
  projectId: string;
  value: ProjectHistoryStoryDetail;
  presentationRevision: string;
  onChanged: () => Promise<void>;
}) {
  const story = value.story;
  const [title, setTitle] = useState(story.humanTitle);
  const [summary, setSummary] = useState(story.oneSentenceSummary);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");

  useEffect(() => {
    setTitle(story.humanTitle);
    setSummary(story.oneSentenceSummary);
  }, [story.id, story.humanTitle, story.oneSentenceSummary]);

  useEffect(() => setMessage(""), [story.id]);

  async function submit(type: string, fields: Record<string, unknown> = {}) {
    setBusy(true);
    setMessage("");
    try {
      await createProjectHistoryCorrection(readSession().accessToken, projectId, {
        type,
        targetType: "STORY",
        targetId: story.id,
        ...(presentationRevision ? { expectedPresentationRevision: presentationRevision } : {}),
        ...fields,
      });
      await onChanged();
      setMessage("展示内容已更新。");
    } catch (exception) {
      setMessage(exception instanceof Error ? exception.message : "展示修正失败，请刷新后重试。");
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <section className="rounded-card border border-line bg-white p-6 shadow-card">
        <div className="flex flex-wrap items-center justify-between gap-3 text-xs text-muted">
          <p>变化故事 · {formatMoment(story.occurredFrom)} → {formatMoment(story.occurredTo)}</p>
          <div className="flex flex-wrap gap-2">
            <span className="rounded-full bg-surfaceAlt px-3 py-1">{projectHistoryRoleLabel(story.role)}</span>
            <span className="rounded-full bg-brand-soft px-3 py-1 text-brand">{projectHistoryPresentationLabel(story.presentationAuthority)}</span>
          </div>
        </div>
        <h2 className="mt-1 text-xl font-semibold">{story.humanTitle}</h2>
        <p className="mt-3 text-sm leading-6 text-body">{story.oneSentenceSummary}</p>
        <div className="mt-5 grid gap-4 lg:grid-cols-3">
          <StateCard label="原来状态" value={story.beforeState} />
          <StateCard label="本次变化" value={story.change} />
          <StateCard label="当前结果" value={story.afterState} />
        </div>
        {story.reason ? <p className="mt-4 text-sm"><span className="font-semibold">有证据支持的原因：</span>{story.reason}</p> : null}
        {story.laterOutcome ? <p className="mt-3 text-sm"><span className="font-semibold">后续结果：</span>{story.laterOutcome}</p> : null}
        <p className="mt-4 text-xs text-muted">{story.rawEventCount} 个来源事件 · {story.evidenceCount} 条证据</p>
        <div className="mt-6 border-t border-line pt-5">
          <h3 className="text-sm font-semibold">展示修正</h3>
          <div className="grid gap-4 lg:grid-cols-2">
            <label className="text-sm text-body">
              标题
              <input className="mt-2 w-full rounded-field border border-line bg-white px-3 py-2" value={title} onChange={(event) => setTitle(event.target.value)} maxLength={240} />
            </label>
            <label className="text-sm text-body">
              摘要
              <textarea className="mt-2 min-h-24 w-full rounded-field border border-line bg-white px-3 py-2" value={summary} onChange={(event) => setSummary(event.target.value)} maxLength={1200} />
            </label>
          </div>
          <div className="mt-4 flex flex-wrap gap-2">
            <button type="button" className="inline-flex items-center gap-2 rounded-field bg-brand px-3 py-2 text-sm text-white disabled:opacity-50" disabled={busy || !title.trim()} onClick={() => submit("RENAME_STORY", { title: title.trim(), declaredTitle: title.trim() })}><Save aria-hidden="true" size={16} />保存标题</button>
            <button type="button" className="inline-flex items-center gap-2 rounded-field border border-line px-3 py-2 text-sm text-body disabled:opacity-50" disabled={busy || !summary.trim()} onClick={() => submit("EDIT_SUMMARY", { summary: summary.trim(), declaredSummary: summary.trim() })}><Save aria-hidden="true" size={16} />保存摘要</button>
            <button type="button" className="inline-flex items-center gap-2 rounded-field border border-line px-3 py-2 text-sm text-body disabled:opacity-50" disabled={busy} onClick={() => submit("HIDE_STORY")}><EyeOff aria-hidden="true" size={16} />从默认阅读中隐藏</button>
            <button type="button" className="inline-flex items-center gap-2 rounded-field border border-line px-3 py-2 text-sm text-body disabled:opacity-50" disabled={busy} onClick={() => submit("PIN_STORY")}><Pin aria-hidden="true" size={16} />置顶阅读</button>
            <button type="button" className="inline-flex items-center gap-2 rounded-field border border-amber-300 bg-amber-50 px-3 py-2 text-sm text-amber-900 disabled:opacity-50" disabled={busy} onClick={() => submit("RESTORE_AUTOMATIC")}><RotateCcw aria-hidden="true" size={16} />恢复自动展示</button>
          </div>
          {message ? <p className="mt-3 text-sm text-amber-800" role="status">{message}</p> : null}
        </div>
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

      <details className="rounded-card border border-line bg-white p-6 shadow-card">
        <summary className="cursor-pointer text-lg font-semibold">查看来源事件、Commit 与 Evidence</summary>
        <div className="mt-4 space-y-3">
          {value.events.map((event) => <HistoryEventCard event={event} key={event.id} projectId={projectId} />)}
        </div>
      </details>

      {story.conflicts.length ? <ListSection title="冲突" items={story.conflicts} tone="warning" /> : null}
      {story.correctionConflicts?.length ? <ListSection title="展示修正冲突" items={story.correctionConflicts} tone="warning" /> : null}
      {story.unknowns.length ? <ListSection title="未知" items={story.unknowns} tone="warning" /> : null}
      {story.limitations.length ? <ListSection title="覆盖限制" items={story.limitations} tone="warning" /> : null}
      <details className="rounded-card border border-line bg-white p-6 shadow-card">
          <summary className="cursor-pointer text-sm font-semibold">查看工程详情与审计信息</summary>
          <dl className="mt-4 grid gap-3 text-xs text-muted md:grid-cols-2">
            <div><dt className="font-medium text-body">归纳权威</dt><dd className="mt-1">{story.authority}</dd></div>
            <div><dt className="font-medium text-body">摘要状态</dt><dd className="mt-1">{story.summaryStatus}</dd></div>
            <div><dt className="font-medium text-body">展示角色</dt><dd className="mt-1">{story.role ?? "PRIMARY"}</dd></div>
            <div><dt className="font-medium text-body">展示权威</dt><dd className="mt-1">{story.presentationAuthority ?? "AUTOMATIC"}</dd></div>
          </dl>
          <p className="mt-4 text-xs text-muted">自动标题：{story.automaticTitle ?? story.humanTitle}</p>
          <p className="mt-2 text-xs text-muted">自动摘要：{story.automaticSummary ?? story.oneSentenceSummary}</p>
          {story.technicalAtomRefs?.length ? <p className="mt-3 text-xs text-muted">Technical Atom：{story.technicalAtomRefs.join("、")}</p> : null}
          {story.technicalDetails?.length ? <ul className="mt-3 space-y-2 text-sm text-body">{story.technicalDetails.map((item, index) => <li key={`${item}-${index}`}>{item}</li>)}</ul> : null}
      </details>
    </>
  );
}

function HistoryEventCard({ projectId, event }: { projectId: string; event: ProjectHistoryEvent }) {
  const [evidence, setEvidence] = useState<ProjectHistoryEvidence | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const sourceLink = safeHistoryDeepLink(event.rawSourceDeepLink);

  async function loadEvidence() {
    if (evidence || loading) return;
    setLoading(true);
    setError("");
    try {
      setEvidence(await getProjectHistoryEvidence(readSession().accessToken, projectId, event.id));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Evidence 详情读取失败");
    } finally {
      setLoading(false);
    }
  }

  return (
    <article className="rounded-field border border-line bg-surfaceAlt p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="font-medium">{event.userSummary || event.safeSourceLabel}</h3>
        <span className="text-xs text-muted">{projectHistoryTransitionLabel(event.transition)} · {projectHistorySourceTypeLabel(event.sourceType)}</span>
      </div>
      <p className="mt-2 text-xs text-muted">{formatMoment(event.occurredAt)} · {event.evidenceRefs.length} 条 Evidence · {projectHistoryRewriteStateLabel(event.rewriteState)}</p>
      {event.affectedPaths.length ? (
        <div className="mt-3 text-xs text-body">
          <p className="font-medium">涉及文件</p>
          <ul className="mt-1 space-y-1 text-muted">
            {event.affectedPaths.slice(0, 12).map((path) => <li className="break-all" key={path}>{path}</li>)}
          </ul>
        </div>
      ) : null}
      <details className="mt-3 text-xs text-muted">
        <summary className="cursor-pointer font-medium text-body">查看原始提交与工程信息</summary>
        <p className="mt-2 break-words">原始提交信息：{event.safeSourceLabel}</p>
      </details>
      <div className="mt-3 flex flex-wrap gap-2">
        <button type="button" className="inline-flex items-center gap-2 rounded-field border border-line bg-white px-3 py-2 text-xs text-body disabled:opacity-50" disabled={loading} onClick={loadEvidence}>
          <FileSearch aria-hidden="true" size={15} />{loading ? "正在读取" : evidence ? "Evidence 已展开" : "查看 Evidence 详情"}
        </button>
        {sourceLink ? (
          <a className="inline-flex items-center gap-2 rounded-field border border-line bg-white px-3 py-2 text-xs text-body" href={sourceLink} rel="noreferrer" target={sourceLink.startsWith("https://") ? "_blank" : undefined}>
            <ExternalLink aria-hidden="true" size={15} />打开原始来源
          </a>
        ) : null}
      </div>
      {error ? <p className="mt-3 text-xs text-rose-700" role="status">{error}</p> : null}
      {evidence ? (
        <div className="mt-3 space-y-2 border-t border-line pt-3">
          {evidence.items.length ? evidence.items.map((item) => {
            const deepLink = safeHistoryDeepLink(item.deepLink);
            return (
              <div className="text-xs text-muted" key={`${item.type}:${item.reference}`}>
                <p className="font-medium text-body">{item.label || item.type}</p>
                <p className="mt-1 break-all">{item.reference} · {item.currentness} · {item.validation}</p>
                {deepLink ? <a className="mt-1 inline-flex items-center gap-1 text-brand hover:underline" href={deepLink} rel="noreferrer" target={deepLink.startsWith("https://") ? "_blank" : undefined}><ExternalLink aria-hidden="true" size={13} />打开证据</a> : null}
                {item.limitations.length ? <p className="mt-1 text-amber-800">{item.limitations.join("；")}</p> : null}
              </div>
            );
          }) : <p className="text-xs text-muted">当前事件没有可进一步展开的 Evidence。</p>}
          {evidence.truncated ? <p className="text-xs text-amber-800">Evidence 详情达到安全上限，当前仅显示有界结果。</p> : null}
        </div>
      ) : null}
    </article>
  );
}

function ThreadView({ projectId, value }: { projectId: string; value: ProjectHistoryThreadDetail }) {
  return (
    <>
      <section className="rounded-card border border-line bg-white p-6 shadow-card">
        <p className="text-xs text-muted">演变链</p>
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
        <details className="mt-4 border-t border-line pt-4 text-xs text-muted">
          <summary className="cursor-pointer font-medium text-body">查看工程详情与审计信息</summary>
          <p className="mt-3">主题类型：{value.thread.subjectType} · 展示权威：{value.thread.presentationAuthority ?? "AUTOMATIC"}</p>
        </details>
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
        <div className="flex flex-wrap items-center gap-2 text-xs text-muted">
          <span className="rounded-full bg-surfaceAlt px-2.5 py-1">{projectHistoryRoleLabel(story.role)}</span>
          <span>{story.rawEventCount} 个事件 · {story.evidenceCount} 条证据</span>
        </div>
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

function safeHistoryDeepLink(value: string | null | undefined) {
  const candidate = value?.trim() ?? "";
  return /^(https:\/\/|\/projects\/|obsidian:\/\/)/i.test(candidate) ? candidate : "";
}
