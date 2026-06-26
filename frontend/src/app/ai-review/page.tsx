"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { Clipboard, Download, FileText, Gauge, History, Layers3, RefreshCw, Save, Sparkles } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { ProjectContextBar, Toast } from "@/components/ui";
import { SourceCardList, type SourceCardItem } from "@/components/sources/SourceCardList";
import { useAutoDismissNotice } from "@/hooks/useAutoDismissNotice";
import { useProjectSelection } from "@/hooks/useProjectSelection";
import { compactProjectPath } from "@/lib/project-insights";
import {
  generateAiOutput,
  getProjectMemory,
  listAiOutputs,
  listDevLogs,
  listProjectEvolutionRecords,
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
import { firstUsefulLine } from "@/lib/text-summary";

const outputLabels: Record<AiOutputType, string> = {
  WEEKLY_REPORT: "周报",
  PROJECT_SUMMARY: "项目复盘",
  RESUME_BULLET: "简历描述",
  README_SECTION: "README 草稿",
};

const outputDescriptions: Record<AiOutputType, string> = {
  WEEKLY_REPORT: "基于已确认项目资产、每日回顾和今日开发证据，整理阶段汇报。",
  PROJECT_SUMMARY: "面向作品集、项目复盘和对外介绍，突出定位、能力和工程决策。",
  RESUME_BULLET: "压缩为简历可用项目经历，强调动作、技术和结果。",
  README_SECTION: "生成可继续编辑的 README 段落，保留来源线索。",
};

export default function AiReviewPage() {
  const { projects, selectedProject, selectedProjectId, selectProject, loadingProjects, projectError } = useProjectSelection();
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [logs, setLogs] = useState<DevLog[]>([]);
  const [outputs, setOutputs] = useState<AiOutput[]>([]);
  const [evolutionRecords, setEvolutionRecords] = useState<ProjectEvolutionRecord[]>([]);
  const [memory, setMemory] = useState<ProjectMemory | null>(null);
  const [selectedType, setSelectedType] = useState<AiOutputType>("WEEKLY_REPORT");
  const [activeSourcePanel, setActiveSourcePanel] = useState<"memory" | "reviews" | "tasks" | "growth">("memory");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [selectedOutputId, setSelectedOutputId] = useState("");
  const [editableContent, setEditableContent] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);

  const selectedOutput = useMemo(
    () => outputs.find((output) => output.id === selectedOutputId) ?? outputs[0],
    [outputs, selectedOutputId],
  );
  const doneTasks = tasks.filter((task) => task.status === "DONE");
  const reviewLogs = logs.filter((log) => log.category === "REVIEW");
  const sourcePanel = buildSourcePanel(activeSourcePanel, memory, reviewLogs, tasks, evolutionRecords);
  const outputReadiness = useMemo(() => {
    const connected = [
      { key: "assets", label: "项目资产", ready: Boolean(memory) },
      { key: "reviews", label: "每日回顾", ready: reviewLogs.length > 0 },
      { key: "dev", label: "今日开发", ready: tasks.length > 0 },
      { key: "timeline", label: "项目时间线", ready: evolutionRecords.length > 0 },
    ];
    const score = connected.filter((item) => item.ready).length;
    const level = score >= 4 ? "高" : score >= 2 ? "中" : "低";
    const missing = [
      { key: "tests", label: "测试结果" },
      { key: "release", label: "上线记录" },
      { key: "risks", label: "风险决策" },
    ];
    return { connected, score, level, missing };
  }, [memory, reviewLogs.length, tasks.length, evolutionRecords.length]);

  useEffect(() => {
    refreshProjectContext(selectedProjectId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedProjectId]);

  useEffect(() => {
    setEditableContent(selectedOutput?.content ?? buildFallbackDraft(selectedType, selectedProject, memory, reviewLogs, doneTasks, evolutionRecords));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedOutputId, outputs, selectedType, selectedProjectId, memory, logs, tasks, evolutionRecords]);

  useAutoDismissNotice(error, notice, () => {
    setNotice("");
    setError("");
  });

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
        <ProjectContextBar
          actions={(
            <div className="flex items-center gap-2 text-sm text-muted">
              <FileText className="h-4 w-4" />
              已生成 {outputs.length} 份
            </div>
          )}
          leadingExtras={(
            <>
              <SourceQuickFilter active={activeSourcePanel === "memory"} label="项目资产" value={memory ? 1 : 0} onClick={() => setActiveSourcePanel("memory")} />
              <SourceQuickFilter active={activeSourcePanel === "reviews"} label="每日回顾" value={reviewLogs.length} onClick={() => setActiveSourcePanel("reviews")} />
              <SourceQuickFilter active={activeSourcePanel === "tasks"} label="今日开发" value={tasks.length} onClick={() => setActiveSourcePanel("tasks")} />
              <SourceQuickFilter active={activeSourcePanel === "growth"} label="项目时间线" value={evolutionRecords.length} onClick={() => setActiveSourcePanel("growth")} />
            </>
          )}
          onSelect={selectProject}
          projects={projects}
          selectedProjectId={selectedProjectId}
        />

        <div className="grid gap-6 xl:grid-cols-[420px_minmax(0,1fr)_360px]">
          <section className="space-y-5">
            <section className="rounded-md border border-line bg-white p-5 shadow-panel">
              <div className="mb-3 flex items-center gap-2">
                <Layers3 className="h-4 w-4 text-slate-700" />
                <h2 className="font-semibold">生成依据</h2>
              </div>
              <div className="grid grid-cols-2 gap-2 text-sm">
                <OutputSourceMetric href={`/project-intelligence/fact-sources?projectId=${selectedProjectId}`} label="项目资产" ready={Boolean(memory)} value={memory ? "已连接" : "缺失"} />
                <OutputSourceMetric href={`/project-intelligence/timeline?projectId=${selectedProjectId}`} label="项目时间线" ready={evolutionRecords.length > 0} value={`${evolutionRecords.length} 条`} />
                <OutputSourceMetric href={`/dev-logs/sources?projectId=${selectedProjectId}&date=${new Date().toISOString().slice(0, 10)}`} label="每日回顾" ready={reviewLogs.length > 0} value={`${reviewLogs.length} 条`} />
                <OutputSourceMetric href={`/tasks?projectId=${selectedProjectId}`} label="今日开发" ready={tasks.length > 0} value={`${tasks.length} 条`} />
              </div>
              <p className="mt-3 text-xs leading-5 text-muted">
                输出优先使用已确认项目资产和成长记录；缺少来源时仍可生成草稿，但需要人工补充。
              </p>
            </section>

            <section className="rounded-md border border-line bg-white p-5 shadow-panel">
              <div className="mb-3 flex items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <Gauge className="h-4 w-4 text-slate-700" />
                  <h2 className="font-semibold">输出素材完整度</h2>
                </div>
                <span className={`rounded-full px-2.5 py-1 text-xs font-semibold text-white ${outputReadiness.level === "高" ? "bg-emerald-700" : outputReadiness.level === "中" ? "bg-amber-600" : "bg-rose-600"}`}>
                  {outputReadiness.level} · {outputReadiness.score}/4
                </span>
              </div>
              <p className="mb-3 text-xs leading-5 text-muted">这是生成结果参考度，不是项目质量评分。素材越完整，输出越具体可用。</p>
              <p className="mb-1 text-xs font-semibold text-slate-700">已连接素材</p>
              <ul className="mb-3 space-y-1 text-sm leading-6 text-slate-700">
                {outputReadiness.connected.map((item) => (
                  <li key={item.key}>{item.ready ? "✓" : "—"} {item.label}</li>
                ))}
              </ul>
              <p className="mb-1 text-xs font-semibold text-slate-700">建议补充素材</p>
              <ul className="space-y-1 text-sm leading-6 text-slate-700">
                {outputReadiness.missing.map((item) => (
                  <li key={item.key}>— {item.label}</li>
                ))}
              </ul>
            </section>

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
            <SourceCardList compactBody={compactSourceText} empty={sourcePanel.empty} items={sourcePanel.items} title={sourcePanel.title} />
          </aside>
        </div>

        <Toast error={error || projectError} notice={notice} />
        {loading || loadingProjects ? <div className="fixed inset-x-0 bottom-0 h-1 bg-slate-950" /> : null}
      </div>
    </AppShell>
  );
}

