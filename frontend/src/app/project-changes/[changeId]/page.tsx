"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { ArrowLeft, ArrowRight, Check, RefreshCw, Save, Trash2 } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { Badge, Button, Card, InfoBubble } from "@/components/ui";
import { archiveTargetsLabel, changeDisplayTitle, changeOutcomeSummary, compactPath, parseAffectedFiles } from "@/components/tasks/change-review-utils";
import {
  acceptProjectChange,
  getProjectChange,
  ignoreProjectChange,
  updateProjectChange,
  type ProjectChange,
  type ProjectChangePayload,
} from "@/lib/api";
import { readSession } from "@/lib/auth";

const changeKindLabels: Record<ProjectChangePayload["changeKind"], string> = {
  CAPABILITY: "能力",
  BUGFIX: "修复",
  REFACTOR: "重构",
  CONFIG: "配置",
  DOCS: "文档",
  TEST: "测试",
  RISK: "风险",
  DECISION: "决策",
  LEARNING: "经验",
  ASSET: "素材",
  UNKNOWN: "待判断",
};

const impactLabels: Record<ProjectChangePayload["impactLevel"], string> = {
  MAJOR: "主要",
  MINOR: "次要",
  MAINTENANCE: "维护",
  UNCERTAIN: "待判断",
};

type ChangeEditState = ProjectChangePayload;

