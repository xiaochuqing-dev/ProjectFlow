"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import {
  ArrowRight,
  Clipboard,
  FileCode2,
  FolderTree,
  History,
  RefreshCw,
  Save,
  ScanLine,
  Settings,
  ShieldAlert,
  Upload,
  X,
} from "lucide-react";
import { AppShell } from "@/components/AppShell";
import {
  Badge,
  Button,
  Card,
  EmptyState,
  PageContainer,
  ProjectContextBar,
  SectionHeader,
  Toast,
} from "@/components/ui";
import {
  deleteProject,
  createEvidenceBundle,
  draftProjectChangeFromEvidenceBundle,
  getProjectMemory,
  importProjectZip,
  listAiOutputs,
  listAiProviders,
  listAiSuggestions,
  listProjectChangeConflicts,
  listProjectChanges,
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
  writeProjectFlowProtocol,
  type AiProvider,
  type AiOutput,
  type AiSuggestion,
  type ChangeConflict,
  type EvidenceBundle,
  type Project,
  type ProjectAnalysis,
  type ProjectChange,
  type ProjectEvolutionRecord,
  type ProjectMaterial,
  type ProjectMemory,
  type TaskItem,
  type WorkSessionCandidate,
  type WorkSessionScanResult,
} from "@/lib/api";
import { buildProjectArchitecture, compactProjectPath, projectZipPaths } from "@/lib/project-insights";
import { projectFlowSteps, resolveProjectFlowState } from "@/lib/project-flow-state";
import { rememberSelectedProjectId, resolveSelectedProjectId } from "@/lib/project-selection";
import { readSession } from "@/lib/auth";
import { useProjectAnalysisJobs } from "@/lib/use-project-analysis-jobs";

type Step =
  | { kind: "no_project" }
  | { kind: "no_material" }
  | { kind: "no_path" }
  | { kind: "has_pending"; count: number }
  | { kind: "scan_updates" };

