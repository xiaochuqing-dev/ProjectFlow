"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useParams, usePathname, useRouter, useSearchParams } from "next/navigation";
import { ArrowLeft, ArrowRight, FileCode2, FolderTree, RefreshCw, Search, Settings, ShieldAlert } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import {
  getProject,
  listAiProviders,
  listProjectMaterials,
  type AiProvider,
  type Project,
  type ProjectMaterial,
} from "@/lib/api";
import { buildFileInsights, buildModuleGroups, projectZipPaths, type FileInsight } from "@/lib/project-insights";
import { readSession } from "@/lib/auth";
import { useProjectAnalysisJobs } from "@/lib/use-project-analysis-jobs";
import { updateProjectFileViewSearch, type ProjectFileViewStatePatch } from "@/lib/project-file-view-state";

export default function ProjectFilesPage() {
  const params = useParams<{ projectId: string }>();
  const pathname = usePathname();
  const router = useRouter();
  const searchParams = useSearchParams();
  const selectedModule = searchParams.get("module") ?? "";
  const selectedPath = searchParams.get("file") ?? "";
  const query = searchParams.get("q") ?? "";
  const [project, setProject] = useState<Project | null>(null);
  const [providers, setProviders] = useState<AiProvider[]>([]);
  const [materials, setMaterials] = useState<ProjectMaterial[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const { jobs, jobError, enqueueFileAnalysis } = useProjectAnalysisJobs(params.projectId);

  const configuredProvider = providers.find((provider) => provider.id && provider.apiKeyConfigured);
  const paths = useMemo(() => projectZipPaths(materials), [materials]);
  const files = useMemo(() => buildFileInsights(paths), [paths]);
  const modules = useMemo(() => buildModuleGroups(paths), [paths]);
  const filteredFiles = files.filter((file) => {
    const moduleMatches = !selectedModule || file.moduleName === selectedModule;
    const queryMatches = !query.trim() || file.path.toLowerCase().includes(query.trim().toLowerCase());
    return moduleMatches && queryMatches;
  });
  const selectedFile = files.find((file) => file.path === selectedPath) ?? filteredFiles[0] ?? null;
  const latestFileJobs = useMemo(() => {
    const byPath = new Map<string, (typeof jobs)[number]>();
    for (const job of jobs) {
      if (job.jobType === "FILE" && job.filePath && !byPath.has(job.filePath)) {
        byPath.set(job.filePath, job);
      }
    }
    return byPath;
  }, [jobs]);

  useEffect(() => {
    const session = readSession();
    if (!session) {
      return;
    }

    setLoading(true);
    Promise.all([
      getProject(session.accessToken, params.projectId),
      listProjectMaterials(session.accessToken, params.projectId),
      listAiProviders(session.accessToken),
    ])
      .then(([projectRecord, materialItems, providerItems]) => {
        setProject(projectRecord);
        setMaterials(materialItems);
        setProviders(providerItems);
      })
      .catch((exception) => setError(exception instanceof Error ? exception.message : "文件理解加载失败"))
      .finally(() => setLoading(false));
  }, [params.projectId]);

  async function handleAnalyzeFile(path: string) {
    const session = readSession();
    if (!session) {
      return;
    }

    setError("");
    try {
      await enqueueFileAnalysis(path);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "文件分析失败");
    }
  }

  function updateViewState(patch: ProjectFileViewStatePatch) {
    const search = updateProjectFileViewSearch(new URLSearchParams(searchParams.toString()), patch);
    router.replace(search ? `${pathname}?${search}` : pathname, { scroll: false });
  }

  return (
    <AppShell eyebrow="项目画像" title="文件理解">
      <div className="min-h-[calc(100vh-4rem)] bg-surface p-6">
        <section className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-md border border-line bg-white p-4 shadow-panel">
          <div className="min-w-0">
            <Link className="mb-2 inline-flex items-center gap-1 text-sm font-semibold text-slate-600 hover:text-slate-950" href="/dashboard">
              <ArrowLeft className="h-4 w-4" />
              返回工作台
            </Link>
            <h2 className="truncate text-lg font-semibold text-slate-950">{project?.name ?? "项目文件"}</h2>
            <p className="mt-1 text-sm text-muted">
              {configuredProvider ? "已配置模型：当前先展示本地规则解释，深度分析结果需确认后写入档案。" : "未配置 API：当前只展示本地规则基础解释。"}
            </p>
          </div>
          {!configuredProvider ? (
            <Link className="inline-flex items-center gap-2 rounded-md border border-line bg-white px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50" href="/settings">
              <Settings className="h-4 w-4" />
              配置模型
            </Link>
          ) : (
            <span className="rounded-md bg-emerald-50 px-3 py-2 text-sm font-semibold text-emerald-700">模型可用</span>
          )}
        </section>

        {error ? <div className="mb-5 rounded-md border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">{error}</div> : null}
        {jobError ? <div className="mb-5 rounded-md border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">{jobError}</div> : null}

        {paths.length === 0 && !loading ? (
          <section className="grid min-h-96 place-items-center rounded-md border border-line bg-white p-8 text-center shadow-panel">
            <div className="max-w-md">
              <FolderTree className="mx-auto h-10 w-10 text-slate-400" />
              <h3 className="mt-4 font-semibold text-slate-950">先导入完整项目 zip</h3>
              <p className="mt-2 text-sm leading-6 text-muted">
                文件理解依赖 zip 目录树。当前项目还没有可分析材料，回到工作台导入后再查看目录、模块和文件作用。
              </p>
              <Link className="mt-5 inline-flex items-center gap-2 rounded-md bg-slate-950 px-4 py-2 text-sm font-semibold text-white" href="/dashboard">
                去导入 <ArrowRight className="h-4 w-4" />
              </Link>
            </div>
          </section>
        ) : (
          <section className="grid gap-5 xl:grid-cols-[280px_minmax(340px,0.9fr)_minmax(360px,1fr)]">
            <aside className="rounded-md border border-line bg-white shadow-panel">
              <div className="border-b border-line p-4">
                <div className="flex items-center gap-2">
                  <FolderTree className="h-4 w-4 text-slate-700" />
                  <h2 className="font-semibold">模块</h2>
                </div>
              </div>
              <div className="space-y-2 p-3">
                <ModuleButton
                  active={!selectedModule}
                  count={files.length}
                  label="全部"
                  onClick={() => updateViewState({ module: "", file: "" })}
                />
                {modules.map((module) => (
                  <ModuleButton
                    active={selectedModule === module.name}
                    count={module.count}
                    key={module.name}
                    label={module.name}
                    onClick={() => updateViewState({ module: module.name, file: "" })}
                  />
                ))}
              </div>
            </aside>

            <section className="rounded-md border border-line bg-white shadow-panel">
              <div className="border-b border-line p-4">
                <label className="flex h-10 items-center gap-2 rounded-md border border-line bg-slate-50 px-3">
                  <Search className="h-4 w-4 text-slate-500" />
                  <input
                    className="min-w-0 flex-1 bg-transparent text-sm outline-none"
                    onChange={(event) => {
                      updateViewState({ query: event.target.value, file: "" });
                    }}
                    placeholder="搜索文件路径"
                    value={query}
                  />
                </label>
              </div>
              <div className="max-h-[calc(100vh-220px)] overflow-auto">
                {filteredFiles.map((file) => (
                  <button
                    className={`block w-full border-b border-line px-4 py-3 text-left text-sm hover:bg-slate-50 ${
                      selectedFile?.path === file.path ? "bg-slate-950 text-white hover:bg-slate-900" : "text-slate-700"
                    }`}
                    key={file.path}
                    onClick={() => updateViewState({ file: file.path })}
                    type="button"
                  >
                    <div className="flex items-center justify-between gap-3">
                      <span className="truncate font-medium">{file.name}</span>
                      <span className={`shrink-0 rounded-md px-2 py-1 text-xs ${selectedFile?.path === file.path ? "bg-white/10 text-white" : typeTone(file.fileType)}`}>
                        {signalLabel(file.fileType)}
                      </span>
                    </div>
                    <p className={`mt-1 truncate font-mono text-xs ${selectedFile?.path === file.path ? "text-white/70" : "text-muted"}`}>{file.path}</p>
                  </button>
                ))}
                {!loading && filteredFiles.length === 0 ? <p className="p-5 text-sm text-muted">没有匹配文件。</p> : null}
                {loading ? <div className="h-1 bg-slate-950" /> : null}
              </div>
            </section>

            <section className="rounded-md border border-line bg-white shadow-panel">
              {selectedFile ? (
                <FileDetail
                  job={latestFileJobs.get(selectedFile.path) ?? null}
                  file={selectedFile}
                  onAnalyze={() => handleAnalyzeFile(selectedFile.path)}
                  providerConfigured={Boolean(configuredProvider)}
                />
              ) : (
                <div className="grid min-h-80 place-items-center p-8 text-center text-sm text-muted">选择左侧文件查看解释。</div>
              )}
            </section>
          </section>
        )}
      </div>
    </AppShell>
  );
}

