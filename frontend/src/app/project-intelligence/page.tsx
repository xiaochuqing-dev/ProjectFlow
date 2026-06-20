"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { CheckCircle2, DatabaseZap, History, RefreshCw, Save, ShieldCheck, Trash2 } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import {
  getProjectMemory,
  deleteProjectAnalysisRecord,
  listAiSuggestions,
  listProjectAnalysisRecords,
  listProjectEvolutionRecords,
  listProjectFactSources,
  listProjects,
  updateProjectMemory,
  type AiSuggestion,
  type Project,
  type ProjectAnalysisRecord,
  type ProjectEvolutionRecord,
  type ProjectFactSource,
  type ProjectMemory,
  type ProjectMemoryPayload,
} from "@/lib/api";
import { readSession } from "@/lib/auth";
import { rememberSelectedProjectId, resolveSelectedProjectId } from "@/lib/project-selection";
import { useProjectAnalysisJobs } from "@/lib/use-project-analysis-jobs";

const fieldConfig: Array<{
  key: keyof ProjectMemoryPayload;
  label: string;
  source: string;
  rows: number;
}> = [
  { key: "positioning", label: "项目定位", source: "用户确认 / zip 分析 / 建议采纳", rows: 4 },
  { key: "currentStage", label: "当前阶段", source: "用户确认优先", rows: 2 },
  { key: "completedCapabilities", label: "已完成能力", source: "采纳记录 / 每日回顾", rows: 5 },
  { key: "inProgressCapabilities", label: "进行中能力", source: "任务变化 / agent result", rows: 5 },
  { key: "currentRisks", label: "当前风险", source: "风险建议 / 用户手动", rows: 5 },
  { key: "technicalDecisions", label: "技术决策", source: "变更审查采纳", rows: 5 },
  { key: "developerLearnings", label: "经验沉淀", source: "每日回顾 / 模型总结", rows: 5 },
  { key: "showcaseAssets", label: "可展示成果", source: "成果素材采纳", rows: 5 },
  { key: "nextStepSuggestions", label: "下一步目标", source: "用户确认 / agent result", rows: 5 },
];