export default function DashboardPage() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [providers, setProviders] = useState<AiProvider[]>([]);
  const [materials, setMaterials] = useState<ProjectMaterial[]>([]);
  const [suggestions, setSuggestions] = useState<AiSuggestion[]>([]);
  const [evolutionRecords, setEvolutionRecords] = useState<ProjectEvolutionRecord[]>([]);
  const [evidenceBundles, setEvidenceBundles] = useState<EvidenceBundle[]>([]);
  const [changeConflicts, setChangeConflicts] = useState<ChangeConflict[]>([]);
  const [changes, setChanges] = useState<ProjectChange[]>([]);
  const [outputs, setOutputs] = useState<AiOutput[]>([]);
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [memory, setMemory] = useState<ProjectMemory | null>(null);
  const [selectedProjectId, setSelectedProjectId] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [showZipImport, setShowZipImport] = useState(false);
  const [showFlowGuide, setShowFlowGuide] = useState(false);
  const [statsFocus, setStatsFocus] = useState<"materials" | "changes" | "sessions" | "tasks" | "">("");
  const [projectPath, setProjectPath] = useState("");
  const [globalRule, setGlobalRule] = useState("");
  const [workSessionScan, setWorkSessionScan] = useState<WorkSessionScanResult | null>(null);
  const [scanWarnings, setScanWarnings] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [importing, setImporting] = useState(false);
  const [deletingProject, setDeletingProject] = useState(false);
  const [savingProjectPath, setSavingProjectPath] = useState(false);
  const [writingProtocol, setWritingProtocol] = useState(false);
  const [scanningWorkSessions, setScanningWorkSessions] = useState(false);
  const [syncingContext, setSyncingContext] = useState(false);
  const [scanningAgentResults, setScanningAgentResults] = useState(false);
  const [creatingEvidenceFor, setCreatingEvidenceFor] = useState("");
  const [draftingChangeFor, setDraftingChangeFor] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const selectedProject = useMemo(
    () => projects.find((project) => project.id === selectedProjectId),
    [projects, selectedProjectId],
  );
  const { jobs, jobError, enqueueProjectAnalysis } = useProjectAnalysisJobs(selectedProjectId);
  const latestProjectJob = jobs.find((job) => job.jobType === "PROJECT") ?? null;
  const analyzing = latestProjectJob?.status === "QUEUED" || latestProjectJob?.status === "RUNNING";
  const rawAnalysis = latestProjectJob?.status === "SUCCEEDED" ? latestProjectJob.projectResult : null;
  const analysisRejectedByNoise = rawAnalysis ? projectAnalysisContainsNoise(rawAnalysis) : false;
  const analysis = analysisRejectedByNoise ? null : rawAnalysis;
  const pendingSuggestions = suggestions.filter((suggestion) => suggestion.status === "PENDING");
  const pendingChanges = changes.filter((change) => change.status === "PENDING" || change.status === "EDITED");
  const pendingReviewCount = pendingSuggestions.length + pendingChanges.length;
  const configuredProvider = providers.find((provider) => provider.id && provider.apiKeyConfigured);
  const paths = useMemo(() => projectZipPaths(materials), [materials]);
  const architecture = useMemo(() => buildProjectArchitecture(paths), [paths]);
  const hasMaterials = materials.length > 0;
  const hasProjectZipMaterial = materials.some((material) => material.sourceType === "PROJECT_ZIP");
  const hasUsableProjectZip = paths.length > 0;
  const hasProjectPath = Boolean(projectPath.trim());
  const activeTasks = tasks.filter((task) => task.status !== "DONE");
  const analysisWarning = analysisRejectedByNoise
    ? "旧分析结果包含 .codex-run、old-git 或 Git 内部对象，已停止展示。请重新导入有效项目 zip，或点击重新分析生成干净画像。"
    : latestProjectJob?.status === "SUCCEEDED" && !rawAnalysis && latestProjectJob.errorMessage
      ? latestProjectJob.errorMessage
      : "";
  const todaySessions = workSessionScan?.sessions ?? [];
  const projectFlowState = resolveProjectFlowState({
    project: selectedProject,
    materials,
    memory,
    workSessions: todaySessions,
    evidenceBundles,
    pendingChanges,
    outputs,
  });

  // 当前下一步 —— 把原来的平铺改成单一焦点
  const currentStep: Step = useMemo(() => {
    if (!selectedProject) return { kind: "no_project" };
    if (!hasMaterials) return { kind: "no_material" };
    if (!hasProjectPath) return { kind: "no_path" };
    if (pendingReviewCount > 0) return { kind: "has_pending", count: pendingReviewCount };
    return { kind: "scan_updates" };
  }, [selectedProject, hasMaterials, hasProjectPath, pendingReviewCount]);
  const shouldShowZipImport = currentStep.kind === "no_project" || showZipImport;

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
      setChangeConflicts([]);
      setChanges([]);
      setOutputs([]);
      setTasks([]);
      setMemory(null);
      setProjectPath("");
      setWorkSessionScan(null);
      return;
    }

    try {
      const [materialItems, suggestionItems, evolutionItems, taskItems, memoryRecord, workSessions, bundles, conflicts, changeItems, outputItems] = await Promise.all([
        listProjectMaterials(session.accessToken, projectId),
        listAiSuggestions(session.accessToken, projectId),
        listProjectEvolutionRecords(session.accessToken, projectId),
        listTasks(session.accessToken, projectId),
        getProjectMemory(session.accessToken, projectId),
        listProjectWorkSessions(session.accessToken, projectId),
        listProjectEvidenceBundles(session.accessToken, projectId),
        listProjectChangeConflicts(session.accessToken, projectId),
        listProjectChanges(session.accessToken, projectId),
        listAiOutputs(session.accessToken, projectId),
      ]);
      setMaterials(materialItems);
      setSuggestions(suggestionItems);
      setEvolutionRecords(evolutionItems);
      setEvidenceBundles(bundles);
      setChangeConflicts(conflicts);
      setChanges(changeItems);
      setOutputs(outputItems);
      setTasks(taskItems);
      setMemory(memoryRecord);
      setProjectPath(memoryRecord.localProjectPath ?? "");
      setWorkSessionScan(workSessions.length ? workSessionListResult(projectId, memoryRecord.localProjectPath ?? "", workSessions) : null);
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
      const result = await importProjectZip(session.accessToken, file);
      setProjects((current) => {
        const exists = current.some((project) => project.id === result.project.id);
        return exists ? current.map((project) => (project.id === result.project.id ? result.project : project)) : [result.project, ...current];
      });
      rememberSelectedProjectId(result.project.id);
      setSelectedProjectId(result.project.id);
      setNotice("项目 zip 已导入，已生成基础画像和结构理解。");
      setFile(null);
      setShowZipImport(false);
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
      setScanWarnings(result.warnings);
      setNotice(result.sessions.length ? `已生成 ${result.sessions.length} 个今日变化候选。` : "今天暂未发现可归因的 Git 变化。");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "今日变化扫描失败");
    } finally {
      setScanningWorkSessions(false);
    }
  }

  async function handleCreateEvidenceBundle(sessionId: string) {
    const session = readSession();
    if (!session || !selectedProjectId) {
      return;
    }

    setCreatingEvidenceFor(sessionId);
    setError("");
    setNotice("");
    try {
      const bundle = await createEvidenceBundle(session.accessToken, sessionId);
      setNotice(`证据包已生成：${bundle.changedFiles} 个文件，下一步生成候选变更。`);
      await refreshProjectContext(selectedProjectId);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "证据包生成失败");
    } finally {
      setCreatingEvidenceFor("");
    }
  }

  async function handleDraftChangeFromEvidence(bundleId: string) {
    const session = readSession();
    if (!session || !selectedProjectId) {
      return;
    }

    setDraftingChangeFor(bundleId);
    setError("");
    setNotice("");
    try {
      await draftProjectChangeFromEvidenceBundle(session.accessToken, bundleId);
      setNotice("已从证据包生成候选变更。下一步进入变更审查，采纳后会写入项目档案和事实来源。");
      await refreshProjectContext(selectedProjectId);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "候选变更生成失败");
    } finally {
      setDraftingChangeFor("");
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

  return (
    <AppShell
      actions={
        <Link href="/settings">
          <Button variant="secondary" size="md">
            <Settings className="h-4 w-4" />
            设置模型
          </Button>
        </Link>
      }
      eyebrow="V3.1 Core"
      title="工作台"
    >
      <PageContainer>
        <ProjectContextBar
          projects={projects}
          selectedProjectId={selectedProjectId}
          onSelect={setSelectedProjectId}
          placeholder={projects.length === 0 ? "暂无项目，导入 zip 即可创建" : "选择项目"}
          leadingExtras={
            <>
              <Button
                variant="secondary"
                size="sm"
                onClick={() => setShowZipImport((current) => !current)}
                title="导入完整项目 zip，创建一个新的 ProjectFlow 项目。"
              >
                <Upload className="h-3.5 w-3.5" />
                添加项目
              </Button>
              {selectedProject ? <Badge label={selectedProject.status} /> : null}
              <Badge
                label={configuredProvider ? `模型：${configuredProvider.name}` : "未配置模型"}
                tone={configuredProvider ? "success" : "warning"}
                dot
              />
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setShowFlowGuide(true)}
                title="查看从导入项目到生成输出的完整独立上手流程。"
              >
                打开上手流程
              </Button>
              <button
                className="inline-flex items-center gap-1 text-xs font-medium text-danger-fg transition-colors hover:text-danger-fg/80 disabled:opacity-50"
                disabled={!selectedProjectId || deletingProject}
                onClick={handleDeleteSelectedProject}
                title="删除当前 ProjectFlow 项目记录，不删除本地真实源码文件夹。"
                type="button"
              >
                {deletingProject ? "删除中" : "删除项目"}
              </button>
            </>
          }
        />

        {/* 关键指标行 */}
        <section className="mb-6 grid grid-cols-2 gap-3 lg:grid-cols-4">
          <InteractiveStat
            active={statsFocus === "materials"}
            hint={hasUsableProjectZip ? `${paths.length} 个文件信号` : "暂无源码结构"}
            label="项目材料"
            onClick={() => setStatsFocus(statsFocus === "materials" ? "" : "materials")}
            value={materials.length}
          />
          <InteractiveStat
            active={statsFocus === "changes"}
            hint="去变更审查"
            label="待确认变更"
            onClick={() => setStatsFocus(statsFocus === "changes" ? "" : "changes")}
            tone={pendingReviewCount ? "warning" : "slate"}
            value={pendingReviewCount}
          />
          <InteractiveStat
            active={statsFocus === "sessions"}
            hint="Work Session"
            label="今日候选"
            onClick={() => setStatsFocus(statsFocus === "sessions" ? "" : "sessions")}
            tone={todaySessions.length ? "brand" : "slate"}
            value={todaySessions.length}
          />
          <InteractiveStat
            active={statsFocus === "tasks"}
            hint={memory?.currentStage || selectedProject?.status || "—"}
            label="进行中任务"
            onClick={() => setStatsFocus(statsFocus === "tasks" ? "" : "tasks")}
            value={activeTasks.length}
          />
        </section>

        {statsFocus ? (
          <StatsFocusPanel
            activeTasks={activeTasks}
            architecture={architecture}
            changes={pendingChanges}
            focus={statsFocus}
            paths={paths}
            suggestions={pendingSuggestions}
            workSessions={todaySessions}
          />
        ) : null}

        <FlowGuideDialog onClose={() => setShowFlowGuide(false)} open={showFlowGuide} state={projectFlowState} />

        <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_400px]">
          {/* 主列 */}
          <div className="space-y-5">
            {shouldShowZipImport ? (
              <ZipImportPanel
                file={file}
                setFile={setFile}
                importing={importing}
                onImportZip={handleImportZip}
                canClose={currentStep.kind !== "no_project"}
                onClose={() => {
                  setShowZipImport(false);
                  setFile(null);
                }}
              />
            ) : null}

            {/* 项目接入卡：只处理已选项目的本地路径、协议、扫描和同步 */}
            <ProjectAccessCard
              step={currentStep}
              hasSelectedProject={Boolean(selectedProjectId)}
              projectPath={projectPath}
              setProjectPath={setProjectPath}
              savingProjectPath={savingProjectPath}
              onSavePath={handleSaveProjectPath}
              writingProtocol={writingProtocol}
              onWriteProtocol={handleWriteProtocol}
              scanningAgentResults={scanningAgentResults}
              onScanAgentResults={handleScanAgentResults}
              syncingContext={syncingContext}
              onSyncContext={handleSyncContext}
              onCopyGlobalRule={handleCopyGlobalRule}
            />

            <EvidenceFlowPanel
              bundles={evidenceBundles}
              creatingEvidenceFor={creatingEvidenceFor}
              draftingChangeFor={draftingChangeFor}
              hasProjectPath={hasProjectPath}
              onCreateEvidenceBundle={handleCreateEvidenceBundle}
              onDraftChange={handleDraftChangeFromEvidence}
              onScanWorkSessions={handleScanWorkSessions}
              scanningWorkSessions={scanningWorkSessions}
              selectedProjectId={selectedProjectId}
              workSessions={todaySessions}
            />

            {/* 项目画像速览（极简，不再重复完整画像） */}
            <Card shadow="card">
              <SectionHeader
                eyebrow="项目画像"
                title={selectedProject?.name ?? "先导入项目"}
                actions={
                  selectedProjectId ? (
                    <div className="flex flex-wrap items-center gap-2">
                      <Button
                        variant="primary"
                        size="sm"
                        disabled={analyzing || !hasUsableProjectZip}
                        onClick={handleRunAnalysis}
                        title={hasUsableProjectZip ? "基于当前有效项目 zip 重新生成项目画像。" : "当前项目还没有可分析的源码、配置或文档目录结构。"}
                      >
                        {analyzing ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <FileCode2 className="h-3.5 w-3.5" />}
                        {latestProjectJob?.status === "QUEUED"
                          ? "等待分析"
                          : analyzing
                            ? "模型分析中"
                            : analysis
                              ? "重新分析"
                              : configuredProvider
                                ? "运行模型分析"
                                : "本地规则分析"}
                      </Button>
                      <Link className="inline-flex items-center gap-1 text-sm font-semibold text-brand hover:text-brand-hover" href="/project-intelligence">
                        完整画像 <ArrowRight className="h-4 w-4" />
                      </Link>
                    </div>
                  ) : null
                }
              />
              {selectedProject && hasMaterials ? (
                <div className="p-5">
                  <p className="max-w-4xl text-base leading-7 text-body">
                    {analysis?.summary || (!hasUsableProjectZip && hasProjectZipMaterial
                      ? "当前项目材料没有识别到有效源码、配置或文档结构；如果这是旧导入，建议重新导入完整项目 zip，或删除这个错误项目记录。"
                      : memory?.positioning || selectedProject.description || "已导入项目材料，正在使用本地规则生成基础项目画像。配置模型后可生成更完整的架构、风险和文件解释。")}
                  </p>
                  {analysis ? (
                    <div className="mt-4 rounded-card border border-line bg-surfaceAlt p-4">
                      <div className="mb-2 flex flex-wrap items-center gap-2">
                        <Badge label={analysis.analysisSource === "MODEL_ANALYSIS" ? "模型分析" : "本地规则"} tone={analysis.modelUsed ? "success" : "warning"} />
                        <span className="text-xs text-muted">{analysis.message}</span>
                      </div>
                      <p className="text-sm leading-6 text-body">{analysis.architecture}</p>
                      {analysis.risks.length ? (
                        <p className="mt-2 line-clamp-2 text-sm text-warning-fg">风险：{analysis.risks.join("；")}</p>
                      ) : null}
                    </div>
                  ) : null}
                  {latestProjectJob?.status === "FAILED" ? (
                    <p className="mt-3 rounded-field border border-danger/30 bg-danger-soft p-3 text-sm text-danger-fg">
                      分析任务失败：{latestProjectJob.errorMessage ?? "未知错误"}
                    </p>
                  ) : null}
                  {analysisWarning ? (
                    <p className="mt-3 rounded-field border border-warning/30 bg-warning-soft p-3 text-sm leading-6 text-warning-fg">
                      {analysisWarning}
                    </p>
                  ) : null}
                  {jobError ? <p className="mt-3 text-sm text-warning-fg">{jobError}</p> : null}
                  <div className="mt-5 grid gap-3 sm:grid-cols-3">
                    <MiniFact label="工程阶段" value={memory?.currentStage || selectedProject.status} />
                    <MiniFact label="进行中" value={memory?.inProgressCapabilities || activeTasks[0]?.title || "等待确认"} />
                    <MiniFact label="风险" value={memory?.currentRisks || "暂无已确认风险"} />
                  </div>
                </div>
              ) : (
                <EmptyState
                  icon={<ShieldAlert className="h-5 w-5" />}
                  title={selectedProject ? "先导入项目材料" : "先创建或导入项目"}
                  description={
                    selectedProject
                      ? "当前项目还没有 zip 或 agent 材料。导入后才能生成项目画像、架构理解和文件解释。"
                      : "ProjectFlow 需要先拿到真实项目，才会展示画像、风险和文件理解。请从下方导入完整项目 zip。"
                  }
                />
              )}
            </Card>

          </div>

          {/* 侧列 */}
          <aside className="space-y-5">
            <ArchitectureQuickEntry architecture={architecture} hasUsableProjectZip={hasUsableProjectZip} paths={paths} selectedProjectId={selectedProjectId} />

            {/* 最近活动流：把分散的变化/建议/候选压缩成只读摘要流 */}
            <Card shadow="card">
              <SectionHeader
                eyebrow="动态"
                title="最近活动"
                icon={<History className="h-4 w-4" />}
              />
              <ActivityFeed
                evolutionRecords={evolutionRecords}
                pendingSuggestions={pendingSuggestions}
                workSessions={todaySessions}
                hasProjectPath={hasProjectPath}
                onScanWorkSessions={handleScanWorkSessions}
                scanningWorkSessions={scanningWorkSessions}
                selectedProjectId={selectedProjectId}
              />
            </Card>

            {/* 模型状态 */}
            <Card shadow="card" padding="md">
              <p className="text-sm font-semibold text-ink">模型状态</p>
              <p className="mt-2 text-sm leading-6 text-muted">
                {configuredProvider ? "已配置模型。深度分析结果必须经用户确认后写入项目档案。" : "未配置 API。文件理解页会显示本地规则解释，深度分析入口会引导到设置页。"}
              </p>
              {!configuredProvider ? (
                <Link className="mt-3 inline-flex" href="/settings">
                  <Button variant="secondary" size="sm">
                    去设置模型 <ArrowRight className="h-3.5 w-3.5" />
                  </Button>
                </Link>
              ) : null}
            </Card>

            {scanWarnings.length ? (
              <div className="rounded-card border border-warning/30 bg-warning-soft p-4 text-xs leading-5 text-warning-fg">
                {scanWarnings.map((warning) => (
                  <div key={warning}>{warning}</div>
                ))}
              </div>
            ) : null}

            {changeConflicts.length ? (
              <Card shadow="card" padding="md">
                <p className="text-sm font-semibold text-warning-fg">冲突待审查</p>
                <p className="mt-2 text-sm leading-6 text-warning-fg">
                  检测到 {changeConflicts.length} 个文件级证据重叠，需要确认是连续修改还是冲突。
                </p>
                <div className="mt-3 space-y-2">
                  {changeConflicts.slice(0, 3).map((conflict) => (
                    <div className="rounded-field bg-warning-soft/60 px-3 py-2 text-xs text-warning-fg" key={conflict.id}>
                      {conflict.filePath} · {conflict.severity}
                    </div>
                  ))}
                </div>
              </Card>
            ) : null}
          </aside>
        </div>
      </PageContainer>

      <Toast error={error} notice={notice} />
      {loading ? <div className="fixed inset-x-0 bottom-0 h-1 bg-brand" /> : null}
    </AppShell>
  );
}

