"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import {
  ArrowRight,
  Clipboard,
  Database,
  FileArchive,
  FileCode2,
  FolderTree,
  GitPullRequestArrow,
  RefreshCw,
  Save,
  ScanLine,
  Settings,
  ShieldAlert,
  Trash2,
  Upload,
} from "lucide-react";
import { AppShell } from "@/components/AppShell";
import {
  applyAiSuggestions,
  createEvidenceBundle,
  deleteProject,
  draftProjectChangeFromEvidenceBundle,
  getProjectMemory,
  ignoreAiSuggestion,
  importProjectZip,
  listAiProviders,
  listAiSuggestions,
  listProjectAgentSignatureFeedback,
  listProjectChangeConflicts,
  listProjectEvidenceBundles,
  listProjectEvolutionRecords,
  listProjectMaterials,
  listProjectWorkSessions,
  listProjects,
  listTasks,
  saveProjectLocalPath,
  scanProjectWorkSessions,
  scanProjectFlowAgentResults,
  syncProjectContext,
  updateWorkSession,
  writeProjectFlowProtocol,
  type AgentSignatureFeedback,
  type AiProvider,
  type AiSuggestion,
  type ChangeConflict,
  type EvidenceBundle,
  type Project,
  type ProjectAnalysis,
  type ProjectEvolutionRecord,
  type ProjectMaterial,
  type ProjectMemory,
  type TaskItem,
  type WorkSessionCandidate,
  type WorkSessionScanResult,
} from "@/lib/api";
import { buildModuleGroups, projectZipPaths } from "@/lib/project-insights";
import { rememberSelectedProjectId, resolveSelectedProjectId } from "@/lib/project-selection";
import { readSession } from "@/lib/auth";
import { useProjectAnalysisJobs } from "@/lib/use-project-analysis-jobs";

