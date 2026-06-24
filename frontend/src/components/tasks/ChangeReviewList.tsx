import Link from "next/link";
import { Check, ClipboardCheck, RefreshCw, Trash2 } from "lucide-react";
import type { AiSuggestion, Project, ProjectChange } from "@/lib/api";
import { archiveTargetsLabel, changeDisplayTitle, changeOutcomeSummary, changeKindLabels, changePreview, impactLabels, suggestionLabels } from "./change-review-utils";

type ChangeReviewListProps = {
  applying: boolean;
  ignoringId: string;
  loading: boolean;
  loadingProjects: boolean;
  onAcceptChange: (ids: string[]) => void;
  onApplySuggestions: () => void;
  onIgnoreChange: (id: string) => void;
  onStartEditingSuggestion: (suggestion: AiSuggestion) => void;
  onToggleChange: (id: string) => void;
  onToggleSuggestion: (id: string) => void;
  pendingChanges: ProjectChange[];
  pendingSuggestions: AiSuggestion[];
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
          <h2 className="font-semibold">结构化变更</h2>
        </div>
        <p className="text-sm text-muted">{props.selectedProject?.name ?? "暂无项目"}</p>
      </div>
      <div className="divide-y divide-line">
        {props.pendingChanges.map((change) => (
          <article className="grid gap-4 p-5 md:grid-cols-[28px_150px_minmax(0,1fr)_auto]" key={change.id}>
            <input
              checked={props.selectedChangeIds.includes(change.id)}
              className="mt-1 h-4 w-4 accent-slate-950"
              onChange={() => props.onToggleChange(change.id)}
              type="checkbox"
            />
            <div>
              <span className="rounded-md bg-amber-50 px-2 py-1 text-xs font-medium text-amber-800">
                {changeKindLabels[change.changeKind]} · {impactLabels[change.impactLevel]}
              </span>
              <p className="mt-2 text-xs text-muted">{change.sourceType}</p>
              <p className="mt-1 text-xs text-muted">{new Date(change.createdAt).toLocaleString()}</p>
            </div>
            <Link className="min-w-0 text-left" href={`/project-changes/${change.id}`}>
              <h3 className="font-semibold text-slate-950">{changeDisplayTitle(change)}</h3>
              <p className="mt-2 line-clamp-2 text-sm leading-6 text-slate-600">{changeOutcomeSummary(change)}</p>
              <p className="mt-2 line-clamp-1 text-xs font-semibold text-slate-500">{archiveTargetsLabel(change)}</p>
              <p className="mt-1 line-clamp-1 text-xs text-muted">{changePreview(change)}</p>
            </Link>
            <div className="flex flex-wrap items-start gap-2">
              <Link
                className="inline-flex h-9 items-center rounded-md border border-line bg-white px-3 text-xs font-semibold text-slate-700 hover:border-slate-300 hover:bg-slate-50"
                href={`/project-changes/${change.id}`}
              >
                完整审查
              </Link>
              <button
                className="inline-flex h-9 items-center gap-1 rounded-md bg-slate-950 px-3 text-xs font-semibold text-white disabled:opacity-60"
                disabled={props.applying}
                onClick={() => props.onAcceptChange([change.id])}
                type="button"
              >
                <Check className="h-3.5 w-3.5" />
                采纳
              </button>
              <button
                className="inline-flex h-9 w-9 items-center justify-center rounded-md border border-line bg-white text-slate-500 hover:bg-rose-50 hover:text-rose-700 disabled:opacity-60"
                disabled={props.ignoringId === change.id}
                onClick={() => props.onIgnoreChange(change.id)}
                title="忽略"
                type="button"
              >
                {props.ignoringId === change.id ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
              </button>
            </div>
          </article>
        ))}
        {props.pendingSuggestions.length > 0 ? (
          <div className="bg-slate-50 p-4">
            <div className="mb-3 flex items-center justify-between gap-3 text-sm">
              <span className="font-semibold text-slate-700">兼容候选建议</span>
              <button
                className="rounded-md border border-line bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 disabled:opacity-60"
                disabled={props.applying || props.selectedSuggestionIds.length === 0}
                onClick={props.onApplySuggestions}
                type="button"
              >
                采纳旧建议 {props.selectedSuggestionIds.length}
              </button>
            </div>
            <div className="space-y-3">
              {props.pendingSuggestions.slice(0, 5).map((suggestion) => (
                <article className="rounded-md border border-line bg-white p-4 text-sm" key={suggestion.id}>
                  <div className="mb-2 flex items-center justify-between gap-3">
                    <label className="flex min-w-0 items-center gap-2">
                      <input
                        checked={props.selectedSuggestionIds.includes(suggestion.id)}
                        className="h-4 w-4 accent-slate-950"
                        onChange={() => props.onToggleSuggestion(suggestion.id)}
                        type="checkbox"
                      />
                      <span className="truncate font-medium text-slate-950">{suggestion.title}</span>
                    </label>
                    <span className="shrink-0 rounded-md bg-slate-100 px-2 py-1 text-xs text-muted">{suggestionLabels[suggestion.type]}</span>
                  </div>
                  <button className="line-clamp-2 text-left leading-5 text-slate-600" onClick={() => props.onStartEditingSuggestion(suggestion)} type="button">
                    {suggestion.reason}
                  </button>
                </article>
              ))}
            </div>
          </div>
        ) : null}
        {!props.loading && props.pendingChanges.length === 0 && props.pendingSuggestions.length === 0 ? (
          <div className="grid min-h-80 place-items-center p-8 text-center text-sm text-muted">
            暂无待确认变更。回到工作台扫描 agent result 或导入项目 zip 后，这里会集中处理候选事实、风险、决策和成果素材。
          </div>
        ) : null}
        {props.loading || props.loadingProjects ? <div className="h-1 bg-slate-950" /> : null}
      </div>
    </section>
  );
}
