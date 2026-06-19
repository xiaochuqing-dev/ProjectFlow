"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { ArrowLeft, DatabaseZap, RefreshCw, ShieldCheck, Trash2 } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import {
  deleteProjectAnalysisRecord,
  getProjectAnalysisRecord,
  type ProjectAnalysisRecord,
} from "@/lib/api";
import { readSession } from "@/lib/auth";

export default function AnalysisRecordDetailPage() {
  const params = useParams<{ recordId: string }>();
  const router = useRouter();
  const [record, setRecord] = useState<ProjectAnalysisRecord | null>(null);
  const [loading, setLoading] = useState(true);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    const session = readSession();
    if (!session) {
      setLoading(false);
      setError("请先登录后再查看分析记录。");
      return;
    }

    setLoading(true);
    setError("");
    getProjectAnalysisRecord(session.accessToken, params.recordId)
      .then(setRecord)
      .catch((exception) => setError(exception instanceof Error ? exception.message : "分析记录加载失败"))
      .finally(() => setLoading(false));
  }, [params.recordId]);

  async function handleDelete() {
    const session = readSession();
    if (!session || !record) {
      return;
    }

    setDeleting(true);
    setError("");
    try {
      await deleteProjectAnalysisRecord(session.accessToken, record.id);
      router.push("/project-intelligence");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "分析记录删除失败");
    } finally {
      setDeleting(false);
    }
  }

  return (
    <AppShell eyebrow="分析记录" title="记录详情">
      <div className="min-h-[calc(100vh-4rem)] bg-surface p-6">
        <section className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-md border border-line bg-white p-4 shadow-panel">
          <div className="min-w-0">
            <Link className="mb-2 inline-flex items-center gap-1 text-sm font-semibold text-slate-600 hover:text-slate-950" href="/project-intelligence">
              <ArrowLeft className="h-4 w-4" />
              返回项目画像
            </Link>
            <h2 className="truncate text-lg font-semibold text-slate-950">
              {record ? recordTitle(record) : "分析记录"}
            </h2>
            <p className="mt-1 text-sm text-muted">
              集中查看单条分析记录，避免在工作台和侧栏堆叠长内容。
            </p>
          </div>
          {record ? (
            <button
              className="inline-flex items-center gap-2 rounded-md border border-rose-200 bg-rose-50 px-4 py-2 text-sm font-semibold text-rose-700 hover:bg-rose-100 disabled:opacity-60"
              disabled={deleting}
              onClick={handleDelete}
              type="button"
            >
              {deleting ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
              删除记录
            </button>
          ) : null}
        </section>

        {error ? <div className="mb-5 rounded-md border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">{error}</div> : null}
        {loading ? <div className="h-1 rounded-full bg-slate-950" /> : null}

        {record ? (
          <section className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_340px]">
            <article className="rounded-md border border-line bg-white shadow-panel">
              <div className="border-b border-line p-6">
                <div className="mb-3 flex flex-wrap items-center gap-2">
                  <span className="rounded-md bg-slate-950 px-2.5 py-1 text-xs font-semibold text-white">
                    {record.recordType === "FILE" ? "文件分析" : "项目分析"}
                  </span>
                  <span className={`rounded-md px-2.5 py-1 text-xs font-semibold ${record.modelUsed ? "bg-emerald-50 text-emerald-700" : "bg-amber-50 text-amber-800"}`}>
                    {record.modelUsed ? "模型参与" : "本地规则"}
                  </span>
                  <span className="rounded-md bg-slate-100 px-2.5 py-1 text-xs text-muted">{record.confidence}</span>
                </div>
                <h3 className="text-xl font-semibold leading-8 text-slate-950">{record.summary}</h3>
                {record.filePath ? <p className="mt-3 break-all font-mono text-xs text-muted">{record.filePath}</p> : null}
              </div>
              <div className="p-6">
                <p className="mb-3 text-sm font-semibold text-slate-950">完整内容</p>
                <pre className="whitespace-pre-wrap rounded-md border border-line bg-slate-50 p-4 text-sm leading-7 text-slate-700">
                  {record.details}
                </pre>
              </div>
            </article>

            <aside className="space-y-5">
              <div className="rounded-md border border-line bg-white p-5 shadow-panel">
                <div className="mb-3 flex items-center gap-2">
                  <DatabaseZap className="h-4 w-4 text-slate-700" />
                  <h3 className="font-semibold text-slate-950">来源</h3>
                </div>
                <dl className="space-y-3 text-sm">
                  <InfoRow label="分析来源" value={record.analysisSource} />
                  <InfoRow label="模型" value={record.providerName ?? "未使用 API"} />
                  <InfoRow label="创建时间" value={new Date(record.createdAt).toLocaleString()} />
                </dl>
              </div>

              <div className="rounded-md border border-line bg-white p-5 shadow-panel">
                <div className="mb-3 flex items-center gap-2">
                  <ShieldCheck className="h-4 w-4 text-emerald-600" />
                  <h3 className="font-semibold text-slate-950">处理原则</h3>
                </div>
                <p className="text-sm leading-6 text-slate-600">
                  这条记录是分析结果，不会自动覆盖项目档案。需要进入项目画像页手动确认后，才应成为后续回顾、成果输出或 agent 上下文的正式来源。
                </p>
              </div>
            </aside>
          </section>
        ) : !loading && !error ? (
          <section className="grid min-h-80 place-items-center rounded-md border border-line bg-white p-8 text-center text-sm text-muted shadow-panel">
            未找到分析记录。
          </section>
        ) : null}
      </div>
    </AppShell>
  );
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-start justify-between gap-4 border-b border-line pb-3 last:border-0 last:pb-0">
      <dt className="text-muted">{label}</dt>
      <dd className="text-right font-medium text-slate-950">{value}</dd>
    </div>
  );
}

function recordTitle(record: ProjectAnalysisRecord) {
  if (record.recordType === "FILE") {
    return record.filePath ?? "文件分析记录";
  }
  return "项目分析记录";
}
