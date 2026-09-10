"use client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { Activity, ArrowRight, BookOpenText, GitBranch } from "lucide-react";
import { type WorkspaceProject, type WorkspaceStory, type WorkspaceView } from "@/lib/workspace-preview";
import { claimLabels, supportedClaim, type WorkspaceClaim } from "@/lib/workspace-claims";
import { ProjectMark } from "./WorkspacePages";
import { StoryCard, HistoryBoundaries } from "./HistoryReader";
import { getProjectUnderstanding, refreshProjectUnderstanding, getProjectAnalysisJob, listProjectAnalysisJobs, type ProjectUnderstandingSnapshot, type ProjectAnalysisJob } from "@/lib/api";
import { readSession } from "@/lib/auth";

export function RealCurrentPage({ project, href, onStory }: { project: WorkspaceProject; href: (view: WorkspaceView) => string; onStory: (story: WorkspaceStory) => void }) {
  const [materialSummary, setMaterialSummary] = useState<WorkspaceClaim | null>(null);
  useEffect(() => setMaterialSummary(null), [project.id]);
  const identity = project.declarations?.find(item => item.kind === "IDENTITY");
  const plans = (project.declarations ?? []).filter(item => item.kind === "PLAN" && supportedClaim({ text: item.text, kind: "PLAN", classification: "DECLARED", sources: [item.source] }));
  const summary = materialSummary;
  const confirmed = project.stories.filter(story => story.confirmedOutcome).slice(0, 4);
  return <div className="pf-current-page pf-real-current">
    <section className="pf-panel pf-hero"><div className="pf-hero-art"/><div className="pf-hero-heading"><ProjectMark color={project.color}/><div><h2>{project.name}</h2><p>{identity?.text || project.description}</p></div><span className="pf-chip recorded">{project.status}</span></div>
      <p className="pf-claim-badge">{identity ? `项目声明摘录 · ${identity.source}:${identity.line}` : "项目填写资料 · 尚未核实"}</p>
      {summary && <p className="pf-hero-summary">{summary.text}</p>}
      {summary && <div className="pf-current-sources"><span className="pf-claim-badge">系统归纳</span>{materialSummary ? <a href="#current-material-sources">查看当前材料来源 <ArrowRight size={14}/></a> : <Link href={href("history")}>根据已保存的变化记录 <ArrowRight size={14}/></Link>}</div>}
      <div className="pf-hero-stats"><div><Activity size={18}/> 最近成功读取：{project.updated}</div><Link href={href("worklines")}><GitBranch size={17}/>查看开发工作线</Link></div>
    </section>
    {(project.stale || project.degraded) && <div className="pf-notice" role="status">{project.stale ? "材料可能已变化，当前展示上次保存结果。" : "部分来源或历史尚不完整；以下结论只覆盖已读取内容。"}</div>}
    <CurrentMaterialUnderstanding project={project} onSummary={setMaterialSummary}/>
    <section className="pf-panel pf-real-section"><h2>直接证据支持的变化</h2><p>可确认的范围以来源为准：观察到文件变化不等于功能已经通过运行验收。</p>
      {confirmed.length ? confirmed.map(story => <StoryCard key={story.id} story={story} onStory={onStory}/>) : <p className="pf-notice">现有记录尚不足以展示可确认的结果。项目声明和计划不会填入这里。</p>}
    </section>
    <section className="pf-panel pf-real-section"><div className="pf-section-line"><h2>最近发生了什么</h2><Link href={href("history")}>查看历程 <ArrowRight size={14}/></Link></div>
      {project.stories.slice(0, 5).map(story => <button className="pf-change-row" key={story.id} onClick={() => onStory(story)}><span className="pf-change-copy"><strong>{story.title}</strong><small>{claimLabels[story.classification ?? "UNKNOWN"]} · 查看来源</small></span><time>{story.date}</time></button>)}
      {!project.stories.length && <div className="pf-inline-empty"><BookOpenText size={28}/><p>尚无变化记录。有当前材料而没有历史，也是有效状态。</p></div>}
    </section>
    <section className="pf-panel pf-real-section"><h2>项目明确写过的计划</h2><p>文档中的声明可能包含历史计划，不代表当前承诺或已经完成。</p>
      {plans.length ? plans.map((plan, i) => <details className="pf-source-details" key={i}><summary><span className="pf-claim-badge">项目明确声明</span> {plan.text}</summary><p>来源：{plan.source} 第 {plan.line} 行</p><p>源文摘录：{plan.text}</p><p>读取时间：{plan.observedAt}</p><small>来源版本：{plan.sourceHash}</small></details>) : <p className="pf-notice">暂无明确计划记录。没有已读取的来源支持下一里程碑、完成百分比或预计发布时间。</p>}
    </section>
    <section className="pf-panel pf-real-section"><h2>冲突与未知</h2><HistoryBoundaries conflicts={project.attention} unknowns={project.unknowns}/>{!project.attention.length && !project.unknowns.length && <p>已读取记录中未列出冲突；这不代表项目不存在问题。</p>}</section>
  </div>;
}

