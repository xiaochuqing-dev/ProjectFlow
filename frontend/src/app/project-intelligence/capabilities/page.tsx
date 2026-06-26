"use client";

import { Suspense, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ArrowLeft, CheckCircle2, Clipboard, ListChecks, ShieldCheck } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { Badge, ProjectContextBar, Toast } from "@/components/ui";
import { useProjectSelection } from "@/hooks/useProjectSelection";
import { getProjectMemory, type ProjectMemory } from "@/lib/api";
import { readSession } from "@/lib/auth";
import { capabilityBulletItems } from "@/lib/project-memory-display";

export default function CompletedCapabilitiesPage() {
  return (
    <Suspense fallback={<AppShell eyebrow="项目理解" title="能力与成果"><div className="min-h-[calc(100vh-4rem)] bg-surface p-6"><div className="h-1 bg-slate-950" /></div></AppShell>}>
      <CompletedCapabilitiesContent />
    </Suspense>
  );
}

function CompletedCapabilitiesContent() {
  const searchParams = useSearchParams();
  const queryProjectId = searchParams.get("projectId") ?? "";
  const { projects, selectedProject, selectedProjectId, selectProject, loadingProjects, projectError } = useProjectSelection({ queryProjectId });
  const [memory, setMemory] = useState<ProjectMemory | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const capabilityAssets = useMemo(() => buildCapabilityAssets(memory), [memory]);

  useEffect(() => {
    const session = readSession();
    if (!session || !selectedProjectId) {
      setMemory(null);
      setLoading(false);
      return;
    }

    setLoading(true);
    setError("");
    getProjectMemory(session.accessToken, selectedProjectId)
      .then(setMemory)
      .catch((exception) => setError(exception instanceof Error ? exception.message : "能力与成果加载失败"))
      .finally(() => setLoading(false));
  }, [selectedProjectId]);

  async function copyExpression(value: string) {
    try {
      await navigator.clipboard.writeText(value);
      setNotice("已复制可复用表达。");
    } catch {
      setError("复制失败，请手动复制表达内容。");
    }
  }

  return (
    <AppShell eyebrow="项目理解" title={selectedProject ? `${selectedProject.name} · 能力与成果` : "能力与成果"}>
      <div className="min-h-[calc(100vh-4rem)] bg-surface p-6">
        <ProjectContextBar
          actions={(
            <Link className="inline-flex items-center gap-1 rounded-md border border-line bg-white px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50" href={`/project-intelligence?projectId=${selectedProjectId}`}>
              <ArrowLeft className="h-4 w-4" />
              回到项目理解
            </Link>
          )}
          leadingExtras={(
            <>
              <Badge label={`${capabilityAssets.length} 项能力`} tone={capabilityAssets.length ? "success" : "warning"} />
              <Badge label={`版本 ${memory?.version ?? "-"}`} />
            </>
          )}
          onSelect={selectProject}
          projects={projects}
          selectedProjectId={selectedProjectId}
        />

        <section className="rounded-md border border-line bg-white shadow-panel">
          <div className="flex flex-wrap items-start justify-between gap-3 border-b border-line p-5">
            <div className="flex gap-3">
              <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-emerald-700 text-white">
                <ListChecks className="h-5 w-5" />
              </span>
              <div>
                <h2 className="text-xl font-semibold text-slate-950">能力与成果</h2>
                <p className="mt-1 text-sm leading-6 text-slate-600">把已确认能力整理成可解释、有证据、可用于 README / 简历 / 面试的能力资产。</p>
              </div>
            </div>
            <span className="rounded-full bg-emerald-800 px-3 py-1 text-sm font-semibold text-white">{capabilityAssets.length} 项</span>
          </div>

          <div className="grid gap-4 p-5 lg:grid-cols-2">
            {capabilityAssets.map((asset, index) => (
              <CapabilityAssetCard asset={asset} index={index} key={`${asset.name}-${index}`} onCopy={copyExpression} />
            ))}
            {capabilityAssets.length === 0 ? (
              <p className="rounded-md border border-line bg-slate-50 p-4 text-sm text-muted">暂无已确认能力。采纳开发成果或保存项目资产后，会先形成能力候选，再经用户确认进入这里。</p>
            ) : null}
          </div>
        </section>

        <Toast error={error || projectError} notice={notice} />
        {loading || loadingProjects ? <div className="fixed inset-x-0 bottom-0 h-1 bg-slate-950" /> : null}
      </div>
    </AppShell>
  );
}

type CapabilityAsset = {
  name: string;
  problem: string;
  importance: string;
  recognized: string[];
  evidence: string[];
  reusableExpression: string;
};