function ModuleButton({ active, count, label, onClick }: { active: boolean; count: number; label: string; onClick: () => void }) {
  return (
    <button
      className={`flex w-full items-center justify-between rounded-md px-3 py-2 text-sm ${
        active ? "bg-slate-950 font-semibold text-white" : "text-slate-700 hover:bg-slate-50"
      }`}
      onClick={onClick}
      type="button"
    >
      <span>{label}</span>
      <span className={active ? "text-white/70" : "text-muted"}>{count}</span>
    </button>
  );
}

function FileDetail({
  job,
  file,
  onAnalyze,
  providerConfigured,
}: {
  job: import("@/lib/api").ProjectAnalysisJob | null;
  file: FileInsight;
  onAnalyze: () => void;
  providerConfigured: boolean;
}) {
  const active = job?.status === "SUCCEEDED" ? job.fileResult : null;
  const analyzing = job?.status === "QUEUED" || job?.status === "RUNNING";
  return (
    <div>
      <div className="border-b border-line p-5">
        <div className="mb-3 flex items-center gap-2">
          <FileCode2 className="h-5 w-5 text-slate-700" />
          <h2 className="min-w-0 truncate text-lg font-semibold text-slate-950">{file.name}</h2>
        </div>
        <p className="break-all font-mono text-xs text-muted">{file.path}</p>
      </div>
      <div className="space-y-5 p-5">
        <div>
          <p className="text-sm font-semibold text-slate-950">基础解释</p>
          <p className="mt-2 text-sm leading-6 text-slate-600">{active?.summary ?? file.summary}</p>
        </div>
        <div className="grid gap-3 sm:grid-cols-3">
          <Signal label="职责" value={active?.role ?? file.role} />
          <Signal label="重要性" value={active?.importance ?? file.importance} />
          <Signal label="风险" value={active?.riskLevel ?? file.riskLevel} />
        </div>
        <div className={`rounded-md border p-4 text-sm leading-6 ${(active?.riskLevel ?? file.riskLevel) === "high" || (active?.riskLevel ?? file.riskLevel) === "medium" ? "border-amber-200 bg-amber-50 text-amber-900" : "border-line bg-slate-50 text-slate-600"}`}>
          <div className="mb-2 flex items-center gap-2 font-semibold">
            <ShieldAlert className="h-4 w-4" />
            风险说明
          </div>
          {active?.riskNotes ?? file.riskNotes}
        </div>
        {active?.evidence.length ? (
          <div className="rounded-md border border-line bg-slate-50 p-4">
            <p className="text-sm font-semibold text-slate-950">判断依据</p>
            <ul className="mt-2 space-y-1 text-sm leading-6 text-slate-600">
              {active.evidence.map((item) => <li key={item}>- {item}</li>)}
            </ul>
          </div>
        ) : null}
        {active?.relatedFiles.length ? (
          <div className="rounded-md border border-line bg-white p-4">
            <p className="text-sm font-semibold text-slate-950">关联文件</p>
            <p className="mt-2 break-all font-mono text-xs leading-5 text-slate-600">{active.relatedFiles.join(" · ")}</p>
          </div>
        ) : null}
        <div className="rounded-md border border-line bg-white p-4">
          <p className="text-sm font-semibold text-slate-950">深度分析</p>
          <p className="mt-2 text-sm leading-6 text-slate-600">
            {active
              ? active.message
              : job?.status === "FAILED"
                ? `分析失败：${job.errorMessage ?? "未知错误"}`
                : analyzing
                  ? "任务正在后台运行。刷新或离开页面不会中断，返回后会自动恢复状态。"
              : providerConfigured
                ? "模型已配置。点击按钮会调用后端 JSON 分析接口；失败时自动回落到本地规则。"
                : "未配置 API，不会展示空分析结果。点击按钮会返回本地规则解释，并提示配置模型。"}
          </p>
          <div className="mt-4 flex flex-wrap items-center gap-2">
            <button
              className="inline-flex items-center gap-2 rounded-md bg-slate-950 px-3 py-2 text-sm font-semibold text-white disabled:opacity-60"
              disabled={analyzing}
              onClick={onAnalyze}
              type="button"
            >
              {analyzing ? <RefreshCw className="h-4 w-4 animate-spin" /> : <RefreshCw className="h-4 w-4" />}
              {job?.status === "QUEUED"
                ? "等待分析"
                : analyzing
                  ? "模型分析中"
                  : active
                    ? "重新分析"
                    : providerConfigured
                      ? "运行模型分析"
                      : "运行本地分析"}
            </button>
            {!providerConfigured ? (
              <Link className="inline-flex items-center gap-2 rounded-md border border-line px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50" href="/settings">
              <Settings className="h-4 w-4" />
              去配置模型
              </Link>
            ) : null}
            {active ? <span className="text-xs text-muted">{analysisSourceLabel(active.analysisSource)} · {confidenceLabel(active.confidence)}</span> : null}
          </div>
          {active?.limitations ? <p className="mt-3 text-xs leading-5 text-muted">分析局限：{active.limitations}</p> : null}
        </div>
      </div>
    </div>
  );
}

