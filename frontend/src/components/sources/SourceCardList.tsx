import type { ReactNode } from "react";
import { Layers3 } from "lucide-react";

export type SourceCardItem = {
  title: string;
  body: string;
  meta?: string;
};

type SourceCardListProps = {
  empty: string;
  icon?: ReactNode;
  items: SourceCardItem[];
  title: string;
  maxHeightClassName?: string;
  compactBody?: (value: string) => string;
};

export function SourceCardList({
  empty,
  icon,
  items,
  title,
  maxHeightClassName = "max-h-80 overflow-auto",
  compactBody = (value) => value,
}: SourceCardListProps) {
  return (
    <section className="rounded-md border border-line bg-white shadow-panel">
      <div className="flex items-center gap-2 border-b border-line px-5 py-4">
        {icon ?? <Layers3 className="h-4 w-4 text-slate-700" />}
        <h2 className="font-semibold">{title}</h2>
      </div>
      <div className={`space-y-3 p-5 ${maxHeightClassName}`.trim()}>
        {items.length ? items.map((item) => (
          <article className="rounded-md border border-line bg-slate-50 px-3 py-2 text-sm" key={`${title}-${item.title}-${item.body}`}>
            <div className="mb-1 flex items-center justify-between gap-3">
              <p className="font-semibold text-slate-950">{item.title}</p>
              {item.meta ? <span className="shrink-0 rounded-md bg-white px-2 py-1 text-xs text-muted">{item.meta}</span> : null}
            </div>
            <p className="line-clamp-3 leading-5 text-slate-600">{compactBody(item.body)}</p>
          </article>
        )) : <p className="text-sm text-muted">{empty}</p>}
      </div>
    </section>
  );
}