export default function DashboardPage() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [providers, setProviders] = useState<AiProvider[]>([]);
  const [materials, setMaterials] = useState<ProjectMaterial[]>([]);
  const [suggestions, setSuggestions] = useState<AiSuggestion[]>([]);
  const [evolutionRecords, setEvolutionRecords] = useState<ProjectEvolutionRecord[]>([]);
  const [evidenceBundles, setEvidenceBundles] = useState<EvidenceBundle[]>([]);
  const [agentFeedback, setAgentFeedback] = useState<AgentSignatureFeedback[]>([]);
  const [changeConflicts, setChangeConflicts] = useState<ChangeConflict[]>([]);
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [memory, setMemory] = useState<ProjectMemory | null>(null);
  const [selectedProjectId, setSelectedProjectId] = useState("");
  const [selectedSuggestionIds, setSelectedSuggestionIds] = useState<string[]>([]);
  const [file, setFile] = useState<File | null>(null);
  const [projectPath, setProjectPath] = useState("");
  const [globalRule, setGlobalRule] = useState("");
  const [workSessionScan, setWorkSessionScan] = useState<WorkSessionScanResult | null>(null);
  const [workSessionDrafts, setWorkSessionDrafts] = useState<Record<string, { agentType: string; taskIntent: string }>>({});
  const [scanWarnings, setScanWarnings] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [importing, setImporting] = useState(false);
  const [applying, setApplying] = useState(false);
  const [deletingProject, setDeletingProject] = useState(false);
  const [savingProjectPath, setSavingProjectPath] = useState(false);
  const [writingProtocol, setWritingProtocol] = useState(false);
  const [scanningWorkSessions, setScanningWorkSessions] = useState(false);
  const [savingWorkSessionId, setSavingWorkSessionId] = useState("");
  const [creatingBundleSessionId, setCreatingBundleSessionId] = useState("");
  const [draftingChangeBundleId, setDraftingChangeBundleId] = useState("");
  const [syncingContext, setSyncingContext] = useState(false);
  const [scanningAgentResults, setScanningAgentResults] = useState(false);
  const [ignoringSuggestions, setIgnoringSuggestions] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const selectedProject = useMemo(
    () => projects.find((project) => project.id === selectedProjectId),
    [projects, selectedProjectId],
  );
  const { jobs, jobError, enqueueProjectAnalysis } = useProjectAnalysisJobs(selectedProjectId);
  const latestProjectJob = jobs.find((job) => job.jobType === "PROJECT") ?? null;
  const rawAnalysis = latestProjectJob?.status === "SUCCEEDED" ? latestProjectJob.projectResult : null;
  const analysisRejectedByNoise = rawAnalysis ? projectAnalysisContainsNoise(rawAnalysis) : false;
  const analysis = analysisRejectedByNoise ? null : rawAnalysis;
  const analyzing = latestProjectJob?.status === "QUEUED" || latestProjectJob?.status === "RUNNING";
  const pendingSuggestions = suggestions.filter((suggestion) => suggestion.status === "PENDING");
  const configuredProvider = providers.find((provider) => provider.id && provider.apiKeyConfigured);
  const paths = useMemo(() => projectZipPaths(materials), [materials]);
  const moduleGroups = useMemo(() => buildModuleGroups(paths), [paths]);
  const hasMaterials = materials.length > 0;
  const hasProjectZipMaterial = materials.some((material) => material.sourceType === "PROJECT_ZIP");
  const hasUsableProjectZip = paths.length > 0;
  const hasProjectPath = Boolean(projectPath.trim());
  const latestChange = evolutionRecords[0];
  const activeTasks = tasks.filter((task) => task.status !== "DONE");
  const analysisWarning = analysisRejectedByNoise
    ? "旧分析结果包含 .codex-run、old-git 或 Git 内部对象，已停止展示。请重新导入有效项目 zip，或点击重新分析生成干净画像。"
    : latestProjectJob?.status === "SUCCEEDED" && !rawAnalysis && latestProjectJob.errorMessage
      ? latestProjectJob.errorMessage
      : "";

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

  useEffect(() => {
    const session = readSession();
    if (!session) {
      return;
    }

    setLoading(true);
    Promise.all([listProjects(session.accessToken), listAiProviders(session.accessToken)])
      .then(([projectItems, providerItems]) => {
        setProjects(projectItems);
        setProviders(providerItems);
        setSelectedProjectId(resolveSelectedProjectId(projectItems));
      })
      .catch((exception) => setError(exception instanceof Error ? exception.message : "工作台数据加载失败"))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    refreshProjectContext(selectedProjectId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedProjectId]);

  async function refreshProjectContext(projectId: string) {
    const session = readSession();
    if (!session || !projectId) {
      setMaterials([]);
      setSuggestions([]);
      setEvolutionRecords([]);
      setEvidenceBundles([]);
      setAgentFeedback([]);
      setChangeConflicts([]);
      setTasks([]);
      setMemory(null);
      setProjectPath("");
      setWorkSessionScan(null);
      setWorkSessionDrafts({});
      setSelectedSuggestionIds([]);
      return;
    }

    try {
      const [materialItems, suggestionItems, evolutionItems, taskItems, memoryRecord, workSessions, bundles, feedbackItems, conflicts] = await Promise.all([
        listProjectMaterials(session.accessToken, projectId),
        listAiSuggestions(session.accessToken, projectId),
        listProjectEvolutionRecords(session.accessToken, projectId),
        listTasks(session.accessToken, projectId),
        getProjectMemory(session.accessToken, projectId),
        listProjectWorkSessions(session.accessToken, projectId),
        listProjectEvidenceBundles(session.accessToken, projectId),
        listProjectAgentSignatureFeedback(session.accessToken, projectId),
        listProjectChangeConflicts(session.accessToken, projectId),
      ]);
      setMaterials(materialItems);
      setSuggestions(suggestionItems);
      setEvolutionRecords(evolutionItems);
      setEvidenceBundles(bundles);
      setAgentFeedback(feedbackItems);
      setChangeConflicts(conflicts);
      setTasks(taskItems);
      setMemory(memoryRecord);
      setProjectPath(memoryRecord.localProjectPath ?? "");
      setWorkSessionScan(workSessions.length ? workSessionListResult(projectId, memoryRecord.localProjectPath ?? "", workSessions) : null);
      setWorkSessionDrafts(workSessionDraftMap(workSessions));
      setSelectedSuggestionIds(suggestionItems.filter((item) => item.status === "PENDING").map((item) => item.id));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "项目上下文加载失败");
    }
  }

  async function handleImportZip(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const session = readSession();
    if (!session || !file) {
      return;
    }

    setImporting(true);
    setError("");
    setNotice("");
    try {
      const result = await importProjectZip(session.accessToken, file, selectedProjectId || undefined);
      setProjects((current) => {
        const exists = current.some((project) => project.id === result.project.id);
        return exists ? current.map((project) => (project.id === result.project.id ? result.project : project)) : [result.project, ...current];
      });
      rememberSelectedProjectId(result.project.id);
      setSelectedProjectId(result.project.id);
      setNotice("项目 zip 已导入，已生成基础画像和结构理解。");
      setFile(null);
      await refreshProjectContext(result.project.id);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "项目 zip 导入失败");
    } finally {
      setImporting(false);
    }
  }

  async function handleDeleteSelectedProject() {
    const session = readSession();
    if (!session || !selectedProjectId || !selectedProject) {
      return;
    }
    const confirmed = window.confirm(`删除 ProjectFlow 中的项目“${selectedProject.name}”？这只会删除 ProjectFlow 保存的数据，不会删除你磁盘上的真实源码文件夹。`);
    if (!confirmed) {
      return;
    }

    setDeletingProject(true);
    setError("");
    setNotice("");
    try {
      await deleteProject(session.accessToken, selectedProjectId);
      const updatedProjects = await listProjects(session.accessToken);
      const nextProjectId = resolveSelectedProjectId(updatedProjects, "");
      setProjects(updatedProjects);
      rememberSelectedProjectId(nextProjectId);
      setSelectedProjectId(nextProjectId);
      setNotice("已删除 ProjectFlow 项目记录；本地源码文件未被删除。");
      if (!nextProjectId) {
        await refreshProjectContext("");
      }
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "项目删除失败");
    } finally {
      setDeletingProject(false);
    }
  }

  async function handleApplySuggestions(ids = selectedSuggestionIds) {
    const session = readSession();
    if (!session || !selectedProjectId || ids.length === 0) {
      return;
    }

    setApplying(true);
    setError("");
    setNotice("");
    try {
      await applyAiSuggestions(session.accessToken, selectedProjectId, ids);
      setNotice(`已采纳 ${ids.length} 条变更，并写入项目档案。`);
      await refreshProjectContext(selectedProjectId);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "建议采纳失败");
    } finally {
      setApplying(false);
    }
  }

  async function handleIgnoreSuggestion(suggestionId: string) {
    const session = readSession();
    if (!session || !selectedProjectId) {
      return;
    }
    setIgnoringSuggestions(true);
    setError("");
    setNotice("");
    try {
      await ignoreAiSuggestion(session.accessToken, suggestionId);
      setNotice("已忽略这条变更。");
      await refreshProjectContext(selectedProjectId);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "变更忽略失败");
    } finally {
      setIgnoringSuggestions(false);
    }
  }

  async function handleSaveProjectPath() {
    const session = readSession();
    if (!session || !selectedProjectId || !projectPath.trim()) {
      setError("先选择项目，并填写真实项目文件夹路径。");
      return;
    }

    setSavingProjectPath(true);
    setError("");
    setNotice("");
    try {
      const memoryRecord = await saveProjectLocalPath(session.accessToken, selectedProjectId, projectPath.trim());
      setMemory(memoryRecord);
      setProjectPath(memoryRecord.localProjectPath ?? projectPath.trim());
      setNotice("已保存本地项目路径；扫描和同步会复用这个路径。");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "本地项目路径保存失败");
    } finally {
      setSavingProjectPath(false);
    }
  }

  async function handleWriteProtocol() {
    const session = readSession();
    if (!session || !selectedProjectId || !projectPath.trim()) {
      setError("先选择项目，并填写真实项目文件夹路径。");
      return;
    }

    setWritingProtocol(true);
    setError("");
    setNotice("");
    try {
      const result = await writeProjectFlowProtocol(session.accessToken, selectedProjectId, projectPath.trim(), "");
      setGlobalRule(result.globalRule);
      setNotice(result.alreadyLinked ? "已刷新 .projectflow 协议和上下文。" : "已完成首次接入，agent 可以按协议写回结果。");
      await refreshProjectContext(selectedProjectId);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : ".projectflow 协议写入失败");
    } finally {
      setWritingProtocol(false);
    }
  }

  async function handleRunAnalysis() {
    const session = readSession();
    if (!session || !selectedProjectId || !hasUsableProjectZip) {
      setError("先导入包含源码、配置或文档的完整项目 zip，再运行项目画像分析。");
      return;
    }

    setError("");
    setNotice("");
    try {
      await enqueueProjectAnalysis();
      setNotice("分析任务已提交。可离开或刷新页面，任务会继续运行。");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "项目画像分析失败");
    }
  }

  async function handleScanAgentResults() {
    const session = readSession();
    if (!session || !selectedProjectId || !projectPath.trim()) {
      setError("先选择项目，并填写真实项目文件夹路径。");
      return;
    }

    setScanningAgentResults(true);
    setError("");
    setNotice("");
    setScanWarnings([]);
    try {
      const result = await scanProjectFlowAgentResults(session.accessToken, selectedProjectId, projectPath.trim());
      setScanWarnings(result.warnings);
      setNotice(result.importedResults ? `已识别 ${result.importedResults} 份 agent result，生成待审查变更。` : "未发现新的 agent result。");
      await refreshProjectContext(selectedProjectId);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Agent 更新扫描失败");
    } finally {
      setScanningAgentResults(false);
    }
  }

  async function handleScanWorkSessions() {
    const session = readSession();
    if (!session || !selectedProjectId) {
      return;
    }

    setScanningWorkSessions(true);
    setError("");
    setNotice("");
    setScanWarnings([]);
    try {
      const result = await scanProjectWorkSessions(session.accessToken, selectedProjectId);
      setWorkSessionScan(result);
      setWorkSessionDrafts(workSessionDraftMap(result.sessions));
      setScanWarnings(result.warnings);
      setNotice(result.sessions.length ? `已生成 ${result.sessions.length} 个今日变化候选。` : "今天暂未发现可归因的 Git 变化。");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "今日变化扫描失败");
    } finally {
      setScanningWorkSessions(false);
    }
  }

  async function handleUpdateWorkSession(sessionId: string) {
    const session = readSession();
    const draft = workSessionDrafts[sessionId];
    if (!session || !draft) {
      return;
    }

    setSavingWorkSessionId(sessionId);
    setError("");
    setNotice("");
    try {
      const updated = await updateWorkSession(session.accessToken, sessionId, draft.agentType, draft.taskIntent);
      setWorkSessionScan((current) => current ? {
        ...current,
        sessions: current.sessions.map((item) => item.sessionId === updated.sessionId ? updated : item),
      } : current);
      setWorkSessionDrafts((current) => ({
        ...current,
        [updated.sessionId]: { agentType: updated.agentType, taskIntent: updated.taskIntent },
      }));
      if (selectedProjectId) {
        setAgentFeedback(await listProjectAgentSignatureFeedback(session.accessToken, selectedProjectId));
      }
      setNotice("已保存 Work Session 归因校正。");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Work Session 校正失败");
    } finally {
      setSavingWorkSessionId("");
    }
  }

  async function handleCreateEvidenceBundle(sessionId: string) {
    const session = readSession();
    if (!session) {
      return;
    }

    setCreatingBundleSessionId(sessionId);
    setError("");
    setNotice("");
    try {
      const bundle = await createEvidenceBundle(session.accessToken, sessionId);
      setEvidenceBundles((current) => [bundle, ...current.filter((item) => item.id !== bundle.id)]);
      if (selectedProjectId) {
        setChangeConflicts(await listProjectChangeConflicts(session.accessToken, selectedProjectId));
      }
      setNotice("已生成 Evidence Bundle，可用于后续候选变更审查。");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Evidence Bundle 生成失败");
    } finally {
      setCreatingBundleSessionId("");
    }
  }

  async function handleDraftChange(bundleId: string) {
    const session = readSession();
    if (!session) {
      return;
    }

    setDraftingChangeBundleId(bundleId);
    setError("");
    setNotice("");
    try {
      await draftProjectChangeFromEvidenceBundle(session.accessToken, bundleId);
      setNotice("已从 Evidence Bundle 生成候选变更，请到变更审查中确认。");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "候选变更生成失败");
    } finally {
      setDraftingChangeBundleId("");
    }
  }

  async function handleSyncContext() {
    const session = readSession();
    if (!session || !selectedProjectId) {
      return;
    }

    setSyncingContext(true);
    setError("");
    setNotice("");
    try {
      const result = await syncProjectContext(session.accessToken, selectedProjectId);
      setNotice(`已同步确认上下文：${result.writtenFiles.join(", ")}`);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "确认上下文同步失败");
    } finally {
      setSyncingContext(false);
    }
  }

  async function handleCopyGlobalRule() {
    const rule = globalRule || "If the current project root contains `.projectflow/agent-protocol.md`, read it before work. After finishing development work, write a ProjectFlow Agent Result to `.projectflow/inbox/` or the task result file. Do not directly modify ProjectFlow task state.";
    try {
      await navigator.clipboard.writeText(rule);
      setNotice("已复制全局 agent 规则。");
    } catch {
      setError("浏览器没有允许复制，请手动复制全局规则。");
    }
  }

  function toggleSuggestion(id: string) {
    setSelectedSuggestionIds((current) =>
      current.includes(id) ? current.filter((item) => item !== id) : [...current, id],
    );
  }

  return (
    <AppShell
      actions={
        <Link className="flex items-center gap-2 rounded-md border border-line bg-white px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50" href="/settings">
          <Settings className="h-4 w-4" />
          设置模型
        </Link>
      }
      eyebrow="V3.1 Core"
      title="工作台"
    >
      <div className="min-h-[calc(100vh-4rem)] bg-surface">
        <section className="border-b border-line bg-white px-8 py-4">
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div className="flex flex-wrap items-center gap-3">
              <select
                className="h-10 min-w-72 rounded-md border border-line bg-white px-3 text-sm outline-none focus:border-slate-950"
                onChange={(event) => {
                  rememberSelectedProjectId(event.target.value);
                  setSelectedProjectId(event.target.value);
                }}
                value={selectedProjectId}
              >
                <option value="">选择项目，或导入 zip 创建</option>
                {projects.map((project) => (
                  <option key={project.id} value={project.id}>
                    {project.name}
                  </option>
                ))}
              </select>
              <StatusChip label={selectedProject?.status ?? "NEW"} />
              <StatusChip label={configuredProvider ? `模型：${configuredProvider.name}` : "未配置模型"} tone={configuredProvider ? "green" : "amber"} />
              <button
                className="inline-flex h-10 items-center gap-2 rounded-md border border-rose-200 bg-white px-3 text-sm font-semibold text-rose-700 hover:bg-rose-50 disabled:opacity-50"
                disabled={!selectedProjectId || deletingProject}
                onClick={handleDeleteSelectedProject}
                title="删除当前 ProjectFlow 项目记录，不删除本地真实源码文件夹。用于清理误导入或重复项目。"
                type="button"
              >
                <Trash2 className="h-4 w-4" />
                {deletingProject ? "删除中" : "删除项目"}
              </button>
            </div>
            <div className="flex items-center gap-2 text-xs text-muted">
              <Database className="h-4 w-4" />
              材料 {materials.length} · 文件 {paths.length} · 待确认 {pendingSuggestions.length}
            </div>
          </div>
        </section>

        <section className="px-8 pt-6">
          <div className="rounded-md border border-line bg-white shadow-panel">
            <div className="border-b border-line px-5 py-4">
              <p className="text-sm text-muted">当前第一步</p>
              <h2 className="mt-1 text-lg font-semibold text-slate-950">{primaryActionTitle(Boolean(selectedProject), hasUsableProjectZip, hasProjectPath, pendingSuggestions.length)}</h2>
            </div>
            <div className="grid gap-0 lg:grid-cols-[360px_minmax(0,1fr)]">
              <form className="space-y-4 border-b border-line p-5 lg:border-b-0 lg:border-r" onSubmit={handleImportZip}>
                <label className="block rounded-md border border-dashed border-line bg-slate-50 p-4">
                  <span className="mb-2 block text-sm font-medium text-slate-700">导入完整项目 zip</span>
                  <input
                    accept=".zip,application/zip"
                    className="w-full text-sm text-slate-600"
                    onChange={(event) => setFile(event.target.files?.[0] ?? null)}
                    type="file"
                  />
                </label>
                <button
                  className="flex w-full items-center justify-center gap-2 rounded-md bg-slate-950 px-4 py-2.5 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
                  disabled={!file || importing}
                  type="submit"
                >
                  {importing ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />}
                  {importing ? "导入中..." : "导入并生成基础画像"}
                </button>
              </form>

              <div className="p-5">
                <p className="mb-3 text-sm font-semibold text-slate-950">本地项目接入</p>
                <input
                  className="h-10 w-full rounded-md border border-line bg-white px-3 text-sm outline-none focus:border-slate-950"
                  onChange={(event) => setProjectPath(event.target.value)}
                  placeholder="真实项目文件夹路径"
                  value={projectPath}
                />
                <div className="mt-3 grid gap-2 sm:grid-cols-2 xl:grid-cols-5">
                  <button
                    className="inline-flex h-10 items-center justify-center gap-2 rounded-md bg-slate-950 px-3 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
                    disabled={!selectedProjectId || !projectPath.trim() || savingProjectPath}
                    onClick={handleSaveProjectPath}
                    title="只记录本地项目根目录，切换项目和刷新页面后继续复用，不写入目标项目文件。"
                    type="button"
                  >
                    {savingProjectPath ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
                    保存路径
                  </button>
                  <button
                    className="inline-flex h-10 items-center justify-center gap-2 rounded-md border border-line bg-white px-3 text-sm font-semibold text-slate-700 hover:bg-slate-100 disabled:opacity-60"
                    disabled={!selectedProjectId || !projectPath.trim() || writingProtocol}
                    onClick={handleWriteProtocol}
                    title="在目标项目生成 ProjectFlow 协议、上下文目录和结果收件箱，供 Agent 按规则写回结果。"
                    type="button"
                  >
                    {writingProtocol ? <RefreshCw className="h-4 w-4 animate-spin" /> : <FileCode2 className="h-4 w-4" />}
                    写入/刷新协议
                  </button>
                  <button
                    className="inline-flex h-10 items-center justify-center gap-2 rounded-md border border-line bg-white px-3 text-sm font-semibold text-slate-700 hover:bg-slate-100 disabled:opacity-60"
                    disabled={!selectedProjectId || !projectPath.trim() || scanningAgentResults}
                    onClick={handleScanAgentResults}
                    title="读取目标项目的 ProjectFlow 结果收件箱，把 Agent 写回内容转成待审查变更。"
                    type="button"
                  >
                    {scanningAgentResults ? <RefreshCw className="h-4 w-4 animate-spin" /> : <ScanLine className="h-4 w-4" />}
                    扫描 Agent Result
                  </button>
                  <button
                    className="inline-flex h-10 items-center justify-center gap-2 rounded-md border border-line bg-white px-3 text-sm font-semibold text-slate-700 hover:bg-slate-100 disabled:opacity-60"
                    disabled={!selectedProjectId || !projectPath.trim() || syncingContext}
                    onClick={handleSyncContext}
                    title="把已经采纳和确认的项目档案写回目标项目上下文目录，供后续 Agent 读取。"
                    type="button"
                  >
                    {syncingContext ? <RefreshCw className="h-4 w-4 animate-spin" /> : <FolderTree className="h-4 w-4" />}
                    同步确认上下文
                  </button>
                  <button
                    className="inline-flex h-10 items-center justify-center gap-2 rounded-md bg-slate-100 px-3 text-sm font-semibold text-slate-700 hover:bg-slate-200"
                    onClick={handleCopyGlobalRule}
                    title="复制给其他 Agent 使用的通用规则，让它们按 ProjectFlow 协议输出结果。"
                    type="button"
                  >
                    <Clipboard className="h-4 w-4" />
                    复制规则
                  </button>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section className="grid gap-6 px-8 py-6 xl:grid-cols-[300px_minmax(0,1fr)]">
          <aside className="space-y-4">
            <section className="rounded-md border border-line bg-white p-5 shadow-panel">
              <p className="text-sm font-semibold text-slate-950">模型状态</p>
              <p className="mt-2 text-sm leading-6 text-slate-600">
                {configuredProvider ? "已配置模型。当前页面先展示基础画像，深度分析结果必须经用户确认后写入项目档案。" : "未配置 API。文件理解页会显示本地规则解释，深度分析入口会引导到设置页。"}
              </p>
              {!configuredProvider ? (
                <Link className="mt-4 inline-flex items-center gap-2 rounded-md border border-line px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50" href="/settings">
                  去设置模型 <ArrowRight className="h-4 w-4" />
                </Link>
              ) : null}
            </section>

            {scanWarnings.length ? (
              <section className="rounded-md border border-amber-200 bg-amber-50 p-4 text-xs leading-5 text-amber-900">
                {scanWarnings.map((warning) => (
                  <div key={warning}>{warning}</div>
                ))}
              </section>
            ) : null}

            {agentFeedback.length ? (
              <section className="rounded-md border border-line bg-white p-5 shadow-panel">
                <p className="text-sm font-semibold text-slate-950">归因校正规则</p>
                <p className="mt-2 text-sm leading-6 text-slate-600">
                  当前项目已保存 {agentFeedback.length} 条校正反馈，后续扫描同一 Agent 痕迹时会优先复用。
                </p>
                <div className="mt-3 space-y-2">
                  {agentFeedback.slice(0, 3).map((feedback) => (
                    <div className="rounded-md bg-slate-50 px-3 py-2 text-xs text-slate-600" key={feedback.id}>
                      {feedback.agentName}: {feedback.originalAgentType} {"->"} {feedback.correctedAgentType}
                    </div>
                  ))}
                </div>
              </section>
            ) : null}

            {changeConflicts.length ? (
              <section className="rounded-md border border-amber-200 bg-amber-50 p-5 shadow-panel">
                <p className="text-sm font-semibold text-amber-950">冲突待审查</p>
                <p className="mt-2 text-sm leading-6 text-amber-900">
                  检测到 {changeConflicts.length} 个文件级证据重叠，需要确认是连续修改还是冲突。
                </p>
                <div className="mt-3 space-y-2">
                  {changeConflicts.slice(0, 3).map((conflict) => (
                    <div className="rounded-md bg-white/70 px-3 py-2 text-xs text-amber-950" key={conflict.id}>
                      {conflict.filePath} · {conflict.severity}
                    </div>
                  ))}
                </div>
              </section>
            ) : null}
          </aside>

          <div className="space-y-5">
            <section className="rounded-md border border-line bg-white shadow-panel">
              <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line px-5 py-4">
                <div>
                  <p className="text-sm text-muted">今日变化概览</p>
                  <h2 className="text-lg font-semibold text-slate-950">自动归因候选</h2>
                </div>
                <button
                  className="inline-flex items-center gap-2 rounded-md bg-slate-950 px-3 py-2 text-sm font-semibold text-white disabled:opacity-60"
                  disabled={!selectedProjectId || !hasProjectPath || scanningWorkSessions}
                  onClick={handleScanWorkSessions}
                  type="button"
                >
                  {scanningWorkSessions ? <RefreshCw className="h-4 w-4 animate-spin" /> : <ScanLine className="h-4 w-4" />}
                  刷新变化
                </button>
              </div>
              {workSessionScan?.sessions.length ? (
                <div className="divide-y divide-line">
                  {workSessionScan.sessions.map((session) => (
                    <article className="grid gap-4 p-5 lg:grid-cols-[minmax(0,1fr)_220px]" key={session.sessionId}>
                      <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                          <StatusChip label={session.agentType === "UNKNOWN" ? "unknown" : session.agentType} tone="amber" />
                          <StatusChip label={confidenceLabel(session.attributionConfidence)} tone={session.attributionConfidence === "HIGH" ? "green" : "slate"} />
                          <StatusChip label={session.detectionMethod === "USER_CORRECTED" ? "人工校正" : "Git evidence"} tone={session.detectionMethod === "USER_CORRECTED" ? "green" : "slate"} />
                          <span className="text-xs text-muted">{formatSessionRange(session.startTime, session.endTime)}</span>
                        </div>
                        <p className="mt-3 text-sm font-semibold text-slate-950">{session.taskIntent}</p>
                        <p className="mt-2 line-clamp-2 text-sm leading-6 text-slate-600">
                          {session.evidence[0] ?? "仅从 Git evidence 生成候选，尚未接入授权 Agent 日志。"}
                        </p>
                        <div className="mt-3 flex flex-wrap gap-2">
                          {session.affectedModules.slice(0, 5).map((module) => (
                            <span className="rounded-md bg-slate-100 px-2 py-1 text-xs text-slate-600" key={module}>{module}</span>
                          ))}
                        </div>
                        <div className="mt-4 grid gap-2 rounded-lg border border-slate-200 bg-slate-50 p-3 md:grid-cols-[150px_minmax(0,1fr)_auto]">
                          <select
                            className="rounded-md border border-line bg-white px-3 py-2 text-sm text-slate-800"
                            onChange={(event) => setWorkSessionDrafts((current) => ({
                              ...current,
                              [session.sessionId]: {
                                agentType: event.target.value,
                                taskIntent: current[session.sessionId]?.taskIntent ?? session.taskIntent,
                              },
                            }))}
                            value={workSessionDrafts[session.sessionId]?.agentType ?? session.agentType}
                          >
                            {agentTypeOptions.map((option) => (
                              <option key={option.value} value={option.value}>{option.label}</option>
                            ))}
                          </select>
                          <input
                            className="rounded-md border border-line bg-white px-3 py-2 text-sm text-slate-800"
                            onChange={(event) => setWorkSessionDrafts((current) => ({
                              ...current,
                              [session.sessionId]: {
                                agentType: current[session.sessionId]?.agentType ?? session.agentType,
                                taskIntent: event.target.value,
                              },
                            }))}
                            placeholder="补充这轮工作的真实任务意图"
                            value={workSessionDrafts[session.sessionId]?.taskIntent ?? session.taskIntent}
                          />
                          <button
                            className="rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white disabled:opacity-60"
                            disabled={savingWorkSessionId === session.sessionId}
                            onClick={() => handleUpdateWorkSession(session.sessionId)}
                            type="button"
                          >
                            {savingWorkSessionId === session.sessionId ? "保存中" : "保存校正"}
                          </button>
                        </div>
                      </div>
                      <div className="grid grid-cols-3 gap-2 text-center lg:grid-cols-1 lg:text-left">
                        <MiniFact label="文件" value={`${session.changedFiles}`} />
                        <MiniFact label="新增" value={`+${session.addedLines}`} />
                        <MiniFact label="删除" value={`-${session.deletedLines}`} />
                        <button
                          className="col-span-3 rounded-md border border-slate-300 px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-60 lg:col-span-1"
                          disabled={creatingBundleSessionId === session.sessionId}
                          onClick={() => handleCreateEvidenceBundle(session.sessionId)}
                          type="button"
                        >
                          {creatingBundleSessionId === session.sessionId
                            ? "生成中"
                            : evidenceBundles.some((bundle) => bundle.workSessionId === session.sessionId)
                              ? "更新证据包"
                              : "生成证据包"}
                        </button>
                      </div>
                    </article>
                  ))}
                </div>
              ) : (
                <div className="p-5 text-sm leading-6 text-muted">
                  {hasProjectPath
                    ? "点击“刷新变化”后，ProjectFlow 会读取已绑定项目的今日 Git evidence，生成可审查的 Work Session 候选。"
                    : "先在上方绑定真实项目路径，ProjectFlow 才能读取 Git evidence。不会扫描用户主目录或全局 Agent 日志。"}
                </div>
              )}
              {evidenceBundles.length ? (
                <div className="border-t border-line bg-slate-50 px-5 py-4">
                  <p className="text-sm font-semibold text-slate-900">已生成 Evidence Bundle：{evidenceBundles.length}</p>
                  <div className="mt-3 grid gap-2 md:grid-cols-2">
                    {evidenceBundles.slice(0, 4).map((bundle) => (
                      <div className="rounded-md border border-slate-200 bg-white p-3 text-sm" key={bundle.id}>
                        <div className="flex flex-wrap items-center gap-2">
                          <StatusChip label={bundle.agentType === "UNKNOWN" ? "unknown" : bundle.agentType} tone="slate" />
                          <span className="text-xs text-muted">{bundle.sources[0]?.sourceType ?? "UNKNOWN_SOURCE"}</span>
                        </div>
                        <p className="mt-2 font-semibold text-slate-900">{bundle.taskIntent || "未补充任务意图"}</p>
                        <p className="mt-1 text-xs text-slate-500">
                          {bundle.changedFiles} 文件，+{bundle.addedLines}/-{bundle.deletedLines}，Agent Claim {bundle.agentClaims.length} 条
                        </p>
                        <button
                          className="mt-3 rounded-md border border-slate-300 px-3 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-60"
                          disabled={draftingChangeBundleId === bundle.id}
                          onClick={() => handleDraftChange(bundle.id)}
                          type="button"
                        >
                          {draftingChangeBundleId === bundle.id ? "生成中" : "生成候选变更"}
                        </button>
                      </div>
                    ))}
                  </div>
                </div>
              ) : null}
            </section>

            <section className="rounded-md border border-line bg-white shadow-panel">
              <div className="flex items-center justify-between border-b border-line px-5 py-4">
                <div>
                  <p className="text-sm text-muted">项目画像</p>
                  <h2 className="text-lg font-semibold text-slate-950">{selectedProject?.name ?? "先导入项目"}</h2>
                </div>
                {selectedProjectId ? (
                  <div className="flex flex-wrap items-center gap-2">
                    <button
                      className="inline-flex items-center gap-2 rounded-md bg-slate-950 px-3 py-2 text-sm font-semibold text-white disabled:opacity-60"
                      disabled={analyzing || !hasUsableProjectZip}
                      onClick={handleRunAnalysis}
                      title={hasUsableProjectZip ? "基于当前有效项目 zip 重新生成项目画像。" : "当前项目还没有可分析的源码、配置或文档目录结构。"}
                      type="button"
                    >
                      {analyzing ? <RefreshCw className="h-4 w-4 animate-spin" /> : <FileCode2 className="h-4 w-4" />}
                      {latestProjectJob?.status === "QUEUED"
                        ? "等待分析"
                        : analyzing
                          ? "模型分析中"
                          : analysis
                            ? "重新分析"
                            : configuredProvider
                              ? "运行模型分析"
                              : "本地规则分析"}
                    </button>
                    <Link className="inline-flex items-center gap-1 text-sm font-semibold text-slate-700 hover:text-slate-950" href="/project-intelligence">
                      查看完整画像 <ArrowRight className="h-4 w-4" />
                    </Link>
                  </div>
                ) : null}
              </div>
              {selectedProject && hasMaterials ? (
                <div className="grid gap-0 md:grid-cols-[minmax(0,1.4fr)_minmax(260px,0.8fr)]">
                  <div className="border-b border-line p-5 md:border-b-0 md:border-r">
                    <p className="max-w-4xl text-base leading-7 text-slate-800">
                      {analysis?.summary || (!hasUsableProjectZip && hasProjectZipMaterial
                        ? "当前项目材料没有识别到有效源码、配置或文档结构；如果这是旧导入，建议重新导入完整项目 zip，或删除这个错误项目记录。"
                        : memory?.positioning || selectedProject.description || "已导入项目材料，正在使用本地规则生成基础项目画像。配置模型后可生成更完整的架构、风险和文件解释。")}
                    </p>
                    {analysis ? (
                      <div className="mt-4 rounded-md border border-line bg-slate-50 p-4">
                        <div className="mb-2 flex flex-wrap items-center gap-2">
                          <StatusChip label={analysis.analysisSource === "MODEL_ANALYSIS" ? "模型分析" : "本地规则"} tone={analysis.modelUsed ? "green" : "amber"} />
                          <span className="text-xs text-muted">{analysis.message}</span>
                        </div>
                        <p className="text-sm leading-6 text-slate-600">{analysis.architecture}</p>
                        {analysis.risks.length ? (
                          <p className="mt-2 line-clamp-2 text-sm text-amber-800">风险：{analysis.risks.join("；")}</p>
                        ) : null}
                        {analysis.evidence.length ? (
                          <p className="mt-2 line-clamp-2 text-xs leading-5 text-muted">依据：{analysis.evidence.join("；")}</p>
                        ) : null}
                      </div>
                    ) : null}
                    {latestProjectJob?.status === "FAILED" ? (
                      <p className="mt-3 rounded-md border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700">
                        分析任务失败：{latestProjectJob.errorMessage ?? "未知错误"}
                      </p>
                    ) : null}
                    {analysisWarning ? (
                      <p className="mt-3 rounded-md border border-amber-200 bg-amber-50 p-3 text-sm leading-6 text-amber-800">
                        {analysisWarning}
                      </p>
                    ) : null}
                    {jobError ? <p className="mt-3 text-sm text-amber-700">{jobError}</p> : null}
                    <div className="mt-5 grid gap-3 sm:grid-cols-3">
                      <MiniFact label="工程阶段" value={memory?.currentStage || selectedProject.status} />
                      <MiniFact label="进行中" value={memory?.inProgressCapabilities || activeTasks[0]?.title || "等待确认"} />
                      <MiniFact label="风险" value={memory?.currentRisks || "暂无已确认风险"} />
                    </div>
                  </div>
                  <div className="p-5">
                    <p className="text-sm font-semibold text-slate-950">最近变化</p>
                    <p className="mt-2 line-clamp-5 text-sm leading-6 text-slate-600">
                      {latestChange?.summary || "扫描 agent result 或采纳变更后，这里只显示最近变化摘要。更多内容进入每日回顾查看。"}
                    </p>
                    {pendingSuggestions.length ? (
                      <Link className="mt-4 inline-flex items-center gap-2 rounded-md bg-slate-950 px-3 py-2 text-sm font-semibold text-white" href="/tasks">
                        待确认 {pendingSuggestions.length} 条 <ArrowRight className="h-4 w-4" />
                      </Link>
                    ) : null}
                  </div>
                </div>
              ) : (
                <EmptyProjectState hasProject={Boolean(selectedProject)} />
              )}
            </section>

            <section className="rounded-md border border-line bg-white shadow-panel">
              <div className="flex items-center justify-between border-b border-line px-5 py-4">
                <div className="flex items-center gap-2">
                  <FolderTree className="h-4 w-4 text-slate-700" />
                  <h2 className="font-semibold">架构与文件理解</h2>
                </div>
                <span className="text-sm text-muted">{paths.length ? `${paths.length} 个文件信号` : "等待导入"}</span>
              </div>
              <div className="grid gap-3 p-5 md:grid-cols-2 xl:grid-cols-3">
                {moduleGroups.map((group) => (
                  <Link
                    className="rounded-md border border-line bg-slate-50 p-4 transition hover:-translate-y-0.5 hover:border-slate-400 hover:bg-white hover:shadow-panel"
                    href={`/projects/${selectedProjectId}/files?module=${encodeURIComponent(group.name)}`}
                    key={group.name}
                  >
                    <div className="flex items-center justify-between gap-3">
                      <h3 className="text-lg font-semibold">{group.name}</h3>
                      <span className="rounded-full bg-white px-2 py-1 text-xs text-muted">{group.count}</span>
                    </div>
                    <p className="mt-3 line-clamp-2 text-sm leading-6 text-slate-600">{group.summary}</p>
                    <div className="mt-4 flex items-center justify-between text-xs text-muted">
                      <span>{group.important[0] ?? "暂无关键文件"}</span>
                      <ArrowRight className="h-4 w-4" />
                    </div>
                  </Link>
                ))}
                {moduleGroups.length === 0 ? (
                  <div className="rounded-md border border-dashed border-line p-6 text-sm leading-6 text-muted">
                    还没有目录结构。先在左侧导入完整项目 zip，ProjectFlow 才能生成架构与文件理解。
                  </div>
                ) : null}
              </div>
            </section>

            {pendingSuggestions.length ? (
              <section className="rounded-md border border-line bg-white shadow-panel">
                <div className="flex items-center justify-between border-b border-line px-5 py-4">
                  <h2 className="font-semibold">待确认变化</h2>
                  <Link className="text-sm font-semibold text-slate-700 hover:text-slate-950" href="/tasks">集中审查</Link>
                </div>
                <div className="divide-y divide-line">
                  {pendingSuggestions.slice(0, 3).map((suggestion) => (
                    <article className="grid gap-3 p-4 text-sm md:grid-cols-[24px_minmax(0,1fr)_auto]" key={suggestion.id}>
                      <input checked={selectedSuggestionIds.includes(suggestion.id)} onChange={() => toggleSuggestion(suggestion.id)} type="checkbox" />
                      <div className="min-w-0">
                        <p className="truncate font-medium text-slate-950">{suggestion.title}</p>
                        <p className="mt-1 line-clamp-1 text-slate-600">{suggestion.reason}</p>
                      </div>
                      <div className="flex gap-2">
                        <button className="rounded-md bg-slate-950 px-3 py-1.5 text-xs font-semibold text-white disabled:opacity-60" disabled={applying} onClick={() => handleApplySuggestions([suggestion.id])} type="button">
                          采纳
                        </button>
                        <button className="rounded-md border border-line bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 disabled:opacity-60" disabled={ignoringSuggestions} onClick={() => handleIgnoreSuggestion(suggestion.id)} type="button">
                          忽略
                        </button>
                      </div>
                    </article>
                  ))}
                </div>
                <div className="border-t border-line p-4">
                  <button className="inline-flex items-center gap-2 rounded-md bg-slate-950 px-4 py-2 text-sm font-semibold text-white disabled:opacity-60" disabled={applying || selectedSuggestionIds.length === 0} onClick={() => handleApplySuggestions()} type="button">
                    <GitPullRequestArrow className="h-4 w-4" />
                    采纳选中 {selectedSuggestionIds.length}
                  </button>
                </div>
              </section>
            ) : null}
          </div>
        </section>

        {error ? <div className="fixed bottom-5 left-1/2 z-50 -translate-x-1/2 rounded-md border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 shadow-panel">{error}</div> : null}
        {notice ? <div className="fixed bottom-5 left-1/2 z-50 -translate-x-1/2 rounded-md border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700 shadow-panel">{notice}</div> : null}
        {loading ? <div className="fixed inset-x-0 bottom-0 h-1 bg-slate-950" /> : null}
      </div>
    </AppShell>
  );
}