function SourceQuickFilter({ active, label, onClick, value }: { active: boolean; label: string; onClick: () => void; value: number }) {
  return (
    <button
      className={`rounded-md px-3 py-1.5 text-xs font-semibold transition hover:-translate-y-0.5 hover:shadow-sm ${
        active ? "bg-slate-950 text-white" : "bg-slate-100 text-slate-600 hover:bg-white"
      }`}
      onClick={onClick}
      type="button"
    >
      {label} {value}
    </button>
  );
}

function OutputSourceMetric({ href, label, ready, value }: { href: string; label: string; ready: boolean; value: string }) {
  return (
    <Link className={`rounded-md border px-3 py-2 transition hover:-translate-y-0.5 hover:shadow-sm ${ready ? "border-emerald-100 bg-emerald-50 text-emerald-800" : "border-line bg-slate-50 text-slate-500"}`} href={href}>
      <p className="text-xs">{label}</p>
      <p className="mt-1 font-semibold">{value}</p>
    </Link>
  );
}

function buildSourcePanel(
  active: "memory" | "reviews" | "tasks" | "growth",
  memory: ProjectMemory | null,
  reviewLogs: DevLog[],
  tasks: TaskItem[],
  evolutionRecords: ProjectEvolutionRecord[],
) {
  if (active === "reviews") {
    return {
      title: "每日回顾来源",
      empty: "无每日回顾。",
      items: reviewLogs.slice(0, 5).map((log) => ({
        title: log.title,
        body: firstUsefulLine(log.content) || "无内容",
        meta: log.logDate,
      })),
    };
  }
  if (active === "tasks") {
    return {
      title: "今日开发来源",
      empty: "无今日开发证据。",
      items: tasks.slice(0, 6).map((task) => ({
        title: task.title,
        body: task.description || task.status,
        meta: task.status,
      })),
    };
  }
  if (active === "growth") {
    return {
      title: "项目时间线来源",
      empty: "无项目时间线记录。",
      items: evolutionRecords.slice(0, 5).map((record) => ({
        title: record.summary,
        body: firstUsefulLine(record.detectedChanges) || "已记录项目变化。",
        meta: new Date(record.createdAt).toLocaleDateString(),
      })),
    };
  }
  return {
    title: "项目资产来源",
    empty: "无已确认项目资产。",
    items: memory ? [
      sourceCardItem("定位", memory.positioning),
      sourceCardItem("能力", memory.completedCapabilities),
      sourceCardItem("决策", memory.technicalDecisions),
      sourceCardItem("成果", memory.showcaseAssets),
    ].filter((item): item is SourceCardItem => Boolean(item)) : [],
  };
}

function sourceCardItem(label: string, value: string | undefined): SourceCardItem | null {
  const line = firstUsefulLine(value);
  return line ? { title: label, body: line, meta: "已确认" } : null;
}

function compactSourceText(value: string) {
  return value
    .split(/\s+/)
    .map((part) => part.includes("/") ? compactProjectPath(part) : part)
    .join(" ");
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

- 基于 ${projectName} 的已确认项目资产，沉淀开发过程、技术决策和成果素材。
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

## 项目资产
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
