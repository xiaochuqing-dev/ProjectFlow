"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import {
  AlertTriangle,
  CheckCircle2,
  Clipboard,
  Database,
  FileArchive,
  FileCode2,
  FolderKanban,
  History,
  ListChecks,
  RefreshCw,
  ScanLine,
  Settings,
  Trash2,
  Upload,
} from "lucide-react";
import { AppShell } from "@/components/AppShell";
import {
  applyAiSuggestions,
  getProjectMemory,
  ignoreAiSuggestion,
  importProjectZip,
  listAiProviders,
  listAiSuggestions,
  listProjectEvolutionRecords,
  listProjectMaterials,
  listProjects,
  listTasks,
  scanProjectFlowAgentResults,
  writeAgentTaskBrief,
  writeProjectFlowProtocol,
  type AiProvider,
  type AiSuggestion,
  type Project,
  type ProjectEvolutionRecord,
  type ProjectImportAnalyzeResult,
  type ProjectMaterial,
  type ProjectProfile,
  type TaskItem,
} from "@/lib/api";
import { readSession } from "@/lib/auth";

const suggestionLabels = {
  UPDATE_PROJECT_MEMORY: "档案",
  CREATE_TASK: "任务",
  CREATE_DEV_LOG: "日志",
  RECORD_TECHNICAL_DECISION: "决策",
  RECORD_RISK: "风险",
  RECORD_DEVELOPER_LEARNING: "收获",
  UPDATE_CURRENT_STAGE: "阶段",
  GENERATE_ASSET_SUMMARY: "成果",
};

