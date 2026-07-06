import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { Card, SectionHeader } from "@/components/ui";

export function OutputOptionsCard({ selectedProjectId }: { selectedProjectId: string }) {
  return (
    <Card shadow="card">
      <SectionHeader
        eyebrow="成果出口"
        title="当前可生成"
        actions={
          selectedProjectId ? (
            <Link className="inline-flex items-center gap-1 text-sm font-semibold text-brand hover:text-brand-hover" href="/ai-review">
              去成果输出 <ArrowRight className="h-4 w-4" />
            </Link>
          ) : null
        }
      />
      <div className="grid gap-3 p-5 sm:grid-cols-2 lg:grid-cols-4">
        <OutputOption title="README 草稿" text="把已确认项目沉淀整理成可继续编辑的项目介绍。" />
        <OutputOption title="简历描述" text="压缩为简历项目经历，突出动作、技术和结果。" />
        <OutputOption title="项目复盘" text="用项目理解、开发推进段和项目时间线整理阶段总结。" />
        <OutputOption title="周报" text="把近期确认内容转成可复用的阶段汇报。" />
      </div>
      <p className="border-t border-line px-5 py-3 text-xs leading-5 text-muted">
        确认建议沉淀后，这些输出会优先引用项目沉淀和可信依据；来源不足时仍可生成草稿，但需要人工补充。
      </p>
    </Card>
  );
}

function OutputOption({ title, text }: { title: string; text: string }) {
  return (
    <div className="rounded-field border border-line bg-surfaceAlt p-3">
      <p className="text-sm font-semibold text-ink">{title}</p>
      <p className="mt-1 text-xs leading-5 text-muted">{text}</p>
    </div>
  );
}
