import type { ReactNode } from "react";
import Link from "next/link";
import { ArrowRight, RefreshCw, ScanLine } from "lucide-react";
import { Badge, Button, InfoBubble } from "@/components/ui";
import type { AiSuggestion, ProjectEvolutionRecord, WorkSessionCandidate } from "@/lib/api";
import { firstUsefulLine } from "@/lib/text-summary";

export type ActivityFeedProps = {
  evolutionRecords: ProjectEvolutionRecord[];
  pendingSuggestions: AiSuggestion[];
  workSessions: WorkSessionCandidate[];
  hasProjectPath: boolean;
  onScanWorkSessions: () => void;
  scanningWorkSessions: boolean;
  selectedProjectId: string;
};

export function ActivityFeed(props: ActivityFeedProps) {
  type FeedItem = {
    id: string;
    badge: ReactNode;
    badgeTone: "brand" | "warning" | "success" | "slate";
    title: string;
    impact: string;
    href?: string;
    hrefLabel?: string;
  };

  const pendingItems = props.pendingSuggestions.slice(0, 3).map<FeedItem>((suggestion) => ({
      id: `sug-${suggestion.id}`,
      badge: "待确认",
      badgeTone: "warning",
      title: suggestion.title,
      impact: "候选信息尚未进入项目沉淀，确认后才会影响后续输出。",
      href: "/tasks",
      hrefLabel: "审查",
    }));
  const acceptedItems = props.evolutionRecords.slice(0, 3).map<FeedItem>((record) => ({
      id: `evo-${record.id}`,
      badge: "已采纳",
      badgeTone: "success",
      title: record.summary,
      impact: activityImpactSummary(record.detectedChanges || record.summary, "已沉淀到项目成长记录，可用于每日回顾和成果输出。"),
    }));
  const groups = [
    { key: "pending", title: "待审查", items: pendingItems, empty: "暂无待审查候选。" },
    { key: "accepted", title: "已入档", items: acceptedItems, empty: "暂无已采纳变化。" },
  ];

  if (groups.every((group) => group.items.length === 0)) {
    return (
      <div className="p-5">
        <p className="text-sm leading-6 text-muted">
          {props.hasProjectPath
            ? "点击下方刷新，ProjectFlow 会读取已绑定项目的今日 Git evidence 生成可审查候选。"
            : "先绑定真实项目路径，ProjectFlow 才能读取 Git evidence。不会扫描用户主目录或全局 Agent 日志。"}
        </p>
        <Button
          variant="secondary"
          size="sm"
          className="mt-3"
          disabled={!props.hasProjectPath || props.scanningWorkSessions}
          onClick={props.onScanWorkSessions}
        >
          {props.scanningWorkSessions ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <ScanLine className="h-3.5 w-3.5" />}
          刷新变化
        </Button>
      </div>
    );
  }

  return (
    <div>
      <div className="border-b border-line px-5 py-3">
        <div className="flex items-center justify-between gap-3 rounded-field bg-surfaceAlt px-3 py-2 text-xs text-muted">
          <span>今日变化在左侧闭环处理，这里只留档案审计摘要。</span>
          <InfoBubble label={`${props.workSessions.length} 个今日候选`} />
        </div>
      </div>
      <div className="space-y-3 p-4">
        {groups.map((group) => (
          <ActivityGroup group={group} key={group.key} />
        ))}
      </div>
    </div>
  );
}

function ActivityGroup({
  group,
}: {
  group: {
    key: string;
    title: string;
    empty: string;
    items: Array<{
      id: string;
      badge: ReactNode;
      badgeTone: "brand" | "warning" | "success" | "slate";
      title: string;
      impact: string;
      href?: string;
      hrefLabel?: string;
    }>;
  };
}) {
  return (
    <section className="rounded-field border border-line bg-surfaceAlt/70 p-3">
      <div className="mb-2 flex items-center justify-between gap-2">
        <p className="text-xs font-semibold text-ink">{group.title}</p>
        <InfoBubble label={`${group.items.length} 条`} />
      </div>
      {group.items.length ? (
        <div className="space-y-2">
          {group.items.map((item) => (
            <article className="rounded-field border border-line bg-elevated p-3" key={item.id}>
              <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                <Badge label={item.badge} tone={item.badgeTone} />
                {item.href ? (
                  <Link className="shrink-0" href={item.href}>
                    <Button variant="ghost" size="sm">
                      {item.hrefLabel} <ArrowRight className="h-3.5 w-3.5" />
                    </Button>
                  </Link>
                ) : null}
              </div>
              <p className="line-clamp-2 text-sm font-semibold leading-6 text-ink">{item.title}</p>
              <p className="mt-1 line-clamp-2 text-xs leading-5 text-muted">{item.impact}</p>
            </article>
          ))}
        </div>
      ) : (
        <p className="rounded-field bg-elevated px-3 py-2 text-xs text-muted">{group.empty}</p>
      )}
    </section>
  );
}

function activityImpactSummary(value: string | undefined, fallback: string) {
  return firstUsefulLine(value, fallback);
}
