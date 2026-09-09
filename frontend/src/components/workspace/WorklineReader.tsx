"use client";
import { useEffect, useState } from "react";
import { GitBranch, ArrowRight, Search } from "lucide-react";
import { getProjectWorklines, type ProjectWorkline, type ProjectWorklinePage } from "@/lib/api";
import { readSession } from "@/lib/auth";
import { claimLabels } from "@/lib/workspace-claims";
import { WorkspaceDialog } from "./WorkspaceDialog";
import { ReadError, ReadingPages } from "./HistoryReader";
const labels: Record<string, string> = { ALL: "全部", MAIN: "主线", REVIEW: "等待审查", DEPENDENT: "有依赖的工作", DEVELOPING: "正在开发", UNKNOWN: "状态待确认", INACTIVE: "长期未活动", HISTORY: "已合入 / 历史" };
const mergeLabels: Record<string, string> = { MERGED: "PR 已合并，HEAD 匹配", CONTAINED: "提交已包含在主线", OPEN_PR: "PR 尚未合并", UNKNOWN: "是否合入尚不确定" };
const sourceLabels: Record<string, string> = { AVAILABLE: "GitHub 已读取", PARTIAL: "GitHub 部分可用", UNAVAILABLE: "GitHub 不可用", NO_REMOTE: "未配置 GitHub", NOT_READ: "尚未读取" };
const date = (at: string) => at ? new Date(at).toLocaleString("zh-CN", { hour12: false }) : "时间未知";

