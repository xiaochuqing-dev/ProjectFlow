"use client";

import { FormEvent, Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import {
  GitPullRequestArrow,
  RefreshCw,
} from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { ChangeReviewList } from "@/components/tasks/ChangeReviewList";
import { ChangeReviewSidebar } from "@/components/tasks/ChangeReviewSidebar";
import { Badge, ProjectContextBar, Toast } from "@/components/ui";
import { useAutoDismissNotice } from "@/hooks/useAutoDismissNotice";
import { useProjectSelection } from "@/hooks/useProjectSelection";
import {
  acceptProjectChange,
  applyAiSuggestions,
  ignoreProjectChange,
  listAiSuggestions,
  listProjectChanges,
  listProjectMaterials,
  listTasks,
  updateAiSuggestion,
  type AiSuggestion,
  type ProjectChange,
  type ProjectMaterial,
  type TaskItem,
} from "@/lib/api";
import { readSession } from "@/lib/auth";

type EditState = {
  id: string;
  title: string;
  reason: string;
  payloadText: string;
};

export default function TasksPage() {
  return (
    <Suspense fallback={<AppShell eyebrow="从今日开发到项目资产" title="开发成果审查"><div className="min-h-[calc(100vh-4rem)] bg-surface p-8"><div className="h-1 bg-slate-950" /></div></AppShell>}>
      <TasksPageContent />
    </Suspense>
  );
}

function TasksPageContent() {
  const searchParams = useSearchParams();
  const queryProjectId = searchParams.get("projectId") ?? "";
  const { projects, selectedProject, selectedProjectId, selectProject, loadingProjects, projectError } = useProjectSelection({ queryProjectId });
  const [changes, setChanges] = useState<ProjectChange[]>([]);
  const [suggestions, setSuggestions] = useState<AiSuggestion[]>([]);
  const [materials, setMaterials] = useState<ProjectMaterial[]>([]);
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [selectedChangeIds, setSelectedChangeIds] = useState<string[]>([]);
  const [selectedSuggestionIds, setSelectedSuggestionIds] = useState<string[]>([]);
  const [editing, setEditing] = useState<EditState | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(true);
  const [savingSuggestion, setSavingSuggestion] = useState(false);
  const [applying, setApplying] = useState(false);
  const [ignoringId, setIgnoringId] = useState("");

  const pendingChanges = changes.filter((change) => change.status === "PENDING" || change.status === "EDITED");
  const acceptedChanges = changes.filter((change) => change.status === "ACCEPTED" || change.status === "MERGED");
  const ignoredChanges = changes.filter((change) => change.status === "IGNORED");
  const pendingSuggestions = suggestions.filter((suggestion) => suggestion.status === "PENDING");
  const selectedSuggestion = editing ? suggestions.find((suggestion) => suggestion.id === editing.id) : pendingSuggestions[0];
  const selectedChange = pendingChanges[0];
  const latestMaterial = materials[0];

  useEffect(() => {
    refreshProjectContext(selectedProjectId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedProjectId]);

  useAutoDismissNotice(error, notice, () => {
    setNotice("");
    setError("");
  });

  async function refreshProjectContext(projectId: string) {
    const session = readSession();
    if (!session || !projectId) {
      setChanges([]);
      setSuggestions([]);
      setMaterials([]);
      setTasks([]);
      setSelectedChangeIds([]);
      setSelectedSuggestionIds([]);
      setEditing(null);
      return;
    }

    setLoading(true);
    setError("");
    try {
      const [changeItems, suggestionItems, materialItems, taskItems] = await Promise.all([
        listProjectChanges(session.accessToken, projectId),
        listAiSuggestions(session.accessToken, projectId),
        listProjectMaterials(session.accessToken, projectId),
        listTasks(session.accessToken, projectId),
      ]);
      setChanges(changeItems);
      setSuggestions(suggestionItems);
      setMaterials(materialItems);
      setTasks(taskItems);
      setSelectedChangeIds(changeItems.filter((change) => change.status === "PENDING" || change.status === "EDITED").map((change) => change.id));
      setSelectedSuggestionIds(suggestionItems.filter((suggestion) => suggestion.status === "PENDING").map((suggestion) => suggestion.id));
      setEditing(null);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "变更审查数据加载失败");
    } finally {
      setLoading(false);
    }
  }

  function startEditing(suggestion: AiSuggestion) {
    setEditing({
      id: suggestion.id,
      title: suggestion.title,
      reason: suggestion.reason,
      payloadText: JSON.stringify(suggestion.payload, null, 2),
    });
  }

  async function handleSaveSuggestion(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const session = readSession();
    if (!session || !editing) {
      return;
    }

    let payload: Record<string, unknown>;
    try {
      payload = JSON.parse(editing.payloadText) as Record<string, unknown>;
    } catch {
      setError("Payload 必须是合法 JSON。");
      return;
    }

    setSavingSuggestion(true);
    setError("");
    setNotice("");
    try {
      const updated = await updateAiSuggestion(session.accessToken, editing.id, editing.title, editing.reason, payload);
      setSuggestions((current) => current.map((suggestion) => (suggestion.id === updated.id ? updated : suggestion)));
      setEditing(null);
      setNotice("候选变更已保存，采纳时会使用编辑后的内容。");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "候选变更保存失败");
    } finally {
      setSavingSuggestion(false);
    }
  }

  async function handleAcceptChanges(ids = selectedChangeIds) {
    const session = readSession();
    if (!session || !selectedProjectId || ids.length === 0) {
      return;
    }

    setApplying(true);
    setError("");
    setNotice("");
    try {
      await Promise.all(ids.map((id) => acceptProjectChange(session.accessToken, id)));
      setNotice(`已采纳 ${ids.length} 条待确认内容，已写入项目资产和可信依据。`);
      await refreshProjectContext(selectedProjectId);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "采纳结构化变更失败");
    } finally {
      setApplying(false);
    }
  }

  async function handleApply(ids = selectedSuggestionIds) {
    const session = readSession();
    if (!session || !selectedProjectId || ids.length === 0) {
      return;
    }

    setApplying(true);
    setError("");
    setNotice("");
    try {
      await applyAiSuggestions(session.accessToken, selectedProjectId, ids);
      setNotice(`已采纳 ${ids.length} 条候选内容，并生成项目资产、开发证据和回顾来源。`);
      await refreshProjectContext(selectedProjectId);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "采纳变更失败");
    } finally {
      setApplying(false);
    }
  }

  async function handleIgnoreChange(id: string) {
    const session = readSession();
    if (!session || !selectedProjectId) {
      return;
    }

    setIgnoringId(id);
    setError("");
    setNotice("");
    try {
      await ignoreProjectChange(session.accessToken, id);
      setNotice("结构化变更已忽略。");
      await refreshProjectContext(selectedProjectId);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "忽略结构化变更失败");
    } finally {
      setIgnoringId("");
    }
  }

  function toggleChange(id: string) {
    setSelectedChangeIds((current) =>
      current.includes(id) ? current.filter((item) => item !== id) : [...current, id],
    );
  }

  function toggleSuggestion(id: string) {
    setSelectedSuggestionIds((current) =>
      current.includes(id) ? current.filter((item) => item !== id) : [...current, id],
    );
  }

  return (
    <AppShell eyebrow="从今日开发到项目资产" title="开发成果审查">
      <div className="min-h-[calc(100vh-4rem)] bg-surface p-8">
        <ProjectContextBar
          actions={(
            <button
              className="inline-flex items-center gap-2 rounded-md bg-slate-950 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
              disabled={applying || selectedChangeIds.length === 0}
              onClick={() => handleAcceptChanges()}
              type="button"
            >
              {applying ? <RefreshCw className="h-4 w-4 animate-spin" /> : <GitPullRequestArrow className="h-4 w-4" />}
              采纳选中 {selectedChangeIds.length}
            </button>
          )}
          leadingExtras={(
            <>
              <Badge label={`待确认 ${pendingChanges.length}`} tone="warning" />
              <Badge label={`已采纳 ${acceptedChanges.length}`} tone="success" />
              <Badge label={`已忽略 ${ignoredChanges.length}`} tone="slate" />
            </>
          )}
          onSelect={selectProject}
          projects={projects}
          selectedProjectId={selectedProjectId}
        />

        <section className="mb-6 rounded-md border border-line bg-white p-4 text-sm shadow-panel">
          <div className="mb-4">
            <p className="font-semibold text-slate-950">确认哪些开发成果可以进入项目资产</p>
            <p className="mt-1 text-sm leading-6 text-slate-600">
              这里不处理通用待办，而是把今日开发、Agent 写回和证据依据整理成待确认内容。采纳后会进入项目资产、可信依据和项目时间线。
            </p>
          </div>
          <div className="grid gap-3 lg:grid-cols-4">
            <FlowStep title="1. 确认入档" text="待确认内容会进入已采纳列表，并按类型写入项目资产字段和可信依据。" />
            <FlowStep title="2. 项目资产" text="能力、风险、技术决策、经验和成果素材会在项目理解页继续查看和修正。" />
            <FlowStep title="3. 上下文同步" text="点击工作台的同步确认上下文后，已采纳信息会写回本地项目上下文。" />
            <FlowStep title="4. 输出复用" text="成果输出、每日回顾、README 草稿和 Agent 后续任务会优先使用这些已确认信息。" />
          </div>
        </section>

        <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_420px]">
          <ChangeReviewList
            applying={applying}
            ignoringId={ignoringId}
            loading={loading}
            loadingProjects={loadingProjects}
            onAcceptChange={handleAcceptChanges}
            onApplySuggestions={() => handleApply()}
            onIgnoreChange={handleIgnoreChange}
            onStartEditingSuggestion={startEditing}
            onToggleChange={toggleChange}
            onToggleSuggestion={toggleSuggestion}
            pendingChanges={pendingChanges}
            pendingSuggestions={pendingSuggestions}
            selectedChangeIds={selectedChangeIds}
            selectedProject={selectedProject}
            selectedSuggestionIds={selectedSuggestionIds}
          />
          <ChangeReviewSidebar
            editing={editing}
            latestMaterial={latestMaterial}
            onChangeEditing={setEditing}
            onSaveSuggestion={handleSaveSuggestion}
            savingSuggestion={savingSuggestion}
            selectedChange={selectedChange}
            selectedSuggestion={selectedSuggestion}
            tasks={tasks}
          />
        </div>

        <Toast error={error || projectError} notice={notice} />
      </div>
    </AppShell>
  );
}

function FlowStep({ title, text }: { title: string; text: string }) {
  return (
    <div className="rounded-md bg-slate-50 p-3">
      <p className="font-semibold text-slate-950">{title}</p>
      <p className="mt-1 leading-5 text-slate-600">{text}</p>
    </div>
  );
}
