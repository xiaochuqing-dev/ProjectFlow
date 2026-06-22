import Link from "next/link";
import { ArrowRight, FolderTree } from "lucide-react";
import { Button, Card, SectionHeader } from "@/components/ui";
import { buildProjectArchitecture } from "@/lib/project-insights";

export function ArchitectureQuickEntry({
  architecture,
  hasUsableProjectZip,
  paths,
  selectedProjectId,
}: {
  architecture: ReturnType<typeof buildProjectArchitecture>;
  hasUsableProjectZip: boolean;
  paths: string[];
  selectedProjectId: string;
}) {
  if (!hasUsableProjectZip) {
    return (
      <Card shadow="card" padding="md">
        <p className="text-xs font-semibold text-muted">架构入口</p>
        <p className="mt-2 text-sm leading-6 text-muted">导入完整项目 zip 后，这里会显示项目形态、入口、核心和依赖数量。</p>
      </Card>
    );
  }
  const facts = [
    ["形态", architecture.shapeLabel],
    ["入口", `${architecture.entrypoints.length} 个`],
    ["核心", `${architecture.coreModules.length} 个`],
    ["依赖", `${architecture.dependencySignals.length} 个`],
  ];
  return (
    <Card shadow="card" padding="none" className="overflow-hidden border-brand/20">
      <SectionHeader
        eyebrow="架构入口"
        title={architecture.summary || architecture.shapeLabel}
        icon={<FolderTree className="h-4 w-4" />}
        actions={
          <Link href={`/projects/${selectedProjectId}/files`}>
            <Button variant="primary" size="sm">
              完整结构 <ArrowRight className="h-3.5 w-3.5" />
            </Button>
          </Link>
        }
      />
      <div className="grid grid-cols-2 gap-2 p-4">
        {facts.map(([label, value]) => (
          <div className="rounded-field border border-line bg-surfaceAlt px-3 py-2" key={label}>
            <p className="text-xs text-muted">{label}</p>
            <p className="mt-1 truncate text-sm font-semibold text-ink">{value}</p>
          </div>
        ))}
      </div>
      <div className="border-t border-line px-4 py-3 text-xs text-muted">
        {paths.length} 个文件信号 · {architecture.shapeTags.join(" / ")}
      </div>
    </Card>
  );
}
