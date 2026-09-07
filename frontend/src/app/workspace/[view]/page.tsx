import { Suspense } from "react";
import { notFound } from "next/navigation";
import { Workspace } from "@/components/workspace/Workspace";
import { workspaceViews, type WorkspaceView } from "@/lib/workspace-preview";

export default async function WorkspacePage({
  params,
}: {
  params: Promise<{ view: string }>;
}) {
  const { view } = await params;
  if (!workspaceViews.includes(view as WorkspaceView)) notFound();
  return (
    <Suspense
      fallback={
        <div className="pf-boot" role="status">
          正在打开项目工作区…
        </div>
      }
    >
      <Workspace view={view as WorkspaceView} />
    </Suspense>
  );
}
