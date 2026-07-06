"use client";

import { FormEvent, Suspense, useEffect, useState } from "react";
import { useSearchParams, useRouter } from "next/navigation";
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
  confirmProjectChange,
  applyAiSuggestions,
  ignoreProjectChange,
  listAiSuggestions,
  listProjectChanges,
  listProjectMaterials,
  listProjectSediments,
  listTasks,
  updateAiSuggestion,
  type AiSuggestion,
  type ProjectChange,
  type ProjectMaterial,
  type ProjectSediment,
  type SedimentAction,
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
    <Suspense fallback={<AppShell eyebrow="待整理变更到项目沉淀" title="沉淀确认"><div className="min-h-[calc(100vh-4rem)] bg-surface p-8"><div className="h-1 bg-slate-950" /></div></AppShell>}>
      <TasksPageContent />
    </Suspense>
  );
}

function TasksPageContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const queryProjectId = searchParams.get("projectId") ?? "";
  const { projects, selectedProject, selectedProjectId, selectProject, loadingProjects, projectError } = useProjectSelection({ queryProjectId });
  function handleSelectProject(projectId: string) {
    selectProject(projectId);
    router.replace(`/tasks?projectId=${projectId}`);
  }
  const [changes, setChanges] = useState<ProjectChange[]>([]);
  const [suggestions, setSuggestions] = useState<AiSuggestion[]>([]);
  const [materials, setMaterials] = useState<ProjectMaterial[]>([]);
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [sediments, setSediments] = useState<ProjectSediment[]>([]);
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
      setSediments([]);
      setSelectedChangeIds([]);
      setSelectedSuggestionIds([]);
      setEditing(null);
      return;
    }

    setLoading(true);
    setError("");
    try {
      const [changeItems, suggestionItems, materialItems, taskItems, sedimentItems] = await Promise.all([
        listProjectChanges(session.accessToken, projectId),
        listAiSuggestions(session.accessToken, projectId),
        listProjectMaterials(session.accessToken, projectId),
        listTasks(session.accessToken, projectId),
        listProjectSediments(session.accessToken, projectId),
      ]);
      setChanges(changeItems);
      setSuggestions(suggestionItems);
      setMaterials(materialItems);
      setTasks(taskItems);
      setSediments(sedimentItems);
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

  async function handleConfirmChange(changeId: string, action: SedimentAction, targetSedimentId: string | null) {
    const session = readSession();
    if (!session || !selectedProjectId) return;
    const change = changes.find((item) => item.id === changeId);
    setApplying(action !== "IGNORE");
    setIgnoringId(action === "IGNORE" ? changeId : "");
    setError("");
    setNotice("");
    try {
      if (change?.developmentSegmentId) {
        await confirmProjectChange(session.accessToken, changeId, action, targetSedimentId);
      } else if (action === "IGNORE") {
        await ignoreProjectChange(session.accessToken, changeId);
      } else {
        await acceptProjectChange(session.accessToken, changeId);
      }
      setNotice(action === "IGNORE" ? "建议已忽略。" : "建议已确认并写入项目沉淀。");
      await refreshProjectContext(selectedProjectId);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "建议沉淀确认失败");
    } finally {
      setApplying(false);
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
    <AppShell eyebrow="待整理变更到项目沉淀" title="沉淀确认">
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
              新建选中 {selectedChangeIds.length}
            </button>
          )}
          leadingExtras={(
            <>
              <Badge label={`待确认 ${pendingChanges.length}`} tone="warning" />
              <Badge label={`已采纳 ${acceptedChanges.length}`} tone="success" />
              <Badge label={`已忽略 ${ignoredChanges.length}`} tone="slate" />
            </>
          )}
          onSelect={handleSelectProject}
          projects={projects}
          selectedProjectId={selectedProjectId}
        />

        <section className="mb-6 rounded-md border border-line bg-white p-4 text-sm shadow-panel">
          <div className="mb-4">
            <p className="font-semibold text-slate-950">确认开发推进段应如何进入项目沉淀</p>
            <p className="mt-1 text-sm leading-6 text-slate-600">
              规则负责采证，模型负责理解，规则再次校验证据。你最终决定新建、合并、只补证据或忽略。
            </p>
          </div>
          <div className="grid gap-3 lg:grid-cols-4">
            <FlowStep title="1. 查看开发推进段" text="先理解这一组变化解决了什么问题，并核对来源概览。" />
            <FlowStep title="2. 选择处理方式" text="新建、合并、补证据或忽略，ProjectFlow 不替你作最终决定。" />
            <FlowStep title="3. 形成项目沉淀" text="确认后的内容进入稳定详情页，原始证据继续保留。" />
            <FlowStep title="4. 后续复用" text="README、复盘和成果输出优先引用已确认沉淀。" />
          </div>
        </section>

        <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_420px]">
          <ChangeReviewList
            applying={applying}
            ignoringId={ignoringId}
            loading={loading}
            loadingProjects={loadingProjects}
            onConfirmChange={handleConfirmChange}
            onApplySuggestions={() => handleApply()}
            onStartEditingSuggestion={startEditing}
            onToggleChange={toggleChange}
            onToggleSuggestion={toggleSuggestion}
            pendingChanges={pendingChanges}
            pendingSuggestions={pendingSuggestions}
            sediments={sediments}
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
