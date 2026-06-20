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
  Stat,
  Toast,
} from "@/components/ui";
import {
  deleteProject,
  getProjectMemory,
  importProjectZip,
  listAiProviders,
  listAiSuggestions,
  listProjectChangeConflicts,
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
  type AiSuggestion,
  type ChangeConflict,
  type Project,
  type ProjectAnalysis,
  type ProjectEvolutionRecord,
  type ProjectMaterial,
  type ProjectMemory,
  type TaskItem,
  type WorkSessionCandidate,
  type WorkSessionScanResult,
} from "@/lib/api";
import { buildProjectArchitecture, compactProjectPath, projectZipPaths } from "@/lib/project-insights";
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
  const [changeConflicts, setChangeConflicts] = useState<ChangeConflict[]>([]);
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [memory, setMemory] = useState<ProjectMemory | null>(null);
  const [selectedProjectId, setSelectedProjectId] = useState("");
  const [file, setFile] = useState<File | null>(null);
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
  const pendingSuggestions = suggestions.filter((suggestion) => suggestion.status === "PENDING");
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

  // 当前下一步 —— 把原来的平铺改成单一焦点
  const currentStep: Step = useMemo(() => {
    if (!selectedProject) return { kind: "no_project" };
    if (!hasMaterials) return { kind: "no_material" };
    if (!hasProjectPath) return { kind: "no_path" };
    if (pendingSuggestions.length > 0) return { kind: "has_pending", count: pendingSuggestions.length };
    return { kind: "scan_updates" };
  }, [selectedProject, hasMaterials, hasProjectPath, pendingSuggestions.length]);

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
      setChangeConflicts([]);
      setTasks([]);
      setMemory(null);
      setProjectPath("");
      setWorkSessionScan(null);
      return;
    }

    try {
      const [materialItems, suggestionItems, evolutionItems, taskItems, memoryRecord, workSessions, conflicts] = await Promise.all([
        listProjectMaterials(session.accessToken, projectId),
        listAiSuggestions(session.accessToken, projectId),
        listProjectEvolutionRecords(session.accessToken, projectId),
        listTasks(session.accessToken, projectId),
        getProjectMemory(session.accessToken, projectId),
        listProjectWorkSessions(session.accessToken, projectId),
        listProjectChangeConflicts(session.accessToken, projectId),
      ]);
      setMaterials(materialItems);
      setSuggestions(suggestionItems);
      setEvolutionRecords(evolutionItems);
      setChangeConflicts(conflicts);
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
          leadingExtras={
            <>
              {selectedProject ? <Badge label={selectedProject.status} /> : null}
              <Badge
                label={configuredProvider ? `模型：${configuredProvider.name}` : "未配置模型"}
                tone={configuredProvider ? "success" : "warning"}
                dot
              />
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
          <Stat label="项目材料" value={materials.length} hint={hasUsableProjectZip ? `${paths.length} 个文件信号` : "暂无源码结构"} />
          <Stat label="待确认变更" value={pendingSuggestions.length} tone={pendingSuggestions.length ? "warning" : "slate"} hint="去变更审查" />
          <Stat label="今日候选" value={todaySessions.length} tone={todaySessions.length ? "brand" : "slate"} hint="Work Session" />
          <Stat label="进行中任务" value={activeTasks.length} hint={memory?.currentStage || selectedProject?.status || "—"} />
        </section>

        <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_360px]">
          {/* 主列 */}
          <div className="space-y-5">
            {/* 当前下一步引导卡：单一焦点 */}
            <NextStepCard
              step={currentStep}
              hasUsableProjectZip={hasUsableProjectZip}
              hasProjectZipMaterial={hasProjectZipMaterial}
              file={file}
              setFile={setFile}
              importing={importing}
              onImportZip={handleImportZip}
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

            {/* 项目画像速览（极简，不再重复完整画像） */}
            <Card shadow="card">
              <SectionHeader
                eyebrow="项目画像"
                title={selectedProject?.name ?? "先导入项目"}
                actions={
                  selectedProjectId ? (
                    <Link className="inline-flex items-center gap-1 text-sm font-semibold text-brand hover:text-brand-hover" href="/project-intelligence">
                      查看完整画像 <ArrowRight className="h-4 w-4" />
                    </Link>
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

            {/* 架构入口（极简，只留入口链接与形态摘要，详情去文件页） */}
            {hasUsableProjectZip ? (
              <Card shadow="card">
                <SectionHeader
                  eyebrow="架构与文件理解"
                  title={architecture.summary || "项目结构"}
                  icon={<FolderTree className="h-4 w-4" />}
                  actions={
                    <Link href={`/projects/${selectedProjectId}/files`}>
                      <Button variant="secondary" size="sm">
                        完整结构 <ArrowRight className="h-3.5 w-3.5" />
                      </Button>
                    </Link>
                  }
                />
                <div className="grid gap-3 p-5 sm:grid-cols-3">
                  <MiniFact label="入口" value={architecture.entrypoints[0]?.path ? compactProjectPath(architecture.entrypoints[0].path) : "未识别"} />
                  <MiniFact label="核心模块" value={architecture.coreModules[0]?.path ? compactProjectPath(architecture.coreModules[0].path) : "待确认"} />
                  <MiniFact label="文件信号" value={`${paths.length} 个`} />
                </div>
              </Card>
            ) : null}
          </div>

          {/* 侧列 */}
          <aside className="space-y-5">
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
/* 当前下一步引导卡                                                    */
/* ------------------------------------------------------------------ */

type NextStepCardProps = {
  step: Step;
  hasUsableProjectZip: boolean;
  hasProjectZipMaterial: boolean;
  file: File | null;
  setFile: (file: File | null) => void;
  importing: boolean;
  onImportZip: (event: FormEvent<HTMLFormElement>) => void;
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

function NextStepCard(props: NextStepCardProps) {
  const { step } = props;
  const stepMeta: Record<Step["kind"], { title: string; subtitle: string }> = {
    no_project: { title: "导入项目", subtitle: "导入完整项目 zip，开始建立项目画像与结构理解。" },
    no_material: { title: "导入完整 zip", subtitle: "当前项目还没有可分析的材料。导入完整项目 zip 后才能生成画像。" },
    no_path: { title: "绑定真实路径", subtitle: "绑定本地项目文件夹后，才能扫描 Agent 结果和今日变化。" },
    has_pending: { title: "确认变化", subtitle: `当前有 ${step.kind === "has_pending" ? step.count : 0} 条待确认变更，去变更审查集中处理。` },
    scan_updates: { title: "扫描更新", subtitle: "项目已就绪。扫描 Agent 结果或今日变化，获取新的候选证据。" },
  };
  const meta = stepMeta[step.kind];

  return (
    <Card shadow="cardLg" className="overflow-hidden border-brand/20">
      {/* 顶部靛蓝强调条 */}
      <div className="h-1 bg-brand" />
      <div className="p-5">
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            <p className="text-xs font-semibold uppercase tracking-[0.16em] text-brand">当前下一步</p>
            <h2 className="mt-1 text-lg font-semibold text-ink">{meta.title}</h2>
            <p className="mt-1 text-sm leading-6 text-muted">{meta.subtitle}</p>
          </div>
          <Badge label="Step" tone="brand" dot />
        </div>

        <div className="mt-4">
          {/* 不论处于哪一步，zip 导入始终可达（最常见的真实入口） */}
          {(step.kind === "no_project" || step.kind === "no_material") ? (
            <form onSubmit={props.onImportZip}>
              <label className="block rounded-card border border-dashed border-lineStrong bg-surfaceAlt p-4 transition hover:border-brand">
                <span className="mb-2 block text-sm font-medium text-body">导入完整项目 zip</span>
                <input
                  accept=".zip,application/zip"
                  className="w-full text-sm text-muted"
                  onChange={(event) => props.setFile(event.target.files?.[0] ?? null)}
                  type="file"
                />
              </label>
              <Button variant="primary" type="submit" className="mt-3 w-full" disabled={!props.file || props.importing}>
                {props.importing ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />}
                {props.importing ? "导入中..." : "导入并生成基础画像"}
              </Button>
            </form>
          ) : null}

          {/* 绑定路径阶段：主操作聚焦保存路径，其余接入动作降为次级 */}
          {step.kind === "no_path" ? (
            <div>
              <input
                className="h-10 w-full rounded-field border border-line bg-elevated px-3 text-sm outline-none transition focus:border-brand focus-visible:shadow-focus"
                onChange={(event) => props.setProjectPath(event.target.value)}
                placeholder="真实项目文件夹路径"
                value={props.projectPath}
              />
              <Button variant="primary" className="mt-3 w-full" disabled={props.savingProjectPath} onClick={props.onSavePath}>
                {props.savingProjectPath ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
                保存路径
              </Button>
            </div>
          ) : null}

          {/* 已绑定路径后：接入动作横向排列 */}
          {step.kind !== "no_project" && step.kind !== "no_material" ? (
            <div className="grid gap-2 sm:grid-cols-2">
              <Button
                variant="secondary"
                size="sm"
                disabled={!props.projectPath.trim() || props.writingProtocol}
                onClick={props.onWriteProtocol}
                title="在目标项目生成 ProjectFlow 协议、上下文目录和结果收件箱。"
              >
                {props.writingProtocol ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <FileCode2 className="h-3.5 w-3.5" />}
                写入/刷新协议
              </Button>
              <Button
                variant="secondary"
                size="sm"
                disabled={!props.projectPath.trim() || props.scanningAgentResults}
                onClick={props.onScanAgentResults}
                title="读取目标项目的 ProjectFlow 结果收件箱。"
              >
                {props.scanningAgentResults ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <ScanLine className="h-3.5 w-3.5" />}
                扫描 Agent Result
              </Button>
              <Button
                variant="secondary"
                size="sm"
                disabled={props.syncingContext}
                onClick={props.onSyncContext}
                title="把已确认档案写回目标项目上下文目录。"
              >
                {props.syncingContext ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <FolderTree className="h-3.5 w-3.5" />}
                同步确认上下文
              </Button>
              <Button variant="secondary" size="sm" onClick={props.onCopyGlobalRule} title="复制给其他 Agent 使用的通用规则。">
                <Clipboard className="h-3.5 w-3.5" />
                复制规则
              </Button>
            </div>
          ) : null}

          {/* 有待确认变化：直接引导去变更审查 */}
          {step.kind === "has_pending" ? (
            <Link href="/tasks" className="mt-1 inline-flex w-full">
              <Button variant="primary" className="w-full">
                去变更审查处理 {step.count} 条 <ArrowRight className="h-4 w-4" />
              </Button>
            </Link>
          ) : null}
        </div>
      </div>
    </Card>
  );
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