export default function ProjectChangeDetailPage() {
  const params = useParams<{ changeId: string }>();
  const router = useRouter();
  const [change, setChange] = useState<ProjectChange | null>(null);
  const [draft, setDraft] = useState<ChangeEditState | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [acting, setActing] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  useEffect(() => {
    const session = readSession();
    if (!session) {
      setError("请先登录后再查看建议沉淀。");
      setLoading(false);
      return;
    }

    getProjectChange(session.accessToken, params.changeId)
      .then((item) => {
        setChange(item);
        setDraft(toPayload(item));
      })
      .catch((exception) => setError(exception instanceof Error ? exception.message : "建议沉淀加载失败"))
      .finally(() => setLoading(false));
  }, [params.changeId]);

  async function handleSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const session = readSession();
    if (!session || !draft) return;

    setSaving(true);
    setError("");
    setNotice("");
    try {
      const updated = await updateProjectChange(session.accessToken, params.changeId, draft);
      setChange(updated);
      setDraft(toPayload(updated));
      setNotice("修正已保存。确认时会使用修正后的内容写入项目沉淀。");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "建议沉淀保存失败");
    } finally {
      setSaving(false);
    }
  }

  async function handleAccept() {
    const session = readSession();
    if (!session) return;

    setActing("accept");
    setError("");
    setNotice("");
    try {
      const updated = await acceptProjectChange(session.accessToken, params.changeId);
      setChange(updated);
      setDraft(toPayload(updated));
      setNotice("已确认。项目沉淀、可信依据、项目时间线和输出来源会使用这条事实。");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "确认建议沉淀失败");
    } finally {
      setActing("");
    }
  }

  async function handleIgnore() {
    const session = readSession();
    if (!session) return;

    setActing("ignore");
    setError("");
    setNotice("");
    try {
      const updated = await ignoreProjectChange(session.accessToken, params.changeId);
      setChange(updated);
      setDraft(toPayload(updated));
      setNotice("已忽略。这不会删除原始证据，只会移出待确认队列。");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "忽略建议沉淀失败");
    } finally {
      setActing("");
    }
  }

  function updateField<K extends keyof ChangeEditState>(key: K, value: ChangeEditState[K]) {
    setDraft((current) => current && { ...current, [key]: value });
  }

  return (
    <AppShell eyebrow="自动化审查" title={change ? changeDisplayTitle(change) : "待确认内容详情"}>
      <div className="min-h-[calc(100vh-4rem)] bg-surface p-6">
        <section className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-md border border-line bg-white p-4 shadow-panel">
          <div className="min-w-0">
            <button className="mb-2 inline-flex items-center gap-1 text-sm font-semibold text-slate-600 hover:text-slate-950" onClick={() => router.back()} type="button">
              <ArrowLeft className="h-4 w-4" />
              返回上一步
            </button>
            <h2 className="truncate text-xl font-semibold text-slate-950">待确认内容详情</h2>
            {change ? (
              <p className="mt-1 text-sm text-muted">
                {new Date(change.createdAt).toLocaleString()} · {statusLabel(change.status)} · {sourceLabel(change.sourceType)}
              </p>
            ) : null}
          </div>
          <div className="flex flex-wrap gap-2">
            <Button disabled={!change || acting !== "" || change.status === "ACCEPTED"} loading={acting === "accept"} onClick={handleAccept} variant="primary">
              {acting === "accept" ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />}
              确认沉淀
            </Button>
            <Button disabled={!change || acting !== "" || change.status === "IGNORED"} loading={acting === "ignore"} onClick={handleIgnore} variant="danger">
              {acting === "ignore" ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
              忽略
            </Button>
          </div>
        </section>

        {error ? <div className="mb-5 rounded-md border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">{error}</div> : null}
        {notice ? <div className="mb-5 rounded-md border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-700">{notice}</div> : null}
        {loading ? <div className="h-1 bg-slate-950" /> : null}

        {draft && change ? (
          <form className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_340px]" onSubmit={handleSave}>
            <div className="space-y-5">
              <Card shadow="card">
                <div className="border-b border-line p-5">
                  <p className="mb-2 text-sm font-semibold text-brand">沉淀确认</p>
                  <div className="mb-3 flex flex-wrap items-center gap-2">
                    <Badge label={changeKindLabels[draft.changeKind]} tone="brand" />
                    <Badge label={impactLabels[draft.impactLevel]} tone={draft.impactLevel === "MAJOR" ? "warning" : "slate"} />
                    <InfoBubble label={sourceLabel(change.sourceType)} />
                    <InfoBubble label={statusLabel(change.status)} />
                  </div>
                  <h3 className="text-lg font-semibold leading-7 text-slate-950">{changeDisplayTitle(draft)}</h3>
                  <p className="mt-3 whitespace-pre-line text-sm leading-7 text-slate-700">{changeOutcomeSummary({ ...change, ...draft })}</p>
                  <p className="mt-3 text-sm font-semibold text-slate-600">{archiveTargetsLabel({ ...change, ...draft })}</p>
                </div>
                <div className="grid gap-3 p-5 md:grid-cols-4">
                  <MiniEvidence title="涉及文件" value={fileCount(draft.affectedFiles)} />
                  <MiniEvidence title="测试证据" value={draft.testEvidence ? "已记录" : "未采集"} />
                  <MiniEvidence title="构建证据" value={draft.buildEvidence ? "已记录" : "未采集"} />
                  <MiniEvidence title="审查判断" value={reviewReadiness(draft)} />
                </div>
              </Card>

              <Card shadow="card">
                <div className="border-b border-line p-5">
                  <h3 className="font-semibold text-slate-950">审查判断</h3>
                  <p className="mt-1 text-sm text-muted">当前页只保留入库判断。完整文件、Git、测试和构建证据进入独立追溯页。</p>
                </div>
                <div className="grid gap-3 p-5 md:grid-cols-2">
                  <ReviewSignal label="是否可确认" value={reviewReadiness(draft)} />
                  <ReviewSignal label="缺少测试" value={draft.testEvidence && !draft.testEvidence.includes("未采集") ? "否" : "是，建议补充或人工确认"} />
                  <ReviewSignal label="缺少构建" value={draft.buildEvidence && !draft.buildEvidence.includes("未采集") ? "否" : "是，建议补充或人工确认"} />
                  <ReviewSignal label="关键文件" value={keyFilePreview(draft.affectedFiles)} />
                </div>
                <div className="border-t border-line p-5">
                  <Link href={`/project-changes/${change.id}/evidence`}>
                    <Button variant="secondary">
                      查看完整证据 <ArrowRight className="h-3.5 w-3.5" />
                    </Button>
                  </Link>
                </div>
              </Card>

              <Card shadow="card">
                <div className="border-b border-line p-5">
                  <h3 className="font-semibold text-slate-950">建议写入项目沉淀</h3>
                  <p className="mt-1 text-sm text-muted">确认后，这些候选会成为项目沉淀和后续输出的来源。</p>
                </div>
                <div className="grid gap-3 p-5 md:grid-cols-2">
                  {changeMemoryTargets(draft).map((target) => (
                    <ArchiveCandidate key={target} target={target} text={targetText(target, draft)} />
                  ))}
                </div>
              </Card>

              <Card shadow="card">
                <details>
                  <summary className="cursor-pointer p-5 font-semibold text-slate-950 hover:bg-slate-50">
                    修正 AI 总结
                  </summary>
                  <div className="space-y-4 border-t border-line p-5">
                    <div className="grid gap-3 md:grid-cols-2">
                      <SelectField label="变更类型" onChange={(value) => updateField("changeKind", value as ChangeEditState["changeKind"])} options={changeKindLabels} value={draft.changeKind} />
                      <SelectField label="影响级别" onChange={(value) => updateField("impactLevel", value as ChangeEditState["impactLevel"])} options={impactLabels} value={draft.impactLevel} />
                    </div>
                    <TextField label="标题" onChange={(value) => updateField("title", value)} value={draft.title} />
                    <TextArea label="摘要" onChange={(value) => updateField("summary", value)} rows={3} value={draft.summary} />
                    <TextArea label="变更细节" onChange={(value) => updateField("details", value)} rows={6} value={draft.details} />
                    <TextArea label="影响文件" mono onChange={(value) => updateField("affectedFiles", value)} rows={6} value={draft.affectedFiles} />
                    <TextArea label="关联任务" onChange={(value) => updateField("relatedTasks", value)} rows={3} value={draft.relatedTasks} />
                    <TextArea label="测试证据" onChange={(value) => updateField("testEvidence", value)} rows={3} value={draft.testEvidence} />
                    <TextArea label="构建证据" onChange={(value) => updateField("buildEvidence", value)} rows={3} value={draft.buildEvidence} />
                    <TextArea label="风险备注" onChange={(value) => updateField("riskNotes", value)} rows={3} value={draft.riskNotes} />
                    <TextArea label="技术决策" onChange={(value) => updateField("decisionNotes", value)} rows={3} value={draft.decisionNotes} />
                    <TextArea label="经验沉淀" onChange={(value) => updateField("learningNotes", value)} rows={3} value={draft.learningNotes} />
                    <TextArea label="成果素材" onChange={(value) => updateField("assetCandidates", value)} rows={3} value={draft.assetCandidates} />
                    <Button disabled={saving} loading={saving} type="submit" variant="secondary">
                      {saving ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
                      保存修正
                    </Button>
                  </div>
                </details>
              </Card>
            </div>

            <aside className="space-y-4">
              <Card shadow="card" padding="md">
                <h3 className="font-semibold text-slate-950">来源与去向</h3>
                <dl className="mt-4 space-y-3 text-sm">
                  <InfoLine label="来源" value={sourceLabel(change.sourceType)} />
                  <InfoLine label="来源引用" value={change.sourceRef || "无"} />
                  <InfoLine label="更新时间" value={new Date(change.updatedAt).toLocaleString()} />
                </dl>
                <div className="mt-4 grid gap-2">
                  <Link href={`/project-intelligence?projectId=${change.projectId}`}>
                    <Button fullWidth variant="secondary">
                      看项目沉淀 <ArrowRight className="h-3.5 w-3.5" />
                    </Button>
                  </Link>
                  <Link href={`/project-intelligence/timeline?projectId=${change.projectId}`}>
                    <Button fullWidth variant="secondary">
                      看项目时间线 <ArrowRight className="h-3.5 w-3.5" />
                    </Button>
                  </Link>
                </div>
              </Card>
              <Card shadow="card" padding="md">
                <h3 className="font-semibold text-slate-950">审查原则</h3>
                <p className="mt-2 text-sm leading-6 text-slate-600">
                  AI 和本地规则只生成候选。用户确认后才写入正式项目沉淀；不确定就忽略或展开修正区修改。
                </p>
              </Card>
            </aside>
          </form>
        ) : !loading ? (
          <section className="rounded-md border border-line bg-white p-8 text-center text-sm text-muted shadow-panel">
            没有找到这条建议沉淀。
          </section>
        ) : null}
      </div>
    </AppShell>
  );
}