function Signal({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-md border border-line bg-slate-50 p-3">
      <p className="text-xs text-muted">{label}</p>
      <p className="mt-1 text-sm font-semibold text-slate-950">{signalLabel(value)}</p>
    </div>
  );
}

function signalLabel(value: string) {
  const labels: Record<string, string> = {
    critical: "核心",
    important: "重要",
    normal: "一般",
    high: "高",
    medium: "中",
    low: "低",
    none: "未发现",
    source: "源码",
    test: "测试",
    config: "配置",
    docs: "文档",
    script: "脚本",
    asset: "资源",
    build: "构建产物",
    env: "环境配置",
    unknown: "未知",
  };
  return labels[value.toLowerCase()] ?? value;
}

function analysisSourceLabel(value: string) {
  return value === "MODEL_ANALYSIS" ? "模型分析" : "本地规则";
}

function confidenceLabel(value: string) {
  const labels: Record<string, string> = {
    high: "高置信度",
    medium: "中置信度",
    low: "低置信度",
  };
  return labels[value.toLowerCase()] ?? value;
}

function typeTone(type: FileInsight["fileType"]) {
  const tones = {
    source: "bg-blue-50 text-blue-800",
    test: "bg-emerald-50 text-emerald-700",
    config: "bg-amber-50 text-amber-800",
    docs: "bg-slate-100 text-slate-600",
    script: "bg-cyan-50 text-cyan-800",
    asset: "bg-slate-100 text-slate-600",
    build: "bg-slate-100 text-slate-500",
    env: "bg-rose-50 text-rose-700",
    unknown: "bg-slate-100 text-slate-600",
  };
  return tones[type];
}