export default function DashboardPage() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [providers, setProviders] = useState<AiProvider[]>([]);
  const [materials, setMaterials] = useState<ProjectMaterial[]>([]);
  const [suggestions, setSuggestions] = useState<AiSuggestion[]>([]);
  const [evolutionRecords, setEvolutionRecords] = useState<ProjectEvolutionRecord[]>([]);
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState("");
  const [selectedSuggestionIds, setSelectedSuggestionIds] = useState<string[]>([]);
  const [file, setFile] = useState<File | null>(null);
  const [projectPath, setProjectPath] = useState("");
  const [requirements, setRequirements] = useState("");
  const [globalRule, setGlobalRule] = useState("");
  const [scanWarnings, setScanWarnings] = useState<string[]>([]);
  const [importResult, setImportResult] = useState<ProjectImportAnalyzeResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [importing, setImporting] = useState(false);
  const [applying, setApplying] = useState(false);
  const [writingProtocol, setWritingProtocol] = useState(false);
  const [scanningAgentResults, setScanningAgentResults] = useState(false);
  const [writingBriefTaskId, setWritingBriefTaskId] = useState("");
  const [ignoringSuggestions, setIgnoringSuggestions] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const selectedProject = useMemo(
    () => projects.find((project) => project.id === selectedProjectId) ?? importResult?.project,
    [importResult, projects, selectedProjectId],
  );
  const pendingSuggestions = suggestions.filter((suggestion) => suggestion.status === "PENDING");
  const configuredProvider = providers.find((provider) => provider.id && provider.apiKeyConfigured);
  const persistedProjectProfile = useMemo(
    () => importResult?.projectProfile ?? buildPersistedProjectProfile(selectedProject, materials, pendingSuggestions),
    [importResult, materials, pendingSuggestions, selectedProject],
  );

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
        setSelectedProjectId(projectItems[0]?.id ?? "");
      })
      .catch((exception) => setError(exception instanceof Error ? exception.message : "项目管理数据加载失败"))
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
      setTasks([]);
      setSelectedSuggestionIds([]);
      setProjectPath("");
      return;
    }

    try {
      const [materialItems, suggestionItems, evolutionItems, taskItems, memory] = await Promise.all([
        listProjectMaterials(session.accessToken, projectId),
        listAiSuggestions(session.accessToken, projectId),
        listProjectEvolutionRecords(session.accessToken, projectId),
        listTasks(session.accessToken, projectId),
        getProjectMemory(session.accessToken, projectId),
      ]);
      setMaterials(materialItems);
      setSuggestions(suggestionItems);
      setEvolutionRecords(evolutionItems);
      setTasks(taskItems);
      setProjectPath(memory.localProjectPath ?? "");
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
      setImportResult(result);
      setProjects((current) => {
        const exists = current.some((project) => project.id === result.project.id);
        return exists ? current.map((project) => (project.id === result.project.id ? result.project : project)) : [result.project, ...current];
      });
      setSelectedProjectId(result.project.id);
      setMaterials((current) => [result.material, ...current.filter((item) => item.id !== result.material.id)]);
      setSuggestions(result.suggestions);
      setSelectedSuggestionIds(result.suggestions.map((suggestion) => suggestion.id));
      setNotice("项目 zip 已导入，系统已生成本地项目画像和待确认建议。");
      setFile(null);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "项目 zip 导入失败");
    } finally {
      setImporting(false);
    }
  }

  async function handleApplySuggestions() {
    const session = readSession();
    if (!session || !selectedProjectId || selectedSuggestionIds.length === 0) {
      return;
    }

    setApplying(true);
    setError("");
    setNotice("");
    try {
      await applyAiSuggestions(session.accessToken, selectedProjectId, selectedSuggestionIds);
      setNotice("已采纳选中建议，并更新项目档案。");
      await refreshProjectContext(selectedProjectId);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "建议采纳失败");
    } finally {
      setApplying(false);
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
      const result = await writeProjectFlowProtocol(session.accessToken, selectedProjectId, projectPath.trim(), requirements.trim());
      setProjectPath(projectPath.trim());
      setGlobalRule(result.globalRule);
      setNotice(
        result.alreadyLinked
          ? `项目已接入 ProjectFlow，本次已刷新 ${result.writtenFiles.length} 个协议文件和上下文。`
          : `已完成首次接入，写入 ${result.writtenFiles.length} 个 .projectflow 文件并保存项目路径。`,
      );
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : ".projectflow 协议写入失败");
    } finally {
      setWritingProtocol(false);
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
      setProjectPath(projectPath.trim());
      setScanWarnings(result.warnings);
      if (result.importedResults === 0) {
        setNotice(result.warnings.length ? "扫描完成，但有 result 文件需要修正。" : "没有发现新的 agent 结果文件。");
      } else {
        setMaterials((current) => [...result.materials, ...current]);
        setSuggestions((current) => [...result.suggestions, ...current]);
        setSelectedSuggestionIds((current) => [...result.suggestions.map((suggestion) => suggestion.id), ...current]);
        setNotice(`已识别 ${result.importedResults} 份 agent 结果，并生成待确认建议。`);
      }
      await refreshProjectContext(selectedProjectId);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Agent 更新扫描失败");
    } finally {
      setScanningAgentResults(false);
    }
  }

  async function handleCopyGlobalRule() {
    const rule = globalRule || "如果当前项目根目录存在 `.projectflow/agent-protocol.md`，开始工作前先读取它。完成开发任务后，必须按该协议把结果写入 `.projectflow/inbox/` 或对应任务目录的 `result.md`，不要直接修改 ProjectFlow 的真实任务状态。";
    try {
      await navigator.clipboard.writeText(rule);
      setNotice("已复制全局 agent 规则。");
    } catch {
      setError("浏览器没有允许复制，请手动复制全局规则。");
    }
  }

  async function handleWriteTaskBrief(taskId: string) {
    const session = readSession();
    if (!session || !selectedProjectId || !projectPath.trim()) {
      setError("先选择项目，并填写真实项目文件夹路径。");
      return;
    }

    setWritingBriefTaskId(taskId);
    setError("");
    setNotice("");
    try {
      const result = await writeAgentTaskBrief(session.accessToken, selectedProjectId, taskId, projectPath.trim(), requirements.trim());
      setProjectPath(projectPath.trim());
      setNotice(`已写入任务 brief：${result.briefPath}`);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "任务 brief 写入失败");
    } finally {
      setWritingBriefTaskId("");
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
      setNotice("已移除这条待确认建议。");
      await refreshProjectContext(selectedProjectId);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "建议移除失败");
    } finally {
      setIgnoringSuggestions(false);
    }
  }

  async function handleIgnoreSelectedSuggestions() {
    const session = readSession();
    if (!session || !selectedProjectId || selectedSuggestionIds.length === 0) {
      return;
    }
    setIgnoringSuggestions(true);
    setError("");
    setNotice("");
    try {
      await Promise.all(selectedSuggestionIds.map((id) => ignoreAiSuggestion(session.accessToken, id)));
      setNotice(`已移除 ${selectedSuggestionIds.length} 条待确认建议。`);
      await refreshProjectContext(selectedProjectId);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "批量移除建议失败");
    } finally {
      setIgnoringSuggestions(false);
    }
  }

  function formatPayloadPreview(payload: Record<string, unknown>) {
    const taskRef = typeof payload.taskRef === "string" && payload.taskRef ? `任务引用：${payload.taskRef}` : "";
    const taskTitle = typeof payload.taskTitle === "string" && payload.taskTitle ? `任务：${payload.taskTitle}` : "";
    const sourceFile = typeof payload.sourceFile === "string" && payload.sourceFile ? `来源：${payload.sourceFile}` : "";
    return [taskTitle, taskRef, sourceFile].filter(Boolean).join(" · ");
  }

  function toggleSuggestion(id: string) {
    setSelectedSuggestionIds((current) =>
      current.includes(id) ? current.filter((item) => item !== id) : [...current, id],
    );
  }

  return (
    <AppShell
      actions={
        <Link className="flex items-center gap-2 rounded-full border border-line bg-white px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50" href="/settings">
          <Settings className="h-4 w-4" />
          个人设置
        </Link>
      }
      eyebrow="V2 Core"
      title="项目管理"
    >
      <div className="grid min-h-[calc(100vh-4rem)] grid-rows-[auto_1fr] bg-surface">
        <section className="border-b border-line bg-white px-8 py-4">
          <div className="space-y-3">
            <div className="flex flex-wrap items-center justify-between gap-4">
              <div className="flex flex-wrap items-center gap-3">
                <select
                  className="min-w-72 rounded-md border border-line bg-white px-3 py-2 text-sm outline-none focus:border-brand"
                  onChange={(event) => setSelectedProjectId(event.target.value)}
                  value={selectedProjectId}
                >
                  <option value="">未选择项目，导入 zip 后自动创建</option>
                  {projects.map((project) => (
                    <option key={project.id} value={project.id}>
                      {project.name}
                    </option>
                  ))}
                </select>
                <span className="rounded-md bg-slate-100 px-2.5 py-1 text-xs text-slate-600">
                  {selectedProject?.status ?? "NEW"}
                </span>
                <span className="rounded-md bg-blue-50 px-2.5 py-1 text-xs text-brand">
                  {configuredProvider ? `模型：${configuredProvider.name}` : "本地项目画像可用，真实模型未配置"}
                </span>
              </div>
              <div className="flex items-center gap-2 text-xs text-muted">
                <Database className="h-4 w-4" />
                材料 {materials.length} · 待确认 {pendingSuggestions.length} · 演进 {evolutionRecords.length}
              </div>
            </div>

            <div className="grid gap-2 rounded-md border border-line bg-slate-50 p-2 xl:grid-cols-[minmax(220px,1fr)_minmax(260px,1.2fr)_auto_auto_auto]">
              <input
                className="h-10 rounded border border-line bg-white px-3 text-sm outline-none focus:border-brand"
                onChange={(event) => setProjectPath(event.target.value)}
                placeholder="真实项目文件夹路径；写入协议后会保存并自动回填"
                value={projectPath}
              />
              <input
                className="h-10 rounded border border-line bg-white px-3 text-sm outline-none focus:border-brand"
                onChange={(event) => setRequirements(event.target.value)}
                placeholder="本次需求或非目标，可留空；开发者也可以直接在 agent 里说明"
                value={requirements}
              />
              <button
                className="inline-flex h-10 items-center justify-center gap-2 rounded bg-slate-950 px-3 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
                disabled={!selectedProjectId || !projectPath.trim() || writingProtocol}
                onClick={handleWriteProtocol}
                type="button"
              >
                {writingProtocol ? <RefreshCw className="h-4 w-4 animate-spin" /> : <FileCode2 className="h-4 w-4" />}
                写入协议
              </button>
              <button
                className="inline-flex h-10 items-center justify-center gap-2 rounded border border-line bg-white px-3 text-sm font-semibold text-slate-700 hover:bg-slate-100 disabled:opacity-60"
                disabled={!selectedProjectId || !projectPath.trim() || scanningAgentResults}
                onClick={handleScanAgentResults}
                type="button"
              >
                {scanningAgentResults ? <RefreshCw className="h-4 w-4 animate-spin" /> : <ScanLine className="h-4 w-4" />}
                扫描更新
              </button>
              <button
                className="inline-flex h-10 items-center justify-center gap-2 rounded border border-line bg-white px-3 text-sm font-semibold text-slate-700 hover:bg-slate-100"
                onClick={handleCopyGlobalRule}
                type="button"
              >
                <Clipboard className="h-4 w-4" />
                复制规则
              </button>
            </div>
          </div>
        </section>

        <div className="grid gap-5 p-6 xl:grid-cols-[420px_minmax(0,1fr)_320px]">
          <section className="space-y-5">
            <form className="rounded-md border border-line bg-white p-5 shadow-panel" onSubmit={handleImportZip}>
              <div className="mb-4 flex items-center justify-between gap-3">
                <div>
                  <h2 className="text-base font-semibold text-slate-950">首次导入完整项目</h2>
                  <p className="mt-1 text-sm text-muted">上传项目 zip，系统会自动创建项目并推断项目名。</p>
                </div>
                <FileArchive className="h-5 w-5 text-brand" />
              </div>

              <label className="block rounded-md border border-dashed border-line bg-slate-50 p-4">
                <span className="mb-2 block text-sm font-medium text-slate-700">项目 zip</span>
                <input
                  accept=".zip,application/zip"
                  className="w-full text-sm text-slate-600"
                  onChange={(event) => setFile(event.target.files?.[0] ?? null)}
                  type="file"
                />
                <span className="mt-2 block text-xs text-muted">
                  会排除 .git、node_modules、dist、.next、logs、.env 和二进制文件。
                </span>
              </label>

              <button
                className="mt-4 flex w-full items-center justify-center gap-2 rounded-md bg-brand px-4 py-2.5 text-sm font-semibold text-white hover:bg-blue-600 disabled:opacity-60"
                disabled={!file || importing}
                type="submit"
              >
                {importing ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />}
                {importing ? "导入中..." : "导入并生成项目画像"}
              </button>
            </form>

            <div className="rounded-md border border-line bg-white p-5 shadow-panel">
              <div className="mb-3 flex items-center gap-2">
                <FolderKanban className="h-4 w-4 text-brand" />
                <h2 className="text-base font-semibold text-slate-950">Agent 写回协议</h2>
              </div>
              <p className="text-sm leading-6 text-slate-600">
                协议写入后，开发者可以直接在 agent 里提需求。agent 完工后按 `.projectflow/agent-protocol.md` 写回结果，ProjectFlow 扫描后生成待确认更新。
              </p>
              {scanWarnings.length ? (
                <div className="mt-3 space-y-1 rounded border border-amber-200 bg-amber-50 p-3 text-xs leading-5 text-amber-900">
                  {scanWarnings.map((warning) => (
                    <div key={warning}>{warning}</div>
                  ))}
                </div>
              ) : null}
            </div>

            <div className="rounded-md border border-line bg-white shadow-panel">
              <div className="flex items-center justify-between gap-3 border-b border-line px-4 py-3">
                <div className="flex items-center gap-2">
                  <ListChecks className="h-4 w-4 text-brand" />
                  <h2 className="text-base font-semibold text-slate-950">任务队列</h2>
                </div>
                <span className="text-xs text-muted">{tasks.length} 个任务</span>
              </div>
              <div className="divide-y divide-line">
                {tasks.slice(0, 6).map((task) => (
                  <div className="grid grid-cols-[1fr_auto] items-center gap-3 px-4 py-3 text-sm" key={task.id}>
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <p className="truncate font-medium text-slate-900">{task.title}</p>
                        <span className="rounded bg-slate-100 px-1.5 py-0.5 text-[11px] text-slate-600">{task.status}</span>
                      </div>
                      <p className="mt-1 line-clamp-1 text-xs text-muted">{task.description || "暂无任务说明"}</p>
                    </div>
                    <button
                      className="rounded border border-line bg-white px-2.5 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-60"
                      disabled={!projectPath.trim() || writingBriefTaskId === task.id}
                      onClick={() => handleWriteTaskBrief(task.id)}
                      type="button"
                    >
                      {writingBriefTaskId === task.id ? "写入中" : "写入 brief"}
                    </button>
                  </div>
                ))}
                {tasks.length === 0 ? (
                  <div className="px-4 py-5 text-sm text-muted">
                    暂无任务。可以先采纳 AI 候选任务，或直接让 agent 工作后由 ProjectFlow 扫描生成任务建议。
                  </div>
                ) : null}
              </div>
            </div>

            {!configuredProvider ? (
              <div className="rounded-md border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-900">
                <div className="mb-1 flex items-center gap-2 font-semibold">
                  <AlertTriangle className="h-4 w-4" />
                  真实模型未配置
                </div>
                当前会使用本地规则生成基础项目画像。需要模型增强建议时，到
                <Link className="mx-1 font-semibold underline" href="/settings">个人设置</Link>
                配置 DeepSeek 或 OpenAI-compatible provider。
              </div>
            ) : null}
          </section>

          <section className="space-y-5">
            <div className="rounded-md border border-line bg-white shadow-panel">
              <div className="border-b border-line px-5 py-3">
                <h2 className="text-base font-semibold text-slate-950">项目档案 / 初始分析</h2>
              </div>
              {persistedProjectProfile ? (
                <div className="grid gap-0 text-sm md:grid-cols-2">
                  {[
                    ["推断项目名", persistedProjectProfile.inferredProjectName],
                    ["当前阶段", persistedProjectProfile.currentStage],
                    ["技术栈", persistedProjectProfile.techStack.join(", ") || "未识别"],
                    ["最该补齐", persistedProjectProfile.mostImportantGap],
                    ["README", persistedProjectProfile.hasReadme ? "有" : "缺失"],
                    ["测试", persistedProjectProfile.hasTests ? "有" : "缺失"],
                    ["启动脚本", persistedProjectProfile.hasStartScript ? "有" : "缺失"],
                    ["部署配置", persistedProjectProfile.hasDeployConfig ? "有" : "缺失"],
                  ].map(([label, value]) => (
                    <div className="grid grid-cols-[96px_1fr] border-b border-line px-5 py-3 even:bg-slate-50/60" key={label}>
                      <span className="text-muted">{label}</span>
                      <span className="font-medium text-slate-800">{value}</span>
                    </div>
                  ))}
                  <div className="border-b border-line px-5 py-3 md:col-span-2">
                    <p className="mb-2 text-muted">结构摘要</p>
                    <div className="max-h-48 overflow-auto rounded-md bg-slate-950 p-3 font-mono text-xs leading-5 text-slate-100">
                      {persistedProjectProfile.moduleStructure.slice(0, 60).map((item) => (
                        <div key={item}>{item}</div>
                      ))}
                    </div>
                  </div>
                </div>
              ) : (
                <div className="grid min-h-72 place-items-center p-8 text-center text-sm text-muted">
                  上传完整项目 zip 后，这里会显示项目名、技术栈、结构、缺口和当前阶段。后续 agent 写回结果会进入待确认建议。
                </div>
              )}
            </div>

            <div className="rounded-md border border-line bg-white shadow-panel">
              <div className="flex items-center justify-between gap-3 border-b border-line px-5 py-3">
                <div className="flex items-center gap-2">
                  <ListChecks className="h-4 w-4 text-brand" />
                  <h2 className="text-base font-semibold text-slate-950">待确认建议</h2>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    className="rounded-md border border-line bg-white px-3 py-1.5 text-sm font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-60"
                    disabled={ignoringSuggestions || selectedSuggestionIds.length === 0}
                    onClick={handleIgnoreSelectedSuggestions}
                    type="button"
                  >
                    {ignoringSuggestions ? "移除中..." : `忽略选中 ${selectedSuggestionIds.length}`}
                  </button>
                  <button
                    className="rounded-md bg-slate-950 px-3 py-1.5 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
                    disabled={applying || selectedSuggestionIds.length === 0}
                    onClick={handleApplySuggestions}
                    type="button"
                  >
                    {applying ? "采纳中..." : `采纳选中 ${selectedSuggestionIds.length}`}
                  </button>
                </div>
              </div>
              <div className="divide-y divide-line">
                {pendingSuggestions.map((suggestion) => {
                  const payloadPreview = formatPayloadPreview(suggestion.payload);
                  return (
                    <div className="grid grid-cols-[24px_72px_1fr_auto] gap-3 px-5 py-3 text-sm hover:bg-slate-50" key={suggestion.id}>
                      <input
                        checked={selectedSuggestionIds.includes(suggestion.id)}
                        disabled={suggestion.status !== "PENDING"}
                        onChange={() => toggleSuggestion(suggestion.id)}
                        type="checkbox"
                      />
                      <span className="text-xs text-muted">{suggestionLabels[suggestion.type]}</span>
                      <span>
                        <span className="block font-medium text-slate-900">{suggestion.title}</span>
                        <span className="mt-1 block leading-5 text-slate-600">{suggestion.reason}</span>
                        {payloadPreview ? (
                          <span className="mt-2 block rounded bg-slate-100 px-2 py-1 font-mono text-[11px] text-slate-600">
                            {payloadPreview}
                          </span>
                        ) : null}
                      </span>
                      <button
                        className="inline-flex h-8 w-8 items-center justify-center rounded border border-line bg-white text-slate-500 hover:bg-rose-50 hover:text-rose-700 disabled:opacity-50"
                        disabled={ignoringSuggestions || suggestion.status !== "PENDING"}
                        onClick={() => handleIgnoreSuggestion(suggestion.id)}
                        title="忽略这条建议"
                        type="button"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  );
                })}
                {pendingSuggestions.length === 0 ? (
                  <div className="p-8 text-center text-sm text-muted">暂无建议。先导入完整项目 zip。</div>
                ) : null}
              </div>
            </div>
          </section>

          <aside className="space-y-5">
            <div className="rounded-md border border-line bg-white p-5 shadow-panel">
              <div className="mb-3 flex items-center gap-2">
                <CheckCircle2 className="h-4 w-4 text-emerald-600" />
                <h2 className="text-base font-semibold text-slate-950">当前项目</h2>
              </div>
              <p className="font-semibold text-slate-950">{selectedProject?.name ?? "尚未创建"}</p>
              <p className="mt-2 text-sm leading-6 text-slate-600">
                {selectedProject?.description ?? "导入 zip 后自动创建项目档案。"}
              </p>
              {selectedProject?.techStack.length ? (
                <div className="mt-3 flex flex-wrap gap-2">
                  {selectedProject.techStack.map((item) => (
                    <span className="rounded bg-slate-100 px-2 py-1 text-xs text-slate-600" key={item}>{item}</span>
                  ))}
                </div>
              ) : null}
            </div>

            <div className="rounded-md border border-line bg-white p-5 shadow-panel">
              <div className="mb-3 flex items-center gap-2">
                <History className="h-4 w-4 text-brand" />
                <h2 className="text-base font-semibold text-slate-950">最近材料</h2>
              </div>
              <div className="space-y-3">
                {materials.slice(0, 5).map((material) => (
                  <div className="rounded-md border border-line p-3 text-sm" key={material.id}>
                    <p className="font-medium text-slate-800">{material.sourceType}</p>
                    <p className="mt-1 line-clamp-2 leading-5 text-muted">{material.normalizedSummary}</p>
                  </div>
                ))}
                {materials.length === 0 ? <p className="text-sm text-muted">暂无材料。</p> : null}
              </div>
            </div>

            <div className="rounded-md border border-line bg-white p-5 shadow-panel">
              <h2 className="mb-3 text-base font-semibold text-slate-950">最近演进</h2>
              {evolutionRecords[0] ? (
                <div className="text-sm leading-6 text-slate-600">
                  <p className="font-medium text-slate-900">{evolutionRecords[0].summary}</p>
                  <p className="mt-2 whitespace-pre-line">{evolutionRecords[0].nextSteps}</p>
                </div>
              ) : (
                <p className="text-sm text-muted">采纳建议后生成项目演进记录。</p>
              )}
            </div>
          </aside>
        </div>

        {error ? <div className="fixed bottom-5 left-1/2 z-50 -translate-x-1/2 rounded-md border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 shadow-panel">{error}</div> : null}
        {notice ? <div className="fixed bottom-5 left-1/2 z-50 -translate-x-1/2 rounded-md border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700 shadow-panel">{notice}</div> : null}
        {loading ? <div className="fixed inset-x-0 bottom-0 h-1 bg-brand" /> : null}
      </div>
    </AppShell>
  );
}

