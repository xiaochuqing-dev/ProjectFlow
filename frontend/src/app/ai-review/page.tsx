"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { Clipboard, Download, FileText, Layers3, RefreshCw, Sparkles } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import {
  generateAiOutput,
  listAiOutputs,
  listDevLogs,
  listProjects,
  listTasks,
  type AiOutput,
  type AiOutputType,
  type DevLog,
  type Project,
  type TaskItem,
} from "@/lib/api";
import { readSession } from "@/lib/auth";

const outputLabels: Record<AiOutputType, string> = {
  WEEKLY_REPORT: "周报",
  PROJECT_SUMMARY: "项目总结",
  RESUME_BULLET: "简历要点",
  README_SECTION: "README 段落",
};

const outputDescriptions: Record<AiOutputType, string> = {
  WEEKLY_REPORT: "适合阶段复盘、周记和项目推进汇报。",
  PROJECT_SUMMARY: "适合作品集、GitHub README 和项目介绍。",
  RESUME_BULLET: "适合压缩成简历项目经历和面试讲述素材。",
  README_SECTION: "适合直接整理到仓库文档中。",
};

const outputTemplates: Record<AiOutputType, string> = {
  WEEKLY_REPORT: `# 本周项目周报

## 本周完成
- 

## 遇到的问题
- 

## 技术决策
- 

## 下周计划
- `,
  PROJECT_SUMMARY: `# 项目总结

## 项目背景

## 核心功能
- 

## 技术实现
- 

## 工程亮点
- `,
  RESUME_BULLET: `# 简历项目要点

- 独立负责/参与实现 ...
- 使用 ... 技术完成 ...
- 通过 ... 解决 ...
- 项目沉淀了 ...`,
  README_SECTION: `## Project Overview

### Features
- 

### Tech Stack
- 

### Engineering Highlights
- `,
};

