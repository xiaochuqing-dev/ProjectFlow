"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { Clipboard, Download, FileText, History, Layers3, RefreshCw, Save, Sparkles } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import {
  generateAiOutput,
  getProjectMemory,
  listAiOutputs,
  listDevLogs,
  listProjectEvolutionRecords,
  listProjects,
  listTasks,
  type AiOutput,
  type AiOutputType,
  type DevLog,
  type Project,
  type ProjectEvolutionRecord,
  type ProjectMemory,
  type TaskItem,
} from "@/lib/api";
import { readSession } from "@/lib/auth";
import { rememberSelectedProjectId, resolveSelectedProjectId } from "@/lib/project-selection";

const outputLabels: Record<AiOutputType, string> = {
  WEEKLY_REPORT: "周报",
  PROJECT_SUMMARY: "项目复盘",
  RESUME_BULLET: "简历描述",
  README_SECTION: "README 草稿",
};

const outputDescriptions: Record<AiOutputType, string> = {
  WEEKLY_REPORT: "基于已确认档案、每日回顾和任务证据，整理阶段汇报。",
  PROJECT_SUMMARY: "面向作品集、项目复盘和对外介绍，突出定位、能力和工程决策。",
  RESUME_BULLET: "压缩为简历可用项目经历，强调动作、技术和结果。",
  README_SECTION: "生成可继续编辑的 README 段落，保留来源线索。",
};

