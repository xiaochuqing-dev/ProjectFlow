"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { ArrowLeft, Clipboard, FileText } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { Badge, Card } from "@/components/ui";
import { changeDisplayTitle, compactPath, isRuntimeArtifact, parseAffectedFiles } from "@/components/tasks/change-review-utils";
import { getProjectChange, type ProjectChange } from "@/lib/api";
import { readSession } from "@/lib/auth";

export default function ProjectChangeEvidencePage() {
  const params = useParams<{ changeId: string }>();
  const router = useRouter();
  const [change, setChange] = useState<ProjectChange | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [copied, setCopied] = useState("");

  useEffect(() => {
    const session = readSession();
    if (!session) {
      setError("请先登录后再查看完整证据。");
      setLoading(false);
      return;
    }

    getProjectChange(session.accessToken, params.changeId)
      .then(setChange)
      .catch((exception) => setError(exception instanceof Error ? exception.message : "完整证据加载失败"))
      .finally(() => setLoading(false));
  }, [params.changeId]);

  const fileGroups = useMemo(() => groupFilesByModule(parseAffectedFiles(change?.affectedFiles ?? "")), [change?.affectedFiles]);

  async function copyPath(path: string) {
    try {
      await navigator.clipboard.writeText(path);
      setCopied(path);
      window.setTimeout(() => setCopied(""), 1400);
    } catch {
      setCopied("");
    }
  }

  return (
    <AppShell eyebrow="变更追溯" title="完整证据">
      <div className="min-h-[calc(100vh-4rem)] bg-surface p-6">
        <section className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-md border border-line bg-white p-4 shadow-panel">
          <div className="min-w-0">
            <button className="mb-2 inline-flex items-center gap-1 text-sm font-semibold text-slate-600 hover:text-slate-950" onClick={() => router.back()} type="button">
              <ArrowLeft className="h-4 w-4" />
              返回结构化变更详情
            </button>
            <h2 className="truncate text-xl font-semibold text-slate-950">{change ? changeDisplayTitle(change) : "完整证据"}</h2>
            <p className="mt-1 text-sm text-muted">这里承载完整路径、Git evidence、测试证据、构建证据和来源材料。</p>
          </div>
          {change ? (
            <Link className="rounded-md border border-line px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50" href={`/project-changes/${change.id}`}>
              返回审查页
            </Link>
          ) : null}
        </section>

        {error ? <div className="mb-5 rounded-md border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">{error}</div> : null}
        {loading ? <div className="h-1 bg-slate-950" /> : null}

        {change ? (
          <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_340px]">
            <div className="space-y-5">
              <Card shadow="card">
                <div className="border-b border-line p-5">
                  <div className="flex items-center gap-2">
                    <FileText className="h-4 w-4 text-slate-700" />
                    <h3 className="font-semibold text-slate-950">完整文件列表</h3>
                  </div>
                  <p className="mt-1 text-sm text-muted">按模块分组。运行产物会被标记为噪音，不作为主叙事。</p>
                </div>
                <div className="divide-y divide-line">
                  {fileGroups.map((group) => (
                    <section className="p-5" key={group.name}>
                      <div className="mb-3 flex flex-wrap items-center gap-2">
                        <Badge label={`${group.label} · ${group.files.length}`} tone={group.runtime ? "warning" : "slate"} />
                        {group.runtime ? <span className="text-xs text-muted">运行产物或依赖目录</span> : null}
                      </div>
                      <div className="space-y-2">
                        {group.files.map((file) => (
                          <div className="flex items-center justify-between gap-3 rounded-md border border-line bg-slate-50 px-3 py-2" key={file}>
                            <code className="min-w-0 truncate text-xs text-slate-700" title={file}>{compactPath(file)}</code>
                            <button
                              className="inline-flex shrink-0 items-center gap-1 rounded-md border border-line bg-white px-2 py-1 text-xs font-semibold text-slate-600 hover:bg-slate-100"
                              onClick={() => copyPath(file)}
                              type="button"
                            >
                              <Clipboard className="h-3.5 w-3.5" />
                              {copied === file ? "已复制" : "复制路径"}
                            </button>
                          </div>
                        ))}
                      </div>
                    </section>
                  ))}
                  {fileGroups.length === 0 ? <p className="p-5 text-sm text-muted">未记录影响文件。</p> : null}
                </div>
              </Card>

              <EvidenceSection title="Git evidence" value={change.details} />
              <EvidenceSection title="测试证据" value={change.testEvidence} />
              <EvidenceSection title="构建证据" value={change.buildEvidence} />
              <EvidenceSection title="风险备注" value={change.riskNotes} />
              <EvidenceSection title="来源材料" value={change.sourceRef || change.sourceType} />
            </div>

            <aside className="space-y-5">
              <Card shadow="card" padding="md">
                <h3 className="font-semibold text-slate-950">追溯说明</h3>
                <p className="mt-2 text-sm leading-6 text-slate-600">
                  审查页用于判断是否采纳；本页用于事后核查完整证据。这里可以很长，但不会挤占变更列表和项目资产入口。
                </p>
              </Card>
              <Card shadow="card" padding="md">
                <h3 className="font-semibold text-slate-950">来源状态</h3>
                <dl className="mt-4 space-y-3 text-sm">
                  <InfoLine label="来源类型" value={change.sourceType} />
                  <InfoLine label="状态" value={change.status} />
                  <InfoLine label="更新时间" value={new Date(change.updatedAt).toLocaleString()} />
                </dl>
              </Card>
            </aside>
          </div>
        ) : !loading ? (
          <section className="rounded-md border border-line bg-white p-8 text-center text-sm text-muted shadow-panel">
            没有找到这条变更证据。
          </section>
        ) : null}
      </div>
    </AppShell>
  );
}

