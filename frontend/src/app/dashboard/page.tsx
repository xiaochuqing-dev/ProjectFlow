"use client";

import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import {
  ArrowRight,
  FileCode2,
  History,
  RefreshCw,
  ScanLine,
  Settings,
  ShieldAlert,
  Upload,
} from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { ActivityFeed } from "@/components/dashboard/ActivityFeed";
import { ArchitectureQuickEntry } from "@/components/dashboard/ArchitectureQuickEntry";
import { PendingChangesPanel } from "@/components/dashboard/PendingChangesPanel";
import { FlowGuideDialog } from "@/components/dashboard/FlowGuideDialog";
import { InteractiveStat, MiniFact, StatsFocusPanel } from "@/components/dashboard/DashboardStats";
import { ProjectAccessCard, ZipImportPanel } from "@/components/dashboard/ProjectAccessCard";
import { OutputOptionsCard } from "@/components/dashboard/OutputOptionsCard";
import type { DashboardStep } from "@/components/dashboard/types";
import {
  Badge,
  Button,
  Card,
  EmptyState,
  InfoBubble,
  PageContainer,
  ProjectContextBar,
  SectionHeader,
  Toast,
} from "@/components/ui";
import {
  deleteProject,
  getProjectMemory,
  getProjectGitHubStatus,
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
  scanProjectFlowAgentResults,
  syncProjectContext,
  writeProjectFlowProtocol,
  type AiProvider,
  type AiOutput,
  type AiSuggestion,
  type ChangeConflict,
  type EvidenceBundle,
  type Project,
  type ProjectChange,
  type ProjectEvolutionRecord,
  type ProjectMaterial,
  type ProjectMemory,
  type GitHubStatus,
  type TaskItem,
  type WorkSessionCandidate,
  type WorkSessionScanResult,
} from "@/lib/api";
import { buildProjectArchitecture, compactProjectPath, projectZipPaths } from "@/lib/project-insights";
import { resolveProjectFlowState } from "@/lib/project-flow-state";
import { rememberSelectedProjectId, resolveSelectedProjectId } from "@/lib/project-selection";
import { readSession } from "@/lib/auth";
import { clearDashboardSnapshot, patchDashboardSnapshot, readDashboardSnapshot } from "@/lib/dashboard-snapshot";
import { projectAnalysisContainsNoise, useDashboardWorkspace, workSessionListResult } from "@/hooks/useDashboardWorkspace";
import { useGitHubActions } from "@/hooks/useGitHubActions";
import { useProjectAnalysisJobs } from "@/lib/use-project-analysis-jobs";

