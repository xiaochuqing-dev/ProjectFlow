"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { ArrowLeft, ArrowRight, BookOpenText, Clock3, Search, Workflow } from "lucide-react";
import {
  getProjectHistoryChapter, getProjectHistoryStory, getProjectHistoryThread, listProjectHistoryChapters, listProjectHistoryThreads,
  type ProjectHistoryChapter, type ProjectHistoryChapterDetail, type ProjectHistoryPage,
  type ProjectHistoryStory, type ProjectHistoryThread, type ProjectHistoryThreadDetail,
} from "@/lib/api";
import { readSession } from "@/lib/auth";
import { claimLabels } from "@/lib/workspace-claims";
import { projectHistoryPresentationLabel, projectHistoryTransitionLabel } from "@/lib/project-history";
import { persistedStory, workspaceHref, type WorkspaceProject, type WorkspaceStory } from "@/lib/workspace-preview";

type Props = { project: WorkspaceProject; demo: boolean; onStory: (story: WorkspaceStory) => void };

export function HistoryPage({ project, demo, onStory }: Props) {
  const query = useSearchParams();
  const router = useRouter();
  const axis = query.get("axis") === "threads" || query.has("thread") ? "threads"
    : query.has("chapter") || query.get("axis") === "time" ? "chapters" : "overview";
  const chapterId = query.get("chapter") || project.chapters.at(-1)?.id || "";
  const base = workspaceHref("history", demo, project.id);
  const [page, setPage] = useState(0);
  const [chapterList, setChapterList] = useState<ProjectHistoryPage<ProjectHistoryChapter> | null>(null);
  const [listError, setListError] = useState("");
  const [listLoading, setListLoading] = useState(!demo);
  const [retry, setRetry] = useState(0);
  const [detail, setDetail] = useState<ProjectHistoryChapterDetail | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(!demo);
  const [storyPage, setStoryPage] = useState(0);
  const [showSupporting, setShowSupporting] = useState(false);
  const [deepLinkError, setDeepLinkError] = useState("");
  const storyId = query.get("story");
  const onStoryRef = useRef(onStory);
  onStoryRef.current = onStory;
  const selectedId = chapterId || chapterList?.items.at(-1)?.id || "";

  useEffect(() => {
    if (demo || !storyId) return;
    let active = true; setDeepLinkError("");
    getProjectHistoryStory(readSession().accessToken, project.id, storyId)
      .then(value => { if (active) onStoryRef.current(persistedStory(value.story)); })
      .catch(() => { if (active) setDeepLinkError("这条旧链接的变化记录无法读取，其他项目历程仍可查看。"); });
    return () => { active = false; };
  }, [demo, project.id, storyId]);

  useEffect(() => {
    if (demo) return;
    let active = true;
    setListLoading(true); setListError("");
    listProjectHistoryChapters(readSession().accessToken, project.id, page)
      .then((value) => { if (active) setChapterList(value); })
      .catch((e) => { if (active) setListError(e.message); })
      .finally(() => { if (active) setListLoading(false); });
    return () => { active = false; };
  }, [demo, project.id, project.updated, page, retry]);
  useEffect(() => {
    if (demo || axis !== "chapters" || !selectedId) return;
    let active = true;
    setLoading(true); setError(""); setStoryPage(0); setShowSupporting(false);
    getProjectHistoryChapter(readSession().accessToken, project.id, selectedId)
      .then((value) => { if (active) setDetail(value); })
      .catch((e) => { if (active) setError(e.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [demo, project.id, project.updated, selectedId, axis, retry]);

  const chapters = demo ? project.chapters : (chapterList?.items ?? []).filter((c) => !c.hiddenByDefault).map((c) => ({
    id: c.id, title: c.title, summary: c.summary, range: historyDateRange(c.from, c.to), version: "",
  }));
  const liveChapter = detail?.chapter.id === selectedId ? detail : null;
  const chapter = demo ? chapters.find((c) => c.id === selectedId) : liveChapter ? {
    ...liveChapter.chapter, range: historyDateRange(liveChapter.chapter.from, liveChapter.chapter.to),
  } : null;
  const supportingCount = liveChapter?.stories.filter((s) => s.role === "SUPPORTING").length ?? 0;
  const stories = demo ? project.stories.filter((s) => s.chapter === selectedId)
    : (liveChapter?.stories ?? []).filter((s) => !s.hiddenByDefault && (showSupporting || s.role !== "SUPPORTING")).map(persistedStory);

  function openChapter(id: string) { router.push(`${base}&chapter=${encodeURIComponent(id)}`, { scroll: false }); }
  return <div className="pf-history-page">
    {deepLinkError && <p className="pf-notice" role="status">{deepLinkError}</p>}
    <div className="pf-history-lead">
      <span className="pf-eyebrow">{project.name} / PROJECT JOURNEY</span><h2>每一次变化，都有来处。</h2>{demo && <p>{project.summary}</p>}
      <div><Clock3 size={14} />{project.chapters[0]?.range.split(" – ")[0] || "尚无历史起点"}<span>—</span>
        {project.chapters.at(-1)?.range.split(" – ").at(-1) || "等待材料"}
        <span className="pf-chip recorded">{demo ? project.chapters.length : chapterList?.totalElements ?? project.chapters.length} 个时间篇章 · 系统归纳</span></div>
    </div>
    {(project.stale || project.degraded) && <div className="pf-notice" role="status">
      {project.stale ? "来源可能已变化，以下保留上次已保存的历程。" : "部分内容待核对，以下保留已保存的历程。"}
    </div>}
    <div className="pf-history-axis">
      <div className="pf-segmented" aria-label="历程阅读方式">
        <button aria-pressed={axis === "overview"} className={axis === "overview" ? "active" : ""} onClick={() => router.push(base, { scroll: false })}>变化概览</button>
        <button aria-pressed={axis === "chapters"} className={axis === "chapters" ? "active" : ""}
          onClick={() => router.push(`${base}&axis=time${chapterId ? `&chapter=${encodeURIComponent(chapterId)}` : ""}`, { scroll: false })}><BookOpenText size={16} />按时间查看</button>
        <button aria-pressed={axis === "threads"} className={axis === "threads" ? "active" : ""}
          onClick={() => router.push(`${base}&axis=threads${selectedId ? `&chapter=${encodeURIComponent(selectedId)}` : ""}`, { scroll: false })}><Workflow size={16} />按长期主题查看</button>
      </div><span>{axis === "chapters" ? "按来源时间阅读各组变化；日期可以重叠" : axis === "threads" ? "某个功能、问题或方向如何持续变化" : "先看最近发生了什么，再深入时间或主题"}</span>
    </div>
    {axis === "threads" ? <ThreadReader project={project} demo={demo} onStory={onStory}
      chapters={chapterList} chapterPage={page} onChapterPage={setPage} chapterLoading={listLoading}
      chapterError={listError} onChapter={openChapter} onRetry={() => setRetry((n) => n + 1)} /> : axis === "overview" ? <section className="pf-history-overview"><h2>项目变化概览</h2><p>按已记录的来源时间阅读变化；时间依据不完整时，以证据中的限制为准。标题与摘要是系统归纳，计划和冲突保留来源身份。</p>{project.stories.slice(0, 8).map(story => <StoryCard key={story.id} story={story} onStory={onStory}/>)}{!project.stories.length && <p className="pf-notice">尚无可读历史；当前材料不会被编排成虚构的成熟阶段。</p>}</section> : <div className="pf-journey-layout">
      <nav className="pf-chapter-nav" aria-label="项目阶段">
        {listLoading && <p role="status">正在读取篇章目录…</p>}
        {listError && <ReadError message={listError} onRetry={() => setRetry((n) => n + 1)} />}
        {chapters.map((c, i) => <button key={c.id} className={selectedId === c.id ? "active" : ""} aria-pressed={selectedId === c.id} onClick={() => openChapter(c.id)}>
          <span className="pf-chapter-node">{page * 20 + i + 1}</span><span><small>CHAPTER {String(page * 20 + i + 1).padStart(2, "0")}</small><strong>{c.title}</strong><small>{c.range}</small></span>
        </button>)}
        {!demo && <ReadingPages page={chapterList?.page ?? page} totalPages={chapterList?.totalPages ?? 0} onPage={setPage} disabled={listLoading} label="篇章目录" />}
      </nav>
      <div className="pf-chapter-reading">
        {loading && !demo && selectedId && <p role="status">正在读取阶段内容…</p>}
        {error && <ReadError message={error} onRetry={() => setRetry((n) => n + 1)} />}
        {chapter ? <>
          <header><span className="pf-eyebrow">时间篇章</span><h2>{chapter.title}</h2><p>{chapter.summary}</p><span><Clock3 size={13} />{chapter.range}</span></header>
          {!!supportingCount && <label className="pf-check-field pf-reading-filter"><input type="checkbox" checked={showSupporting} onChange={(e) => { setShowSupporting(e.target.checked); setStoryPage(0); }} />包含 {supportingCount} 条支撑工作</label>}
          {stories.slice(storyPage * 12, (storyPage + 1) * 12).map((story) => <StoryCard key={story.id} story={story} onStory={onStory} />)}
          {!stories.length && <p className="pf-page-footnote">这个篇章中暂没有可展示的变化记录。</p>}
          <ReadingPages page={storyPage} totalPages={Math.ceil(stories.length / 12)} onPage={setStoryPage} label="篇章故事" />
          {liveChapter && <HistoryBoundaries limitations={liveChapter.chapter.limitations} />}
        </> : !listLoading && !error && !selectedId && <div className="pf-inline-empty"><BookOpenText size={35} /><h3>这个项目还没有历程篇章</h3><p>当前材料不足以建立历史。已有当前状态仍可继续阅读。</p></div>}
      </div>
    </div>}
  </div>;
}

function ThreadReader({ project, demo, onStory, chapters, chapterPage, onChapterPage, chapterLoading, chapterError, onChapter, onRetry }: Props & {
  chapters: ProjectHistoryPage<ProjectHistoryChapter> | null; chapterPage: number; onChapterPage: (page: number) => void;
  chapterLoading: boolean; chapterError: string; onChapter: (id: string) => void; onRetry: () => void;
}) {
  const query = useSearchParams();
  const router = useRouter();
  const threadId = query.get("thread") || "";
  const page = Math.min(100000, Math.max(0, Math.floor(Number(query.get("threadPage")) || 0)));
  const subject = query.get("subject") || "";
  const [search, setSearch] = useState(subject);
  const [list, setList] = useState<ProjectHistoryPage<ProjectHistoryThread> | null>(null);
  const listScope = useRef("");
  const [detail, setDetail] = useState<ProjectHistoryThreadDetail | null>(null);
  const [loading, setLoading] = useState(!demo);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  const [storyPage, setStoryPage] = useState(0);
  const heading = useRef<HTMLHeadingElement>(null);
  const base = workspaceHref("history", demo, project.id) + (query.get("chapter") ? `&chapter=${encodeURIComponent(query.get("chapter")!)}` : "");
  const listHref = `${base}&axis=threads&threadPage=${page}&subject=${encodeURIComponent(subject)}`;

  useEffect(() => { setSearch(subject); }, [subject]);
  useEffect(() => {
    if (demo) return;
    let active = true;
    setLoading(true); setError("");
    const token = readSession().accessToken;
    const read = threadId ? getProjectHistoryThread(token, project.id, threadId).then((value) => { if (active) setDetail(value); })
      : listProjectHistoryThreads(token, project.id, page, subject).then((value) => { if (active) { listScope.current = `${page}:${subject}`; setList(value); } });
    read.catch((e) => { if (active) setError(e.message); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [demo, project.id, project.updated, threadId, page, subject, retry]);
  useEffect(() => { setStoryPage(0); }, [threadId]);
  useEffect(() => { if (threadId && !loading) heading.current?.focus(); }, [threadId, loading]);

  const examples = demo && project.id === "corporation" ? [
    { id: "demo-collaboration", subjectLabel: "从独立执行到多 Agent 协作", summary: "多 Agent 能力建设 → 企业内测与优化", stories: [project.stories[2], project.stories[0]] },
    { id: "demo-knowledge", subjectLabel: "让企业知识进入协作流程", summary: "知识沉淀与权限边界", stories: [project.stories[1]] },
  ] : [];
  const example = examples.find((item) => item.id === threadId);
  const current = detail?.thread.id === threadId ? detail : null;
  const thread = current?.thread;
  const storyRefs = new Set(thread?.storyRefs ?? []);
  const ordered = [...(current?.stories ?? [])].sort((a, b) => a.occurredFrom.localeCompare(b.occurredFrom) || a.id.localeCompare(b.id));
  const storyItems = demo ? example?.stories ?? [] : ordered.map(persistedStory);
  const missing = thread ? thread.storyRefs.filter((id) => !current?.stories.some((story) => story.id === id)).length : 0;
  const relatedChapters = (chapters?.items ?? []).filter((chapter) => chapter.storyRefs.some((id) => storyRefs.has(id)));
  const visibleList = listScope.current === `${page}:${subject}` ? list : null;

  return <section className="pf-threads-view" aria-label="演变主线阅读">
    {threadId ? <>
      <Link className="pf-text-link pf-reading-back" href={listHref}><ArrowLeft size={15} />返回主线目录</Link>
      {loading && <p role="status">正在读取主线内容…</p>}
      {error && <ReadError message={error} onRetry={() => setRetry((n) => n + 1)} />}
      {(thread || example) ? <>
        <header className="pf-thread-detail-heading">
          <span className="pf-eyebrow">长期主题{demo ? " · 示例" : ""}</span>
          <h2 ref={heading} tabIndex={-1}>{example?.subjectLabel ?? thread?.subjectLabel}</h2>
          <p>{(example?.summary ?? thread?.currentOutcome) || "现有记录尚未确认主题的当前结果。"}</p>
          {!demo && <span><Clock3 size={14} />关联变化覆盖：{historyDateRange(ordered[0]?.occurredFrom, latestStoryTime(ordered))}</span>}
          <small>{storyItems.length} 条可阅读变化 · {demo ? "示例内容" : project.stale ? "可能已过期" : "已保存的演变记录"}</small>
        </header>
        {thread && <HistoryBoundaries conflicts={thread.conflicts} unknowns={thread.unknowns}
          limitations={[...thread.gaps, ...(missing ? [`${missing} 条关联记录目前不可读取，以下内容不代表完整主线。`] : [])]} />}
        <h3 className="pf-reading-section-title">沿着变化继续阅读</h3>
        <div className="pf-thread-story-list">
          {storyItems.slice(storyPage * 12, (storyPage + 1) * 12).map((story) => <StoryCard key={story.id} story={story} onStory={onStory} />)}
          {!storyItems.length && <p className="pf-page-footnote">当前没有可读取的关联故事，主线摘要和缺口仍保留。</p>}
        </div>
        <ReadingPages page={storyPage} totalPages={Math.ceil(storyItems.length / 12)} onPage={setStoryPage} label="主线故事" />
        {!demo && <details className="pf-source-details pf-related-chapters"><summary>查看相关时间篇章</summary>
          {chapterLoading && <p role="status">正在读取篇章关系…</p>}
          {chapterError && <ReadError message={chapterError} onRetry={onRetry} />}
          {relatedChapters.map((chapter) => <button className="pf-button" key={chapter.id} onClick={() => onChapter(chapter.id)}><BookOpenText size={14} />{chapter.title}</button>)}
          {!chapterLoading && !chapterError && !relatedChapters.length && <p>当前目录页中没有关联篇章。</p>}
          <ReadingPages page={chapters?.page ?? chapterPage} totalPages={chapters?.totalPages ?? 0} onPage={onChapterPage} label="篇章关系目录" disabled={chapterLoading} />
        </details>}
        {thread && <details className="pf-source-details"><summary>查看主线工程详情</summary>
          <p>展示来源：{projectHistoryPresentationLabel(thread.presentationAuthority)}</p>
          <p>转换记录：{thread.transitions.map(projectHistoryTransitionLabel).join(" → ") || "尚无记录"}</p>
          <p>证据条目：{thread.evidenceCount}。每条证据在关联故事中按来源展开。</p>
          <p>主线标识：{thread.id}</p><p>展示版本：{current?.presentationRevision}</p>
        </details>}
      </> : !loading && !error && <p className="pf-notice">没有找到这条主线，请返回目录重新选择。</p>}
    </> : <>
      <h2>跨阶段，读懂一个主题的演变</h2><p>主题连接不同篇章中的真实变化；它不是项目完成度，也不替代时间篇章。</p>
      <form className="pf-thread-search" onSubmit={(event) => { event.preventDefault(); router.push(`${base}&axis=threads&subject=${encodeURIComponent(search.trim())}`, { scroll: false }); }}>
        <label className="pf-filter-search"><Search size={15} /><input aria-label="搜索演变主线" placeholder="按主题查找…" value={search} onChange={(event) => setSearch(event.target.value)} maxLength={200} /></label>
        <button className="pf-button" type="submit">查找</button>
      </form>
      {loading && <p role="status">正在读取演变主线…</p>}
      {error && <ReadError message={error} onRetry={() => setRetry((n) => n + 1)} />}
      <div className="pf-thread-grid">
        {demo ? examples.filter((item) => (item.subjectLabel + item.summary).includes(subject)).map((item) => <Link className="pf-thread-card" key={item.id} href={`${listHref}&thread=${item.id}`}>
          <Workflow size={22} /><h3>{item.subjectLabel}</h3><p>{item.summary}</p><span>{item.stories.length} 条变化 · 示例<ArrowRight size={15} /></span>
        </Link>) : visibleList?.items.map((item) => <Link className="pf-thread-card" key={item.id} href={`${listHref}&thread=${encodeURIComponent(item.id)}`}>
          <Workflow size={22} /><h3>{item.subjectLabel}</h3><p>{item.currentOutcome || "当前结果尚未确认"}</p>
          <span>{item.storyRefs.length} 条关联变化<ArrowRight size={15} /></span>
          {!!item.conflicts.length && <small className="pf-chip conflict">存在冲突</small>}
          {!!item.unknowns.length && <small className="pf-chip unknown">仍有未知</small>}
          {!!item.gaps.length && <small className="pf-chip attention">覆盖有缺口</small>}
        </Link>)}
      </div>
      {!loading && !error && !(demo ? examples.filter((item) => (item.subjectLabel + item.summary).includes(subject)).length : visibleList?.items.length) && <div className="pf-inline-empty">
        <Workflow size={30} /><h3>{subject ? "没有匹配的演变主线" : "还没有已保存的演变主线"}</h3>
        <p>{subject ? "可以换个关键词，或清空搜索查看全部主题。" : "材料暂未形成可追溯的长期主题。仍可按项目阶段阅读已有变化。"}</p>
      </div>}
      {!demo && <ReadingPages page={page} totalPages={list?.totalPages ?? 0} onPage={(next) => router.push(`${base}&axis=threads&threadPage=${next}&subject=${encodeURIComponent(subject)}`, { scroll: false })} disabled={loading} label="主线目录" />}
    </>}
  </section>;
}

export function StoryCard({ story, onStory }: { story: WorkspaceStory; onStory: (story: WorkspaceStory) => void }) {
  return <button className="pf-story-card" onClick={() => onStory(story)}>
    <div><time>{story.date}</time><span className={`pf-chip ${story.status}`}>{story.classification ? claimLabels[story.classification] : story.status === "conflict" ? "存在冲突" : story.status === "attention" ? "待核对" : "变化记录"}</span></div>
    <h3>{story.title}</h3><p>{story.summary}</p><footer><span>阅读此前状态、本次变化与当前结果</span><ArrowRight size={16} /></footer>
  </button>;
}

export function HistoryBoundaries({ conflicts = [], unknowns = [], limitations = [] }: { conflicts?: string[]; unknowns?: string[]; limitations?: string[] }) {
  return <div className="pf-history-boundaries">{[
    { label: "存在冲突", items: conflicts, tone: "conflict" }, { label: "尚未确认", items: unknowns, tone: "unknown" }, { label: "覆盖与缺口", items: limitations, tone: "attention" },
  ].filter((group) => group.items.length).map((group) => <section key={group.label} className={group.tone}><h3>{group.label}</h3><ul>{group.items.map((item, i) => <li key={i}>{item}</li>)}</ul></section>)}</div>;
}

export function ReadError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return <div className="pf-read-error" role="alert"><p>{message}</p><button className="pf-button" onClick={onRetry}>重新读取</button></div>;
}

export function ReadingPages({ page, totalPages, onPage, disabled, label }: { page: number; totalPages: number; onPage: (page: number) => void; disabled?: boolean; label: string }) {
  if (totalPages <= 1) return null;
  return <nav className="pf-reading-pages" aria-label={`${label}分页`}>
    <button className="pf-button" disabled={disabled || page === 0} onClick={() => onPage(page - 1)} aria-label={`${label}上一页`}><ArrowLeft size={14} /></button>
    <span>第 {page + 1} / {totalPages} 页</span>
    <button className="pf-button" disabled={disabled || page >= totalPages - 1} onClick={() => onPage(page + 1)} aria-label={`${label}下一页`}><ArrowRight size={14} /></button>
  </nav>;
}

export function historyDateRange(from?: string, to?: string) {
  const date = (value?: string) => value && !Number.isNaN(Date.parse(value)) ? new Date(value).toLocaleDateString("zh-CN") : "时间未知";
  return `${date(from)} – ${date(to)}`;
}

function latestStoryTime(stories: ProjectHistoryStory[]) {
  return stories.map((story) => story.occurredTo).filter(Boolean).sort().at(-1);
}