export default function ProjectIntelligencePage() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState("");
  const [memory, setMemory] = useState<ProjectMemory | null>(null);
  const [formValue, setFormValue] = useState<ProjectMemoryPayload>(emptyPayload());
  const [suggestions, setSuggestions] = useState<AiSuggestion[]>([]);
  const [evolutionRecords, setEvolutionRecords] = useState<ProjectEvolutionRecord[]>([]);
  const [factSources, setFactSources] = useState<ProjectFactSource[]>([]);
  const [analysisRecords, setAnalysisRecords] = useState<ProjectAnalysisRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [deletingRecordId, setDeletingRecordId] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const selectedProject = useMemo(
    () => projects.find((project) => project.id === selectedProjectId),
    [projects, selectedProjectId],
  );
  const { jobs, jobError, enqueueProjectAnalysis } = useProjectAnalysisJobs(selectedProjectId);
  const latestProjectJob = jobs.find((job) => job.jobType === "PROJECT") ?? null;
  const analysis = latestProjectJob?.status === "SUCCEEDED" ? latestProjectJob.projectResult : null;
  const analyzing = latestProjectJob?.status === "QUEUED" || latestProjectJob?.status === "RUNNING";
  const pendingFacts = suggestions.filter((suggestion) => suggestion.status === "PENDING" && suggestion.type === "UPDATE_PROJECT_MEMORY");
  const latestSourceByField = useMemo(() => {
    const entries = new Map<string, ProjectFactSource>();
    for (const source of factSources) {
      if (!entries.has(source.fieldKey)) {
        entries.set(source.fieldKey, source);
      }
    }
    return entries;
  }, [factSources]);

  useEffect(() => {
    const session = readSession();
    if (!session) {
      return;
    }

    setLoading(true);
    listProjects(session.accessToken)
      .then((items) => {
        setProjects(items);
        setSelectedProjectId(resolveSelectedProjectId(items));
      })
      .catch((exception) => setError(exception instanceof Error ? exception.message : "项目列表加载失败"))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    refreshProjectContext(selectedProjectId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedProjectId]);

  useEffect(() => {
    const session = readSession();
    if (!session || !selectedProjectId || latestProjectJob?.status !== "SUCCEEDED") {
      return;
    }
    listProjectAnalysisRecords(session.accessToken, selectedProjectId)
      .then(setAnalysisRecords)
      .catch(() => undefined);
  }, [latestProjectJob?.id, latestProjectJob?.status, selectedProjectId]);

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
      setMemory(null);
      setFormValue(emptyPayload());
      setSuggestions([]);
      setEvolutionRecords([]);
      setFactSources([]);
      setAnalysisRecords([]);
      return;
    }

    setLoading(true);
    setError("");
    try {
      const [memoryRecord, suggestionItems, evolutionItems, sourceItems, analysisItems] = await Promise.all([
        getProjectMemory(session.accessToken, projectId),
        listAiSuggestions(session.accessToken, projectId),
        listProjectEvolutionRecords(session.accessToken, projectId),
        listProjectFactSources(session.accessToken, projectId),
        listProjectAnalysisRecords(session.accessToken, projectId),
      ]);
      setMemory(memoryRecord);
      setFormValue(toPayload(memoryRecord));
      setSuggestions(suggestionItems);
      setEvolutionRecords(evolutionItems);
      setFactSources(sourceItems);
      setAnalysisRecords(analysisItems);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "项目画像加载失败");
    } finally {
      setLoading(false);
    }
  }

  async function handleSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const session = readSession();
    if (!session || !selectedProjectId) {
      return;
    }

    setSaving(true);
    setError("");
    setNotice("");
    try {
      const updated = await updateProjectMemory(session.accessToken, selectedProjectId, formValue);
      setMemory(updated);
      setFormValue(toPayload(updated));
      setFactSources(await listProjectFactSources(session.accessToken, selectedProjectId));
      setNotice("项目档案已保存。后续成果输出会优先使用这些已确认内容。");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "项目档案保存失败");
    } finally {
      setSaving(false);
    }
  }

  function updateField(key: keyof ProjectMemoryPayload, value: string) {
    setFormValue((current) => ({ ...current, [key]: value }));
  }

  async function handleRunAnalysis() {
    const session = readSession();
    if (!session || !selectedProjectId) {
      return;
    }

    setError("");
    setNotice("");
    try {
      await enqueueProjectAnalysis();
      setNotice("分析任务已提交。刷新页面不会中断任务。");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "项目画像分析失败。请先导入完整项目 zip。");
    }
  }

  async function handleDeleteAnalysisRecord(recordId: string) {
    const session = readSession();
    if (!session) {
      return;
    }

    setDeletingRecordId(recordId);
    setError("");
    setNotice("");
    try {
      await deleteProjectAnalysisRecord(session.accessToken, recordId);
      setAnalysisRecords((current) => current.filter((record) => record.id !== recordId));
      setNotice("分析记录已删除。");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "分析记录删除失败");
    } finally {
      setDeletingRecordId("");
    }
  }

  return (
    <AppShell eyebrow="长期档案" title="项目画像">
      <div className="min-h-[calc(100vh-4rem)] bg-surface p-8">
        <section className="mb-6 flex flex-wrap items-center justify-between gap-4 rounded-md border border-line bg-white p-4 shadow-panel">
          <div className="flex flex-wrap items-center gap-3">
            <select
              className="h-10 min-w-72 rounded-md border border-line bg-white px-3 text-sm outline-none focus:border-slate-950"
              disabled={projects.length === 0}
              onChange={(event) => {
                rememberSelectedProjectId(event.target.value);
                setSelectedProjectId(event.target.value);
              }}
              value={selectedProjectId}
            >
              {projects.map((project) => (
                <option key={project.id} value={project.id}>
                  {project.name}
                </option>
              ))}
            </select>
            <span className="rounded-md bg-slate-100 px-2.5 py-1 text-xs text-slate-600">
              版本 {memory?.version ?? "-"}
            </span>
            <span className="rounded-md bg-emerald-50 px-2.5 py-1 text-xs text-emerald-700">
              用户确认内容优先
            </span>
            <button
              className="inline-flex h-9 items-center gap-2 rounded-md bg-slate-950 px-3 text-sm font-semibold text-white disabled:opacity-60"
              disabled={analyzing || !selectedProjectId}
              onClick={handleRunAnalysis}
              type="button"
            >
              {analyzing ? <RefreshCw className="h-4 w-4 animate-spin" /> : null}
              {latestProjectJob?.status === "QUEUED" ? "等待分析" : analyzing ? "模型分析中" : analysis ? "重新分析" : "运行项目分析"}
            </button>
          </div>
          <div className="flex items-center gap-2 text-sm text-muted">
            <ShieldCheck className="h-4 w-4" />
            {selectedProject?.name ?? "暂无项目"}
          </div>
        </section>

        <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
          <form className="rounded-md border border-line bg-white shadow-panel" onSubmit={handleSave}>
            {analysis ? (
              <div className="border-b border-line bg-slate-50 p-5">
                <div className="mb-3 flex flex-wrap items-center gap-2">
                  <span className={`rounded-md px-2.5 py-1 text-xs ${analysis.modelUsed ? "bg-emerald-50 text-emerald-700" : "bg-amber-50 text-amber-800"}`}>
                    {analysis.modelUsed ? "模型分析" : "本地规则"}
                  </span>
                  <span className="text-xs text-muted">{analysis.message}</span>
                </div>
                <p className="text-base leading-7 text-slate-800">{analysis.summary}</p>
                <p className="mt-2 text-sm leading-6 text-slate-600">{analysis.architecture}</p>
                {analysis.risks.length ? <p className="mt-2 text-sm text-amber-800">风险：{analysis.risks.join("；")}</p> : null}
                {analysis.evidence.length ? (
                  <div className="mt-4 rounded-md border border-line bg-white p-4">
                    <p className="text-sm font-semibold text-slate-950">判断依据</p>
                    <ul className="mt-2 space-y-1 text-sm leading-6 text-slate-600">
                      {analysis.evidence.map((item) => <li key={item}>- {item}</li>)}
                    </ul>
                  </div>
                ) : null}
                {analysis.limitations.length ? <p className="mt-3 text-xs leading-5 text-muted">分析局限：{analysis.limitations.join("；")}</p> : null}
              </div>
            ) : null}
            {latestProjectJob?.status === "FAILED" ? (
              <div className="border-b border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">
                分析任务失败：{latestProjectJob.errorMessage ?? "未知错误"}
              </div>
            ) : null}
            {jobError ? <div className="border-b border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">{jobError}</div> : null}
            <div className="flex items-center justify-between border-b border-line px-5 py-4">
              <div className="flex items-center gap-2">
                <DatabaseZap className="h-4 w-4 text-slate-700" />
                <h2 className="font-semibold">可编辑项目档案</h2>
              </div>
              <button
                className="inline-flex items-center gap-2 rounded-md bg-slate-950 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
                disabled={saving || !selectedProjectId}
                type="submit"
              >
                {saving ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
                {saving ? "保存中..." : "保存档案"}
              </button>
            </div>
            <div className="grid gap-0 md:grid-cols-2">
              {fieldConfig.map((field) => {
                const latestSource = latestSourceByField.get(field.key);
                return (
                <label className="border-b border-line p-5 odd:md:border-r" key={field.key}>
                  <div className="mb-2 flex items-center justify-between gap-3">
                    <span className="font-semibold text-slate-950">{field.label}</span>
                    <span className={`rounded-md px-2 py-1 text-xs ${latestSource?.confirmedByUser ? "bg-emerald-50 text-emerald-700" : "bg-slate-100 text-muted"}`}>
                      {latestSource ? sourceLabel(latestSource) : field.source}
                    </span>
                  </div>
                  <textarea
                    className="w-full resize-y rounded-md border border-line bg-slate-50 px-3 py-2 text-sm leading-6 outline-none focus:border-slate-950"
                    onChange={(event) => updateField(field.key, event.target.value)}
                    rows={field.rows}
                    value={formValue[field.key]}
                  />
                </label>
                );
              })}
            </div>
            {loading ? <div className="h-1 bg-slate-950" /> : null}
          </form>

          <aside className="space-y-5">
            <div className="rounded-md border border-line bg-white shadow-panel">
              <div className="flex items-center justify-between border-b border-line px-5 py-4">
                <h2 className="font-semibold">分析记录</h2>
                <span className="rounded-md bg-slate-100 px-2 py-1 text-xs text-muted">{analysisRecords.length} 条</span>
              </div>
              <div className="divide-y divide-line">
                {analysisRecords.slice(0, 8).map((record) => (
                  <article className="p-4 text-sm" key={record.id}>
                    <div className="mb-2 grid grid-cols-[minmax(0,1fr)_auto] items-start gap-3">
                      <div className="min-w-0">
                        <p
                          className="truncate font-medium text-slate-950"
                          title={record.recordType === "FILE" ? record.filePath ?? "文件分析" : "项目分析"}
                        >
                          {record.recordType === "FILE" ? record.filePath ?? "文件分析" : "项目分析"}
                        </p>
                        <p className="mt-1 line-clamp-2 leading-5 text-slate-600">{record.summary}</p>
                      </div>
                      <div className="flex shrink-0 items-center gap-2">
                        <Link
                          className="rounded-md border border-line px-2.5 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
                          href={`/project-analysis-records/${record.id}`}
                        >
                          查看
                        </Link>
                        <button
                          aria-label="删除分析记录"
                          className="inline-flex h-8 w-8 items-center justify-center rounded-md border border-line text-muted hover:border-rose-200 hover:bg-rose-50 hover:text-rose-700 disabled:opacity-50"
                          disabled={deletingRecordId === record.id}
                          onClick={() => handleDeleteAnalysisRecord(record.id)}
                          type="button"
                        >
                          {deletingRecordId === record.id ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
                        </button>
                      </div>
                    </div>
                    <div className="flex flex-wrap items-center gap-2 text-xs text-muted">
                      <span>{record.modelUsed ? "模型" : "本地规则"}</span>
                      <span>{record.providerName ?? "无 API"}</span>
                      <span>{new Date(record.createdAt).toLocaleString()}</span>
                    </div>
                  </article>
                ))}
                {analysisRecords.length === 0 ? <p className="p-5 text-sm text-muted">暂无分析记录。运行项目分析或文件分析后会在这里出现，可随时删除。</p> : null}
              </div>
            </div>

            <div className="rounded-md border border-line bg-white p-5 shadow-panel">
              <div className="mb-3 flex items-center gap-2">
                <CheckCircle2 className="h-4 w-4 text-emerald-600" />
                <h2 className="font-semibold">确认原则</h2>
              </div>
              <p className="text-sm leading-6 text-slate-600">
                AI、zip 分析和 agent result 都只能生成候选档案。用户在这里确认后，才进入正式项目档案，并作为成果输出、每日回顾和 agent 上下文的优先来源。
              </p>
            </div>

            <div className="rounded-md border border-line bg-white shadow-panel">
              <div className="border-b border-line px-5 py-4">
                <h2 className="font-semibold">字段来源链</h2>
              </div>
              <div className="divide-y divide-line">
                {factSources.slice(0, 8).map((source) => (
                  <article className="p-4 text-sm" key={source.id}>
                    <div className="mb-2 flex items-center justify-between gap-3">
                      <p className="font-medium text-slate-950">{fieldLabel(source.fieldKey)}</p>
                      <span className={`rounded-md px-2 py-1 text-xs ${source.confirmedByUser ? "bg-emerald-50 text-emerald-700" : "bg-slate-100 text-muted"}`}>
                        {source.sourceType}
                      </span>
                    </div>
                    <p className="line-clamp-2 leading-5 text-slate-600">{source.value}</p>
                    <p className="mt-2 text-xs text-muted">
                      {source.confidence} · {source.confirmedByUser ? "用户确认" : "待确认"} · {new Date(source.updatedAt).toLocaleString()}
                    </p>
                  </article>
                ))}
                {factSources.length === 0 ? <p className="p-5 text-sm text-muted">保存项目档案或采纳变更后，会生成字段级来源记录。</p> : null}
              </div>
            </div>

            <div className="rounded-md border border-line bg-white shadow-panel">
              <div className="border-b border-line px-5 py-4">
                <h2 className="font-semibold">待确认档案</h2>
              </div>
              <div className="divide-y divide-line">
                {pendingFacts.slice(0, 6).map((suggestion) => (
                  <article className="p-4 text-sm" key={suggestion.id}>
                    <p className="font-medium text-slate-950">{suggestion.title}</p>
                    <p className="mt-1 line-clamp-3 leading-5 text-slate-600">{suggestion.reason}</p>
                  </article>
                ))}
                {pendingFacts.length === 0 ? <p className="p-5 text-sm text-muted">暂无项目档案类候选变更。</p> : null}
              </div>
            </div>

            <div className="rounded-md border border-line bg-white shadow-panel">
              <div className="flex items-center gap-2 border-b border-line px-5 py-4">
                <History className="h-4 w-4 text-slate-700" />
                <h2 className="font-semibold">最近档案变化</h2>
              </div>
              <div className="divide-y divide-line">
                {evolutionRecords.slice(0, 4).map((record) => (
                  <article className="p-4 text-sm" key={record.id}>
                    <p className="font-medium text-slate-950">{record.summary}</p>
                    <p className="mt-1 line-clamp-3 whitespace-pre-line leading-5 text-slate-600">{record.detectedChanges}</p>
                  </article>
                ))}
                {evolutionRecords.length === 0 ? <p className="p-5 text-sm text-muted">采纳变更后会产生演进记录。</p> : null}
              </div>
            </div>
          </aside>
        </div>

        {error ? <div className="fixed bottom-5 left-1/2 z-50 -translate-x-1/2 rounded-md border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 shadow-panel">{error}</div> : null}
        {notice ? <div className="fixed bottom-5 left-1/2 z-50 -translate-x-1/2 rounded-md border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700 shadow-panel">{notice}</div> : null}
      </div>
    </AppShell>
  );
}

function emptyPayload(): ProjectMemoryPayload {
  return {
    positioning: "",
    currentStage: "",
    completedCapabilities: "",
    inProgressCapabilities: "",
    currentRisks: "",
    technicalDecisions: "",
    developerLearnings: "",
    showcaseAssets: "",
    nextStepSuggestions: "",
  };
}

function toPayload(memory: ProjectMemory): ProjectMemoryPayload {
  return {
    positioning: memory.positioning,
    currentStage: memory.currentStage,
    completedCapabilities: memory.completedCapabilities,
    inProgressCapabilities: memory.inProgressCapabilities,
    currentRisks: memory.currentRisks,
    technicalDecisions: memory.technicalDecisions,
    developerLearnings: memory.developerLearnings,
    showcaseAssets: memory.showcaseAssets,
    nextStepSuggestions: memory.nextStepSuggestions,
  };
}

function sourceLabel(source: ProjectFactSource) {
  return `${source.sourceType} · ${source.confirmedByUser ? "已确认" : source.confidence}`;
}

function fieldLabel(fieldKey: string) {
  return fieldConfig.find((field) => field.key === fieldKey)?.label ?? fieldKey;
}
