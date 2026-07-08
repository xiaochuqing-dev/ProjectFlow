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
      <label className="block text-xs font-medium text-slate-700">
        处理方式
        <select className="mt-1 w-full rounded-md border border-line bg-white px-3 py-2 text-sm" onChange={(event) => setAction(event.target.value as SedimentAction)} value={action}>
          <option value="NEW_SEDIMENT">新建沉淀</option>
          <option value="MERGE_EXISTING">合并已有沉淀</option>
          <option value="EVIDENCE_ONLY">只补充证据</option>
          <option value="IGNORE">忽略</option>
        </select>
      </label>
      {needsTarget ? (
        <label className="block text-xs font-medium text-slate-700">
          目标沉淀
          <select className="mt-1 w-full rounded-md border border-line bg-white px-3 py-2 text-sm" onChange={(event) => setTargetId(event.target.value)} value={targetId}>
            <option value="">选择已有沉淀</option>
            {sediments.map((sediment) => <option key={sediment.id} value={sediment.id}>{sediment.title}</option>)}
          </select>
        </label>
      ) : null}
      <button
        className="inline-flex h-9 w-full items-center justify-center gap-1 rounded-md bg-slate-950 px-3 text-xs font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
        disabled={disabled}
        onClick={() => onConfirm(change.id, action, needsTarget ? targetId : null)}
        type="button"
      >
        {applying || ignoring ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <Check className="h-3.5 w-3.5" />}
        {action === "IGNORE" ? "忽略建议" : "确认沉淀"}
      </button>
    </div>
  );
}