/* ------------------------------------------------------------------ */
/* 入口与解释层                                                        */
/* ------------------------------------------------------------------ */

function InteractiveStat({
  active,
  hint,
  label,
  onClick,
  tone = "slate",
  value,
}: {
  active: boolean;
  hint?: string;
  label: string;
  onClick: () => void;
  tone?: "slate" | "brand" | "warning";
  value: number;
}) {
  const toneClass = {
    slate: "hover:border-lineStrong hover:bg-surfaceAlt",
    brand: "hover:border-brand/40 hover:bg-brand-soft",
    warning: "hover:border-warning/40 hover:bg-warning-soft",
  }[tone];
  return (
    <button
      className={`group min-w-0 rounded-card border bg-elevated p-4 text-left transition duration-150 hover:-translate-y-0.5 hover:shadow-card ${
        active ? "border-brand bg-brand-soft shadow-card" : `border-line ${toneClass}`
      }`}
      onClick={onClick}
      type="button"
    >
      <div className="flex items-center justify-between gap-2">
        <p className="text-xs text-muted">{label}</p>
        <ArrowRight className={`h-3.5 w-3.5 transition ${active ? "text-brand" : "text-muted group-hover:text-brand"}`} />
      </div>
      <p className="mt-1.5 text-xl font-semibold leading-7 text-ink">{value}</p>
      {hint ? <p className="mt-0.5 text-xs text-muted">{hint}</p> : null}
    </button>
  );
}

