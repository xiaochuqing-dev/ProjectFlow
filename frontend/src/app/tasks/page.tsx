"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  Check,
  ClipboardCheck,
  FileDiff,
  FileText,
  GitPullRequestArrow,
  Layers3,
  RefreshCw,
  Save,
  ShieldAlert,
  Trash2,
} from "lucide-react";
import { AppShell } from "@/components/AppShell";
import {
  acceptProjectChange,
  applyAiSuggestions,
  ignoreAiSuggestion,
  ignoreProjectChange,
  listAiSuggestions,
  listProjectChanges,
  listProjectMaterials,
  listProjects,
  listTasks,
  updateProjectChange,
  updateAiSuggestion,
  type AiSuggestion,
  type Project,
  type ProjectChange,
  type ProjectChangePayload,
  type ProjectMaterial,
  type TaskItem,
} from "@/lib/api";
import { readSession } from "@/lib/auth";

const suggestionLabels = {
  UPDATE_PROJECT_MEMORY: "项目档案",
  CREATE_TASK: "任务",
  CREATE_DEV_LOG: "每日回顾",
  RECORD_TECHNICAL_DECISION: "技术决策",
  RECORD_RISK: "风险",
  RECORD_DEVELOPER_LEARNING: "经验",
  UPDATE_CURRENT_STAGE: "阶段",
  GENERATE_ASSET_SUMMARY: "成果素材",
};

const changeKindLabels = {
  CAPABILITY: "能力",
  BUGFIX: "修复",
  REFACTOR: "重构",
  CONFIG: "配置",
  DOCS: "文档",
  TEST: "测试",
  RISK: "风险",
  DECISION: "决策",
  LEARNING: "经验",
  ASSET: "素材",
  UNKNOWN: "待判断",
};

const impactLabels = {
  MAJOR: "主要",
  MINOR: "次要",
  MAINTENANCE: "维护",
  UNCERTAIN: "待判断",
};

type EditState = {
  id: string;
  title: string;
  reason: string;
  payloadText: string;
};

type ChangeEditState = ProjectChangePayload & {
  id: string;
};

