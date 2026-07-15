import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { Button, Card } from "@/components/ui";
import type { ChangeBatch, TaskItem } from "@/lib/api";
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
    <button className={`group min-w-0 rounded-card border bg-elevated p-4 text-left transition duration-150 hover:-translate-y-0.5 hover:shadow-card ${active ? "border-brand bg-brand-soft shadow-card" : `border-line ${toneClass}`}`} onClick={onClick} type="button">
      <div className="flex items-center justify-between gap-2"><p className="text-xs text-muted">{label}</p><ArrowRight className={`h-3.5 w-3.5 transition ${active ? "text-brand" : "text-muted group-hover:text-brand"}`} /></div>
      <p className="mt-1.5 text-xl font-semibold leading-7 text-ink">{value}</p>
      {hint ? <p className="mt-0.5 text-xs text-muted">{hint}</p> : null}
    </button>
  );
}

type StatsFocus = "materials" | "facts" | "attention" | "tasks";

export function DashboardOverviewStats({
  activeTasks,
  architecture,
  batch,
  materialsCount,
  materialsHint,
  onFocus,
  paths,
  projectId,
  stageHint,
  statsFocus,
}: {
  activeTasks: TaskItem[];
  architecture: ReturnType<typeof buildProjectArchitecture>;
  batch: ChangeBatch | null | undefined;
  materialsCount: number;
  materialsHint: string;
  onFocus: (focus: StatsFocus | "") => void;
  paths: string[];
  projectId: string;
  stageHint: string;
  statsFocus: StatsFocus | "";
}) {
  const toggle = (focus: StatsFocus) => onFocus(statsFocus === focus ? "" : focus);
  const factCount = batch?.factCount ?? 0;
  const attentionCount = batch?.attentionCount ?? 0;
  return (
    <>
      <section className="mb-6 grid grid-cols-2 gap-3 lg:grid-cols-4">
        <InteractiveStat active={statsFocus === "materials"} hint={materialsHint} label="项目资料" onClick={() => toggle("materials")} value={materialsCount} />
        <InteractiveStat active={statsFocus === "facts"} hint="最近分析批次" label="项目事实" onClick={() => toggle("facts")} tone={factCount ? "brand" : "slate"} value={factCount} />
        <InteractiveStat active={statsFocus === "attention"} hint="不阻塞后续分析" label="需要关注" onClick={() => toggle("attention")} tone={attentionCount ? "warning" : "slate"} value={attentionCount} />
        <InteractiveStat active={statsFocus === "tasks"} hint={stageHint} label="下一步任务" onClick={() => toggle("tasks")} value={activeTasks.length} />
      </section>
      {statsFocus ? <StatsFocusPanel activeTasks={activeTasks} architecture={architecture} batch={batch} focus={statsFocus} paths={paths} projectId={projectId} /> : null}
    </>
  );
}

function StatsFocusPanel({
  activeTasks,
  architecture,
  batch,
  focus,
  paths,
  projectId,
}: {
  activeTasks: TaskItem[];
  architecture: ReturnType<typeof buildProjectArchitecture>;
  batch: ChangeBatch | null | undefined;
  focus: StatsFocus;
  paths: string[];
  projectId: string;
}) {
  const recordsPath = `/sediment-review${projectId ? `?projectId=${projectId}` : ""}`;
  const content = {
    materials: {
      title: "项目资料",
      body: paths.length ? `${architecture.shapeLabel}，识别 ${paths.length} 个文件信号。` : "暂无可用项目文件信号。",
      action: "查看架构入口",
      href: "",
      items: [architecture.entrypoints[0]?.path, architecture.coreModules[0]?.path, architecture.dependencySignals[0]?.path].filter(Boolean) as string[],
    },
    facts: {
      title: "最近批次项目事实",
      body: batch?.factCount ? `已自动记录 ${batch.factCount} 条有证据的项目事实，无需逐条确认。` : "最近批次还没有可展示的项目事实。",
      action: "查看项目记录",
      href: recordsPath,
      items: batch ? [`${batch.newCommitCount} 个提交`, `${batch.changedFileCount} 个文件`, `${batch.segmentCount} 个开发推进段`] : [],
    },
    attention: {
      title: "需要关注",
      body: batch?.attentionCount ? `${batch.attentionCount} 条事实存在证据、时间或质量异常；其他事实和下一次扫描不受影响。` : "最近批次没有需要关注的事实。",
      action: "查看原因与证据",
      href: recordsPath,
      items: batch?.attentionCount ? ["异常不阻塞批次完成", "可随时重新分析"] : [],
    },
    tasks: {
      title: "下一步任务",
      body: activeTasks.length ? "这些任务会参与每日回顾和成果输出。" : "暂无下一步任务。",
      action: "查看每日回顾",
      href: "/dev-logs",
      items: activeTasks.slice(0, 3).map((item) => item.title),
    },
  }[focus];

  return (
    <Card shadow="card" padding="md" className="mb-6 border-brand/20 bg-brand-soft/40">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0"><p className="text-xs font-semibold text-brand">已选入口</p><h3 className="mt-1 text-lg font-semibold text-ink">{content.title}</h3><p className="mt-1 text-sm leading-6 text-body">{content.body}</p><div className="mt-3 flex flex-wrap gap-2">{content.items.length ? content.items.map((item) => <span className="max-w-full break-all rounded-field bg-elevated px-3 py-1.5 font-mono text-xs text-muted" key={item}>{item.includes("/") ? compactProjectPath(item) : item}</span>) : <span className="rounded-field bg-elevated px-3 py-1.5 text-xs text-muted">无</span>}</div></div>
        {content.href ? <Link href={content.href}><Button variant="primary" size="sm">{content.action}<ArrowRight className="h-3.5 w-3.5" /></Button></Link> : null}
      </div>
    </Card>
  );
}

export function MiniFact({ label, value }: { label: string; value: string }) {
  return <div className="min-w-0 rounded-field border border-line bg-surfaceAlt p-3"><p className="text-xs text-muted">{label}</p><p className="mt-1 break-all text-sm font-semibold leading-5 text-ink">{value}</p></div>;
}
