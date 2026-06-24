"use client";

import { useMemo, useState } from "react";
import type { ReactNode } from "react";
import Link from "next/link";
import { ArrowRight, FileText, Search } from "lucide-react";
import { Badge, Button, Card, InfoBubble } from "@/components/ui";

export type ResourceTimelineItem = {
  id: string;
  title: string;
  summary: string;
  date: string;
  type: string;
  status: string;
  source: string;
  meta?: string;
  detail?: ReactNode;
  href?: string;
};

type ResourceTimelineProps = {
  emptyText: string;
  items: ResourceTimelineItem[];
  title: string;
};

export function ResourceTimeline({ emptyText, items, title }: ResourceTimelineProps) {
  const [month, setMonth] = useState("all");
  const [type, setType] = useState("all");
  const [status, setStatus] = useState("all");
  const [keyword, setKeyword] = useState("");

  const months = useMemo(() => uniqueOptions(items.map((item) => safeDate(item.date).slice(0, 7)).filter(Boolean)), [items]);
  const types = useMemo(() => uniqueOptions(items.map((item) => item.type)), [items]);
  const statuses = useMemo(() => uniqueOptions(items.map((item) => item.status)), [items]);
  const filtered = useMemo(() => {
    const query = keyword.trim().toLowerCase();
    return items
      .filter((item) => month === "all" || safeDate(item.date).startsWith(month))
      .filter((item) => type === "all" || item.type === type)
      .filter((item) => status === "all" || item.status === status)
      .filter((item) => !query || [item.title, item.summary, item.source, item.meta].join(" ").toLowerCase().includes(query))
      .sort((left, right) => new Date(right.date).getTime() - new Date(left.date).getTime());
  }, [items, keyword, month, status, type]);
  const groups = useMemo(() => groupByDay(filtered), [filtered]);

  return (
    <Card shadow="card">
      <div className="border-b border-line p-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h2 className="text-base font-semibold text-ink">{title}</h2>
            <p className="mt-1 text-sm text-muted">按月份、日期和状态管理长期记录，列表只保留摘要，完整内容进入独立详情。</p>
          </div>
          <InfoBubble label={`${filtered.length}/${items.length} 条`} />
        </div>
        <div className="mt-4 grid gap-3 lg:grid-cols-[1fr_150px_150px_150px]">
          <label className="relative block">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" />
            <input
              className="h-10 w-full rounded-field border border-line bg-elevated pl-9 pr-3 text-sm outline-none transition focus:border-brand focus:shadow-focus"
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="搜索标题、来源或摘要"
              value={keyword}
            />
          </label>
          <FilterSelect label="月份" onChange={setMonth} options={months} value={month} />
          <FilterSelect label="类型" onChange={setType} options={types} value={type} />
          <FilterSelect label="状态" onChange={setStatus} options={statuses} value={status} />
        </div>
      </div>

      {groups.length ? (
        <div className="divide-y divide-line">
          {groups.map((group) => (
            <section className="grid gap-4 p-5 lg:grid-cols-[170px_minmax(0,1fr)]" key={group.day}>
              <div>
                <p className="font-semibold text-ink">{group.day}</p>
                <p className="mt-1 text-xs text-muted">{group.items.length} 条记录</p>
              </div>
              <div className="space-y-3">
                {group.items.map((item) => (
                  <ResourceCard item={item} key={item.id} />
                ))}
              </div>
            </section>
          ))}
        </div>
      ) : (
        <p className="p-5 text-sm text-muted">{items.length ? "没有符合筛选条件的记录。" : emptyText}</p>
      )}
    </Card>
  );
}

function ResourceCard({ item }: { item: ResourceTimelineItem }) {
  return (
    <article className="rounded-card border border-line bg-surfaceAlt p-4 transition hover:border-lineStrong hover:bg-elevated hover:shadow-sm">
      <div className="grid gap-3 md:grid-cols-[minmax(0,1fr)_auto]">
        <div className="min-w-0">
          <div className="mb-2 flex flex-wrap items-center gap-2">
            <Badge label={item.status} tone={statusTone(item.status)} />
            <InfoBubble label={item.type} />
            <span className="text-xs text-muted">{new Date(item.date).toLocaleTimeString()}</span>
          </div>
          <p className="line-clamp-1 font-semibold text-ink">{item.title}</p>
          <p className="mt-1 line-clamp-2 text-sm leading-6 text-body">{item.summary}</p>
        </div>
        <div className="flex items-center gap-2 self-start">
          <span className="max-w-44 truncate rounded-field bg-elevated px-2 py-1 text-xs text-muted" title={item.source}>
            {item.source}
          </span>
          {item.href ? (
            <Link href={item.href}>
              <Button variant="secondary" size="sm">
                查看详情 <ArrowRight className="h-3.5 w-3.5" />
              </Button>
            </Link>
          ) : (
            <span className="inline-flex h-8 items-center gap-1 rounded-field px-2 text-xs font-semibold text-muted">
              <FileText className="h-3.5 w-3.5" />
              仅摘要
            </span>
          )}
        </div>
      </div>
      {item.meta ? <p className="mt-3 truncate border-t border-line pt-3 font-mono text-xs text-muted" title={item.meta}>{compactPath(item.meta)}</p> : null}
    </article>
  );
}

function FilterSelect({ label, onChange, options, value }: { label: string; onChange: (value: string) => void; options: string[]; value: string }) {
  return (
    <label className="block">
      <span className="sr-only">{label}</span>
      <select
        className="h-10 w-full rounded-field border border-line bg-elevated px-3 text-sm outline-none transition focus:border-brand focus:shadow-focus"
        onChange={(event) => onChange(event.target.value)}
        value={value}
      >
        <option value="all">全部{label}</option>
        {options.map((option) => (
          <option key={option} value={option}>{option}</option>
        ))}
      </select>
    </label>
  );
}

function groupByDay(items: ResourceTimelineItem[]) {
  const groups = new Map<string, ResourceTimelineItem[]>();
  for (const item of items) {
    const day = safeDate(item.date).slice(0, 10) || "未知日期";
    groups.set(day, [...(groups.get(day) ?? []), item]);
  }
  return Array.from(groups.entries()).map(([day, groupItems]) => ({ day, items: groupItems }));
}

function safeDate(value: string) {
  if (!value) return "";
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed.toISOString();
}

function uniqueOptions(values: string[]) {
  return Array.from(new Set(values.filter(Boolean))).sort().reverse();
}

function compactPath(value: string) {
  const normalized = value.replace(/^sourceId:\s*/i, "").replace(/\\/g, "/");
  if (normalized.length <= 72) return value;
  const parts = normalized.split("/").filter(Boolean);
  if (parts.length >= 3) {
    return `${parts[0]}/.../${parts.at(-1)}`;
  }
  return `...${normalized.slice(-69)}`;
}

function statusTone(status: string): "slate" | "brand" | "success" | "warning" | "danger" {
  if (/已确认|用户确认|已采纳|已入档|已完成|SUCCEEDED|ACCEPTED/i.test(status)) return "success";
  if (/待|PENDING|EDITED|候选/i.test(status)) return "warning";
  if (/失败|删除|风险|FAILED|IGNORED/i.test(status)) return "danger";
  if (/模型|分析|FILE|PROJECT/i.test(status)) return "brand";
  return "slate";
}