export default function DashboardPage() {
  // 回到工作台时优先恢复 sessionStorage 快照，避免出现空白页与 ~10s 等待；
  // 快照随后由后台静默刷新覆盖。首次进入或退出登录后无快照。
  const initialSnapshot = typeof window !== "undefined" ? readDashboardSnapshot() : null;
  const [projects, setProjects] = useState<Project[]>(initialSnapshot?.projects ?? []);
  const [providers, setProviders] = useState<AiProvider[]>([]);
  const [materials, setMaterials] = useState<ProjectMaterial[]>(initialSnapshot?.materials ?? []);
  const [suggestions, setSuggestions] = useState<AiSuggestion[]>(initialSnapshot?.suggestions ?? []);
  const [evolutionRecords, setEvolutionRecords] = useState<ProjectEvolutionRecord[]>(initialSnapshot?.evolutionRecords ?? []);
  const [evidenceBundles, setEvidenceBundles] = useState<EvidenceBundle[]>(initialSnapshot?.evidenceBundles ?? []);
  const [changeConflicts, setChangeConflicts] = useState<ChangeConflict[]>(initialSnapshot?.changeConflicts ?? []);
  const [changes, setChanges] = useState<ProjectChange[]>(initialSnapshot?.changes ?? []);
  const [outputs, setOutputs] = useState<AiOutput[]>(initialSnapshot?.outputs ?? []);
  const [tasks, setTasks] = useState<TaskItem[]>(initialSnapshot?.tasks ?? []);
  const [memory, setMemory] = useState<ProjectMemory | null>(initialSnapshot?.memory ?? null);
  const [selectedProjectId, setSelectedProjectId] = useState(initialSnapshot?.selectedProjectId ?? "");
  const [file, setFile] = useState<File | null>(null);
  const [showZipImport, setShowZipImport] = useState(false);
  const [showFlowGuide, setShowFlowGuide] = useState(false);
  const [statsFocus, setStatsFocus] = useState<"materials" | "changes" | "sessions" | "tasks" | "">("");
  const [projectPath, setProjectPath] = useState(initialSnapshot?.memory?.localProjectPath ?? "");
  const [globalRule, setGlobalRule] = useState("");
  const [workSessionScan, setWorkSessionScan] = useState<WorkSessionScanResult | null>(initialSnapshot?.workSessionScan ?? null);
  const [githubStatus, setGithubStatus] = useState<GitHubStatus | null>(initialSnapshot?.githubStatus ?? null);
  const [scanWarnings, setScanWarnings] = useState<string[]>([]);
  // 有快照时先用旧数据渲染、不显示加载条；后台静默刷新。
  const [loading, setLoading] = useState(!initialSnapshot);
  const [importing, setImporting] = useState(false);
  const [deletingProject, setDeletingProject] = useState(false);
  const [savingProjectPath, setSavingProjectPath] = useState(false);
  const [writingProtocol, setWritingProtocol] = useState(false);
  const [syncingContext, setSyncingContext] = useState(false);
  const [scanningAgentResults, setScanningAgentResults] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const { beginContextRequest, isLatestContextRequest } = useDashboardWorkspace();
  const handledScanJobs = useRef(new Set<string>());

  const selectedProject = useMemo(
    () => projects.find((project) => project.id === selectedProjectId),
    [projects, selectedProjectId],
  );
  const { jobs, jobError, enqueueProjectAnalysis, enqueueWorkSessionScan, cancelJob, retryJob } = useProjectAnalysisJobs(selectedProjectId);
  const latestProjectJob = jobs.find((job) => job.jobType === "PROJECT") ?? null;
  const latestScanJob = jobs.find((job) => job.jobType === "WORK_SESSION_SCAN") ?? null;
  const scanningWorkSessions = latestScanJob?.status === "QUEUED" || latestScanJob?.status === "RUNNING" || latestScanJob?.status === "CANCEL_REQUESTED";
  // V3.3.3: GitHub 状态与登录指引操作（刷新只读远程，登录不保存 token）。
  // V3.3.4: 新增打开登录终端、复制命令能力；loginGuide 供接入区展示命令。
  const {
    refreshingGitHub,
    openingTerminal,
    loginGuide,
    refreshGitHub: handleRefreshGitHub,
    showGitHubLogin: handleShowGitHubLogin,
    openLoginTerminal: handleOpenLoginTerminal,
    clearLoginGuide: handleClearLoginGuide,
  } = useGitHubActions(selectedProjectId, setGithubStatus, setNotice, setError);
  const analyzing = latestProjectJob?.status === "QUEUED" || latestProjectJob?.status === "RUNNING";
  const rawAnalysis = latestProjectJob?.status === "SUCCEEDED" ? latestProjectJob.projectResult : null;
  const analysisRejectedByNoise = rawAnalysis ? projectAnalysisContainsNoise(rawAnalysis) : false;
  const analysis = analysisRejectedByNoise ? null : rawAnalysis;
  const pendingSuggestions = suggestions.filter((suggestion) => suggestion.status === "PENDING");
  const pendingChanges = changes.filter((change) => change.status === "PENDING" || change.status === "EDITED");
  const pendingReviewCount = pendingSuggestions.length + pendingChanges.length;
  const configuredProvider = providers.find((provider) => provider.id && provider.apiKeyConfigured && provider.defaultEnabled);
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
  const currentStep: DashboardStep = useMemo(() => {
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

    const hasSnapshot = Boolean(readDashboardSnapshot());
    // 有快照时后台静默刷新，不切回 loading 状态，避免短暂空白。
    if (!hasSnapshot) {
      setLoading(true);
    }
    Promise.all([listProjects(session.accessToken), listAiProviders(session.accessToken)])
      .then(([projectItems, providerItems]) => {
        setProjects(projectItems);
        setProviders(providerItems);
        // 优先沿用快照里已恢复的选中项目，避免回到工作台时项目被重置为列表第一项。
        setSelectedProjectId(resolveSelectedProjectId(projectItems, selectedProjectId));
        // 项目列表刷新后补写快照中的 projects，保证后续快照含最新项目集合。
        patchDashboardSnapshot({ projects: projectItems, selectedProjectId: resolveSelectedProjectId(projectItems, selectedProjectId) });
      })
      .catch((exception) => setError(exception instanceof Error ? exception.message : "工作台数据加载失败"))
      .finally(() => {
        if (!hasSnapshot) {
          setLoading(false);
        }
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    refreshProjectContext(selectedProjectId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedProjectId]);

  useEffect(() => {
    if (!latestScanJob || scanningWorkSessions || handledScanJobs.current.has(latestScanJob.id)) {
      return;
    }
    handledScanJobs.current.add(latestScanJob.id);
    if (latestScanJob.status === "FAILED") {
      setError(latestScanJob.errorMessage ?? "新变化分析失败");
      return;
    }
    const result = latestScanJob.workSessionScanResult;
    if (!result) {
      return;
    }
    let cancelled = false;
    void refreshProjectContext(selectedProjectId).then(() => {
      if (cancelled) {
        return;
      }
      setWorkSessionScan(result);
      setScanWarnings(result.warnings);
      setNotice(result.segments.length ? `已归并为 ${result.segments.length} 个开发推进段。` : "当前没有待整理变更。");
    });
    return () => {
      cancelled = true;
    };
    // refreshProjectContext uses the dashboard request guard; the terminal job id/status is the trigger.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [latestScanJob?.id, latestScanJob?.status, scanningWorkSessions, selectedProjectId]);

  async function refreshProjectContext(projectId: string) {
    const session = readSession();
    const requestId = beginContextRequest();
    // 不在请求开始时清空已有视图：后台刷新进行中应保留快照/旧数据，
    // 避免回到工作台或切换项目时出现短暂空白。仅在请求失败时清空。

    if (!session || !projectId) {
      if (isLatestContextRequest(requestId)) {
        clearProjectContextViewState();
      }
      return;
    }

    const hasSnapshot = Boolean(readDashboardSnapshot());
    if (!hasSnapshot) {
      setLoading(true);
    }
    try {
      const [materialItems, suggestionItems, evolutionItems, taskItems, memoryRecord, workSessions, bundles, conflicts, changeItems, outputItems, github] = await Promise.all([
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
        getProjectGitHubStatus(session.accessToken, projectId).catch(() => null),
      ]);
      if (!isLatestContextRequest(requestId)) {
        return;
      }
      const workSessionResult = workSessions.length ? workSessionListResult(projectId, memoryRecord.localProjectPath ?? "", workSessions) : null;
      setMaterials(materialItems);
      setSuggestions(suggestionItems);
      setEvolutionRecords(evolutionItems);
      setEvidenceBundles(bundles);
      setChangeConflicts(conflicts);
      setChanges(changeItems);
      setOutputs(outputItems);
      setTasks(taskItems);
      setMemory(memoryRecord);
      setGithubStatus(github);
      setProjectPath(memoryRecord.localProjectPath ?? "");
      setWorkSessionScan(workSessionResult);
      patchDashboardSnapshot({
        selectedProjectId: projectId,
        materials: materialItems,
        suggestions: suggestionItems,
        evolutionRecords: evolutionItems,
        evidenceBundles: bundles,
        changeConflicts: conflicts,
        changes: changeItems,
        outputs: outputItems,
        tasks: taskItems,
        memory: memoryRecord,
        workSessionScan: workSessionResult,
        githubStatus: github,
      });
    } catch (exception) {
      if (!isLatestContextRequest(requestId)) {
        return;
      }
      clearProjectContextViewState();
      setError(exception instanceof Error ? exception.message : "项目上下文加载失败");
    } finally {
      if (isLatestContextRequest(requestId)) {
        setLoading(false);
      }
    }
  }

  function clearProjectContextViewState() {
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
    setGithubStatus(null);
    setScanWarnings([]);
    setStatsFocus("");
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
        const nextProjects = exists ? current.map((project) => (project.id === result.project.id ? result.project : project)) : [result.project, ...current];
        patchDashboardSnapshot({ projects: nextProjects, selectedProjectId: result.project.id });
        return nextProjects;
      });
      rememberSelectedProjectId(result.project.id);
      setSelectedProjectId(result.project.id);
      setNotice("项目 zip 已导入，已生成基础项目理解和结构理解。");
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
        clearDashboardSnapshot();
        await refreshProjectContext("");
      } else {
        patchDashboardSnapshot({ projects: updatedProjects, selectedProjectId: nextProjectId });
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
      setNotice("已绑定本地项目；分析新变化和同步上下文会复用这个路径。");
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
      setError("先导入包含源码、配置或文档的完整项目 zip，再运行项目理解分析。");
      return;
    }

    setError("");
    setNotice("");
    try {
      await enqueueProjectAnalysis();
      setNotice("分析任务已提交。可离开或刷新页面，任务会继续运行。");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "项目理解分析失败");
    }
  }

  async function handleScanWorkSessions() {
    if (!selectedProjectId) {
      return;
    }

    setError("");
    setNotice("");
    setScanWarnings([]);
    try {
      await enqueueWorkSessionScan();
      setNotice("变化分析已提交。可离开或刷新页面，任务会继续运行。");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "新变化分析失败");
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
    const rule = globalRule || "ProjectFlow：开始任务前请阅读 `.projectflow/AGENT_PROTOCOL.md`，任务结束后按协议写入 `.projectflow/agent-results/`。";
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
      eyebrow="ProjectFlow V3.3.7"
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
              {selectedProject ? <InfoBubble label={selectedProject.status} title="当前项目阶段，仅展示状态，不是操作按钮。" /> : null}
              <InfoBubble
                label={configuredProvider ? `模型：${configuredProvider.name}` : "未配置模型"}
                tone={configuredProvider ? "success" : "warning"}
                dot
                title={configuredProvider ? "当前可用于项目理解分析的模型配置。" : "未配置模型时仍可使用本地规则分析。"}
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

        <section className="mb-6 grid grid-cols-2 gap-3 lg:grid-cols-4">
          <InteractiveStat
            active={statsFocus === "materials"}
            hint={hasUsableProjectZip ? `${paths.length} 个文件信号` : "暂无源码结构"}
            label="项目资料"
            onClick={() => setStatsFocus(statsFocus === "materials" ? "" : "materials")}
            value={materials.length}
          />
          <InteractiveStat
            active={statsFocus === "changes"}
            hint="去沉淀确认"
            label="建议沉淀"
            onClick={() => setStatsFocus(statsFocus === "changes" ? "" : "changes")}
            tone={pendingReviewCount ? "warning" : "slate"}
            value={pendingReviewCount}
          />
          <InteractiveStat
            active={statsFocus === "sessions"}
            hint="待整理变更"
            label="待整理变更"
            onClick={() => setStatsFocus(statsFocus === "sessions" ? "" : "sessions")}
            tone={todaySessions.length ? "brand" : "slate"}
            value={todaySessions.length}
          />
          <InteractiveStat
            active={statsFocus === "tasks"}
            hint={memory?.currentStage || selectedProject?.status || "—"}
            label="下一步任务"
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
              hasProjectPath={hasProjectPath}
              github={githubStatus}
              refreshingGitHub={refreshingGitHub}
              openingTerminal={openingTerminal}
              loginGuide={loginGuide}
              onRefreshGitHub={handleRefreshGitHub}
              onShowGitHubLogin={handleShowGitHubLogin}
              onOpenLoginTerminal={handleOpenLoginTerminal}
              onClearLoginGuide={handleClearLoginGuide}
              modelName={configuredProvider ? configuredProvider.name : null}
            />

            <PendingChangesPanel
              bundles={evidenceBundles}
              hasProjectPath={hasProjectPath}
              github={githubStatus}
              onScan={handleScanWorkSessions}
              scan={workSessionScan}
              scanning={scanningWorkSessions}
              workSessions={todaySessions}
              onRefreshGitHub={handleRefreshGitHub}
              refreshingGitHub={refreshingGitHub}
              onShowGitHubLogin={handleShowGitHubLogin}
              activeJob={latestScanJob}
              onCancelJob={() => latestScanJob && void cancelJob(latestScanJob.id).catch((exception) => setError(exception instanceof Error ? exception.message : "取消分析失败"))}
              onRetryJob={() => latestScanJob && void retryJob(latestScanJob.id).catch((exception) => setError(exception instanceof Error ? exception.message : "重新运行失败"))}
            />

            {/* 项目理解速览（极简，不再重复完整理解） */}
            <Card shadow="card">
              <SectionHeader
                eyebrow="项目理解"
                title={selectedProject?.name ?? "先导入项目"}
                actions={
                  selectedProjectId ? (
                    <div className="flex flex-wrap items-center gap-2">
                      <Button
                        variant="primary"
                        size="sm"
                        disabled={analyzing || !hasUsableProjectZip}
                        onClick={handleRunAnalysis}
                        title={hasUsableProjectZip ? "基于当前有效项目 zip 重新生成项目理解。" : "当前项目还没有可分析的源码、配置或文档目录结构。"}
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
                        完整项目理解 <ArrowRight className="h-4 w-4" />
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
                      : memory?.positioning || selectedProject.description || "已导入项目材料，正在使用本地规则生成基础项目理解。配置模型后可生成更完整的架构、风险和文件解释。")}
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
                      ? "当前项目还没有 zip 或 agent 材料。导入后才能生成项目理解、架构理解和文件解释。"
                      : "ProjectFlow 需要先拿到真实项目，才会展示画像、风险和文件理解。请从下方导入完整项目 zip。"
                  }
                />
              )}
            </Card>

            <OutputOptionsCard selectedProjectId={selectedProjectId} />

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
                {configuredProvider ? "已配置模型。深度分析结果必须经用户确认后写入项目沉淀。" : "未配置 API。文件理解页会显示本地规则解释，深度分析入口会引导到设置页。"}
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