function buildPersistedProjectProfile(
  selectedProject: Project | undefined,
  materials: ProjectMaterial[],
  pendingSuggestions: AiSuggestion[],
): ProjectProfile | null {
  if (!selectedProject) {
    return null;
  }
  const zipMaterial = materials.find((material) => material.sourceType === "PROJECT_ZIP");
  const moduleStructure = zipMaterial ? parseZipDirectoryTree(zipMaterial.content) : [];
  if (!zipMaterial && moduleStructure.length === 0 && selectedProject.techStack.length === 0) {
    return null;
  }
  const lowerModules = moduleStructure.map((item) => item.toLowerCase());
  const nextTask = pendingSuggestions.find((suggestion) => suggestion.type === "CREATE_TASK");
  return {
    inferredProjectName: selectedProject.name,
    summary: selectedProject.description,
    techStack: selectedProject.techStack,
    moduleStructure,
    currentStage: selectedProject.status,
    hasReadme: lowerModules.some((item) => item.endsWith("readme.md")),
    hasTests: lowerModules.some((item) => item.includes("/test/") || item.startsWith("test/")),
    hasStartScript: lowerModules.some((item) => item.includes("package.json") || item.endsWith(".bat") || item.startsWith("start-")),
    hasDeployConfig: lowerModules.some((item) => item.endsWith("docker-compose.yml") || item.includes("docker/")),
    looksEmptyShell: moduleStructure.length > 0 && !lowerModules.some((item) => item.includes("/src/") || item.startsWith("src/")),
    mostImportantGap: nextTask?.title ?? "继续确认项目画像并规划下一轮开发",
  };
}

function parseZipDirectoryTree(content: string) {
  const lines = content.split(/\r?\n/);
  const treeStart = lines.findIndex((line) => line.trim() === "## Directory tree");
  if (treeStart < 0) {
    return [];
  }
  const result: string[] = [];
  for (const line of lines.slice(treeStart + 1)) {
    if (line.startsWith("## ")) {
      break;
    }
    const trimmed = line.trim();
    if (trimmed.startsWith("- ")) {
      result.push(trimmed.slice(2));
    }
  }
  return result;
}