function CurrentMaterialUnderstanding({ project, onSummary }: { project: WorkspaceProject; onSummary: (summary: WorkspaceClaim | null) => void }) {
  const [snapshot, setSnapshot] = useState<ProjectUnderstandingSnapshot | null>(null);
  const [job, setJob] = useState<ProjectAnalysisJob | null>(null);
  const [error, setError] = useState(""); const [starting, setStarting] = useState(false);
  const running = !!job && ["QUEUED", "RUNNING", "CANCEL_REQUESTED"].includes(job.status);
  useEffect(() => {
    let active = true; const token = readSession().accessToken;
    setSnapshot(previous => previous?.projectId === project.id ? previous : null); setJob(null); setError("");
    getProjectUnderstanding(token, project.id).then(value => { if (active && value?.identity) setSnapshot(value); }).catch(() => {});
    listProjectAnalysisJobs(token, project.id).then(value => { if (active && Array.isArray(value)) setJob(value.find(item => item.jobType === "PROJECT_UNDERSTANDING_REFRESH" && ["QUEUED", "RUNNING", "CANCEL_REQUESTED"].includes(item.status)) ?? null); }).catch(() => {});
    return () => { active = false; };
  }, [project.id, project.updated]);
  useEffect(() => {
    if (!job || !running) return;
    let active = true;
    const timer = setTimeout(async () => {
      try {
        const next = await getProjectAnalysisJob(readSession().accessToken, job.id);
        if (!active) return;
        if (!["QUEUED", "RUNNING", "CANCEL_REQUESTED"].includes(next.status)) {
          const value = await getProjectUnderstanding(readSession().accessToken, project.id);
          if (active && value?.identity) setSnapshot(value);
          if (active && !next.status.startsWith("SUCCEEDED")) setError(next.errorMessage || "本次理解未完成，保留已有结果。");
        }
        if (active) setJob(next);
      } catch { if (active) setError("任务状态暂时无法读取，可稍后重新打开此页。"); }
    }, 4000);
    return () => { active = false; clearTimeout(timer); };
  }, [job, running, project.id]);
  useEffect(() => {
    const known = new Set((snapshot?.sourceMap?.sources ?? []).map(source => source.id));
    const first = snapshot?.quality?.modelUsed ? snapshot.identity?.claims?.find(claim => claim.evidenceRefs.some(ref => known.has(ref))) : null;
    onSummary(first ? supportedClaim({ text: first.text, kind: "SUMMARY", classification: "INFERRED", sources: first.evidenceRefs.filter(ref => known.has(ref)) }) : null);
  }, [snapshot, onSummary]);
  async function refresh() {
    if (starting || running) return; setStarting(true); setError("");
    try { setJob(await refreshProjectUnderstanding(readSession().accessToken, project.id, { qualityMode: "QUALITY_FIRST" })); }
    catch (e) { setError(e instanceof Error ? e.message : "无法开始理解当前材料。"); }
    finally { setStarting(false); }
  }
  const sources = new Map((snapshot?.sourceMap?.sources ?? []).map(source => [source.id, source]));
  const claims = [snapshot?.identity, snapshot?.capabilities, snapshot?.engineeringState].flatMap(section => section?.claims ?? [])
    .map(claim => ({ ...claim, evidenceRefs: claim.evidenceRefs.filter(ref => sources.has(ref)) }))
    .filter(claim => supportedClaim({ text: claim.text, kind: "SUMMARY", classification: "INFERRED", sources: claim.evidenceRefs })).slice(0, 8);
  return <section id="current-material-sources" className="pf-panel pf-real-section pf-material-understanding"><div className="pf-section-line"><h2>当前材料说明了什么</h2><button className="pf-button" disabled={starting || running} onClick={refresh}>{starting || running ? "正在理解当前材料…" : "理解当前材料"}</button></div>
    <p>根据当前材料归纳项目用途与能力。以下是有来源的系统归纳；文档宣称的能力不等于本次已经验证。</p>
    {running && <p className="pf-notice" role="status">{job?.stageMessage || "任务已进入后台，可离开后回来查看。"}</p>}
    {error && <p className="pf-notice" role="alert">{error}</p>}
    {snapshot && <p className="pf-claim-badge">{snapshot.quality?.modelUsed ? "系统归纳" : "本地观察"} · 读取于 {new Date(snapshot.analyzedAt).toLocaleString("zh-CN")} · {snapshot.currentStatus === "STALE" ? "材料已变化，需更新" : "仅覆盖该次读取"}</p>}
    {snapshot && !snapshot.quality?.modelUsed && <p className="pf-notice">尚无成功的模型理解；以下保留本地观察。可点击“理解当前材料”重试，文件数量不能说明产品功能已经实现。</p>}
    {claims.map(claim => <details className="pf-source-details" key={claim.id}><summary>{claim.text}</summary>{claim.evidenceRefs.map(ref => { const source = sources.get(ref)!; return <p key={ref}>{source.locator} · {source.summary} · {source.currentness === "CURRENT" ? "读取时为当前材料" : source.currentness === "STALE" ? "材料可能过期" : "当前性待核实"}</p>; })}</details>)}
    {!claims.length && !running && <p className="pf-notice">尚无有来源的语义说明。可在配置模型后显式读取当前材料。</p>}
    {snapshot?.unknowns?.length ? <details className="pf-source-details"><summary>理解范围与未知</summary>{snapshot.unknowns.slice(0, 8).map((unknown, i) => <p key={i}>{unknown}</p>)}</details> : null}
  </section>;
}