function MiniEvidence({ title, value }: { title: string; value: string }) {
  return (
    <div className="rounded-md border border-line bg-slate-50 p-3">
      <p className="text-xs text-muted">{title}</p>
      <p className="mt-1 font-semibold text-slate-950">{value}</p>
    </div>
  );
}

function ArchiveCandidate({ target, text }: { target: string; text: string }) {
  return (
    <article className="rounded-md border border-line bg-slate-50 p-4">
      <p className="font-semibold text-slate-950">{target}</p>
      <p className="mt-2 line-clamp-4 whitespace-pre-line text-sm leading-6 text-slate-600">{text || "确认后按变化摘要写入。"}</p>
    </article>
  );
}

function SelectField({ label, onChange, options, value }: { label: string; onChange: (value: string) => void; options: Record<string, string>; value: string }) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-medium text-slate-700">{label}</span>
      <select className="w-full rounded-md border border-line px-3 py-2 text-sm outline-none focus:border-slate-950" onChange={(event) => onChange(event.target.value)} value={value}>
        {Object.entries(options).map(([optionValue, optionLabel]) => (
          <option key={optionValue} value={optionValue}>{optionLabel}</option>
        ))}
      </select>
    </label>
  );
}

function TextField({ label, onChange, value }: { label: string; onChange: (value: string) => void; value: string }) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-medium text-slate-700">{label}</span>
      <input className="w-full rounded-md border border-line px-3 py-2 text-sm outline-none focus:border-slate-950" onChange={(event) => onChange(event.target.value)} value={value} />
    </label>
  );
}