function EvidenceSection({ title, value }: { title: string; value: string }) {
  return (
    <Card shadow="card">
      <div className="flex items-center justify-between gap-3 border-b border-line p-5">
        <h3 className="font-semibold text-slate-950">{title}</h3>
        {!value || value.includes("未采集") ? <Badge label="未生成" tone="warning" /> : <Badge label="已记录" tone="success" />}
      </div>
      <pre className="max-h-[520px] overflow-auto whitespace-pre-wrap p-5 text-sm leading-7 text-slate-700">
        {value || "未生成。"}
      </pre>
    </Card>
  );
}

function InfoLine({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs text-muted">{label}</dt>
      <dd className="mt-1 break-all font-medium text-slate-800">{value}</dd>
    </div>
  );
}

function groupFilesByModule(files: string[]) {
  const groups = new Map<string, { label: string; runtime: boolean; files: string[] }>();
  for (const file of files) {
    const group = fileGroup(file);
    const existing = groups.get(group.name) ?? { label: group.label, runtime: group.runtime, files: [] };
    existing.files.push(file);
    groups.set(group.name, existing);
  }
  return Array.from(groups.entries()).map(([name, group]) => ({ name, ...group }));
}

function fileGroup(path: string) {
  const normalized = path.replace(/\\/g, "/").toLowerCase();
  if (isRuntimeArtifact(path)) return { name: "runtime", label: "运行产物", runtime: true };
  if (normalized.startsWith("frontend/") || normalized.includes("/frontend/") || normalized.includes("/src/app/")) return { name: "frontend", label: "前端", runtime: false };
  if (normalized.startsWith("backend/") || normalized.includes("/backend/") || normalized.includes("/src/main/java/")) return { name: "backend", label: "后端", runtime: false };
  if (normalized.startsWith("docs/") || normalized.endsWith(".md")) return { name: "docs", label: "文档", runtime: false };
  if (normalized.includes("/test/") || normalized.includes("/tests/") || normalized.includes(".test.") || normalized.includes(".spec.")) return { name: "tests", label: "测试", runtime: false };
  if (normalized.endsWith(".yml") || normalized.endsWith(".yaml") || normalized.endsWith(".json") || normalized.endsWith(".toml") || normalized.endsWith(".xml")) return { name: "config", label: "配置", runtime: false };
  return { name: "other", label: "其他", runtime: false };
}
