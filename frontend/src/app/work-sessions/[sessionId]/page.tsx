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
              <ChangeIntentCard session={session} />
              <FileChangeSummary session={session} />

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

              <EvidenceTimeline evidence={session.evidence} />
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

function ChangeIntentCard({ session }: { session: WorkSessionCandidate }) {
  return (
    <Card title="具体改了什么">
      <div className="grid gap-3 md:grid-cols-[minmax(0,1fr)_220px]">
        <div>
          <p className="text-base font-semibold leading-7 text-slate-950">{readableSessionTitle(session)}</p>
          <p className="mt-2 text-sm leading-7 text-slate-700">
            本轮变化覆盖 {session.changedFiles} 个文件，新增 {session.addedLines} 行，删除 {session.deletedLines} 行。
            主要影响 {session.affectedModules.length ? session.affectedModules.join("、") : "未识别模块"}。
          </p>
          <p className="mt-2 text-sm leading-7 text-slate-600">
            {primaryEvidenceSentence(session)}
          </p>
        </div>
        <div className="rounded-md border border-line bg-slate-50 p-3 text-sm">
          <p className="text-xs text-muted">判断用途</p>
          <p className="mt-1 leading-6 text-slate-700">确认是否生成证据包，并决定是否进入变更审查。</p>
        </div>
      </div>
    </Card>
  );
}

function FileChangeSummary({ session }: { session: WorkSessionCandidate }) {
  const groups = summarizeFiles(session.files);
  return (
    <Card title="影响范围">
      <div className="grid gap-3 md:grid-cols-3">
        {groups.map((group) => (
          <div className="rounded-md border border-line bg-slate-50 p-3" key={group.label}>
            <div className="mb-2 flex items-center justify-between gap-2">
              <p className="text-sm font-semibold text-slate-950">{group.label}</p>
              <span className="rounded-full bg-white px-2 py-0.5 text-xs text-slate-600">{group.count}</span>
            </div>
            <p className="line-clamp-2 text-xs leading-5 text-slate-600">{group.hint}</p>
          </div>
        ))}
      </div>
    </Card>
  );
}

function EvidenceTimeline({ evidence }: { evidence: string[] }) {
  return (
    <Card title="Git 证据">
      <div className="space-y-3">
        {evidence.length ? evidence.map((item, index) => (
          <div className="grid gap-3 rounded-md border border-line bg-slate-50 p-3 text-sm md:grid-cols-[32px_minmax(0,1fr)]" key={`${item}-${index}`}>
            <div className="grid h-8 w-8 place-items-center rounded-full bg-white text-slate-600">
              <GitCommitHorizontal className="h-4 w-4" />
            </div>
            <div className="min-w-0">
              <p className="text-xs font-semibold text-slate-500">证据 {index + 1}</p>
              <p className="mt-1 break-words leading-6 text-slate-700">{item}</p>
            </div>
          </div>
        )) : (
          <p className="rounded-md bg-slate-50 p-3 text-sm text-muted">暂无 Git 证据。</p>
        )}
      </div>
    </Card>
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

function readableSessionTitle(session: WorkSessionCandidate) {
  if (session.taskIntent && !session.taskIntent.startsWith("Git commit") && !session.taskIntent.includes("WORKTREE")) {
    return session.taskIntent;
  }
  return `更新 ${session.affectedModules[0] ?? "项目"} 相关内容`;
}

function primaryEvidenceSentence(session: WorkSessionCandidate) {
  const firstEvidence = session.evidence.find((item) => item && !item.startsWith("提交线索"));
  if (firstEvidence) {
    return firstEvidence;
  }
  return `改动集中在 ${session.files.slice(0, 3).map(compactProjectPath).join("、") || "未识别文件"}。`;
}

function summarizeFiles(files: string[]) {
  const groups = [
    { label: "界面与交互", count: files.filter((file) => /frontend|app|components|\.tsx$|\.jsx$|\.css$/.test(file)).length, hint: "可能影响页面显示、按钮行为或用户流程。" },
    { label: "后端与数据", count: files.filter((file) => /backend|controller|service|repository|entity|\.java$|\.py$/.test(file)).length, hint: "可能影响接口、业务规则或数据写入。" },
    { label: "文档与配置", count: files.filter((file) => /docs|README|\.md$|package\.json|pom\.xml|\.yml$|\.json$/.test(file)).length, hint: "可能影响说明、依赖、启动或构建。" },
  ];
  const known = groups.reduce((total, group) => total + group.count, 0);
  if (files.length > known) {
    groups.push({ label: "其他文件", count: files.length - known, hint: "需要结合文件路径判断具体影响。" });
  }
  return groups.filter((group) => group.count > 0);
}
