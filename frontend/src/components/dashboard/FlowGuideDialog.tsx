import Link from "next/link";
import { ArrowRight, X } from "lucide-react";
import { Button } from "@/components/ui";
import { projectFlowSteps, resolveProjectFlowState } from "@/lib/project-flow-state";

export function FlowGuideDialog({
  onClose,
  open,
  state,
}: {
  onClose: () => void;
  open: boolean;
  state: ReturnType<typeof resolveProjectFlowState>;
}) {
  if (!open) {
    return null;
  }
  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-ink/35 p-4" role="dialog" aria-modal="true" aria-label="独立上手流程">
      <div className="max-h-[86vh] w-full max-w-5xl overflow-auto rounded-card border border-line bg-elevated shadow-cardLg">
        <div className="flex flex-wrap items-start justify-between gap-3 border-b border-line p-5">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-brand">独立上手流程</p>
            <h2 className="mt-2 text-2xl font-semibold text-ink">{state.title}</h2>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-body">{state.description}</p>
          </div>
          <Button variant="ghost" size="sm" onClick={onClose} aria-label="关闭上手流程">
            <X className="h-4 w-4" />
            关闭
          </Button>
        </div>
        <div className="space-y-5 p-5">
          <p className="rounded-field bg-surfaceAlt px-4 py-3 text-sm leading-6 text-muted">{state.helper}</p>
          <FlowStepStrip state={state} />
          {state.primaryHref ? (
            <Link className="inline-flex" href={state.primaryHref} onClick={onClose}>
              <Button variant="primary" size="md">
                下一步：{state.primaryAction} <ArrowRight className="h-4 w-4" />
              </Button>
            </Link>
          ) : (
            <div className="inline-flex rounded-field bg-brand-soft px-4 py-2 text-sm font-semibold text-brand">
              下一步：{state.primaryAction}
            </div>
          )}
          {state.secondaryAction && state.secondaryHref ? (
            <Link className="ml-3 inline-flex" href={state.secondaryHref} onClick={onClose}>
              <Button variant="ghost" size="md">
                {state.secondaryAction}
              </Button>
            </Link>
          ) : null}
        </div>
      </div>
    </div>
  );
}

export function FlowStepStrip({ state }: { state: ReturnType<typeof resolveProjectFlowState> }) {
  const colors = [
    "bg-emerald-50 border-emerald-100 text-emerald-900",
    "bg-cyan-50 border-cyan-100 text-cyan-900",
    "bg-blue-50 border-blue-100 text-blue-900",
    "bg-amber-50 border-amber-100 text-amber-900",
    "bg-indigo-50 border-indigo-100 text-indigo-900",
    "bg-slate-100 border-slate-200 text-slate-800",
  ];
  return (
    <div className="grid gap-3 md:grid-cols-3 xl:grid-cols-6">
      {projectFlowSteps.map((step, index) => {
        const done = state.completedSteps.includes(step.key);
        const active = state.nextStep === step.key && !done;
        return (
          <div className={`rounded-card border p-4 ${colors[index % colors.length]} ${active ? "ring-2 ring-brand/40" : ""}`} key={step.key}>
            <div className="flex items-center gap-2">
              <span className={`grid h-7 w-7 place-items-center rounded-full text-xs font-semibold ${done ? "bg-success text-white" : active ? "bg-brand text-white" : "bg-white/80 text-ink"}`}>
                {index + 1}
              </span>
              <p className="text-sm font-semibold">{step.label}</p>
            </div>
            <p className="mt-3 text-xs leading-5 opacity-80">{step.description}</p>
          </div>
        );
      })}
    </div>
  );
}