export function WorklineReader({ projectId, demo }: { projectId: string; demo: boolean }) {
  const [data, setData] = useState<ProjectWorklinePage | null>(null);
  const [page, setPage] = useState(0), [group, setGroup] = useState("ALL"), [query, setQuery] = useState(""), [search, setSearch] = useState("");
  const [loading, setLoading] = useState(!demo), [error, setError] = useState(""), [retry, setRetry] = useState(0);
  const [selected, setSelected] = useState<ProjectWorkline | null>(null);
  useEffect(() => {
    if (demo) return;
    let active = true; setLoading(true); setError(""); setSelected(null);
    getProjectWorklines(readSession().accessToken, projectId, page, group, query)
      .then(value => { if (active) setData(value); }).catch(e => { if (active) setError(e.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [projectId, demo, page, group, query, retry]);
  if (demo) return <div className="pf-inline-empty"><GitBranch size={32}/><h2>开发工作线来自真实 Git 与 PR</h2><p>此设计示例没有 Git 来源。打开真实项目并更新状态后查看。</p></div>;
  return <section className="pf-worklines-page">
    <div className="pf-history-lead"><span className="pf-eyebrow">DEVELOPMENT WORKLINES</span><h2>看清哪些工作正在发生</h2>
      <p>PR 中明确说过的目标与系统归纳分别标识。分支名称本身不能证明用途。</p>
      {data && <div>{data.branchCount} 条已读取分支 · 主线：{data.defaultBranch || "尚未确认"}</div>}
    </div>
    {data && <p className="pf-page-footnote">本地观察：{date(data.observedAt)} · {sourceLabels[data.githubStatus] || "GitHub 状态未知"}：{date(data.githubObservedAt)}</p>}
    {data?.stale && <div className="pf-notice" role="status">以下是保存的观察，可能已过期。更新项目状态可重新核对来源。</div>}
    {data && !["AVAILABLE", "NOT_READ"].includes(data.githubStatus) && <div className="pf-notice">远程协作信息不完整；本地工作线仍可阅读，PR 状态保持未知。</div>}
    <div className="pf-workline-filters">
      <div className="pf-segmented" aria-label="工作线分类">{Object.entries(labels).filter(([key]) => key === "ALL" || (data?.groups[key] ?? 0) > 0).map(([key, label]) =>
        <button key={key} aria-pressed={group === key} className={group === key ? "active" : ""} onClick={() => { setGroup(key); setPage(0); }}>{label} <span>{key === "ALL" ? data?.branchCount ?? 0 : data?.groups[key]}</span></button>)}</div>
      <form className="pf-thread-search" onSubmit={e => { e.preventDefault(); setQuery(search.trim()); setPage(0); }}><Search size={16}/><input aria-label="搜索开发工作线" value={search} onChange={e => setSearch(e.target.value)} placeholder="搜索分支或工作内容"/><button className="pf-button">搜索</button></form>
    </div>
    {loading && <p role="status">正在读取已保存的工作线…</p>}
    {error && <ReadError message={error} onRetry={() => setRetry(n => n + 1)}/>}
    {!loading && !error && <div className="pf-workline-list">{data?.items.map(item => <button key={item.id} className={`pf-workline-card ${item.state === "HISTORY" || item.state === "INACTIVE" ? "quiet" : ""}`} onClick={() => setSelected(item)}>
      <div className="pf-workline-meta"><span className="pf-chip recorded">{labels[item.state]}</span><span className="pf-claim-badge">{claimLabels[item.classification]}</span>{item.pullRequest && <span>PR #{item.pullRequest.number}{item.pullRequest.draft ? " · 草稿" : ""}</span>}</div>
      <h3>{item.purpose}</h3><p className="pf-branch-name"><GitBranch size={14}/>{item.branch}</p>
      <p>{mergeLabels[item.mergeState]}{item.dependsOn ? ` · PR 基于 ${item.dependsOn}` : ""}</p>
      <footer><time>最后活动 {date(item.lastActivity)}</time><span>查看依据 <ArrowRight size={14}/></span></footer>
    </button>)}</div>}
    {!loading && !error && !data?.items.length && <div className="pf-inline-empty"><GitBranch size={32}/><h3>暂无可读工作线</h3><p>没有 Git、尚未更新或筛选无结果都是有效状态。已有项目资料和历程仍可阅读。</p></div>}
    <ReadingPages page={page} totalPages={data?.totalPages ?? 0} onPage={setPage} disabled={loading} label="工作线"/>
    {!!data?.limitations.length && <details className="pf-source-details"><summary>读取范围与限制{data.truncated ? " · 覆盖不完整" : ""}</summary>{data.limitations.map(text => <p key={text}>{text}</p>)}</details>}
    {selected && <WorkspaceDialog title="工作线依据" onClose={() => setSelected(null)} className="pf-workline-dialog">
      <h2>{selected.purpose}</h2><span className="pf-claim-badge">{claimLabels[selected.classification]}</span><p className="pf-branch-name">{selected.branch}</p>
      <div className="pf-fields"><div><label>合入状态</label><p>{mergeLabels[selected.mergeState]}</p></div><div><label>相对主线的提交关系</label><p>领先 {selected.ahead ?? "未知"} / 落后 {selected.behind ?? "未知"}</p></div></div>
      <p className="pf-page-footnote">领先 / 落后计数比较 Git 提交，不能证明工作尚未合入。</p>
      {selected.remoteHeadDiffers && <p className="pf-notice">本地与 GitHub 分支 HEAD 不同；本地 diff 不代表远程最新内容。</p>}
      {selected.pullRequest && <section className="pf-source-details"><h3>PR 中的明确声明</h3><a href={selected.pullRequest.url} target="_blank" rel="noreferrer">PR #{selected.pullRequest.number}：{selected.pullRequest.title}</a><p>{selected.pullRequest.excerpt}</p><p>PR 基于 {selected.pullRequest.base}，状态：{selected.pullRequest.state === "MERGED" ? "已合并" : selected.pullRequest.state === "CLOSED" ? "已关闭" : selected.pullRequest.draft ? "草稿" : "等待审查"}</p><p>检查记录：{selected.pullRequest.checks || "暂无记录"}</p>{selected.pullRequest.issues.map(issue => <p key={issue}>关联 Issue：{issue}</p>)}</section>}
      <details className="pf-source-details"><summary>提交样本与变更文件</summary><p>采样起点 {date(selected.firstSampleActivity)}；它不是分支创建时间。</p>{selected.commitSubjects.map((text, i) => <p key={i}>{text}</p>)}<p>提交作者：{selected.authors.join("、") || "未读取"}</p><ul>{selected.changedFiles.map(file => <li key={file}>{file}</li>)}</ul></details>
      <h3>结论来源</h3>{selected.sources.map((source, i) => <div className="pf-source-details" key={i}><strong>{source.label}</strong><p>{source.reference}</p><small>观察于 {date(source.observedAt)}</small></div>)}
      {selected.limitations.map(text => <p className="pf-notice" key={text}>{text}</p>)}
    </WorkspaceDialog>}
  </section>;
}