function TextArea({ label, mono = false, onChange, rows, value }: { label: string; mono?: boolean; onChange: (value: string) => void; rows: number; value: string }) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-medium text-slate-700">{label}</span>
      <textarea
        className={`w-full rounded-md border border-line px-3 py-2 text-sm leading-6 outline-none focus:border-slate-950 ${mono ? "bg-slate-50 font-mono text-xs" : ""}`}
        onChange={(event) => onChange(event.target.value)}
        rows={rows}
        value={value}
      />
    </label>
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

function toPayload(change: ProjectChange): ChangeEditState {
  return {
    changeKind: change.changeKind,
    impactLevel: change.impactLevel,
    title: change.title,
    summary: change.summary,
    details: change.details,
    affectedFiles: change.affectedFiles,
    relatedTasks: change.relatedTasks,
    testEvidence: change.testEvidence,
    buildEvidence: change.buildEvidence,
    riskNotes: change.riskNotes,
    decisionNotes: change.decisionNotes,
    learningNotes: change.learningNotes,
    assetCandidates: change.assetCandidates,
  };
}

function changeMemoryTargets(change: Pick<ProjectChangePayload, "assetCandidates" | "changeKind" | "decisionNotes" | "learningNotes" | "riskNotes">) {
  const targets = new Set<string>();
  switch (change.changeKind) {
    case "RISK":
      targets.add("当前风险");
      break;
    case "DECISION":
      targets.add("技术决策");
      break;
    case "LEARNING":
      targets.add("经验沉淀");
      break;
    case "ASSET":
      targets.add("可展示成果");
      break;
    default:
      targets.add("已完成能力");
      break;
  }
  if (change.riskNotes) targets.add("当前风险");
  if (change.decisionNotes) targets.add("技术决策");
  if (change.learningNotes) targets.add("经验沉淀");
  if (change.assetCandidates) targets.add("可展示成果");
  return Array.from(targets);
}

function targetText(target: string, change: ProjectChangePayload) {
  if (target === "当前风险") return change.riskNotes;
  if (target === "技术决策") return change.decisionNotes;
  if (target === "经验沉淀") return change.learningNotes;
  if (target === "可展示成果") return change.assetCandidates;
  return change.summary;
}

function fileCount(value: string) {
  const count = value.split(/\r?\n/).map((line) => line.trim()).filter(Boolean).length;
  return count ? `${count} 个` : "未记录";
}

function ReviewSignal({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-md border border-line bg-slate-50 p-4">
      <p className="text-xs text-muted">{label}</p>
      <p className="mt-2 text-sm font-semibold leading-6 text-slate-800">{value}</p>
    </div>
  );
}

function reviewReadiness(change: ProjectChangePayload) {
  if (change.riskNotes && !change.testEvidence) return "需人工复核";
  if (change.testEvidence?.includes("未采集") || change.buildEvidence?.includes("未采集")) return "可确认，但需注意验证缺口";
  return "可确认";
}

function keyFilePreview(value: string) {
  const files = parseAffectedFiles(value);
  if (!files.length) return "未记录关键文件";
  return files.slice(0, 2).map(compactPath).join("、") + (files.length > 2 ? ` 等 ${files.length} 个` : "");
}

function sourceLabel(value: string) {
  if (value === "EVIDENCE_BUNDLE") return "证据包";
  if (value === "AGENT_RESULT") return "Agent 结果";
  if (value === "PROJECT_ZIP") return "项目 zip";
  if (value === "MODEL_SUMMARY") return "模型总结";
  if (value === "USER_MANUAL") return "用户手动";
  return value;
}

function statusLabel(value: string) {
  if (value === "PENDING") return "待确认";
  if (value === "EDITED") return "已修正";
  if (value === "ACCEPTED") return "已确认";
  if (value === "IGNORED") return "已忽略";
  if (value === "MERGED") return "已合并";
  return value;
}
