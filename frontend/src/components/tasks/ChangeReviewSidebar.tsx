import type { FormEvent } from "react";
import Link from "next/link";
import { FileDiff, FileText, Layers3, RefreshCw, Save, ShieldAlert } from "lucide-react";
import type { AiSuggestion, ProjectChange, ProjectMaterial, TaskItem } from "@/lib/api";
import { changeKindLabels, changeMemoryTargets, impactLabels } from "./change-review-utils";

type EditState = {
  id: string;
  title: string;
  reason: string;
  payloadText: string;
};

type ChangeReviewSidebarProps = {
  editing: EditState | null;
  latestMaterial?: ProjectMaterial;
  onChangeEditing: (value: EditState | null) => void;
  onSaveSuggestion: (event: FormEvent<HTMLFormElement>) => void;
  savingSuggestion: boolean;
  selectedChange?: ProjectChange;
  selectedSuggestion?: AiSuggestion;
  tasks: TaskItem[];
};

export function ChangeReviewSidebar(props: ChangeReviewSidebarProps) {
  return (
    <aside className="space-y-5">
      <AcceptedWriteTarget selectedChange={props.selectedChange} />
      <ReviewEntry
        editing={props.editing}
        onChangeEditing={props.onChangeEditing}
        onSaveSuggestion={props.onSaveSuggestion}
        savingSuggestion={props.savingSuggestion}
        selectedChange={props.selectedChange}
      />
      <SourceMaterial latestMaterial={props.latestMaterial} selectedChange={props.selectedChange} selectedSuggestion={props.selectedSuggestion} />
      <TaskEvidence tasks={props.tasks} />
      <section className="rounded-md border border-amber-200 bg-amber-50 p-5 text-sm leading-6 text-amber-900">
        <div className="mb-2 flex items-center gap-2 font-semibold">
          <ShieldAlert className="h-4 w-4" />
          审查边界
        </div>
        采纳前是候选建议，采纳后才进入项目档案和成果资产。忽略不会删除原始材料，只是把这条候选变更移出待确认队列。
      </section>
    </aside>
  );
}

function AcceptedWriteTarget({ selectedChange }: { selectedChange?: ProjectChange }) {
  return (
    <section className="rounded-md border border-line bg-white shadow-panel">
      <div className="flex items-center justify-between gap-3 border-b border-line px-5 py-4">
        <div className="flex items-center gap-2">
          <Layers3 className="h-4 w-4 text-slate-700" />
          <h2 className="font-semibold">采纳后写入</h2>
        </div>
        <Link className="text-xs font-semibold text-slate-700 hover:text-slate-950" href="/project-intelligence">
          看项目档案
        </Link>
      </div>
      <div className="space-y-3 p-5 text-sm leading-6 text-slate-600">
        {selectedChange ? (
          <>
            <p className="font-medium text-slate-950">{selectedChange.title}</p>
            <div className="flex flex-wrap gap-2">
              {changeMemoryTargets(selectedChange).map((target) => (
                <span className="rounded-md bg-slate-100 px-2.5 py-1 text-xs text-slate-700" key={target}>
                  {target}
                </span>
              ))}
            </div>
            <p>采纳后会写入上方字段的事实来源，并被每日回顾、README 草稿、周报和后续同步上下文复用。</p>
          </>
        ) : (
          <p className="text-muted">选择一条待确认变更后，这里会显示它最终进入项目档案的字段。</p>
        )}
      </div>
    </section>
  );
}

