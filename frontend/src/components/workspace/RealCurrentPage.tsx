"use client";
import Link from "next/link";
import { Activity, ArrowRight, BookOpenText, GitBranch } from "lucide-react";
import { type WorkspaceProject, type WorkspaceStory, type WorkspaceView } from "@/lib/workspace-preview";
import { claimLabels, supportedClaim } from "@/lib/workspace-claims";
import { ProjectMark } from "./WorkspacePages";
import { StoryCard, HistoryBoundaries } from "./HistoryReader";

export function RealCurrentPage({ project, href, onStory }: { project: WorkspaceProject; href: (view: WorkspaceView) => string; onStory: (story: WorkspaceStory) => void }) {
  const identity = project.declarations?.find(item => item.kind === "IDENTITY");
  const plans = (project.declarations ?? []).filter(item => item.kind === "PLAN" && supportedClaim({ text: item.text, kind: "PLAN", classification: "DECLARED", sources: [item.source] }));
  const summary = supportedClaim({ text: project.summary, kind: "SUMMARY", classification: "INFERRED", sources: project.sourceStoryRefs ?? [] });
  const confirmed = project.stories.filter(story => story.confirmedOutcome).slice(0, 4);
  return <div className="pf-current-page pf-real-current">
    <section className="pf-panel pf-hero"><div className="pf-hero-art"/><div className="pf-hero-heading"><ProjectMark color={project.color}/><div><h2>{project.name}</h2><p>{identity?.text || project.description}</p></div><span className="pf-chip recorded">{project.status}</span></div>
      <p className="pf-claim-badge">{identity ? `项目声明摘录 · ${identity.source}:${identity.line}` : "项目填写资料 · 尚未核实"}</p>
      <p className="pf-hero-summary">{summary?.text || "尚无有来源的当前状态摘要。添加材料并更新后，再查看可确认的结果。"}</p>
      {summary && <div className="pf-current-sources"><span className="pf-claim-badge">系统归纳</span><Link href={href("history")}>根据已保存的变化记录 <ArrowRight size={14}/></Link></div>}
      <div className="pf-hero-stats"><div><Activity size={18}/> 最近成功读取：{project.updated}</div><Link href={href("worklines")}><GitBranch size={17}/>查看开发工作线</Link></div>
    </section>
    {(project.stale || project.degraded) && <div className="pf-notice" role="status">{project.stale ? "材料可能已变化，当前展示上次保存结果。" : "部分来源或历史尚不完整；以下结论只覆盖已读取内容。"}</div>}
    <section className="pf-panel pf-real-section"><h2>已经确认的结果</h2><p>结果来自直接证据；可读措辞仍是系统归纳。验证范围见来源详情。</p>
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