export default function TasksPage() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState("");
  const [changes, setChanges] = useState<ProjectChange[]>([]);
  const [suggestions, setSuggestions] = useState<AiSuggestion[]>([]);
  const [materials, setMaterials] = useState<ProjectMaterial[]>([]);
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [selectedChangeIds, setSelectedChangeIds] = useState<string[]>([]);
  const [selectedSuggestionIds, setSelectedSuggestionIds] = useState<string[]>([]);
  const [editing, setEditing] = useState<EditState | null>(null);
  const [editingChange, setEditingChange] = useState<ChangeEditState | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(true);
  const [savingSuggestion, setSavingSuggestion] = useState(false);
  const [savingChange, setSavingChange] = useState(false);
  const [applying, setApplying] = useState(false);
  const [ignoringId, setIgnoringId] = useState("");

  const selectedProject = useMemo(
    () => projects.find((project) => project.id === selectedProjectId),
    [projects, selectedProjectId],
  );
  const pendingChanges = changes.filter((change) => change.status === "PENDING" || change.status === "EDITED");
  const acceptedChanges = changes.filter((change) => change.status === "ACCEPTED" || change.status === "MERGED");
  const ignoredChanges = changes.filter((change) => change.status === "IGNORED");
  const pendingSuggestions = suggestions.filter((suggestion) => suggestion.status === "PENDING");
  const selectedSuggestion = editing ? suggestions.find((suggestion) => suggestion.id === editing.id) : pendingSuggestions[0];
  const selectedChange = editingChange ? changes.find((change) => change.id === editingChange.id) : pendingChanges[0];
  const latestMaterial = materials[0];

  useEffect(() => {
    const session = readSession();
    if (!session) {
      return;
    }

    setLoading(true);
    listProjects(session.accessToken)
      .then((items) => {
        setProjects(items);
        setSelectedProjectId(items[0]?.id ?? "");
      })
      .catch((exception) => setError(exception instanceof Error ? exception.message : "项目加载失败"))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    refreshProjectContext(selectedProjectId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedProjectId]);

  useEffect(() => {
    if (!notice && !error) {
      return;
    }
    const timeout = window.setTimeout(() => {
      setNotice("");
      setError("");
    }, 4200);
    return () => window.clearTimeout(timeout);
  }, [error, notice]);

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
      setEditingChange(null);
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
      setEditingChange(null);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "变更审查数据加载失败");
    } finally {
      setLoading(false);
    }
  }

  function startEditing(suggestion: AiSuggestion) {
    setEditingChange(null);
    setEditing({
      id: suggestion.id,
      title: suggestion.title,
      reason: suggestion.reason,
      payloadText: JSON.stringify(suggestion.payload, null, 2),
    });
  }

  function startEditingChange(change: ProjectChange) {
    setEditing(null);
    setEditingChange(toChangePayload(change));
  }

  async function handleSaveChange(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const session = readSession();
    if (!session || !editingChange) {
      return;
    }

    const { id, ...payload } = editingChange;
    setSavingChange(true);
    setError("");
    setNotice("");
    try {
      const updated = await updateProjectChange(session.accessToken, id, payload);
      setChanges((current) => current.map((change) => (change.id === updated.id ? updated : change)));
      setEditingChange(null);
      setNotice("结构化变更已保存。");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "结构化变更保存失败");
    } finally {
      setSavingChange(false);
    }
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
      setNotice(`已采纳 ${ids.length} 条结构化变更，并沉淀到项目档案来源。`);
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
      setNotice(`已采纳 ${ids.length} 条变更，并生成项目档案/任务/回顾证据。`);
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

  async function handleIgnore(id: string) {
    const session = readSession();
    if (!session || !selectedProjectId) {
      return;
    }

    setIgnoringId(id);
    setError("");
    setNotice("");
    try {
      await ignoreAiSuggestion(session.accessToken, id);
      setNotice("变更已忽略。");
      await refreshProjectContext(selectedProjectId);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "忽略变更失败");
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
    <AppShell eyebrow="从 agent result 到确认资产" title="变更审查">
      <div className="min-h-[calc(100vh-4rem)] bg-surface p-8">
        <section className="mb-6 rounded-md border border-line bg-white p-4 shadow-panel">
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div className="flex flex-wrap items-center gap-3">
              <select
                className="h-10 min-w-72 rounded-md border border-line bg-white px-3 text-sm outline-none focus:border-slate-950"
                disabled={projects.length === 0}
                onChange={(event) => setSelectedProjectId(event.target.value)}
                value={selectedProjectId}
              >
                {projects.map((project) => (
                  <option key={project.id} value={project.id}>
                    {project.name}
                  </option>
                ))}
              </select>
              <StatusPill label="待确认" value={pendingChanges.length} tone="amber" />
              <StatusPill label="已采纳" value={acceptedChanges.length} tone="emerald" />
              <StatusPill label="已忽略" value={ignoredChanges.length} tone="slate" />
            </div>
            <button
              className="inline-flex items-center gap-2 rounded-md bg-slate-950 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
              disabled={applying || selectedChangeIds.length === 0}
              onClick={() => handleAcceptChanges()}
              type="button"
            >
              {applying ? <RefreshCw className="h-4 w-4 animate-spin" /> : <GitPullRequestArrow className="h-4 w-4" />}
              采纳选中 {selectedChangeIds.length}
            </button>
          </div>
        </section>

        <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_420px]">
          <section className="rounded-md border border-line bg-white shadow-panel">
            <div className="flex items-center justify-between border-b border-line px-5 py-4">
              <div className="flex items-center gap-2">
                <ClipboardCheck className="h-4 w-4 text-slate-700" />
                <h2 className="font-semibold">结构化变更</h2>
              </div>
              <p className="text-sm text-muted">{selectedProject?.name ?? "暂无项目"}</p>
            </div>
            <div className="divide-y divide-line">
              {pendingChanges.map((change) => (
                <article className="grid gap-4 p-5 md:grid-cols-[28px_150px_minmax(0,1fr)_auto]" key={change.id}>
                  <input
                    checked={selectedChangeIds.includes(change.id)}
                    className="mt-1 h-4 w-4 accent-slate-950"
                    onChange={() => toggleChange(change.id)}
                    type="checkbox"
                  />
                  <div>
                    <span className="rounded-md bg-amber-50 px-2 py-1 text-xs font-medium text-amber-800">
                      {changeKindLabels[change.changeKind]} · {impactLabels[change.impactLevel]}
                    </span>
                    <p className="mt-2 text-xs text-muted">{change.sourceType}</p>
                    <p className="mt-1 text-xs text-muted">{new Date(change.createdAt).toLocaleString()}</p>
                  </div>
                  <button className="min-w-0 text-left" onClick={() => startEditingChange(change)} type="button">
                    <h3 className="font-semibold text-slate-950">{change.title}</h3>
                    <p className="mt-2 line-clamp-3 text-sm leading-6 text-slate-600">{change.summary}</p>
                    <p className="mt-2 line-clamp-1 font-mono text-xs text-muted">{changePreview(change)}</p>
                  </button>
                  <div className="flex items-start gap-2">
                    <button
                      className="inline-flex h-9 items-center gap-1 rounded-md bg-slate-950 px-3 text-xs font-semibold text-white disabled:opacity-60"
                      disabled={applying}
                      onClick={() => handleAcceptChanges([change.id])}
                      type="button"
                    >
                      <Check className="h-3.5 w-3.5" />
                      采纳
                    </button>
                    <button
                      className="inline-flex h-9 w-9 items-center justify-center rounded-md border border-line bg-white text-slate-500 hover:bg-rose-50 hover:text-rose-700 disabled:opacity-60"
                      disabled={ignoringId === change.id}
                      onClick={() => handleIgnoreChange(change.id)}
                      title="忽略"
                      type="button"
                    >
                      {ignoringId === change.id ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
                    </button>
                  </div>
                </article>
              ))}
              {pendingSuggestions.length > 0 ? (
                <div className="bg-slate-50 p-4">
                  <div className="mb-3 flex items-center justify-between gap-3 text-sm">
                    <span className="font-semibold text-slate-700">兼容候选建议</span>
                    <button
                      className="rounded-md border border-line bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 disabled:opacity-60"
                      disabled={applying || selectedSuggestionIds.length === 0}
                      onClick={() => handleApply()}
                      type="button"
                    >
                      采纳旧建议 {selectedSuggestionIds.length}
                    </button>
                  </div>
                  <div className="space-y-3">
                    {pendingSuggestions.slice(0, 5).map((suggestion) => (
                      <article className="rounded-md border border-line bg-white p-4 text-sm" key={suggestion.id}>
                        <div className="mb-2 flex items-center justify-between gap-3">
                          <label className="flex min-w-0 items-center gap-2">
                            <input
                              checked={selectedSuggestionIds.includes(suggestion.id)}
                              className="h-4 w-4 accent-slate-950"
                              onChange={() => toggleSuggestion(suggestion.id)}
                              type="checkbox"
                            />
                            <span className="truncate font-medium text-slate-950">{suggestion.title}</span>
                          </label>
                          <span className="shrink-0 rounded-md bg-slate-100 px-2 py-1 text-xs text-muted">{suggestionLabels[suggestion.type]}</span>
                        </div>
                        <button className="line-clamp-2 text-left leading-5 text-slate-600" onClick={() => startEditing(suggestion)} type="button">
                          {suggestion.reason}
                        </button>
                      </article>
                    ))}
                  </div>
                </div>
              ) : null}
              {!loading && pendingChanges.length === 0 && pendingSuggestions.length === 0 ? (
                <div className="grid min-h-80 place-items-center p-8 text-center text-sm text-muted">
                  暂无待确认变更。回到工作台扫描 agent result 或导入项目 zip 后，这里会集中处理候选事实、风险、决策和成果素材。
                </div>
              ) : null}
              {loading ? <div className="h-1 bg-slate-950" /> : null}
            </div>
          </section>

          <aside className="space-y-5">
            <section className="rounded-md border border-line bg-white shadow-panel">
              <div className="flex items-center gap-2 border-b border-line px-5 py-4">
                <FileDiff className="h-4 w-4 text-slate-700" />
                <h2 className="font-semibold">编辑结构化变更</h2>
              </div>
              {editingChange ? (
                <form className="space-y-4 p-5" onSubmit={handleSaveChange}>
                  <div className="grid grid-cols-2 gap-3">
                    <label className="block">
                      <span className="mb-1 block text-sm font-medium text-slate-700">类型</span>
                      <select
                        className="w-full rounded-md border border-line px-3 py-2 text-sm outline-none focus:border-slate-950"
                        onChange={(event) => setEditingChange((current) => current && { ...current, changeKind: event.target.value as ProjectChangePayload["changeKind"] })}
                        value={editingChange.changeKind}
                      >
                        {Object.entries(changeKindLabels).map(([value, label]) => (
                          <option key={value} value={value}>{label}</option>
                        ))}
                      </select>
                    </label>
                    <label className="block">
                      <span className="mb-1 block text-sm font-medium text-slate-700">影响</span>
                      <select
                        className="w-full rounded-md border border-line px-3 py-2 text-sm outline-none focus:border-slate-950"
                        onChange={(event) => setEditingChange((current) => current && { ...current, impactLevel: event.target.value as ProjectChangePayload["impactLevel"] })}
                        value={editingChange.impactLevel}
                      >
                        {Object.entries(impactLabels).map(([value, label]) => (
                          <option key={value} value={value}>{label}</option>
                        ))}
                      </select>
                    </label>
                  </div>
                  <label className="block">
                    <span className="mb-1 block text-sm font-medium text-slate-700">标题</span>
                    <input
                      className="w-full rounded-md border border-line px-3 py-2 text-sm outline-none focus:border-slate-950"
                      onChange={(event) => setEditingChange((current) => current && { ...current, title: event.target.value })}
                      value={editingChange.title}
                    />
                  </label>
                  <label className="block">
                    <span className="mb-1 block text-sm font-medium text-slate-700">摘要</span>
                    <textarea
                      className="min-h-24 w-full rounded-md border border-line px-3 py-2 text-sm leading-6 outline-none focus:border-slate-950"
                      onChange={(event) => setEditingChange((current) => current && { ...current, summary: event.target.value })}
                      value={editingChange.summary}
                    />
                  </label>
                  <label className="block">
                    <span className="mb-1 block text-sm font-medium text-slate-700">影响文件</span>
                    <textarea
                      className="min-h-20 w-full rounded-md border border-line bg-slate-50 px-3 py-2 font-mono text-xs leading-5 outline-none focus:border-slate-950"
                      onChange={(event) => setEditingChange((current) => current && { ...current, affectedFiles: event.target.value })}
                      value={editingChange.affectedFiles}
                    />
                  </label>
                  <label className="block">
                    <span className="mb-1 block text-sm font-medium text-slate-700">风险备注</span>
                    <textarea
                      className="min-h-20 w-full rounded-md border border-line px-3 py-2 text-sm leading-6 outline-none focus:border-slate-950"
                      onChange={(event) => setEditingChange((current) => current && { ...current, riskNotes: event.target.value })}
                      value={editingChange.riskNotes}
                    />
                  </label>
                  <button
                    className="inline-flex w-full items-center justify-center gap-2 rounded-md bg-slate-950 px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-60"
                    disabled={savingChange}
                    type="submit"
                  >
                    {savingChange ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
                    保存结构化变更
                  </button>
                </form>
              ) : editing ? (
                <form className="space-y-4 p-5" onSubmit={handleSaveSuggestion}>
                  <label className="block">
                    <span className="mb-1 block text-sm font-medium text-slate-700">标题</span>
                    <input
                      className="w-full rounded-md border border-line px-3 py-2 text-sm outline-none focus:border-slate-950"
                      onChange={(event) => setEditing((current) => current && { ...current, title: event.target.value })}
                      value={editing.title}
                    />
                  </label>
                  <label className="block">
                    <span className="mb-1 block text-sm font-medium text-slate-700">审查说明</span>
                    <textarea
                      className="min-h-28 w-full rounded-md border border-line px-3 py-2 text-sm leading-6 outline-none focus:border-slate-950"
                      onChange={(event) => setEditing((current) => current && { ...current, reason: event.target.value })}
                      value={editing.reason}
                    />
                  </label>
                  <label className="block">
                    <span className="mb-1 block text-sm font-medium text-slate-700">Payload JSON</span>
                    <textarea
                      className="min-h-40 w-full rounded-md border border-line bg-slate-950 px-3 py-2 font-mono text-xs leading-5 text-slate-50 outline-none focus:border-slate-700"
                      onChange={(event) => setEditing((current) => current && { ...current, payloadText: event.target.value })}
                      value={editing.payloadText}
                    />
                  </label>
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
                  从左侧选择一条结构化变更。编辑后再采纳，ProjectFlow 会把确认内容沉淀到项目档案来源、任务证据、每日回顾或成果素材。
                </div>
              )}
            </section>

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

            <section className="rounded-md border border-amber-200 bg-amber-50 p-5 text-sm leading-6 text-amber-900">
              <div className="mb-2 flex items-center gap-2 font-semibold">
                <ShieldAlert className="h-4 w-4" />
                审查边界
              </div>
              采纳前是候选建议，采纳后才进入项目档案和成果资产。忽略不会删除原始材料，只是把这条候选变更移出待确认队列。
            </section>
          </aside>
        </div>

        {error ? <div className="fixed bottom-5 left-1/2 z-50 -translate-x-1/2 rounded-md border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 shadow-panel">{error}</div> : null}
        {notice ? <div className="fixed bottom-5 left-1/2 z-50 -translate-x-1/2 rounded-md border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700 shadow-panel">{notice}</div> : null}
      </div>
    </AppShell>
  );
}