function StatsFocusPanel({
  activeTasks,
  architecture,
  changes,
  focus,
  paths,
  suggestions,
  workSessions,
}: {
  activeTasks: TaskItem[];
  architecture: ReturnType<typeof buildProjectArchitecture>;
  changes: ProjectChange[];
  focus: "materials" | "changes" | "sessions" | "tasks";
  paths: string[];
  suggestions: AiSuggestion[];
  workSessions: WorkSessionCandidate[];
}) {
  const content = {
    materials: {
      title: "项目材料",
      body: paths.length ? `${architecture.shapeLabel}，识别 ${paths.length} 个文件信号。` : "暂无可用项目文件信号。",
      action: "查看架构入口",
      href: "",
      items: [architecture.entrypoints[0]?.path, architecture.coreModules[0]?.path, architecture.dependencySignals[0]?.path].filter(Boolean) as string[],
    },
    changes: {
      title: "待确认变更",
      body: suggestions.length || changes.length ? "这些候选需要审查后才会进入项目档案。" : "暂无待确认变更。",
      action: "去变更审查",
      href: "/tasks",
      items: [...suggestions.map((item) => item.title), ...changes.map((item) => item.title)].slice(0, 3),
    },
    sessions: {
      title: "今日候选",
      body: workSessions.length ? "这些 Git 变化可继续生成证据包。" : "暂无今日 Git 变化候选。",
      action: "刷新变化",
      href: "",
      items: workSessions.slice(0, 3).map((item) => item.taskIntent || `${item.changedFiles} 个文件变化`),
    },
    tasks: {
      title: "进行中任务",
      body: activeTasks.length ? "这些任务会参与每日回顾和成果输出。" : "暂无进行中任务。",
      action: "查看任务",
      href: "/tasks",
      items: activeTasks.slice(0, 3).map((item) => item.title),
    },
  }[focus];

  return (
    <Card shadow="card" padding="md" className="mb-6 border-brand/20 bg-brand-soft/40">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <p className="text-xs font-semibold text-brand">已选入口</p>
          <h3 className="mt-1 text-lg font-semibold text-ink">{content.title}</h3>
          <p className="mt-1 text-sm leading-6 text-body">{content.body}</p>
          <div className="mt-3 flex flex-wrap gap-2">
            {content.items.length ? content.items.map((item) => (
              <span className="max-w-full break-all rounded-field bg-elevated px-3 py-1.5 font-mono text-xs text-muted" key={item}>
                {item.includes("/") ? compactProjectPath(item) : item}
              </span>
            )) : <span className="rounded-field bg-elevated px-3 py-1.5 text-xs text-muted">无</span>}
          </div>
        </div>
        {content.href ? (
          <Link href={content.href}>
            <Button variant="primary" size="sm">
              {content.action} <ArrowRight className="h-3.5 w-3.5" />
            </Button>
          </Link>
        ) : null}
      </div>
    </Card>
  );
}

