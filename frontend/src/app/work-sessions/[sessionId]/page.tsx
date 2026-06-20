"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { ArrowLeft, FileCode2, GitCommitHorizontal } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { listProjectWorkSessions, type WorkSessionCandidate } from "@/lib/api";
import { readSession } from "@/lib/auth";
import { compactProjectPath } from "@/lib/project-insights";

export default function WorkSessionDetailPage() {
  const params = useParams<{ sessionId: string }>();
  const searchParams = useSearchParams();
  const router = useRouter();
  const projectId = searchParams.get("projectId") ?? "";
  const [sessions, setSessions] = useState<WorkSessionCandidate[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const session = useMemo(
    () => sessions.find((item) => item.sessionId === params.sessionId) ?? null,
    [params.sessionId, sessions],
  );

  useEffect(() => {
    const auth = readSession();
    if (!auth || !projectId) {
      setLoading(false);
      setError("缺少项目参数，无法读取变化详情。");
      return;
    }
    listProjectWorkSessions(auth.accessToken, projectId)
      .then(setSessions)
      .catch((exception) => setError(exception instanceof Error ? exception.message : "变化详情加载失败"))
      .finally(() => setLoading(false));
  }, [projectId]);

  return (
    <AppShell eyebrow="变化详情" title={session?.taskIntent ?? "工作会话详情"}>
      <div className="min-h-[calc(100vh-4rem)] bg-surface p-6">
        <section className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-md border border-line bg-white p-4 shadow-panel">
          <div>
            <button className="mb-2 inline-flex items-center gap-1 text-sm font-semibold text-slate-600 hover:text-slate-950" onClick={() => router.back()} type="button">
              <ArrowLeft className="h-4 w-4" />
              返回上一步
            </button>
            <h2 className="text-xl font-semibold text-slate-950">{session?.taskIntent ?? "变化详情"}</h2>
          </div>
          <Link className="rounded-md border border-line px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50" href="/dashboard">
            回到工作台
          </Link>
        </section>

        {error ? <div className="mb-5 rounded-md border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">{error}</div> : null}
        {loading ? <div className="h-1 bg-slate-950" /> : null}

        {session ? (
          <section className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_320px]">
            <div className="space-y-5">
              <Card title="变化概览">
                <p className="text-sm leading-7 text-slate-700">
                  本轮变化覆盖 {session.changedFiles} 个文件，新增 {session.addedLines} 行、删除 {session.deletedLines} 行。
                  主要模块：{session.affectedModules.length ? session.affectedModules.join("、") : "未明确"}。
                  这份详情用于判断本轮工作是否应该生成证据包、是否需要进入变更审查，以及后续是否可沉淀到项目档案。
                </p>
              </Card>

              <Card title="变更文件">
                <div className="grid gap-2 md:grid-cols-2">
                  {session.files.map((file) => (
                    <div className="rounded-md border border-line bg-slate-50 p-3" key={file}>
                      <div className="mb-2 flex items-center gap-2 text-xs font-semibold text-slate-500">
                        <FileCode2 className="h-4 w-4" />
                        文件
                      </div>
                      <p className="break-all font-mono text-sm leading-6 text-slate-800">{compactProjectPath(file)}</p>
                    </div>
                  ))}
                </div>
              </Card>

              <Card title="Git 证据">
                <div className="space-y-3">
                  {session.evidence.map((item) => (
                    <div className="flex gap-2 rounded-md bg-slate-50 p-3 text-sm leading-6 text-slate-700" key={item}>
                      <GitCommitHorizontal className="mt-1 h-4 w-4 shrink-0 text-slate-500" />
                      <span>{item}</span>
                    </div>
                  ))}
                </div>
              </Card>
            </div>

            <aside className="space-y-3">
              <Metric label="文件" value={`${session.changedFiles}`} />
              <Metric label="新增" value={`+${session.addedLines}`} />
              <Metric label="删除" value={`-${session.deletedLines}`} />
              <Metric label="来源" value={session.detectionMethod === "USER_CORRECTED" ? "人工校正" : "Git 证据"} />
            </aside>
          </section>
        ) : !loading ? (
          <section className="rounded-md border border-line bg-white p-8 text-center text-sm text-muted shadow-panel">
            没有找到这条变化记录。请返回工作台刷新变化后再查看。
          </section>
        ) : null}
      </div>
    </AppShell>
  );
}

function Card({ children, title }: { children: React.ReactNode; title: string }) {
  return (
    <section className="rounded-md border border-line bg-white shadow-panel">
      <div className="border-b border-line px-5 py-4">
        <h3 className="font-semibold text-slate-950">{title}</h3>
      </div>
      <div className="p-5">{children}</div>
    </section>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-md border border-line bg-white p-4 shadow-panel">
      <p className="text-sm text-muted">{label}</p>
      <p className="mt-2 break-all text-xl font-semibold text-slate-950">{value}</p>
    </div>
  );
}