export default function AiReviewPage() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [logs, setLogs] = useState<DevLog[]>([]);
  const [outputs, setOutputs] = useState<AiOutput[]>([]);
  const [evolutionRecords, setEvolutionRecords] = useState<ProjectEvolutionRecord[]>([]);
  const [memory, setMemory] = useState<ProjectMemory | null>(null);
  const [selectedProjectId, setSelectedProjectId] = useState("");
  const [selectedType, setSelectedType] = useState<AiOutputType>("WEEKLY_REPORT");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [selectedOutputId, setSelectedOutputId] = useState("");
  const [editableContent, setEditableContent] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);

  const selectedProject = useMemo(
    () => projects.find((project) => project.id === selectedProjectId),
    [projects, selectedProjectId],
  );
  const selectedOutput = useMemo(
    () => outputs.find((output) => output.id === selectedOutputId) ?? outputs[0],
    [outputs, selectedOutputId],
  );
  const doneTasks = tasks.filter((task) => task.status === "DONE");
  const reviewLogs = logs.filter((log) => log.category === "REVIEW");

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
      .catch((exception) => setError(exception instanceof Error ? exception.message : "项目加载失败"))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    refreshProjectContext(selectedProjectId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedProjectId]);

  useEffect(() => {
    setEditableContent(selectedOutput?.content ?? buildFallbackDraft(selectedType, selectedProject, memory, reviewLogs, doneTasks, evolutionRecords));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedOutputId, outputs, selectedType, selectedProjectId, memory, logs, tasks, evolutionRecords]);

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
      setTasks([]);
      setLogs([]);
      setOutputs([]);
      setEvolutionRecords([]);
      setMemory(null);
      setSelectedOutputId("");
      return;
    }

    setLoading(true);
    setError("");
    try {
      const [taskItems, logItems, outputItems, evolutionItems, memoryRecord] = await Promise.all([
        listTasks(session.accessToken, projectId),
        listDevLogs(session.accessToken, projectId),
        listAiOutputs(session.accessToken, projectId),
        listProjectEvolutionRecords(session.accessToken, projectId),
        getProjectMemory(session.accessToken, projectId),
      ]);
      setTasks(taskItems);
      setLogs(logItems);
      setOutputs(outputItems);
      setEvolutionRecords(evolutionItems);
      setMemory(memoryRecord);
      setSelectedOutputId(outputItems[0]?.id ?? "");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "成果输出上下文加载失败");
    } finally {
      setLoading(false);
    }
  }

  async function handleGenerate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const session = readSession();
    if (!session || !selectedProjectId) {
      return;
    }

    setGenerating(true);
    setError("");
    setNotice("");
    try {
      const output = await generateAiOutput(session.accessToken, selectedProjectId, selectedType, fromDate || null, toDate || null);
      const refreshed = await listAiOutputs(session.accessToken, selectedProjectId);
      setOutputs(refreshed);
      setSelectedOutputId(output.id);
      setEditableContent(output.content);
      setNotice(`${outputLabels[selectedType]}已生成，可继续编辑后复制或下载。`);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "成果生成失败");
    } finally {
      setGenerating(false);
    }
  }

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(editableContent);
      setNotice("已复制当前编辑内容。");
    } catch {
      setError("复制失败，浏览器未授权剪贴板。");
    }
  }

  function handleDownload() {
    const title = selectedOutput?.title || `${selectedProject?.name ?? "projectflow"}-${outputLabels[selectedType]}`;
    const blob = new Blob([editableContent], { type: "text/markdown;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `${title}.md`;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  return (
    <AppShell eyebrow="确认资产到可展示内容" title="成果输出">
      <div className="min-h-[calc(100vh-4rem)] bg-surface p-8">
        <section className="mb-6 rounded-md border border-line bg-white p-4 shadow-panel">
          <div className="flex flex-wrap items-center justify-between gap-4">
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
              <SourcePill label="项目档案" value={memory ? 1 : 0} />
              <SourcePill label="每日回顾" value={reviewLogs.length} />
              <SourcePill label="任务证据" value={tasks.length} />
              <SourcePill label="演进记录" value={evolutionRecords.length} />
            </div>
            <div className="flex items-center gap-2 text-sm text-muted">
              <FileText className="h-4 w-4" />
              已生成 {outputs.length} 份
            </div>
          </div>
        </section>

        <div className="grid gap-6 xl:grid-cols-[420px_minmax(0,1fr)_360px]">
          <section className="space-y-5">
            <form className="rounded-md border border-line bg-white p-5 shadow-panel" onSubmit={handleGenerate}>
              <div className="mb-4 flex items-center gap-2">
                <Sparkles className="h-4 w-4 text-slate-700" />
                <h2 className="font-semibold">生成素材</h2>
              </div>
              <div className="space-y-3">
                {(Object.keys(outputLabels) as AiOutputType[]).map((type) => (
                  <button
                    className={`w-full rounded-md border p-3 text-left transition ${
                      selectedType === type ? "border-slate-950 bg-slate-950 text-white" : "border-line bg-white hover:bg-slate-50"
                    }`}
                    key={type}
                    onClick={() => setSelectedType(type)}
                    type="button"
                  >
                    <p className="font-semibold">{outputLabels[type]}</p>
                    <p className={`mt-1 text-sm leading-5 ${selectedType === type ? "text-slate-200" : "text-slate-600"}`}>{outputDescriptions[type]}</p>
                  </button>
                ))}
              </div>
              <div className="mt-4 grid grid-cols-2 gap-3">
                <input
                  className="rounded-md border border-line px-3 py-2 text-sm outline-none focus:border-slate-950"
                  onChange={(event) => setFromDate(event.target.value)}
                  type="date"
                  value={fromDate}
                />
                <input
                  className="rounded-md border border-line px-3 py-2 text-sm outline-none focus:border-slate-950"
                  onChange={(event) => setToDate(event.target.value)}
                  type="date"
                  value={toDate}
                />
              </div>
              <button
                className="mt-4 flex w-full items-center justify-center gap-2 rounded-md bg-brand px-4 py-2.5 text-sm font-semibold text-white hover:bg-blue-600 disabled:opacity-60"
                disabled={generating || !selectedProjectId}
                type="submit"
              >
                {generating ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Sparkles className="h-4 w-4" />}
                {generating ? "生成中..." : `生成${outputLabels[selectedType]}`}
              </button>
            </form>

            <section className="rounded-md border border-line bg-white shadow-panel">
              <div className="flex items-center gap-2 border-b border-line px-5 py-4">
                <History className="h-4 w-4 text-slate-700" />
                <h2 className="font-semibold">历史输出</h2>
              </div>
              <div className="divide-y divide-line">
                {outputs.map((output) => (
                  <button
                    className={`w-full p-4 text-left text-sm transition ${selectedOutput?.id === output.id ? "bg-blue-50" : "hover:bg-slate-50"}`}
                    key={output.id}
                    onClick={() => setSelectedOutputId(output.id)}
                    type="button"
                  >
                    <div className="flex items-center justify-between gap-3">
                      <p className="font-medium text-slate-950">{output.title}</p>
                      <span className="rounded-md bg-white px-2 py-1 text-xs text-muted">{outputLabels[output.type]}</span>
                    </div>
                    <p className="mt-2 text-xs text-muted">{new Date(output.createdAt).toLocaleString()}</p>
                  </button>
                ))}
                {!loading && outputs.length === 0 ? <p className="p-5 text-sm text-muted">暂无历史输出。</p> : null}
              </div>
            </section>
          </section>

          <section className="rounded-md border border-line bg-white shadow-panel">
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line px-5 py-4">
              <div className="flex items-center gap-2">
                <Save className="h-4 w-4 text-slate-700" />
                <h2 className="font-semibold">可编辑正文</h2>
              </div>
              <div className="flex gap-2">
                <button className="inline-flex items-center gap-2 rounded-md border border-line bg-white px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50" onClick={handleCopy} type="button">
                  <Clipboard className="h-4 w-4" />
                  复制
                </button>
                <button className="inline-flex items-center gap-2 rounded-md bg-slate-950 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800" onClick={handleDownload} type="button">
                  <Download className="h-4 w-4" />
                  下载 .md
                </button>
              </div>
            </div>
            <textarea
              className="min-h-[760px] w-full resize-none border-0 bg-slate-50 p-5 font-mono text-sm leading-7 outline-none"
              onChange={(event) => setEditableContent(event.target.value)}
              value={editableContent}
            />
          </section>

          <aside className="space-y-5">
            <SourceBlock
              icon={<Layers3 className="h-4 w-4 text-slate-700" />}
              title="项目档案来源"
              text={[
                memory?.positioning,
                memory?.completedCapabilities,
                memory?.technicalDecisions,
                memory?.showcaseAssets,
              ].filter(Boolean).join("\n\n") || "暂无已确认项目档案。"}
            />
            <SourceBlock
              icon={<FileText className="h-4 w-4 text-slate-700" />}
              title="每日回顾来源"
              text={reviewLogs.slice(0, 3).map((log) => `${log.logDate} ${log.title}\n${log.content}`).join("\n\n") || "暂无每日回顾。"}
            />
            <SourceBlock
              icon={<History className="h-4 w-4 text-slate-700" />}
              title="演进记录来源"
              text={evolutionRecords.slice(0, 4).map((record) => `${record.summary}\n${record.detectedChanges}`).join("\n\n") || "暂无演进记录。"}
            />
          </aside>
        </div>

        {error ? <div className="fixed bottom-5 left-1/2 z-50 -translate-x-1/2 rounded-md border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 shadow-panel">{error}</div> : null}
        {notice ? <div className="fixed bottom-5 left-1/2 z-50 -translate-x-1/2 rounded-md border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700 shadow-panel">{notice}</div> : null}
        {loading ? <div className="fixed inset-x-0 bottom-0 h-1 bg-slate-950" /> : null}
      </div>
    </AppShell>
  );
}

function SourcePill({ label, value }: { label: string; value: number }) {
  return <span className="rounded-md bg-slate-100 px-2.5 py-1 text-xs text-slate-600">{label} {value}</span>;
}

function SourceBlock({ icon, title, text }: { icon: ReactNode; title: string; text: string }) {
  return (
    <section className="rounded-md border border-line bg-white shadow-panel">
      <div className="flex items-center gap-2 border-b border-line px-5 py-4">
        {icon}
        <h2 className="font-semibold">{title}</h2>
      </div>
      <p className="whitespace-pre-line p-5 text-sm leading-6 text-slate-600">{text}</p>
    </section>
  );
}

function buildFallbackDraft(
  type: AiOutputType,
  project: Project | undefined,
  memory: ProjectMemory | null,
  reviewLogs: DevLog[],
  doneTasks: TaskItem[],
  evolutionRecords: ProjectEvolutionRecord[],
) {
  const projectName = project?.name ?? "项目";
  const sourceLines = [
    memory?.positioning,
    memory?.completedCapabilities,
    memory?.technicalDecisions,
    ...reviewLogs.slice(0, 3).map((log) => log.content),
    ...evolutionRecords.slice(0, 3).map((record) => record.summary),
  ].filter(Boolean);
  const sources = sourceLines.length ? sourceLines.map((item) => `- ${String(item).replace(/\n/g, "\n  ")}`).join("\n") : "- 暂无已确认来源。";

  if (type === "RESUME_BULLET") {
    return `# ${projectName} 简历描述

- 基于 ${projectName} 的已确认项目档案，沉淀开发过程、技术决策和成果素材。
- 推进 ${doneTasks.length} 个已完成任务，形成可复用的项目证据。
- 来源：
${sources}`;
  }

  if (type === "README_SECTION") {
    return `## ${projectName}

### Project Overview
${memory?.positioning || "暂无项目定位。"}

### Confirmed Capabilities
${memory?.completedCapabilities || "暂无已确认能力。"}

### Engineering Notes
${memory?.technicalDecisions || "暂无技术决策。"}

### Sources
${sources}`;
  }

  return `# ${projectName} ${outputLabels[type]}

## 项目档案
${memory?.positioning || "暂无项目定位。"}

## 已确认能力
${memory?.completedCapabilities || "暂无已确认能力。"}

## 每日回顾摘要
${reviewLogs.slice(0, 5).map((log) => `- ${log.logDate} ${log.title}`).join("\n") || "- 暂无每日回顾。"}

## 技术决策与风险
${memory?.technicalDecisions || "暂无技术决策。"}

${memory?.currentRisks || "暂无已确认风险。"}

## 来源
${sources}`;
}