function StatusChip({ label, tone = "slate" }: { label: string; tone?: "slate" | "green" | "amber" }) {
  const styles = {
    slate: "bg-slate-100 text-slate-600",
    green: "bg-emerald-50 text-emerald-700",
    amber: "bg-amber-50 text-amber-800",
  };
  return <span className={`rounded-md px-2.5 py-1 text-xs ${styles[tone]}`}>{label}</span>;
}

function MiniFact({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-md border border-line bg-slate-50 p-3">
      <p className="text-xs text-muted">{label}</p>
      <p className="mt-1 line-clamp-2 text-sm font-semibold text-slate-900">{value}</p>
    </div>
  );
}

function EmptyProjectState({ hasProject }: { hasProject: boolean }) {
  return (
    <div className="grid min-h-64 place-items-center p-8 text-center">
      <div className="max-w-md">
        <ShieldAlert className="mx-auto h-8 w-8 text-slate-400" />
        <h3 className="mt-4 font-semibold text-slate-950">{hasProject ? "先导入项目材料" : "先创建或导入项目"}</h3>
        <p className="mt-2 text-sm leading-6 text-muted">
          {hasProject
            ? "当前项目还没有 zip 或 agent 材料。导入后才能生成项目画像、架构理解和文件解释。"
            : "ProjectFlow 需要先拿到真实项目，才会展示画像、风险和文件理解。请从左侧导入完整项目 zip。"}
        </p>
      </div>
    </div>
  );
}

