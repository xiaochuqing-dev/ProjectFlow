"use client";

import { Suspense, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { ArrowLeft, Check, ChevronDown, Clipboard, RefreshCw, Sparkles, X } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { Badge, Button, ProjectContextBar, Toast } from "@/components/ui";
import { useProjectSelection } from "@/hooks/useProjectSelection";
import {
  analyzeProjectCapabilities,
  getProjectMemory,
  listProjectCapabilityCards,
  updateCapabilityCard,
  type CapabilityCard,
  type ProjectMemory,
} from "@/lib/api";
import { readSession } from "@/lib/auth";

export default function CompletedCapabilitiesPage() {
  return (
    <Suspense fallback={<AppShell eyebrow="项目理解" title="能力与成果"><div className="h-1 bg-slate-950" /></AppShell>}>
      <CompletedCapabilitiesContent />
    </Suspense>
  );
}

function CompletedCapabilitiesContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const queryProjectId = searchParams.get("projectId") ?? "";
  const { projects, selectedProject, selectedProjectId, selectProject, loadingProjects, projectError } = useProjectSelection({ queryProjectId });
  const [cards, setCards] = useState<CapabilityCard[]>([]);
  const [memory, setMemory] = useState<ProjectMemory | null>(null);
  const [loading, setLoading] = useState(false);
  const [analyzing, setAnalyzing] = useState(false);
  const [actingId, setActingId] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  useEffect(() => {
    const session = readSession();
    if (!session || !selectedProjectId) {
      setCards([]);
      setMemory(null);
      return;
    }
    setLoading(true);
    setError("");
    Promise.all([
      listProjectCapabilityCards(session.accessToken, selectedProjectId),
      getProjectMemory(session.accessToken, selectedProjectId),
    ])
      .then(([items, record]) => { setCards(items); setMemory(record); })
      .catch((exception) => setError(exception instanceof Error ? exception.message : "能力与成果加载失败"))
      .finally(() => setLoading(false));
  }, [selectedProjectId]);

  function handleSelectProject(projectId: string) {
    selectProject(projectId);
    router.replace(`/project-intelligence/capabilities?projectId=${projectId}`);
  }

  async function analyzeCapabilities() {
    const session = readSession();
    if (!session || !selectedProjectId) return;
    setAnalyzing(true);
    setError("");
    setNotice("");
    try {
      const items = await analyzeProjectCapabilities(session.accessToken, selectedProjectId);
      setCards((current) => [...current.filter((item) => item.status === "CONFIRMED"), ...items]);
      setNotice(`已基于全部确认沉淀生成 ${items.length} 张候选能力卡片。`);
    } catch (exception) {
      const message = exception instanceof Error ? exception.message : "项目能力分析失败";
      // V3.3.3: 未配置模型时，明确提示去配置模型，不用低质量本地模板伪装完整分析。
      setError(message);
    } finally {
      setAnalyzing(false);
    }
  }

  async function updateCard(card: CapabilityCard, action: "CONFIRM" | "IGNORE") {
    const session = readSession();
    if (!session) return;
    setActingId(card.id);
    setError("");
    try {
      const updated = await updateCapabilityCard(session.accessToken, card.id, action);
      setCards((current) => current.map((item) => item.id === updated.id ? updated : item));
      setNotice(action === "CONFIRM" ? "已确认这一张能力卡片，其他候选保持不变。" : "已忽略这一张候选能力。");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "能力卡片更新失败");
    } finally {
      setActingId("");
    }
  }

  async function copy(value: string) {
    try {
      await navigator.clipboard.writeText(value);
      setNotice("已复制可复用表达。");
    } catch {
      setError("复制失败，请手动复制。");
    }
  }

  const visibleCards = cards.filter((item) => item.status !== "IGNORED");
  const confirmedCount = visibleCards.filter((item) => item.status === "CONFIRMED").length;

  return (
    <AppShell eyebrow="项目理解" title={selectedProject ? `${selectedProject.name} · 能力与成果` : "能力与成果"}>
      <div className="min-h-[calc(100vh-4rem)] bg-surface p-6">
        <ProjectContextBar
          actions={<Link className="inline-flex items-center gap-1 rounded-md border border-line bg-white px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50" href={`/project-intelligence?projectId=${selectedProjectId}`}><ArrowLeft className="h-4 w-4" />回到项目理解</Link>}
          leadingExtras={<><Badge label={`${visibleCards.length} 张能力卡片`} tone={visibleCards.length ? "success" : "warning"} /><Badge label={`${confirmedCount} 张已确认`} /></>}
          onSelect={handleSelectProject}
          projects={projects}
          selectedProjectId={selectedProjectId}
        />

        <section className="rounded-md border border-line bg-white shadow-panel">
          <header className="flex flex-wrap items-start justify-between gap-4 border-b border-line p-5">
            <div className="max-w-2xl">
              <h2 className="text-xl font-semibold text-slate-950">整体项目能力分析</h2>
              <p className="mt-1 text-sm leading-6 text-slate-600">基于已确认项目沉淀、开发推进段和证据引用，一次生成可逐条确认的结构化能力卡片。</p>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <Button disabled={!selectedProjectId || analyzing} loading={analyzing} onClick={analyzeCapabilities} variant="primary">
                {analyzing ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Sparkles className="h-4 w-4" />}
                分析项目能力
              </Button>
              <span className="text-xs text-slate-500">确认后可生成能力解读</span>
            </div>
          </header>

          {visibleCards.length ? (
            <div className="divide-y divide-line">
              {visibleCards.map((card) => (
                <CapabilityCardRow acting={actingId === card.id} card={card} key={card.id} onCopy={copy} onUpdate={updateCard} />
              ))}
            </div>
          ) : !loading ? (
            <div className="p-8 text-center">
              <p className="font-semibold text-slate-950">还没有结构化能力卡片</p>
              <p className="mx-auto mt-2 max-w-xl text-sm leading-6 text-slate-600">先在沉淀确认中确认开发推进段，再点击“分析项目能力”。系统不会再从旧字符串字段生成模板卡片。</p>
            </div>
          ) : <div className="h-1 bg-slate-950" />}

          {memory?.completedCapabilities?.trim() ? (
            <details className="border-t border-line bg-slate-50 px-5 py-4">
              <summary className="cursor-pointer text-sm font-semibold text-slate-700">兼容档案字段</summary>
              <p className="mt-2 text-xs leading-5 text-slate-500">以下是旧版 completedCapabilities 开发者备注，不作为 V3.3.3 正式能力卡片的数据源。</p>
              <pre className="mt-3 whitespace-pre-wrap text-sm leading-6 text-slate-700">{memory.completedCapabilities}</pre>
            </details>
          ) : null}
        </section>

        {/* V3.3.3: 未配置模型时，明确提示去配置模型，不生成低质量本地模板卡片。 */}
        {error && error.includes("未配置模型") ? (
          <div className="mt-4 rounded-md border border-warning/30 bg-warning-soft p-4 text-sm leading-6 text-warning-fg">
            <p className="font-semibold">当前未配置模型，无法进行完整人话能力分析。</p>
            <p className="mt-1">ProjectFlow 不会用低质量本地模板伪装成完整模型分析。请先配置模型，再分析项目能力。</p>
            <Link className="mt-2 inline-flex items-center gap-1 font-semibold text-brand hover:text-brand-hover" href="/settings">
              去设置模型 <ArrowLeft className="h-3.5 w-3.5 rotate-180" />
            </Link>
          </div>
        ) : null}

        <Toast error={error || projectError} notice={notice} />
        {loadingProjects ? <div className="fixed inset-x-0 bottom-0 h-1 bg-slate-950" /> : null}
      </div>
    </AppShell>
  );
}

