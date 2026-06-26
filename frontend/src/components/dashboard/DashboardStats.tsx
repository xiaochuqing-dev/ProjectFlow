import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { Button, Card } from "@/components/ui";
import type { AiSuggestion, ProjectChange, TaskItem, WorkSessionCandidate } from "@/lib/api";
import { compactProjectPath, buildProjectArchitecture } from "@/lib/project-insights";

export function InteractiveStat({
  active,
  hint,
  label,
  onClick,
  tone = "slate",
  value,
}: {
  active: boolean;
  hint?: string;
  label: string;
  onClick: () => void;
  tone?: "slate" | "brand" | "warning";
  value: number;
}) {
  const toneClass = {
    slate: "hover:border-lineStrong hover:bg-surfaceAlt",
    brand: "hover:border-brand/40 hover:bg-brand-soft",
    warning: "hover:border-warning/40 hover:bg-warning-soft",
  }[tone];
  return (
    <button
      className={`group min-w-0 rounded-card border bg-elevated p-4 text-left transition duration-150 hover:-translate-y-0.5 hover:shadow-card ${
        active ? "border-brand bg-brand-soft shadow-card" : `border-line ${toneClass}`
      }`}
      onClick={onClick}
      type="button"
    >
      <div className="flex items-center justify-between gap-2">
        <p className="text-xs text-muted">{label}</p>
        <ArrowRight className={`h-3.5 w-3.5 transition ${active ? "text-brand" : "text-muted group-hover:text-brand"}`} />
      </div>
      <p className="mt-1.5 text-xl font-semibold leading-7 text-ink">{value}</p>
      {hint ? <p className="mt-0.5 text-xs text-muted">{hint}</p> : null}
    </button>
  );
}

export function StatsFocusPanel({
  activeTasks,
  architecture,
  changes,
  focus,
  paths,
  suggestions,
  workSessions,
}: {
  activeTasks: TaskItem[];
  architecture: ReturnType<typeof buildProjectArchitecture>;
  changes: ProjectChange[];
  focus: "materials" | "changes" | "sessions" | "tasks";
  paths: string[];
  suggestions: AiSuggestion[];
  workSessions: WorkSessionCandidate[];
}) {
  const content = {
    materials: {
      title: "项目资料",
      body: paths.length ? `${architecture.shapeLabel}，识别 ${paths.length} 个文件信号。` : "暂无可用项目文件信号。",
      action: "查看架构入口",
      href: "",
      items: [architecture.entrypoints[0]?.path, architecture.coreModules[0]?.path, architecture.dependencySignals[0]?.path].filter(Boolean) as string[],
    },
    changes: {
      title: "待确认成果",
      body: suggestions.length || changes.length ? "这些候选需要确认后才会进入项目资产。" : "暂无待确认成果。",
      action: "去确认成果",
      href: "/tasks",
      items: [...suggestions.map((item) => item.title), ...changes.map((item) => item.title)].slice(0, 3),
    },
    sessions: {
      title: "今日开发记录",
      body: workSessions.length ? "这些 Git 变化可继续整理为原始依据。" : "暂无今日开发记录。",
      action: "刷新今日开发",
      href: "",
      items: workSessions.slice(0, 3).map((item) => item.taskIntent || `${item.changedFiles} 个文件变化`),
    },
    tasks: {
      title: "下一步任务",
      body: activeTasks.length ? "这些任务会参与每日回顾和成果输出。" : "暂无下一步任务。",
      action: "查看开发成果审查",
      href: "/tasks",
      items: activeTasks.slice(0, 3).map((item) => item.title),
    },
  }[focus];

  return (
    <Card shadow="card" padding="md" className="mb-6 border-brand/20 bg-brand-soft/40">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <p className="text-xs font-semibold text-brand">已选入口</p>
          <h3 className="mt-1 text-lg font-semibold text-ink">{content.title}</h3>
          <p className="mt-1 text-sm leading-6 text-body">{content.body}</p>
          <div className="mt-3 flex flex-wrap gap-2">
            {content.items.length ? content.items.map((item) => (
              <span className="max-w-full break-all rounded-field bg-elevated px-3 py-1.5 font-mono text-xs text-muted" key={item}>
                {item.includes("/") ? compactProjectPath(item) : item}
              </span>
            )) : <span className="rounded-field bg-elevated px-3 py-1.5 text-xs text-muted">无</span>}
          </div>
        </div>
        {content.href ? (
          <Link href={content.href}>
            <Button variant="primary" size="sm">
              {content.action} <ArrowRight className="h-3.5 w-3.5" />
            </Button>
          </Link>
        ) : null}
      </div>
    </Card>
  );
}

export function MiniFact({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0 rounded-field border border-line bg-surfaceAlt p-3">
      <p className="text-xs text-muted">{label}</p>
      <p className="mt-1 break-all text-sm font-semibold leading-5 text-ink">{value}</p>
    </div>
  );
}