function FlowGuideDialog({
  onClose,
  open,
  state,
}: {
  onClose: () => void;
  open: boolean;
  state: ReturnType<typeof resolveProjectFlowState>;
}) {
  if (!open) {
    return null;
  }
  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-ink/35 p-4" role="dialog" aria-modal="true" aria-label="独立上手流程">
      <div className="max-h-[86vh] w-full max-w-5xl overflow-auto rounded-card border border-line bg-elevated shadow-cardLg">
        <div className="flex flex-wrap items-start justify-between gap-3 border-b border-line p-5">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-brand">独立上手流程</p>
            <h2 className="mt-2 text-2xl font-semibold text-ink">{state.title}</h2>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-body">{state.description}</p>
          </div>
          <Button variant="ghost" size="sm" onClick={onClose} aria-label="关闭上手流程">
            <X className="h-4 w-4" />
            关闭
          </Button>
        </div>
        <div className="space-y-5 p-5">
          <p className="rounded-field bg-surfaceAlt px-4 py-3 text-sm leading-6 text-muted">{state.helper}</p>
          <FlowStepStrip state={state} />
          {state.primaryHref ? (
            <Link className="inline-flex" href={state.primaryHref} onClick={onClose}>
              <Button variant="primary" size="md">
                下一步：{state.primaryAction} <ArrowRight className="h-4 w-4" />
              </Button>
            </Link>
          ) : (
            <div className="inline-flex rounded-field bg-brand-soft px-4 py-2 text-sm font-semibold text-brand">
              下一步：{state.primaryAction}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function FlowStepStrip({ state }: { state: ReturnType<typeof resolveProjectFlowState> }) {
  const colors = [
    "bg-emerald-50 border-emerald-100 text-emerald-900",
    "bg-cyan-50 border-cyan-100 text-cyan-900",
    "bg-blue-50 border-blue-100 text-blue-900",
    "bg-amber-50 border-amber-100 text-amber-900",
    "bg-indigo-50 border-indigo-100 text-indigo-900",
    "bg-slate-100 border-slate-200 text-slate-800",
  ];
  return (
    <div className="grid gap-3 md:grid-cols-3 xl:grid-cols-6">
      {projectFlowSteps.map((step, index) => {
        const done = state.completedSteps.includes(step.key);
        const active = state.nextStep === step.key && !done;
        return (
          <div className={`rounded-card border p-4 ${colors[index % colors.length]} ${active ? "ring-2 ring-brand/40" : ""}`} key={step.key}>
            <div className="flex items-center gap-2">
              <span className={`grid h-7 w-7 place-items-center rounded-full text-xs font-semibold ${done ? "bg-success text-white" : active ? "bg-brand text-white" : "bg-white/80 text-ink"}`}>
                {index + 1}
              </span>
              <p className="text-sm font-semibold">{step.label}</p>
            </div>
            <p className="mt-3 text-xs leading-5 opacity-80">{step.description}</p>
          </div>
        );
      })}
    </div>
  );
}

function ArchitectureQuickEntry({
  architecture,
  hasUsableProjectZip,
  paths,
  selectedProjectId,
}: {
  architecture: ReturnType<typeof buildProjectArchitecture>;
  hasUsableProjectZip: boolean;
  paths: string[];
  selectedProjectId: string;
}) {
  if (!hasUsableProjectZip) {
    return (
      <Card shadow="card" padding="md">
        <p className="text-xs font-semibold text-muted">架构入口</p>
        <p className="mt-2 text-sm leading-6 text-muted">导入完整项目 zip 后，这里会显示项目形态、入口、核心和依赖数量。</p>
      </Card>
    );
  }
  const facts = [
    ["形态", architecture.shapeLabel],
    ["入口", `${architecture.entrypoints.length} 个`],
    ["核心", `${architecture.coreModules.length} 个`],
    ["依赖", `${architecture.dependencySignals.length} 个`],
  ];
  return (
    <Card shadow="card" padding="none" className="overflow-hidden border-brand/20">
      <SectionHeader
        eyebrow="架构入口"
        title={architecture.summary || architecture.shapeLabel}
        icon={<FolderTree className="h-4 w-4" />}
        actions={
          <Link href={`/projects/${selectedProjectId}/files`}>
            <Button variant="primary" size="sm">
              完整结构 <ArrowRight className="h-3.5 w-3.5" />
            </Button>
          </Link>
        }
      />
      <div className="grid grid-cols-2 gap-2 p-4">
        {facts.map(([label, value]) => (
          <div className="rounded-field border border-line bg-surfaceAlt px-3 py-2" key={label}>
            <p className="text-xs text-muted">{label}</p>
            <p className="mt-1 truncate text-sm font-semibold text-ink">{value}</p>
          </div>
        ))}
      </div>
      <div className="border-t border-line px-4 py-3 text-xs text-muted">
        {paths.length} 个文件信号 · {architecture.shapeTags.join(" / ")}
      </div>
    </Card>
  );
}

/* ------------------------------------------------------------------ */
/* 独立 zip 导入入口                                                    */
/* ------------------------------------------------------------------ */

type ZipImportPanelProps = {
  file: File | null;
  setFile: (file: File | null) => void;
  importing: boolean;
  onImportZip: (event: FormEvent<HTMLFormElement>) => void;
  canClose: boolean;
  onClose: () => void;
};

function ZipImportPanel(props: ZipImportPanelProps) {
  return (
    <Card shadow="card" padding="none" className="overflow-hidden border-brand/20">
      <form className="p-5" onSubmit={props.onImportZip}>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <div className="flex items-center gap-2">
              <Upload className="h-4 w-4 text-brand" />
              <h3 className="text-sm font-semibold text-ink">添加项目</h3>
            </div>
            <p className="mt-1 text-xs leading-5 text-muted">
              选择完整项目 zip，创建新的项目画像和文件结构理解。
            </p>
          </div>
          {props.canClose ? (
            <Button variant="ghost" size="sm" onClick={props.onClose} type="button">
              收起
            </Button>
          ) : null}
        </div>
        <div className="mt-4 grid gap-3 lg:grid-cols-[minmax(0,1fr)_220px]">
          <label className="block rounded-field border border-dashed border-lineStrong bg-surfaceAlt p-4 transition hover:border-brand">
            <span className="mb-2 block text-sm font-medium text-body">选择项目 zip</span>
            <input
              accept=".zip,application/zip"
              className="w-full text-sm text-muted"
              onChange={(event) => props.setFile(event.target.files?.[0] ?? null)}
              type="file"
            />
          </label>
          <Button variant="primary" type="submit" fullWidth disabled={!props.file || props.importing}>
            {props.importing ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />}
            {props.importing ? "导入中..." : "导入并建档"}
          </Button>
        </div>
      </form>
    </Card>
  );
}

/* ------------------------------------------------------------------ */
/* 项目接入卡：本地项目接入                                             */
/* ------------------------------------------------------------------ */

type ProjectAccessCardProps = {
  step: Step;
  hasSelectedProject: boolean;
  projectPath: string;
  setProjectPath: (value: string) => void;
  savingProjectPath: boolean;
  onSavePath: () => void;
  writingProtocol: boolean;
  onWriteProtocol: () => void;
  scanningAgentResults: boolean;
  onScanAgentResults: () => void;
  syncingContext: boolean;
  onSyncContext: () => void;
  onCopyGlobalRule: () => void;
};

function ProjectAccessCard(props: ProjectAccessCardProps) {
  const { step, hasSelectedProject } = props;
  const hint = accessHint(step, hasSelectedProject);

  return (
    <Card shadow="card" padding="none" className="overflow-hidden">
      {/* 顶部状态条：轻量提示当前阶段，但不门控导入入口 */}
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-line bg-brand-soft px-5 py-3">
        <div className="flex items-center gap-2">
          <Badge label="项目接入" tone="brand" dot />
          <p className="text-sm text-body">{hint.title}</p>
        </div>
        {hint.cta && hint.ctaHref ? (
          <Link className="inline-flex items-center gap-1 text-sm font-semibold text-brand hover:text-brand-hover" href={hint.ctaHref}>
            {hint.cta} <ArrowRight className="h-3.5 w-3.5" />
          </Link>
        ) : null}
      </div>

      <div className="p-5">
          <div className="flex items-center gap-2">
            <FolderTree className="h-4 w-4 text-brand" />
            <h3 className="text-sm font-semibold text-ink">本地项目接入</h3>
          </div>
          <p className="mt-1 text-xs leading-5 text-muted">
            绑定真实项目文件夹后，才能扫描 Agent 结果、读取 Git evidence、同步上下文。不会扫描用户主目录。
          </p>
          <input
            className="mt-3 h-10 w-full rounded-field border border-line bg-elevated px-3 text-sm outline-none transition focus:border-brand focus-visible:shadow-focus disabled:cursor-not-allowed disabled:bg-surfaceAlt disabled:text-muted"
            onChange={(event) => props.setProjectPath(event.target.value)}
            placeholder={hasSelectedProject ? "真实项目文件夹路径" : "先在项目下拉选择一个项目"}
            value={props.projectPath}
            disabled={!hasSelectedProject}
          />
          <div className="mt-3 grid gap-2 sm:grid-cols-2">
            <Button
              variant="primary"
              size="sm"
              disabled={!hasSelectedProject || !props.projectPath.trim() || props.savingProjectPath}
              onClick={props.onSavePath}
              title="只记录本地项目根目录，切换项目和刷新页面后继续复用，不写入目标项目文件。"
            >
              {props.savingProjectPath ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <Save className="h-3.5 w-3.5" />}
              保存路径
            </Button>
            <Button
              variant="secondary"
              size="sm"
              disabled={!hasSelectedProject || !props.projectPath.trim() || props.writingProtocol}
              onClick={props.onWriteProtocol}
              title="在目标项目生成 ProjectFlow 协议、上下文目录和结果收件箱，供 Agent 按规则写回结果。"
            >
              {props.writingProtocol ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <FileCode2 className="h-3.5 w-3.5" />}
              写入/刷新协议
            </Button>
            <Button
              variant="secondary"
              size="sm"
              disabled={!hasSelectedProject || !props.projectPath.trim() || props.scanningAgentResults}
              onClick={props.onScanAgentResults}
              title="读取目标项目的 ProjectFlow 结果收件箱，把 Agent 写回内容转成待审查变更。"
            >
              {props.scanningAgentResults ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <ScanLine className="h-3.5 w-3.5" />}
              扫描 Agent Result
            </Button>
            <Button
              variant="secondary"
              size="sm"
              disabled={!hasSelectedProject || !props.projectPath.trim() || props.syncingContext}
              onClick={props.onSyncContext}
              title="把已经采纳和确认的项目档案写回目标项目上下文目录，供后续 Agent 读取。"
            >
              {props.syncingContext ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <FolderTree className="h-3.5 w-3.5" />}
              同步确认上下文
            </Button>
            <Button
              variant="secondary"
              size="sm"
              disabled={!hasSelectedProject}
              onClick={props.onCopyGlobalRule}
              title="复制给其他 Agent 使用的通用规则，让它们按 ProjectFlow 协议输出结果。"
            >
              <Clipboard className="h-3.5 w-3.5" />
              复制规则
            </Button>
          </div>
      </div>
    </Card>
  );
}

function accessHint(step: Step, hasSelectedProject: boolean): { title: string; cta?: string; ctaHref?: string } {
  switch (step.kind) {
    case "no_project":
      return { title: "还没有项目 —— 导入项目 zip 即可创建第一个项目。", cta: undefined };
    case "no_material":
      return { title: "当前项目还没有可分析的材料，导入完整 zip 后生成画像。", cta: undefined };
    case "no_path":
      return { title: "绑定本地项目文件夹路径后，才能扫描 Agent 结果与今日变化。", cta: undefined };
    case "has_pending":
      return { title: `当前有 ${step.count} 条待确认变更。`, cta: "去变更审查", ctaHref: "/tasks" };
    case "scan_updates":
      return { title: "项目已就绪，扫描 Agent Result 或刷新今日变化获取新候选。", cta: undefined };
    default:
      return { title: hasSelectedProject ? "项目接入就绪。" : "导入项目 zip 开始。" };
  }
}

/* ------------------------------------------------------------------ */
/* 今日变化闭环                                                        */
/* ------------------------------------------------------------------ */

type EvidenceFlowPanelProps = {
  workSessions: WorkSessionCandidate[];
  bundles: EvidenceBundle[];
  hasProjectPath: boolean;
  selectedProjectId: string;
  scanningWorkSessions: boolean;
  creatingEvidenceFor: string;
  draftingChangeFor: string;
  onScanWorkSessions: () => void;
  onCreateEvidenceBundle: (sessionId: string) => void;
  onDraftChange: (bundleId: string) => void;
};

function EvidenceFlowPanel(props: EvidenceFlowPanelProps) {
  const bundleBySession = new Map(props.bundles.map((bundle) => [bundle.workSessionId, bundle]));
  const visibleSessions = props.workSessions.slice(0, 3);
  const orphanBundles = props.bundles
    .filter((bundle) => !bundleBySession.has(bundle.workSessionId) || !props.workSessions.some((session) => session.sessionId === bundle.workSessionId))
    .slice(0, Math.max(0, 3 - visibleSessions.length));

  return (
    <Card shadow="card" padding="none" className="overflow-hidden">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line px-5 py-4">
        <div>
          <div className="flex items-center gap-2">
            <History className="h-4 w-4 text-brand" />
            <h3 className="text-sm font-semibold text-ink">今日变化闭环</h3>
          </div>
          <p className="mt-1 text-xs leading-5 text-muted">开发后回来刷新变化，把 Git evidence 变成可审查事实，采纳后进入项目档案和输出来源。</p>
        </div>
        <Button
          variant="secondary"
          size="sm"
          disabled={!props.hasProjectPath || props.scanningWorkSessions}
          onClick={props.onScanWorkSessions}
          title={props.hasProjectPath ? "读取已绑定项目的 Git 变化，生成今日工作候选。" : "先保存真实项目文件夹路径。"}
        >
          {props.scanningWorkSessions ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <ScanLine className="h-3.5 w-3.5" />}
          刷新变化
        </Button>
      </div>

      {visibleSessions.length || orphanBundles.length ? (
        <div className="divide-y divide-line">
          {visibleSessions.map((session) => (
            <EvidenceFlowRow
              bundle={bundleBySession.get(session.sessionId)}
              creatingEvidenceFor={props.creatingEvidenceFor}
              draftingChangeFor={props.draftingChangeFor}
              key={session.sessionId}
              onCreateEvidenceBundle={props.onCreateEvidenceBundle}
              onDraftChange={props.onDraftChange}
              selectedProjectId={props.selectedProjectId}
              session={session}
            />
          ))}
          {orphanBundles.map((bundle) => (
            <EvidenceFlowRow
              bundle={bundle}
              creatingEvidenceFor={props.creatingEvidenceFor}
              draftingChangeFor={props.draftingChangeFor}
              key={bundle.id}
              onCreateEvidenceBundle={props.onCreateEvidenceBundle}
              onDraftChange={props.onDraftChange}
              selectedProjectId={props.selectedProjectId}
            />
          ))}
        </div>
      ) : (
        <div className="p-5 text-sm leading-6 text-muted">
          {props.hasProjectPath
            ? "还没有今日变化。点击刷新后，如果当前项目有 Git 改动或提交，这里会出现候选工作会话。"
            : "先保存本地项目路径，ProjectFlow 才能从这个项目读取 Git evidence。"}
        </div>
      )}
    </Card>
  );
}

function EvidenceFlowRow({
  bundle,
  creatingEvidenceFor,
  draftingChangeFor,
  onCreateEvidenceBundle,
  onDraftChange,
  selectedProjectId,
  session,
}: {
  bundle?: EvidenceBundle;
  creatingEvidenceFor: string;
  draftingChangeFor: string;
  onCreateEvidenceBundle: (sessionId: string) => void;
  onDraftChange: (bundleId: string) => void;
  selectedProjectId: string;
  session?: WorkSessionCandidate;
}) {
  const sessionId = session?.sessionId ?? bundle?.workSessionId ?? "";
  const status = evidenceStatus(bundle);
  const files = (bundle?.files.length ? bundle.files : session?.files ?? []).slice(0, 2);
  const title = bundle?.taskIntent || session?.taskIntent || "今日工作候选";
  const metrics = bundle
    ? `${bundle.changedFiles} 文件 · +${bundle.addedLines}/-${bundle.deletedLines}`
    : session
      ? `${session.changedFiles} 文件 · +${session.addedLines}/-${session.deletedLines}`
      : "等待证据";

  return (
    <article className="grid gap-3 p-4 md:grid-cols-[minmax(0,1fr)_auto]">
      <div className="min-w-0">
        <div className="mb-2 flex flex-wrap items-center gap-2">
          <Badge label={status.label} tone={status.tone} />
          <span className="text-xs text-muted">{metrics}</span>
        </div>
        <p className="line-clamp-2 text-sm font-semibold leading-6 text-ink">{title}</p>
        <div className="mt-2 flex flex-wrap gap-2">
          {files.length ? files.map((file) => (
            <span className="max-w-full break-all rounded-field bg-surfaceAlt px-2 py-1 font-mono text-xs text-muted" key={file}>
              {compactProjectPath(file)}
            </span>
          )) : <span className="text-xs text-muted">暂无文件证据</span>}
        </div>
      </div>
      <EvidenceFlowAction
        bundle={bundle}
        creating={creatingEvidenceFor === sessionId}
        drafting={Boolean(bundle && draftingChangeFor === bundle.id)}
        onCreateEvidenceBundle={() => sessionId && onCreateEvidenceBundle(sessionId)}
        onDraftChange={() => bundle && onDraftChange(bundle.id)}
        selectedProjectId={selectedProjectId}
      />
    </article>
  );
}

function EvidenceFlowAction({
  bundle,
  creating,
  drafting,
  onCreateEvidenceBundle,
  onDraftChange,
  selectedProjectId,
}: {
  bundle?: EvidenceBundle;
  creating: boolean;
  drafting: boolean;
  onCreateEvidenceBundle: () => void;
  onDraftChange: () => void;
  selectedProjectId: string;
}) {
  if (!bundle) {
    return (
      <Button variant="secondary" size="sm" disabled={creating} onClick={onCreateEvidenceBundle}>
        {creating ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <FileCode2 className="h-3.5 w-3.5" />}
        生成证据包
      </Button>
    );
  }
  if (bundle.nextAction === "GENERATE_CHANGE") {
    return (
      <Button variant="primary" size="sm" disabled={drafting} onClick={onDraftChange}>
        {drafting ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <ArrowRight className="h-3.5 w-3.5" />}
        生成候选变更
      </Button>
    );
  }
  if (bundle.nextAction === "REVIEW_CHANGE") {
    return (
      <Link href="/tasks">
        <Button variant="primary" size="sm">
          去变更审查 <ArrowRight className="h-3.5 w-3.5" />
        </Button>
      </Link>
    );
  }
  if (bundle.nextAction === "VIEW_MEMORY") {
    return (
      <Link href="/project-intelligence">
        <Button variant="secondary" size="sm">
          看项目档案 <ArrowRight className="h-3.5 w-3.5" />
        </Button>
      </Link>
    );
  }
  return selectedProjectId ? (
    <Link href="/ai-review">
      <Button variant="secondary" size="sm">
        生成输出 <ArrowRight className="h-3.5 w-3.5" />
      </Button>
    </Link>
  ) : null;
}

function evidenceStatus(bundle?: EvidenceBundle): { label: string; tone: "brand" | "warning" | "success" | "slate" } {
  if (!bundle) {
    return { label: "待生成证据", tone: "slate" };
  }
  if (bundle.status === "READY_FOR_CHANGE") {
    return { label: "证据包就绪", tone: "brand" };
  }
  if (bundle.status === "CHANGE_DRAFTED") {
    return { label: "待审查", tone: "warning" };
  }
  if (bundle.status === "CHANGE_ACCEPTED") {
    return { label: "已入档案", tone: "success" };
  }
  return { label: "已归档", tone: "slate" };
}

/* ------------------------------------------------------------------ */
/* 最近活动流                                                          */
/* ------------------------------------------------------------------ */

type ActivityFeedProps = {
  evolutionRecords: ProjectEvolutionRecord[];
  pendingSuggestions: AiSuggestion[];
  workSessions: WorkSessionCandidate[];
  hasProjectPath: boolean;
  onScanWorkSessions: () => void;
  scanningWorkSessions: boolean;
  selectedProjectId: string;
};

function ActivityFeed(props: ActivityFeedProps) {
  type FeedItem = {
    id: string;
    badge: React.ReactNode;
    badgeTone: "brand" | "warning" | "success" | "slate";
    title: string;
    href?: string;
    hrefLabel?: string;
  };

  const items: FeedItem[] = [
    ...props.pendingSuggestions.slice(0, 3).map<FeedItem>((suggestion) => ({
      id: `sug-${suggestion.id}`,
      badge: "待确认",
      badgeTone: "warning",
      title: suggestion.title,
      href: "/tasks",
      hrefLabel: "审查",
    })),
    ...props.evolutionRecords.slice(0, 3).map<FeedItem>((record) => ({
      id: `evo-${record.id}`,
      badge: "已采纳",
      badgeTone: "success",
      title: record.summary,
    })),
    ...props.workSessions.slice(0, 3).map<FeedItem>((session) => ({
      id: `ws-${session.sessionId}`,
      badge: session.agentType === "UNKNOWN" ? "今日候选" : session.agentType,
      badgeTone: "brand",
      title: session.taskIntent || "未补充任务意图",
      href: `/work-sessions/${session.sessionId}?projectId=${props.selectedProjectId}`,
      hrefLabel: "查看",
    })),
  ];

  if (items.length === 0) {
    return (
      <div className="p-5">
        <p className="text-sm leading-6 text-muted">
          {props.hasProjectPath
            ? "点击下方刷新，ProjectFlow 会读取已绑定项目的今日 Git evidence 生成可审查候选。"
            : "先绑定真实项目路径，ProjectFlow 才能读取 Git evidence。不会扫描用户主目录或全局 Agent 日志。"}
        </p>
        <Button
          variant="secondary"
          size="sm"
          className="mt-3"
          disabled={!props.hasProjectPath || props.scanningWorkSessions}
          onClick={props.onScanWorkSessions}
        >
          {props.scanningWorkSessions ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <ScanLine className="h-3.5 w-3.5" />}
          刷新变化
        </Button>
      </div>
    );
  }

  return (
    <div>
      <div className="border-b border-line px-5 py-3">
        <Button
          variant="secondary"
          size="sm"
          fullWidth
          disabled={!props.hasProjectPath || props.scanningWorkSessions}
          onClick={props.onScanWorkSessions}
        >
          {props.scanningWorkSessions ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <ScanLine className="h-3.5 w-3.5" />}
          刷新今日变化
        </Button>
      </div>
      <div className="divide-y divide-line">
        {items.map((item) => (
          <article className="flex items-start gap-3 p-4" key={item.id}>
            <div className="min-w-0 flex-1">
              <div className="mb-1.5 flex items-center gap-2">
                <Badge label={item.badge} tone={item.badgeTone} />
              </div>
              <p className="line-clamp-2 text-sm leading-6 text-body">{item.title}</p>
            </div>
            {item.href ? (
              <Link className="shrink-0" href={item.href}>
                <Button variant="ghost" size="sm">
                  {item.hrefLabel} <ArrowRight className="h-3.5 w-3.5" />
                </Button>
              </Link>
            ) : null}
          </article>
        ))}
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* 局部小组件                                                          */
/* ------------------------------------------------------------------ */

function MiniFact({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0 rounded-field border border-line bg-surfaceAlt p-3">
      <p className="text-xs text-muted">{label}</p>
      <p className="mt-1 break-all text-sm font-semibold leading-5 text-ink">{value}</p>
    </div>
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