function ReviewEntry({
  editing,
  onChangeEditing,
  onSaveSuggestion,
  savingSuggestion,
  selectedChange,
}: {
  editing: EditState | null;
  onChangeEditing: (value: EditState | null) => void;
  onSaveSuggestion: (event: FormEvent<HTMLFormElement>) => void;
  savingSuggestion: boolean;
  selectedChange?: ProjectChange;
}) {
  return (
    <section className="rounded-md border border-line bg-white shadow-panel">
      <div className="flex items-center gap-2 border-b border-line px-5 py-4">
        <FileDiff className="h-4 w-4 text-slate-700" />
        <h2 className="font-semibold">完整审查入口</h2>
      </div>
      {selectedChange ? (
        <div className="space-y-4 p-5 text-sm leading-6 text-slate-600">
          <div className="rounded-md border border-line bg-slate-50 p-4">
            <div className="mb-2 flex flex-wrap gap-2">
              <span className="rounded-md bg-amber-50 px-2 py-1 text-xs text-amber-800">
                {changeKindLabels[selectedChange.changeKind]} · {impactLabels[selectedChange.impactLevel]}
              </span>
              <span className="rounded-md bg-slate-100 px-2 py-1 text-xs text-muted">{selectedChange.sourceType}</span>
            </div>
            <p className="font-semibold text-slate-950">{selectedChange.title}</p>
            <p className="mt-2 line-clamp-4">{selectedChange.summary}</p>
          </div>
          <Link href={`/project-changes/${selectedChange.id}`}>
            <button className="inline-flex w-full items-center justify-center gap-2 rounded-md bg-slate-950 px-4 py-2.5 text-sm font-semibold text-white hover:bg-slate-800" type="button">
              完整审查与修正
            </button>
          </Link>
          <p className="text-xs leading-5 text-muted">
            列表页只负责筛选和快速采纳。摘要、证据、风险和档案候选的完整审查统一进入独立页，避免在侧栏堆大表单。
          </p>
        </div>
      ) : editing ? (
        <form className="space-y-4 p-5" onSubmit={onSaveSuggestion}>
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">标题</span>
            <input
              className="w-full rounded-md border border-line px-3 py-2 text-sm outline-none focus:border-slate-950"
              onChange={(event) => onChangeEditing({ ...editing, title: event.target.value })}
              value={editing.title}
            />
          </label>
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">审查说明</span>
            <textarea
              className="min-h-28 w-full rounded-md border border-line px-3 py-2 text-sm leading-6 outline-none focus:border-slate-950"
              onChange={(event) => onChangeEditing({ ...editing, reason: event.target.value })}
              value={editing.reason}
            />
          </label>
          <details className="rounded-md border border-line bg-white">
            <summary className="cursor-pointer px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50">
              高级调试：Payload JSON
            </summary>
            <div className="border-t border-line p-3">
              <textarea
                className="min-h-40 w-full rounded-md border border-line bg-slate-950 px-3 py-2 font-mono text-xs leading-5 text-slate-50 outline-none focus:border-slate-700"
                onChange={(event) => onChangeEditing({ ...editing, payloadText: event.target.value })}
                value={editing.payloadText}
              />
            </div>
          </details>
          <button
            className="inline-flex w-full items-center justify-center gap-2 rounded-md bg-slate-950 px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-60"
            disabled={savingSuggestion}
            type="submit"
          >
            {savingSuggestion ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
            保存候选变更
          </button>
        </form>
      ) : (
        <div className="p-5 text-sm leading-6 text-muted">
          从左侧选择一条结构化变更。完整审查页会展示自动摘要、证据卡片、档案候选和手动修正入口。
        </div>
      )}
    </section>
  );
}

function SourceMaterial({
  latestMaterial,
  selectedChange,
  selectedSuggestion,
}: {
  latestMaterial?: ProjectMaterial;
  selectedChange?: ProjectChange;
  selectedSuggestion?: AiSuggestion;
}) {
  return (
    <section className="rounded-md border border-line bg-white shadow-panel">
      <div className="flex items-center gap-2 border-b border-line px-5 py-4">
        <FileText className="h-4 w-4 text-slate-700" />
        <h2 className="font-semibold">来源材料</h2>
      </div>
      <div className="p-5 text-sm leading-6 text-slate-600">
        <p className="font-medium text-slate-950">{latestMaterial?.sourceType ?? "暂无材料"}</p>
        <p className="mt-2 line-clamp-5">{latestMaterial?.normalizedSummary ?? "导入 zip 或扫描 agent result 后，来源材料会用于解释候选变更。 "}</p>
        {selectedChange ? (
          <div className="mt-4 space-y-3 rounded-md bg-slate-100 p-3 text-xs leading-5 text-slate-600">
            <p><span className="font-semibold text-slate-800">来源：</span>{selectedChange.sourceRef || selectedChange.sourceType}</p>
            <p><span className="font-semibold text-slate-800">决策：</span>{selectedChange.decisionNotes || "暂无"}</p>
            <p><span className="font-semibold text-slate-800">测试：</span>{selectedChange.testEvidence || "暂无"}</p>
          </div>
        ) : selectedSuggestion ? (
          <pre className="mt-4 max-h-40 overflow-auto rounded-md bg-slate-100 p-3 font-mono text-xs leading-5 text-slate-600">
            {JSON.stringify(selectedSuggestion.payload, null, 2)}
          </pre>
        ) : null}
      </div>
    </section>
  );
}

function TaskEvidence({ tasks }: { tasks: TaskItem[] }) {
  return (
    <section className="rounded-md border border-line bg-white shadow-panel">
      <div className="flex items-center gap-2 border-b border-line px-5 py-4">
        <Layers3 className="h-4 w-4 text-slate-700" />
        <h2 className="font-semibold">任务证据</h2>
      </div>
      <div className="divide-y divide-line">
        {tasks.slice(0, 5).map((task) => (
          <article className="p-4 text-sm" key={task.id}>
            <div className="mb-2 flex items-center justify-between gap-3">
              <p className="font-medium text-slate-950">{task.title}</p>
              <span className="rounded-md bg-slate-100 px-2 py-1 text-xs text-muted">{task.status}</span>
            </div>
            <p className="line-clamp-3 leading-5 text-slate-600">{task.description || "暂无验收说明。"}</p>
          </article>
        ))}
        {tasks.length === 0 ? <p className="p-5 text-sm text-muted">采纳任务类变更后会形成任务证据。</p> : null}
      </div>
    </section>
  );
}
