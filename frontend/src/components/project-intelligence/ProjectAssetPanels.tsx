import Link from "next/link";
import { ArrowRight, ListChecks } from "lucide-react";
import { capabilityBulletItems } from "@/lib/project-memory-display";
import type { ProjectFactSource, ProjectMemoryPayload } from "@/lib/api";

export const fieldConfig: Array<{
  key: keyof ProjectMemoryPayload;
  label: string;
  source: string;
  rows: number;
}> = [
  { key: "positioning", label: "项目定位", source: "用户确认 / zip 分析 / 建议采纳", rows: 4 },
  { key: "currentStage", label: "当前阶段", source: "用户确认优先", rows: 2 },
  { key: "completedCapabilities", label: "已完成能力", source: "采纳记录 / 每日回顾", rows: 5 },
  { key: "inProgressCapabilities", label: "进行中能力", source: "任务变化 / agent result", rows: 5 },
  { key: "currentRisks", label: "当前风险", source: "风险建议 / 用户手动", rows: 5 },
  { key: "technicalDecisions", label: "技术决策", source: "开发成果审查采纳", rows: 5 },
  { key: "developerLearnings", label: "经验沉淀", source: "每日回顾 / 模型总结", rows: 5 },
  { key: "showcaseAssets", label: "可展示成果", source: "成果素材采纳", rows: 5 },
  { key: "nextStepSuggestions", label: "下一步目标", source: "用户确认 / agent result", rows: 5 },
];

export function sourceLabel(source: ProjectFactSource) {
  return `${source.sourceType} · ${source.confirmedByUser ? "已确认" : source.confidence}`;
}

export function ArchiveFieldReview({
  candidateCount,
  field,
  latestSource,
  onChange,
  projectId,
  value,
}: {
  candidateCount: number;
  field: { key: keyof ProjectMemoryPayload; label: string; source: string; rows: number };
  latestSource?: ProjectFactSource;
  onChange: (value: string) => void;
  projectId: string;
  value: string;
}) {
  const isCompletedCapabilities = field.key === "completedCapabilities";
  return (
    <section className="border-b border-line p-5 odd:md:border-r">
      <div className="mb-3 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="font-semibold text-slate-950">{field.label}</h3>
          <p className="mt-1 text-xs text-muted">
            {latestSource ? `更新于 ${new Date(latestSource.updatedAt).toLocaleString()}` : field.source}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <span className={`rounded-md px-2 py-1 text-xs ${latestSource?.confirmedByUser ? "bg-emerald-50 text-emerald-700" : "bg-slate-100 text-muted"}`}>
            {latestSource ? sourceLabel(latestSource) : "暂无来源"}
          </span>
          {candidateCount ? <span className="rounded-md bg-amber-50 px-2 py-1 text-xs text-amber-800">候选 {candidateCount}</span> : null}
        </div>
      </div>
      {isCompletedCapabilities ? (
        <CompletedCapabilitiesCard projectId={projectId} value={value} />
      ) : (
        <p className="min-h-20 whitespace-pre-line rounded-md border border-line bg-slate-50 p-3 text-sm leading-6 text-slate-700">
          {value || "暂无已确认内容。采纳结构化变更或运行项目分析后，会形成可审查候选。"}
        </p>
      )}
      {latestSource?.sourceId ? (
        <details className="mt-2 rounded-md border border-line bg-white">
          <summary className="cursor-pointer px-3 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50">
            为什么可信？
          </summary>
          <p className="break-all border-t border-line p-3 font-mono text-xs text-muted">高级信息：{latestSource.sourceId}</p>
        </details>
      ) : null}
      <details className="mt-3 rounded-md border border-line bg-white">
        <summary className="cursor-pointer px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50">
          手动修正字段
        </summary>
        <div className="border-t border-line p-3">
          <textarea
            className="w-full resize-y rounded-md border border-line bg-white px-3 py-2 text-sm leading-6 outline-none focus:border-slate-950"
            onChange={(event) => onChange(event.target.value)}
            rows={field.rows}
            value={value}
          />
        </div>
      </details>
    </section>
  );
}

