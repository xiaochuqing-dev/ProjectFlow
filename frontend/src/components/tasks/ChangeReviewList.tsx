import Link from "next/link";
import { Check, ClipboardCheck, RefreshCw } from "lucide-react";
import { useState } from "react";
import type { AiSuggestion, Project, ProjectChange, ProjectSediment, SedimentAction } from "@/lib/api";
import { changeDisplayTitle, changeOutcomeSummary, changeKindLabels, impactLabels, suggestionLabels } from "./change-review-utils";

type ChangeReviewListProps = {
  applying: boolean;
  ignoringId: string;
  loading: boolean;
  loadingProjects: boolean;
  onConfirmChange: (changeId: string, action: SedimentAction, targetSedimentId: string | null) => void;
  onApplySuggestions: () => void;
  onStartEditingSuggestion: (suggestion: AiSuggestion) => void;
  onToggleChange: (id: string) => void;
  onToggleSuggestion: (id: string) => void;
  pendingChanges: ProjectChange[];
  pendingSuggestions: AiSuggestion[];
  sediments: ProjectSediment[];
  selectedChangeIds: string[];
  selectedProject?: Project;
  selectedSuggestionIds: string[];
};

export function ChangeReviewList(props: ChangeReviewListProps) {
  return (
    <section className="rounded-md border border-line bg-white shadow-panel">
      <div className="flex items-center justify-between border-b border-line px-5 py-4">
        <div className="flex items-center gap-2">
          <ClipboardCheck className="h-4 w-4 text-slate-700" />
          <h2 className="font-semibold">建议沉淀</h2>
        </div>
        <p className="text-sm text-muted">{props.selectedProject?.name ?? "暂无项目"}</p>
      </div>

      <div className="divide-y divide-line">
        {props.pendingChanges.map((change) => (
          <article className="grid gap-4 p-5 lg:grid-cols-[28px_minmax(0,1fr)_280px]" key={change.id}>
            <input
              aria-label={`选择 ${change.title}`}
              checked={props.selectedChangeIds.includes(change.id)}
              className="mt-1 h-4 w-4 accent-slate-950"
              onChange={() => props.onToggleChange(change.id)}
              type="checkbox"
            />
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <span className="rounded-md bg-amber-50 px-2 py-1 text-xs font-medium text-amber-800">
                  {changeKindLabels[change.changeKind]} · {impactLabels[change.impactLevel]}
                </span>
                <span className="text-xs text-muted">{change.confidence ?? "待校验"}</span>
              </div>
              <Link href={`/project-changes/${change.id}`}>
                <h3 className="mt-3 font-semibold text-slate-950 break-words">{changeDisplayTitle(change)}</h3>
                <p className="mt-2 line-clamp-2 text-sm leading-6 text-slate-600 break-words">{changeOutcomeSummary(change)}</p>
              </Link>
              <details className="mt-3 text-xs text-muted">
                <summary className="cursor-pointer font-medium text-slate-700">查看建议依据</summary>
                <p className="mt-2">{change.evidenceRefs.length} 条证据引用，来源为 {change.sourceType}。</p>
              </details>
            </div>
            <SedimentActionControls
              applying={props.applying}
              change={change}
              ignoring={props.ignoringId === change.id}
              onConfirm={props.onConfirmChange}
              sediments={props.sediments}
            />
          </article>
        ))}

        {props.pendingSuggestions.length > 0 ? (
          <details className="bg-slate-50 px-5 py-4">
            <summary className="cursor-pointer text-sm font-semibold text-slate-700">旧版候选 ({props.pendingSuggestions.length})</summary>
            <p className="mt-2 text-xs leading-5 text-muted">旧 AiSuggestion 仅用于兼容历史数据，不再抢占沉淀确认主流程。</p>
            <div className="mt-3 space-y-3">
              {props.pendingSuggestions.slice(0, 5).map((suggestion) => (
                <article className="rounded-md border border-line bg-white p-4 text-sm" key={suggestion.id}>
                  <label className="flex min-w-0 items-center gap-2">
                    <input
                      checked={props.selectedSuggestionIds.includes(suggestion.id)}
                      className="h-4 w-4 accent-slate-950"
                      onChange={() => props.onToggleSuggestion(suggestion.id)}
                      type="checkbox"
                    />
                    <span className="truncate font-medium text-slate-950">{suggestion.title}</span>
                    <span className="ml-auto shrink-0 text-xs text-muted">{suggestionLabels[suggestion.type]}</span>
                  </label>
                  <button className="mt-2 line-clamp-2 text-left leading-5 text-slate-600" onClick={() => props.onStartEditingSuggestion(suggestion)} type="button">
                    {suggestion.reason}
                  </button>
                </article>
              ))}
              <button
                className="rounded-md border border-line bg-white px-3 py-2 text-xs font-semibold text-slate-700 disabled:opacity-60"
                disabled={props.applying || props.selectedSuggestionIds.length === 0}
                onClick={props.onApplySuggestions}
                type="button"
              >
                确认旧版候选 {props.selectedSuggestionIds.length}
              </button>
            </div>
          </details>
        ) : null}

        {!props.loading && props.pendingChanges.length === 0 && props.pendingSuggestions.length === 0 ? (
          <div className="grid place-items-center gap-4 p-10 text-center">
            <div>
              <p className="text-base font-semibold text-slate-950">当前没有建议沉淀</p>
              <p className="mt-2 max-w-md text-sm leading-6 text-muted">回到工作台分析新变化，ProjectFlow 会先归并开发推进段，再生成需要你确认的建议。</p>
            </div>
            <Link className="inline-flex items-center gap-1.5 rounded-md bg-slate-950 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-800" href="/dashboard">
              <RefreshCw className="h-4 w-4" />
              分析新变化
            </Link>
          </div>
        ) : null}
        {props.loading || props.loadingProjects ? <div className="h-1 bg-slate-950" /> : null}
      </div>
    </section>
  );
}