function StatusPill({ label, value, tone }: { label: string; value: number; tone: "amber" | "emerald" | "slate" }) {
  const styles = {
    amber: "bg-amber-50 text-amber-800",
    emerald: "bg-emerald-50 text-emerald-700",
    slate: "bg-slate-100 text-slate-600",
  };
  return <span className={`rounded-md px-2.5 py-1 text-xs ${styles[tone]}`}>{label} {value}</span>;
}

function sourcePreview(suggestion: AiSuggestion) {
  const sourceFile = typeof suggestion.payload.sourceFile === "string" ? suggestion.payload.sourceFile : "";
  const taskRef = typeof suggestion.payload.taskRef === "string" ? suggestion.payload.taskRef : "";
  return [sourceFile, taskRef].filter(Boolean).join(" · ") || "payload 可审查";
}

function changePreview(change: ProjectChange) {
  return [change.sourceRef, change.affectedFiles.split("\n")[0], change.riskNotes ? "含风险备注" : ""]
    .filter(Boolean)
    .join(" · ") || "结构化变更可审查";
}

function toChangePayload(change: ProjectChange): ChangeEditState {
  return {
    id: change.id,
    changeKind: change.changeKind,
    impactLevel: change.impactLevel,
    title: change.title,
    summary: change.summary,
    details: change.details,
    affectedFiles: change.affectedFiles,
    relatedTasks: change.relatedTasks,
    testEvidence: change.testEvidence,
    buildEvidence: change.buildEvidence,
    riskNotes: change.riskNotes,
    decisionNotes: change.decisionNotes,
    learningNotes: change.learningNotes,
    assetCandidates: change.assetCandidates,
  };
}