function CapabilityCardRow({
  acting,
  card,
  onCopy,
  onUpdate,
}: {
  acting: boolean;
  card: CapabilityCard;
  onCopy: (value: string) => void;
  onUpdate: (card: CapabilityCard, action: "CONFIRM" | "IGNORE") => void;
}) {
  return (
    <article className="p-5">
      <details className="group">
        <summary className="flex cursor-pointer list-none items-start gap-3">
          <ChevronDown className="mt-1 h-4 w-4 shrink-0 text-slate-500 transition-transform group-open:rotate-180" />
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <h3 className="font-semibold text-slate-950">{card.name}</h3>
              <Badge label={statusLabel(card.status)} tone={card.status === "CONFIRMED" ? "success" : card.status === "NEEDS_EVIDENCE" ? "warning" : "slate"} />
              <Badge label={card.generationMode === "MODEL" ? `模型 · ${card.modelProvider}` : "本地规则兜底"} />
              <span className="text-xs text-slate-400 group-open:hidden">查看详情</span>
            </div>
            <p className="mt-1 max-w-3xl text-sm leading-6 text-slate-600">{card.summary}</p>
          </div>
        </summary>

        <div className="ml-7 mt-4 grid gap-4 lg:grid-cols-[minmax(0,1fr)_280px]">
          <dl className="space-y-3 text-sm leading-6">
            <Info label="解决什么问题" value={card.problemSolved} />
            <Info label="为什么重要" value={card.featureEntry} />
            <div>
              <dt className="text-xs font-semibold text-slate-700">可复用表达</dt>
              <dd className="mt-1 space-y-2">
                <Info label="README 表达" value={card.readmeExpression} copy={() => onCopy(card.readmeExpression)} />
                <Info label="简历表达" value={card.resumeExpression} copy={() => onCopy(card.resumeExpression)} />
                <Info label="面试表达" value={card.interviewExpression} />
              </dd>
            </div>
          </dl>
          <aside className="rounded-md bg-slate-50 p-4 text-xs leading-5 text-slate-600">
            <p className="font-semibold text-slate-800">来源证据</p>
            <p className="mt-2">{card.sourceRefs.length} 个来源，{card.evidenceRefs.length} 条证据。</p>
            {card.fallbackReason ? <p className="mt-2 text-amber-800">{card.fallbackReason}</p> : null}
            {card.status !== "CONFIRMED" ? (
              <div className="mt-4 flex gap-2">
                <Button disabled={acting || card.status === "NEEDS_EVIDENCE"} onClick={() => onUpdate(card, "CONFIRM")} size="sm" variant="primary"><Check className="h-3.5 w-3.5" />确认此项</Button>
                <Button disabled={acting} onClick={() => onUpdate(card, "IGNORE")} size="sm" variant="secondary"><X className="h-3.5 w-3.5" />忽略</Button>
              </div>
            ) : null}
          </aside>
        </div>
      </details>
    </article>
  );
}

function Info({ label, value, copy }: { label: string; value: string; copy?: () => void }) {
  return (
    <div>
      <dt className="text-xs font-semibold text-slate-700">{label}</dt>
      <dd className="mt-1 flex max-w-3xl items-start gap-2 text-slate-800">
        <span>{value}</span>
        {copy ? <button aria-label={`复制${label}`} className="shrink-0 rounded-md p-1 text-slate-500 hover:bg-slate-100 hover:text-slate-900" onClick={copy} type="button"><Clipboard className="h-3.5 w-3.5" /></button> : null}
      </dd>
    </div>
  );
}

function statusLabel(status: CapabilityCard["status"]) {
  if (status === "CONFIRMED") return "已确认";
  if (status === "NEEDS_EVIDENCE") return "需补证据";
  return "候选";
}