function SedimentActionControls({
  applying,
  change,
  ignoring,
  onConfirm,
  sediments,
}: {
  applying: boolean;
  change: ProjectChange;
  ignoring: boolean;
  onConfirm: (changeId: string, action: SedimentAction, targetSedimentId: string | null) => void;
  sediments: ProjectSediment[];
}) {
  const [action, setAction] = useState<SedimentAction>(change.suggestedAction ?? "NEW_SEDIMENT");
  const [targetId, setTargetId] = useState(change.targetSedimentId ?? "");
  const needsTarget = action === "MERGE_EXISTING" || action === "EVIDENCE_ONLY";
  const disabled = applying || ignoring || (needsTarget && !targetId);

  if (!change.developmentSegmentId) {
    return (
      <button className="inline-flex h-9 items-center justify-center gap-1 rounded-md bg-slate-950 px-3 text-xs font-semibold text-white disabled:opacity-60" disabled={applying} onClick={() => onConfirm(change.id, "NEW_SEDIMENT", null)} type="button">
        <Check className="h-3.5 w-3.5" />兼容确认
      </button>
    );
  }

  return (
    <div className="space-y-2">
      <div className="rounded-md bg-blue-50 p-3 text-xs leading-5 text-blue-950">
        <p className="font-semibold">系统推荐：{recommendationLabel(change.suggestedAction ?? "NEW_SEDIMENT", sediments.find((item) => item.id === change.targetSedimentId)?.title)}</p>
        <p className="mt-1">{change.suggestionReason || "系统根据主题、来源和证据重合度给出推荐，你可以在下方调整。"}</p>
      </div>
      {needsTarget ? <TargetSummary sediment={sediments.find((item) => item.id === targetId)} /> : null}
      <button
        className="inline-flex h-9 w-full items-center justify-center gap-1 rounded-md bg-slate-950 px-3 text-xs font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
        disabled={disabled}
        onClick={() => onConfirm(change.id, action, needsTarget ? targetId : null)}
        type="button"
      >
        {applying || ignoring ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <Check className="h-3.5 w-3.5" />}
        {actionButtonLabel(action)}
      </button>
      <details className="rounded-md border border-line bg-white p-2 text-xs">
        <summary className="cursor-pointer font-semibold text-slate-700">调整处理方式</summary>
        <label className="mt-2 block font-medium text-slate-700">
          选择后会发生什么
          <select className="mt-1 w-full rounded-md border border-line bg-white px-3 py-2 text-sm" onChange={(event) => setAction(event.target.value as SedimentAction)} value={action}>
            <option value="NEW_SEDIMENT">新建一条独立项目沉淀</option>
            <option value="MERGE_EXISTING">更新已有沉淀的摘要和证据</option>
            <option value="EVIDENCE_ONLY">只追加来源和证据</option>
            <option value="IGNORE">暂不写入项目沉淀</option>
          </select>
        </label>
        {needsTarget ? (
          <div className="mt-2 space-y-2">
            <p className="font-medium text-slate-700">选择目标项目沉淀</p>
            {sediments.map((sediment) => (
              <label className={`block cursor-pointer rounded-md border p-2 ${targetId === sediment.id ? "border-blue-400 bg-blue-50" : "border-line"}`} key={sediment.id}>
                <span className="flex items-center gap-2"><input checked={targetId === sediment.id} name={`target-${change.id}`} onChange={() => setTargetId(sediment.id)} type="radio" /><span className="font-semibold text-slate-900">{sediment.title}</span></span>
                <span className="mt-1 block line-clamp-2 text-slate-600">{sediment.summary || "暂无摘要"}</span>
                <span className="mt-1 block text-slate-500">{sediment.id === change.targetSedimentId ? "系统判断主题相近" : "手动选择"} · 最近更新 {new Date(sediment.updatedAt).toLocaleDateString("zh-CN")} · 当前 {sediment.evidenceRefs.length} 条证据</span>
              </label>
            ))}
          </div>
        ) : null}
      </details>
    </div>
  );
}

function TargetSummary({ sediment }: { sediment?: ProjectSediment }) {
  if (!sediment) return <p className="text-xs text-amber-700">请先选择要更新的项目沉淀。</p>;
  return (
    <div className="rounded-md border border-line p-2 text-xs leading-5 text-slate-600">
      <p className="font-semibold text-slate-900">目标：《{sediment.title}》</p>
      <p className="line-clamp-2">{sediment.summary || "暂无摘要"}</p>
    </div>
  );
}

function recommendationLabel(action: SedimentAction, targetTitle?: string) {
  if (action === "MERGE_EXISTING") return `更新已有项目沉淀${targetTitle ? `《${targetTitle}》` : ""}`;
  if (action === "EVIDENCE_ONLY") return `仅补充${targetTitle ? `《${targetTitle}》` : "已有沉淀"}的证据`;
  if (action === "IGNORE") return "暂不沉淀";
  return "新建一条项目沉淀";
}

function actionButtonLabel(action: SedimentAction) {
  if (action === "MERGE_EXISTING") return "合并并确认";
  if (action === "EVIDENCE_ONLY") return "补充证据并确认";
  if (action === "IGNORE") return "暂不沉淀";
  return "新建并确认";
}