function CapabilityAssetCard({ asset, index, onCopy }: { asset: CapabilityAsset; index: number; onCopy: (value: string) => void }) {
  return (
    <article className="rounded-md border border-emerald-100 bg-emerald-50 p-4">
      <div className="mb-3 flex items-start justify-between gap-3">
        <div>
          <div className="mb-2 flex items-center gap-2">
            <CheckCircle2 className="h-4 w-4 text-emerald-700" />
            <span className="text-xs font-semibold text-emerald-800">能力 {index + 1}</span>
          </div>
          <h3 className="text-base font-semibold text-emerald-950">{asset.name}</h3>
        </div>
        <button className="inline-flex items-center gap-1 rounded-md bg-white px-2 py-1 text-xs font-semibold text-emerald-900 hover:bg-emerald-100" onClick={() => onCopy(asset.reusableExpression)} type="button">
          <Clipboard className="h-3.5 w-3.5" />
          复制表达
        </button>
      </div>
      <CapabilityBlock title="解决什么问题" value={asset.problem} />
      <CapabilityBlock title="为什么重要" value={asset.importance} />
      <div className="mt-3">
        <p className="text-xs font-semibold text-emerald-800">已识别内容</p>
        <ul className="mt-1 space-y-1 text-sm leading-6 text-emerald-950">
          {asset.recognized.map((item) => <li key={item}>- {item}</li>)}
        </ul>
      </div>
      <details className="mt-3 rounded-md border border-emerald-200 bg-white">
        <summary className="flex cursor-pointer items-center gap-2 px-3 py-2 text-sm font-semibold text-emerald-900 hover:bg-emerald-50">
          <ShieldCheck className="h-4 w-4" />
          来源证据
        </summary>
        <ul className="space-y-1 border-t border-emerald-100 p-3 text-sm leading-6 text-emerald-950">
          {asset.evidence.map((item) => <li key={item}>- {item}</li>)}
        </ul>
      </details>
      <CapabilityBlock title="可复用表达" value={asset.reusableExpression} />
    </article>
  );
}

function CapabilityBlock({ title, value }: { title: string; value: string }) {
  return (
    <div className="mt-3">
      <p className="text-xs font-semibold text-emerald-800">{title}</p>
      <p className="mt-1 text-sm leading-6 text-emerald-950">{value}</p>
    </div>
  );
}

function buildCapabilityAssets(memory: ProjectMemory | null): CapabilityAsset[] {
  const items = capabilityBulletItems(memory?.completedCapabilities ?? "");
  return items.map((item) => ({
    name: capabilityName(item),
    problem: capabilityProblem(item),
    importance: "它说明项目已经不只是保存过程记录，而是能把真实开发活动沉淀成后续 README、简历描述、项目复盘和面试讲解可以复用的工程资产。",
    recognized: recognizedItems(item, memory),
    evidence: [
      "来自已确认项目资产。",
      memory?.updatedAt ? `最近更新于 ${new Date(memory.updatedAt).toLocaleString()}` : "暂无更细来源时间。",
      "更细证据可在相关资产卡的“为什么可信？”中继续追溯。",
    ],
    reusableExpression: reusableExpression(item),
  }));
}

function capabilityName(value: string) {
  return value.replace(/^[-•\d.\s]+/, "").split(/[，。；:：]/)[0].slice(0, 42) || "项目能力";
}

function capabilityProblem(value: string) {
  if (/zip|结构|目录|技术栈|文件/i.test(value)) {
    return "帮助用户从导入项目中理解工程结构、核心模块和运行线索，减少首次理解项目的成本。";
  }
  if (/Git|证据|变化|开发/i.test(value)) {
    return "帮助用户把零散开发变化整理成可审查、可追溯、可复用的开发成果。";
  }
  if (/输出|README|简历|周报|复盘/i.test(value)) {
    return "帮助用户把已确认项目资产转化为对外展示和阶段汇报材料。";
  }
  return "帮助用户把一次具体开发成果沉淀为后续可复用、可展示的项目能力。";
}

function recognizedItems(value: string, memory: ProjectMemory | null) {
  return [
    value,
    memory?.technicalDecisions ? `相关技术决策：${firstLine(memory.technicalDecisions)}` : "暂无关联技术决策。",
    memory?.showcaseAssets ? `可展示成果：${firstLine(memory.showcaseAssets)}` : "暂无单独成果素材。",
  ];
}

function reusableExpression(value: string) {
  return `沉淀了“${capabilityName(value)}”能力，可结合项目资产、开发证据和用户确认内容，用于 README、简历项目亮点和面试讲解。`;
}

function firstLine(value: string) {
  return value.split(/\r?\n/).map((line) => line.trim()).find(Boolean) ?? value;
}