export function CompletedCapabilitiesCard({ projectId, value }: { projectId: string; value: string }) {
  const items = capabilityBulletItems(value, 3);
  return (
    <div className="rounded-md border border-emerald-200 bg-emerald-50 p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 gap-3">
          <span className="mt-1 flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-emerald-700 text-white">
            <ListChecks className="h-4 w-4" />
          </span>
          <div className="min-w-0">
            <p className="font-semibold text-emerald-950">能力与成果</p>
            <p className="mt-1 text-sm leading-5 text-emerald-900">已完成能力会整理成可解释、可复用的能力资产。</p>
          </div>
        </div>
        <span className="rounded-full bg-emerald-800 px-2.5 py-1 text-xs font-semibold text-white">{items.length} 项</span>
      </div>
      <ul className="mt-3 space-y-1 text-sm leading-6 text-emerald-950">
        {(items.length ? items : ["暂无已确认能力。"]).slice(0, 3).map((item) => (
          <li className="line-clamp-1" key={item}>- {item}</li>
        ))}
      </ul>
      <Link className="mt-3 inline-flex items-center gap-1 text-sm font-semibold text-emerald-900 hover:text-emerald-700" href={`/project-intelligence/capabilities?projectId=${projectId}`}>
        查看能力详情
        <ArrowRight className="h-4 w-4" />
      </Link>
    </div>
  );
}

export function SmallEntryLink({ href, label, text }: { href: string; label: string; text: string }) {
  return (
    <Link className="block rounded-md bg-white px-3 py-2 text-sm transition hover:-translate-y-0.5 hover:shadow-sm" href={href}>
      <span className="font-semibold text-slate-950">{label}</span>
      <span className="ml-2 text-xs text-muted">{text}</span>
    </Link>
  );
}

type ArchiveEntryTone = "emerald" | "sky" | "indigo" | "amber" | "rose" | "slate";

const archiveEntryToneStyles: Record<ArchiveEntryTone, { card: string; marker: string; count: string; chip: string }> = {
  emerald: { card: "border-emerald-200 bg-emerald-50", marker: "bg-emerald-700", count: "bg-emerald-800 text-white", chip: "bg-white text-emerald-900" },
  sky: { card: "border-sky-200 bg-sky-50", marker: "bg-sky-700", count: "bg-sky-800 text-white", chip: "bg-white text-sky-900" },
  indigo: { card: "border-indigo-200 bg-indigo-50", marker: "bg-indigo-700", count: "bg-indigo-800 text-white", chip: "bg-white text-indigo-900" },
  amber: { card: "border-amber-200 bg-amber-50", marker: "bg-amber-700", count: "bg-amber-800 text-white", chip: "bg-white text-amber-900" },
  rose: { card: "border-rose-200 bg-rose-50", marker: "bg-rose-700", count: "bg-rose-800 text-white", chip: "bg-white text-rose-900" },
  slate: { card: "border-slate-200 bg-slate-50", marker: "bg-slate-700", count: "bg-slate-800 text-white", chip: "bg-white text-slate-700" },
};

export function ArchiveEntryCard({
  count,
  href,
  label,
  latestAt,
  latestLabel,
  text,
  tone = "slate",
}: {
  count: number;
  href: string;
  label: string;
  latestAt?: string;
  latestLabel?: string;
  text: string;
  tone?: ArchiveEntryTone;
}) {
  const styles = archiveEntryToneStyles[tone];
  return (
    <Link className={`relative block overflow-hidden rounded-md border p-4 pl-5 transition hover:-translate-y-0.5 hover:bg-white hover:shadow-sm ${styles.card}`} href={href}>
      <span className={`absolute inset-y-0 left-0 w-1 ${styles.marker}`} />
      <div className="flex items-center justify-between gap-3">
        <p className="font-semibold text-slate-950">{label}</p>
        <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${styles.count}`}>{count} 条</span>
      </div>
      <div className="mt-3 flex flex-wrap items-center gap-2 text-xs text-muted">
        <span className={`rounded-md px-2 py-1 ${styles.chip}`}>{latestAt ? `最新 ${formatShortDate(latestAt)}` : "暂无更新"}</span>
        {latestLabel ? <span className={`max-w-full truncate rounded-md px-2 py-1 ${styles.chip}`}>{latestLabel}</span> : null}
      </div>
      <p className="mt-2 text-sm leading-5 text-slate-600">{text}</p>
    </Link>
  );
}

export function formatShortDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString(undefined, { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" });
}
