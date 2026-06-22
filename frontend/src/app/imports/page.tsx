"use client";

import { FormEvent, useEffect, useState } from "react";
import { AlertTriangle, CheckCircle2, ClipboardPaste, FileDown, Layers3, RefreshCw } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { useProjectSelection } from "@/hooks/useProjectSelection";
import {
  confirmMarkdownImport,
  listImportRecords,
  listTasks,
  previewMarkdownImport,
  type ImportRecord,
  type MarkdownPreview,
  type TaskItem,
} from "@/lib/api";
import { readSession } from "@/lib/auth";

const sampleMarkdown = `---
title: ProjectFlow 导入页开发
date: 2026-06-05
category: feature
source: codex
tags: frontend,import
minutes: 90
---
# ProjectFlow 导入页开发

## 完成
- 完成 Markdown 解析预览。
- 将确认导入写入结构化开发日志。

## 技术决策
- V1 不引入第三方 Markdown 解析依赖，先覆盖稳定字段。

## 阻塞
- 后续需要接入真实 AI 输出格式。

## 下一步
- 用导入记录支撑 AI 复盘材料。`;

export default function ImportsPage() {
  const { projects, selectedProject, selectedProjectId, selectProject, loadingProjects, projectError } = useProjectSelection();
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [records, setRecords] = useState<ImportRecord[]>([]);
  const [selectedTaskId, setSelectedTaskId] = useState("");
  const [markdown, setMarkdown] = useState(sampleMarkdown);
  const [preview, setPreview] = useState<MarkdownPreview | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [previewing, setPreviewing] = useState(false);
  const [confirming, setConfirming] = useState(false);

  useEffect(() => {
    const session = readSession();
    if (!session || !selectedProjectId) {
      setTasks([]);
      setRecords([]);
      setLoading(false);
      return;
    }

    setPreview(null);
    setSelectedTaskId("");
    setLoading(true);
    Promise.all([
      listTasks(session.accessToken, selectedProjectId),
      listImportRecords(session.accessToken, selectedProjectId),
    ])
      .then(([taskItems, importItems]) => {
        setTasks(taskItems);
        setRecords(importItems);
      })
      .catch((exception) => setError(exception instanceof Error ? exception.message : "导入上下文加载失败"))
      .finally(() => setLoading(false));
  }, [selectedProjectId]);

  async function handlePreview(event?: FormEvent<HTMLFormElement>) {
    event?.preventDefault();
    const session = readSession();
    if (!session || !selectedProjectId) {
      return;
    }

    setPreviewing(true);
    setError("");
    try {
      const result = await previewMarkdownImport(session.accessToken, selectedProjectId, markdown);
      setPreview(result);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Markdown 解析失败");
    } finally {
      setPreviewing(false);
    }
  }

  async function handleConfirm() {
    const session = readSession();
    if (!session || !selectedProjectId || !preview) {
      return;
    }

    setConfirming(true);
    setError("");
    try {
      await confirmMarkdownImport(session.accessToken, selectedProjectId, selectedTaskId || null, markdown);
      const [recordItems] = await Promise.all([
        listImportRecords(session.accessToken, selectedProjectId),
      ]);
      setRecords(recordItems);
      setPreview(null);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "确认导入失败");
    } finally {
      setConfirming(false);
    }
  }

  return (
    <AppShell eyebrow="Markdown 导入" title="导入开发日志">
      <div className="grid min-h-[calc(100vh-4rem)] gap-6 p-8 xl:grid-cols-[420px_1fr]">
        <section className="space-y-4">
          <div className="rounded-lg border border-line bg-white p-5 shadow-panel">
            <div className="mb-4 flex items-center gap-3">
              <div className="grid h-10 w-10 place-items-center rounded-xl bg-slate-900 text-white">
                <Layers3 className="h-5 w-5" />
              </div>
              <div>
                <h2 className="font-semibold">选择导入位置</h2>
                <p className="text-sm text-muted">Markdown 会转成当前项目的开发日志。</p>
              </div>
            </div>

            <div className="space-y-3">
              <select
                className="w-full rounded-lg border border-line bg-white px-3 py-2 text-sm outline-none focus:border-brand"
                disabled={projects.length === 0}
                onChange={(event) => selectProject(event.target.value)}
                value={selectedProjectId}
              >
                {projects.map((project) => (
                  <option key={project.id} value={project.id}>
                    {project.name}
                  </option>
                ))}
              </select>
              <select
                className="w-full rounded-lg border border-line bg-white px-3 py-2 text-sm outline-none focus:border-brand"
                disabled={tasks.length === 0}
                onChange={(event) => setSelectedTaskId(event.target.value)}
                value={selectedTaskId}
              >
                <option value="">项目级日志</option>
                {tasks.map((task) => (
                  <option key={task.id} value={task.id}>
                    {task.title}
                  </option>
                ))}
              </select>
            </div>

            <div className="mt-4 rounded-lg bg-slate-50 p-4">
              <p className="text-sm font-medium">{selectedProject?.name ?? "暂无项目"}</p>
              <p className="mt-1 line-clamp-3 text-sm text-muted">
                {selectedProject?.description ?? "先创建项目，再导入开发过程。"}
              </p>
            </div>
          </div>

          <div className="rounded-lg border border-line bg-white p-5 shadow-panel">
            <h2 className="font-semibold">导入记录</h2>
            <p className="mt-1 text-sm text-muted">确认导入后会在这里留下来源记录。</p>
            <div className="mt-4 space-y-3">
              {records.map((record) => (
                <article className="rounded-lg border border-line p-3" key={record.id}>
                  <div className="flex items-start justify-between gap-3">
                    <p className="font-medium">{record.title}</p>
                    <span className="rounded-full bg-slate-100 px-2 py-1 text-xs text-muted">{record.source}</span>
                  </div>
                  <p className="mt-2 text-xs text-muted">{new Date(record.createdAt).toLocaleString()}</p>
                </article>
              ))}
              {!loading && !loadingProjects && records.length === 0 ? (
                <div className="rounded-lg border border-dashed border-line p-5 text-center text-sm text-muted">
                  暂无导入记录。
                </div>
              ) : null}
            </div>
          </div>
        </section>

        <section className="grid gap-6 xl:grid-cols-[1fr_0.9fr]">
          <form className="rounded-lg border border-line bg-white p-5 shadow-panel" onSubmit={handlePreview}>
            <div className="mb-4 flex items-center justify-between gap-4">
              <div>
                <h2 className="font-semibold">粘贴 Markdown</h2>
                <p className="text-sm text-muted">支持 front matter、完成项、阻塞、下一步等小节。</p>
              </div>
              <ClipboardPaste className="h-5 w-5 text-brand" />
            </div>
            <textarea
              className="min-h-[560px] w-full resize-none rounded-lg border border-line bg-slate-950 p-4 font-mono text-sm leading-6 text-slate-100 outline-none focus:border-brand"
              onChange={(event) => setMarkdown(event.target.value)}
              value={markdown}
            />
            {error || projectError ? <p className="mt-3 text-sm text-rose-600">{error || projectError}</p> : null}
            <button
              className="mt-4 flex w-full items-center justify-center gap-2 rounded-lg bg-brand px-4 py-2.5 text-sm font-semibold text-white hover:bg-blue-600 disabled:opacity-60"
              disabled={previewing || !selectedProjectId}
              type="submit"
            >
              {previewing ? <RefreshCw className="h-4 w-4 animate-spin" /> : <FileDown className="h-4 w-4" />}
              {previewing ? "解析中..." : "解析预览"}
            </button>
          </form>

          <div className="rounded-lg border border-line bg-white p-5 shadow-panel">
            <div className="mb-4 flex items-center justify-between gap-4">
              <div>
                <h2 className="font-semibold">结构化预览</h2>
                <p className="text-sm text-muted">确认字段后写入开发日志。</p>
              </div>
              <CheckCircle2 className="h-5 w-5 text-emerald-600" />
            </div>

            {preview ? (
              <div className="space-y-4">
                <div className="rounded-lg bg-slate-50 p-4">
                  <p className="text-sm text-muted">标题</p>
                  <p className="mt-1 font-semibold">{preview.title}</p>
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <div className="rounded-lg bg-slate-50 p-4">
                    <p className="text-sm text-muted">日期</p>
                    <p className="mt-1 font-semibold">{preview.logDate}</p>
                  </div>
                  <div className="rounded-lg bg-slate-50 p-4">
                    <p className="text-sm text-muted">类型</p>
                    <p className="mt-1 font-semibold">{preview.category}</p>
                  </div>
                  <div className="rounded-lg bg-slate-50 p-4">
                    <p className="text-sm text-muted">耗时</p>
                    <p className="mt-1 font-semibold">{preview.minutesSpent} 分钟</p>
                  </div>
                  <div className="rounded-lg bg-slate-50 p-4">
                    <p className="text-sm text-muted">阻塞</p>
                    <p className="mt-1 font-semibold">{preview.blocked ? "有" : "无"}</p>
                  </div>
                </div>
                <div>
                  <p className="mb-2 text-sm text-muted">标签</p>
                  <div className="flex flex-wrap gap-2">
                    {preview.tags.map((tag) => (
                      <span className="rounded-md bg-slate-100 px-2 py-1 text-xs text-slate-600" key={tag}>
                        {tag}
                      </span>
                    ))}
                  </div>
                </div>
                {preview.warnings.length > 0 ? (
                  <div className="rounded-lg border border-amber-200 bg-amber-50 p-4">
                    <div className="mb-2 flex items-center gap-2 text-sm font-semibold text-amber-800">
                      <AlertTriangle className="h-4 w-4" />
                      解析提示
                    </div>
                    <ul className="space-y-1 text-sm text-amber-800">
                      {preview.warnings.map((warning) => (
                        <li key={warning}>{warning}</li>
                      ))}
                    </ul>
                  </div>
                ) : null}
                <pre className="max-h-72 overflow-auto rounded-lg bg-slate-950 p-4 text-xs leading-5 text-slate-100">
                  {preview.content}
                </pre>
                <button
                  className="flex w-full items-center justify-center gap-2 rounded-lg bg-slate-950 px-4 py-2.5 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
                  disabled={confirming}
                  onClick={handleConfirm}
                  type="button"
                >
                  {confirming ? <RefreshCw className="h-4 w-4 animate-spin" /> : <CheckCircle2 className="h-4 w-4" />}
                  {confirming ? "写入中..." : "确认写入开发日志"}
                </button>
              </div>
            ) : (
              <div className="grid min-h-[560px] place-items-center rounded-lg border border-dashed border-line p-8 text-center">
                <div>
                  <p className="font-semibold text-slate-950">先解析，再确认导入</p>
                  <p className="mt-2 text-sm leading-6 text-muted">
                    左侧粘贴 Codex、Claude Code 或手写 Markdown 日志，系统会先展示结构化预览。
                  </p>
                </div>
              </div>
            )}
          </div>
        </section>
      </div>
    </AppShell>
  );
}