function confidenceLabel(value: string) {
  if (value === "HIGH") return "置信度 high";
  if (value === "MEDIUM") return "置信度 medium";
  if (value === "LOW") return "置信度 low";
  return "置信度 unknown";
}

function formatSessionRange(startTime: string, endTime: string) {
  const formatter = new Intl.DateTimeFormat("zh-CN", {
    hour: "2-digit",
    minute: "2-digit",
  });
  return `${formatter.format(new Date(startTime))} - ${formatter.format(new Date(endTime))}`;
}

const agentTypeOptions = [
  { value: "UNKNOWN", label: "未知" },
  { value: "CODEX", label: "Codex" },
  { value: "CLAUDE_CODE", label: "Claude Code" },
  { value: "CURSOR", label: "Cursor" },
  { value: "DEEPSEEK", label: "DeepSeek" },
  { value: "OTHER", label: "其他" },
];

function workSessionDraftMap(sessions: WorkSessionCandidate[]) {
  return Object.fromEntries(
    sessions.map((session) => [session.sessionId, {
      agentType: session.agentType,
      taskIntent: session.taskIntent,
    }]),
  );
}

function workSessionListResult(projectId: string, projectPath: string, sessions: WorkSessionCandidate[]): WorkSessionScanResult {
  return {
    projectId,
    projectPath,
    branchName: sessions[0]?.branchName ?? "",
    scannedAt: new Date().toISOString(),
    sessions,
    warnings: [],
  };
}

function projectAnalysisContainsNoise(analysis: ProjectAnalysis) {
  return [
    analysis.summary,
    analysis.architecture,
    analysis.message,
    ...analysis.modules,
    ...analysis.risks,
    ...analysis.importantFiles,
    ...analysis.evidence,
    ...analysis.limitations,
  ].some((value) => {
    const lower = value.toLowerCase().replaceAll("\\", "/");
    return lower.includes(".codex-run/")
      || lower.includes("old-git-")
      || lower.includes(".git/objects/")
      || lower.includes(".git/config")
      || lower.includes(".git/head");
  });
}

function primaryActionTitle(hasProject: boolean, hasMaterials: boolean, hasProjectPath: boolean, pendingCount: number) {
  if (!hasProject) return "导入项目";
  if (!hasMaterials) return "导入完整 zip";
  if (!hasProjectPath) return "绑定真实路径";
  if (pendingCount > 0) return "确认变化";
  return "扫描更新";
}
