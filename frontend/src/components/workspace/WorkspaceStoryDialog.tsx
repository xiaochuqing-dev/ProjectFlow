"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ArrowLeft, ExternalLink, GitBranch, Workflow } from "lucide-react";
import {
  getProjectHistoryStory, getProjectHistoryEvidence, type ProjectHistoryStoryDetail,
  type ProjectHistoryEvent, type ProjectHistoryEvidence,
} from "@/lib/api";
import { readSession } from "@/lib/auth";
import {
  projectHistoryPresentationLabel, projectHistoryRewriteStateLabel, projectHistorySourceTypeLabel,
  projectHistoryTransitionLabel, safeHistoryDeepLink,
  projectHistoryEvidenceValidationLabel,
} from "@/lib/project-history";
import { persistedStory, workspaceHref, type WorkspaceProject, type WorkspaceStory } from "@/lib/workspace-preview";
import { HistoryBoundaries, ReadError, ReadingPages } from "./HistoryReader";
import { WorkspaceDialog } from "./WorkspaceDialog";

export function StoryDialog({ story, project, demo, onClose }: {
  story: WorkspaceStory; project: WorkspaceProject; demo: boolean; onClose: () => void;
}) {
  const [detail, setDetail] = useState<ProjectHistoryStoryDetail | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(!demo);
  const [retry, setRetry] = useState(0);
  const [sourcesOpen, setSourcesOpen] = useState(false);
  const [page, setPage] = useState(0);
  const [filePage, setFilePage] = useState(0);
  const [focusedId, setFocusedId] = useState(story.id);
  const [related, setRelated] = useState<ProjectHistoryStoryDetail[]>([]);
  const [relatedError, setRelatedError] = useState("");
  const [relatedPage, setRelatedPage] = useState(0);
  useEffect(() => { setFocusedId(story.id); }, [story.id]);
  useEffect(() => {
    if (demo) return;
    let active = true;
    setLoading(true); setError("");
    setRelated([]); setRelatedError(""); setRelatedPage(0); setPage(0); setFilePage(0);
    getProjectHistoryStory(readSession().accessToken, project.id, focusedId)
      .then((value) => { if (active) setDetail(value); })
      .catch((e) => { if (active) setError(e.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [demo, project.id, focusedId, retry]);
  useEffect(() => {
    if (demo || !detail) return;
    let active = true;
    setRelated([]); setRelatedError("");
    const refs = (detail.story.supportingChangeRefs ?? []).slice(relatedPage * 6, (relatedPage + 1) * 6);
    Promise.all(refs.map(id => getProjectHistoryStory(readSession().accessToken, project.id, id)))
      .then(values => { if (active) setRelated(values); })
      .catch(() => { if (active) setRelatedError("关联范围暂时未读全，可重新读取这条变化。"); });
    return () => { active = false; };
  }, [demo, project.id, detail, relatedPage]);
  const display = detail ? persistedStory(detail.story) : story;
  const longTermThreads = detail?.threads.filter(thread => thread.subjectType !== "RECORD_CONTEXT" && thread.storyRefs.length >= 2) ?? [];
  const missingEvents = detail ? detail.story.eventRefs.filter((id) => !detail.events.some((event) => event.id === id)).length : 0;
  const sourceEvents = detail?.events.filter(event => event.category !== "FILE_CHANGE") ?? [];
  const fileEvents = detail?.events.filter(event => event.category === "FILE_CHANGE") ?? [];
  return <WorkspaceDialog title="变化详情" labelledBy="workspace-story-title" onClose={onClose} className="pf-story-dialog">
    <div className="pf-story-dialog-content">
      <span className="pf-eyebrow">{project.name}{demo ? " · 示例数据" : ""} · {display.date}</span>
      {focusedId !== story.id && <button className="pf-text-link" onClick={() => setFocusedId(story.id)}><ArrowLeft size={14} />返回主变化</button>}
      <h2 id="workspace-story-title">{display.title}</h2><p>{display.summary}</p>
      {loading && <p role="status">正在读取完整故事…</p>}
      {error && <ReadError message={`完整故事读取失败，以下保留已读取的摘要。${error}`} onRetry={() => setRetry((n) => n + 1)} />}
      <div className="pf-transition">{[["此前状态", display.before], ["本次变化", display.change], ["当前结果", display.after]].map(([label, value], index) => <section key={label}>
        <span>0{index + 1}</span><div><h3>{label}</h3><p>{value || "现有材料尚未确认"}</p></div>
      </section>)}</div>
      {detail && <>
        {!!detail.story.supportingChangeRefs?.length && <section className="pf-story-context"><h3>相关来源中的其他变化</h3>
          <p>这些记录按共同提交或相近的来源线索关联，关联本身不证明因果关系。本条变化的直接来源见下方工程证据。</p>
          {relatedError && <ReadError message={relatedError} onRetry={() => setRetry(n => n + 1)} />}
          {related.map(value => {
            const sameCommit = value.events.some(event => event.sourceType === "GIT" && event.sourceRevision
              && detail.events.some(source => source.sourceType === "GIT" && source.sourceRevision === event.sourceRevision));
            return <button className="pf-story-card" key={value.story.id} onClick={() => setFocusedId(value.story.id)}><h4>{value.story.humanTitle}</h4><p>{value.story.change}</p>
              <span>{sameCommit ? "关联依据：同一次 Git 提交" : "关联依据：相近时间的共同来源线索；具体关系尚未确认"}</span></button>;
          })}
          <ReadingPages page={relatedPage} totalPages={Math.ceil(detail.story.supportingChangeRefs.length / 6)} onPage={setRelatedPage} label="关联变化范围" />
        </section>}
        {detail.story.reason && <section className="pf-story-context"><h3>已记录的原因</h3><p>{detail.story.reason}</p></section>}
        {detail.story.laterOutcome && <section className="pf-story-context"><h3>后续结果</h3><p>{detail.story.laterOutcome}</p></section>}
        <HistoryBoundaries conflicts={[...detail.story.conflicts, ...(detail.story.correctionConflicts ?? [])]}
          unknowns={detail.story.unknowns} limitations={[...detail.story.limitations, ...(missingEvents ? [`${missingEvents} 个来源事件暂时不可读取。`] : [])]} />
        {!!longTermThreads.length && <div className="pf-story-threads"><h3>继续阅读相关主线</h3>{longTermThreads.map((thread) => <Link className="pf-button" key={thread.id}
          href={`${workspaceHref("history", demo, project.id)}&axis=threads&thread=${encodeURIComponent(thread.id)}`} onClick={onClose}><Workflow size={14} />{thread.subjectLabel}</Link>)}</div>}
      </>}
      <details className="pf-source-details" onToggle={(event) => setSourcesOpen(event.currentTarget.open)}>
        <summary><GitBranch size={16} />查看工程证据与来源</summary>
        {demo ? <p>演示 Commit {story.evidence}。本原型中的人物、时间、提交和验证结果均为示例，不对应真实工程验收。</p> : <>
          {sourcesOpen && sourceEvents.slice(page * 10, (page + 1) * 10).map((event, index) => <EvidenceEvent key={`${detail?.presentationRevision}:${event.id}`} projectId={project.id} event={event} initialOpen={index === 0} />)}
          <ReadingPages page={page} totalPages={Math.ceil(sourceEvents.length / 10)} onPage={setPage} label="来源事件" />
          {sourcesOpen && !!fileEvents.length && <details className="pf-source-details" open={!sourceEvents.length}>
            <summary>逐文件变更（{fileEvents.length} 条来源）</summary>
            {fileEvents.slice(filePage * 10, (filePage + 1) * 10).map(event => <EvidenceEvent key={`${detail?.presentationRevision}:${event.id}`} projectId={project.id} event={event} initialOpen={false} />)}
            <ReadingPages page={filePage} totalPages={Math.ceil(fileEvents.length / 10)} onPage={setFilePage} label="文件变更" />
          </details>}
          {!loading && detail && !detail.events.length && <p>此故事没有可进一步读取的来源事件，不能据此补充验证结果。</p>}
          <Link href={`/projects/${project.id}/history?compat=1&type=story&id=${encodeURIComponent(focusedId)}`}>工程兼容工具：来源审计与修正<ExternalLink size={14} /></Link>
        </>}
      </details>
      {detail && <details className="pf-source-details"><summary>查看故事工程详情</summary>
        <p>{projectHistoryPresentationLabel(detail.story.presentationAuthority)}</p>
        <dl className="pf-diagnostic-list">{[
          ["故事标识", detail.story.id], ["归纳权威", detail.story.authority], ["摘要状态", detail.story.summaryStatus],
          ["覆盖", detail.story.coverage], ["展示版本", detail.presentationRevision], ["证据角色", detail.story.claimAttribution?.supportClass || "尚未记录"],
        ].map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
        {!!detail.story.claimAttribution?.downgradeReason && <p>{detail.story.claimAttribution.downgradeReason}</p>}
      </details>}
    </div>
    <footer><button className="pf-button" onClick={onClose}><ArrowLeft size={15} />返回阅读</button><span>工程证据始终保留，展示不会改变事实</span></footer>
  </WorkspaceDialog>;
}

function EvidenceEvent({ projectId, event, initialOpen }: { projectId: string; event: ProjectHistoryEvent; initialOpen: boolean }) {
  const [evidence, setEvidence] = useState<ProjectHistoryEvidence | null>(null);
  const [open, setOpen] = useState(initialOpen);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    if (!open) return;
    let active = true;
    setLoading(true); setError("");
    getProjectHistoryEvidence(readSession().accessToken, projectId, event.id)
      .then((value) => { if (active) setEvidence(value); })
      .catch((e) => { if (active) setError(e.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [open, projectId, event.id, retry]);
  const sourceLink = safeHistoryDeepLink(event.rawSourceDeepLink);
  return <article className="pf-event-evidence">
    <h3>{event.userSummary || "来源事件"}</h3>
    {event.category === "FILE_CHANGE" && <p style={{ overflowWrap: "anywhere" }}>{event.affectedPaths.join("、")}</p>}
    <p>{projectHistorySourceTypeLabel(event.sourceType)} · {projectHistoryTransitionLabel(event.transition)} · {projectHistoryRewriteStateLabel(event.rewriteState)}</p>
    <HistoryBoundaries limitations={event.limitations} />
    <button className="pf-button" onClick={() => setOpen(!open)} aria-expanded={open}>{open ? "收起 Evidence 详情" : "查看 Evidence 详情"}</button>
    {open && <div className="pf-evidence-items">
      {loading && <p role="status">正在读取 Evidence…</p>}
      {error && <ReadError message={error} onRetry={() => setRetry((n) => n + 1)} />}
      {evidence?.items.map((item, index) => {
        const deepLink = safeHistoryDeepLink(item.deepLink);
        const sharedLabel = item.label && evidence.items.slice(0, index).some((previous) => previous.label === item.label);
        return <section key={`${item.type}:${item.reference}:${index}`}>
          <h4>{sharedLabel ? "同一来源的关联引用" : item.label || item.type}</h4><p>{item.reference}</p>
          <p>当前性：{projectHistoryRewriteStateLabel(item.currentness)} · {projectHistoryEvidenceValidationLabel(item.validation)}</p>
          <details className="pf-source-details"><summary>工程覆盖数据</summary><p>{item.coverage || "尚未记录"}</p><p>验证类型：{item.validation || "尚未记录"}{item.revision ? ` · 版本：${item.revision}` : ""}</p></details>
          {item.limitations.map((limit) => <p key={limit}>{limit}</p>)}
          {deepLink && <a href={deepLink} target={deepLink.startsWith("https://") ? "_blank" : undefined} rel="noreferrer">打开证据<ExternalLink size={13} /></a>}
        </section>;
      })}
      {evidence && !evidence.items.length && <p>当前事件没有可进一步展开的 Evidence。</p>}
      {evidence?.truncated && <p className="pf-notice">证据详情已达到安全读取上限，当前仅显示有界结果。</p>}
    </div>}
    <details className="pf-source-details"><summary>原始提交与来源信息</summary>
      <p>{event.safeSourceLabel}</p><p>{String(event.coverage?.timeLabel || "来源记录时间；不代表功能完成时间")} · {event.occurredAt}</p><p>{projectHistoryEvidenceValidationLabel(event.epistemicStatus)} · {projectHistoryEvidenceValidationLabel(event.authority)}</p>
      {event.affectedPaths.map((file) => <p key={file}>{file}</p>)}
      {sourceLink && <a href={sourceLink} target={sourceLink.startsWith("https://") ? "_blank" : undefined} rel="noreferrer">打开原始来源<ExternalLink size={13} /></a>}
    </details>
  </article>;
}