export default function AiReviewPage() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [logs, setLogs] = useState<DevLog[]>([]);
  const [outputs, setOutputs] = useState<AiOutput[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState("");
  const [selectedType, setSelectedType] = useState<AiOutputType>("WEEKLY_REPORT");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [selectedOutputId, setSelectedOutputId] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [copyState, setCopyState] = useState("");
  const [templateCopyState, setTemplateCopyState] = useState("");

  const selectedProject = useMemo(
    () => projects.find((project) => project.id === selectedProjectId),
    [projects, selectedProjectId],
  );

  const selectedOutput = useMemo(
    () => outputs.find((output) => output.id === selectedOutputId) ?? outputs[0],
    [outputs, selectedOutputId],
  );

  useEffect(() => {
    const session = readSession();
    if (!session) {
      return;
    }

    setLoading(true);
    listProjects(session.accessToken)
      .then((items) => {
        setProjects(items);
        setSelectedProjectId((current) => current || items[0]?.id || "");
      })
      .catch((exception) => setError(exception instanceof Error ? exception.message : "项目加载失败"))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    const session = readSession();
    if (!session || !selectedProjectId) {
      setTasks([]);
      setLogs([]);
      setOutputs([]);
      return;
    }

    setSelectedOutputId("");
    Promise.all([
      listTasks(session.accessToken, selectedProjectId),
      listDevLogs(session.accessToken, selectedProjectId),
      listAiOutputs(session.accessToken, selectedProjectId),
    ])
      .then(([taskItems, logItems, outputItems]) => {
        setTasks(taskItems);
        setLogs(logItems);
        setOutputs(outputItems);
        setSelectedOutputId(outputItems[0]?.id ?? "");
      })
      .catch((exception) => setError(exception instanceof Error ? exception.message : "复盘上下文加载失败"));
  }, [selectedProjectId]);

  async function handleGenerate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const session = readSession();
    if (!session || !selectedProjectId) {
      return;
    }

    setGenerating(true);
    setError("");
    try {
      const output = await generateAiOutput(
        session.accessToken,
        selectedProjectId,
        selectedType,
        fromDate || null,
        toDate || null,
      );
      const refreshed = await listAiOutputs(session.accessToken, selectedProjectId);
      setOutputs(refreshed);
      setSelectedOutputId(output.id);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "AI 复盘生成失败");
    } finally {
      setGenerating(false);
    }
  }

  async function handleCopy() {
    if (!selectedOutput) {
      return;
    }
    try {
      await navigator.clipboard.writeText(selectedOutput.content);
      setCopyState("已复制");
      window.setTimeout(() => setCopyState(""), 1600);
    } catch {
      setCopyState("复制失败");
    }
  }

  async function handleCopyTemplate(type: AiOutputType) {
    try {
      await navigator.clipboard.writeText(outputTemplates[type]);
      setTemplateCopyState(`${outputLabels[type]}模板已复制`);
      window.setTimeout(() => setTemplateCopyState(""), 1600);
    } catch {
      setTemplateCopyState("模板复制失败");
    }
  }

  function handleDownload() {
    if (!selectedOutput) {
      return;
    }
    const blob = new Blob([selectedOutput.content], { type: "text/markdown;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `${selectedOutput.title}.md`;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  return (
    <AppShell eyebrow="AI 复盘" title="复盘输出">
      <div className="grid min-h-[calc(100vh-4rem)] gap-6 p-8 xl:grid-cols-[400px_1fr]">
        <section className="space-y-4">
          <div className="rounded-lg border border-line bg-white p-5 shadow-panel">
            <div className="mb-4 flex items-center gap-3">
              <div className="grid h-10 w-10 place-items-center rounded-xl bg-slate-900 text-white">
                <Layers3 className="h-5 w-5" />
              </div>
              <div>
                <h2 className="font-semibold">复盘来源</h2>
                <p className="text-sm text-muted">从项目、任务和日志生成 Markdown。</p>
              </div>
            </div>

            <select
              className="w-full rounded-lg border border-line bg-white px-3 py-2 text-sm outline-none focus:border-brand"
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

            <div className="mt-4 grid grid-cols-3 gap-2 text-center">
              <div className="rounded-lg bg-slate-50 p-3">
                <p className="text-lg font-semibold">{tasks.length}</p>
                <p className="text-xs text-muted">任务</p>
              </div>
              <div className="rounded-lg bg-slate-50 p-3">
                <p className="text-lg font-semibold">{logs.length}</p>
                <p className="text-xs text-muted">日志</p>
              </div>
              <div className="rounded-lg bg-slate-50 p-3">
                <p className="text-lg font-semibold">{outputs.length}</p>
                <p className="text-xs text-muted">输出</p>
              </div>
            </div>
          </div>

          <form className="rounded-lg border border-line bg-white p-5 shadow-panel" onSubmit={handleGenerate}>
            <div className="mb-4 flex items-center gap-3">
              <div className="grid h-10 w-10 place-items-center rounded-xl bg-blue-50 text-brand">
                <Sparkles className="h-5 w-5" />
              </div>
              <div>
                <h2 className="font-semibold">选择导出模板</h2>
                <p className="text-sm text-muted">先选用途，再生成或复制模板。</p>
              </div>
            </div>

            <div className="space-y-4">
              <div className="grid gap-3">
                {(Object.keys(outputLabels) as AiOutputType[]).map((type) => (
                  <div
                    className={`rounded-lg border p-3 transition ${
                      selectedType === type ? "border-blue-200 bg-blue-50/60" : "border-line bg-white"
                    }`}
                    key={type}
                  >
                    <button
                      className="w-full text-left"
                      onClick={() => setSelectedType(type)}
                      type="button"
                    >
                      <div className="flex items-center justify-between gap-3">
                        <p className="font-semibold text-slate-950">{outputLabels[type]}</p>
                        <span className="rounded-full bg-white px-2 py-1 text-xs text-muted">模板</span>
                      </div>
                      <p className="mt-1 text-sm leading-6 text-slate-600">{outputDescriptions[type]}</p>
                    </button>
                    <button
                      className="mt-3 text-sm font-semibold text-brand hover:text-blue-700"
                      onClick={() => handleCopyTemplate(type)}
                      type="button"
                    >
                      复制空白模板
                    </button>
                  </div>
                ))}
              </div>
              {templateCopyState ? (
                <p className="rounded-lg bg-emerald-50 px-3 py-2 text-sm text-emerald-700">{templateCopyState}</p>
              ) : null}
              <details className="rounded-lg border border-line bg-slate-50 p-3">
                <summary className="cursor-pointer text-sm font-medium text-slate-700">可选：限制复盘日期范围</summary>
                <div className="mt-3 grid grid-cols-2 gap-3">
                  <input
                    className="rounded-lg border border-line bg-white px-3 py-2 text-sm outline-none focus:border-brand"
                    onChange={(event) => setFromDate(event.target.value)}
                    type="date"
                    value={fromDate}
                  />
                  <input
                    className="rounded-lg border border-line bg-white px-3 py-2 text-sm outline-none focus:border-brand"
                    onChange={(event) => setToDate(event.target.value)}
                    type="date"
                    value={toDate}
                  />
                </div>
              </details>
              {error ? <p className="text-sm text-rose-600">{error}</p> : null}
              <button
                className="flex w-full items-center justify-center gap-2 rounded-lg bg-brand px-4 py-2.5 text-sm font-semibold text-white hover:bg-blue-600 disabled:opacity-60"
                disabled={generating || !selectedProjectId}
                type="submit"
              >
                {generating ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Sparkles className="h-4 w-4" />}
                {generating ? "生成中..." : `生成${outputLabels[selectedType]}`}
              </button>
            </div>
          </form>

          <div className="rounded-lg border border-line bg-white p-5 shadow-panel">
            <h2 className="font-semibold">历史输出</h2>
            <div className="mt-4 space-y-3">
              {outputs.map((output) => (
                <button
                  className={`w-full rounded-lg border p-3 text-left transition ${
                    selectedOutput?.id === output.id ? "border-blue-200 bg-blue-50/50" : "border-line hover:bg-slate-50"
                  }`}
                  key={output.id}
                  onClick={() => setSelectedOutputId(output.id)}
                  type="button"
                >
                  <div className="flex items-start justify-between gap-3">
                    <p className="font-medium">{output.title}</p>
                    <span className="rounded-full bg-white px-2 py-1 text-xs text-muted">{outputLabels[output.type]}</span>
                  </div>
                  <p className="mt-2 text-xs text-muted">{new Date(output.createdAt).toLocaleString()}</p>
                </button>
              ))}
              {!loading && outputs.length === 0 ? (
                <div className="rounded-lg border border-dashed border-line p-5 text-center text-sm text-muted">
                  暂无复盘输出。
                </div>
              ) : null}
            </div>
          </div>
        </section>

        <section className="rounded-lg border border-line bg-white p-5 shadow-panel">
          <div className="mb-4 flex items-center justify-between gap-4">
            <div>
              <h2 className="font-semibold">{selectedOutput?.title ?? "输出预览"}</h2>
              <p className="text-sm text-muted">
                {selectedProject?.name ?? "选择项目"} 的复盘 Markdown，可复制或下载。
              </p>
            </div>
            <FileText className="h-5 w-5 text-brand" />
          </div>

          {selectedOutput ? (
            <>
              <div className="mb-4 flex flex-wrap gap-3">
                <button
                  className="flex items-center gap-2 rounded-lg border border-line bg-white px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50"
                  onClick={handleCopy}
                  type="button"
                >
                  <Clipboard className="h-4 w-4" />
                  {copyState || "复制 Markdown"}
                </button>
                <button
                  className="flex items-center gap-2 rounded-lg bg-slate-950 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-800"
                  onClick={handleDownload}
                  type="button"
                >
                  <Download className="h-4 w-4" />
                  下载 .md
                </button>
              </div>
              <pre className="min-h-[680px] overflow-auto whitespace-pre-wrap rounded-lg bg-slate-950 p-5 text-sm leading-7 text-slate-100">
                {selectedOutput.content}
              </pre>
            </>
          ) : (
            <div className="grid min-h-[680px] place-items-center rounded-lg border border-dashed border-line p-8 text-center">
              <div>
                <p className="font-semibold text-slate-950">还没有可用输出</p>
                <p className="mt-2 text-sm leading-6 text-muted">
                  先选择项目并生成一种复盘材料。输出会保存，避免重复生成。
                </p>
              </div>
            </div>
          )}
        </section>
      </div>
    </AppShell>
  );
}
