import type { FormEvent } from "react";
import Link from "next/link";
import { ArrowRight, Clipboard, FileCode2, FolderTree, RefreshCw, Save, ScanLine, Upload } from "lucide-react";
import { Badge, Button, Card } from "@/components/ui";
import type { DashboardStep } from "./types";

export type ZipImportPanelProps = {
  file: File | null;
  setFile: (file: File | null) => void;
  importing: boolean;
  onImportZip: (event: FormEvent<HTMLFormElement>) => void;
  canClose: boolean;
  onClose: () => void;
};

export function ZipImportPanel(props: ZipImportPanelProps) {
  return (
    <Card shadow="card" padding="none" className="overflow-hidden border-brand/20">
      <form className="p-5" onSubmit={props.onImportZip}>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <div className="flex items-center gap-2">
              <Upload className="h-4 w-4 text-brand" />
              <h3 className="text-sm font-semibold text-ink">添加项目</h3>
            </div>
            <p className="mt-1 text-xs leading-5 text-muted">
              选择完整项目 zip，创建新的项目画像和文件结构理解。node_modules、.next、target、dist、build 会按运行产物处理，不计入源码重点。
            </p>
          </div>
          {props.canClose ? (
            <Button variant="ghost" size="sm" onClick={props.onClose} type="button">
              收起
            </Button>
          ) : null}
        </div>
        <div className="mt-4 grid gap-3 lg:grid-cols-[minmax(0,1fr)_220px]">
          <label className="block rounded-field border border-dashed border-lineStrong bg-surfaceAlt p-4 transition hover:border-brand">
            <span className="mb-2 block text-sm font-medium text-body">选择项目 zip</span>
            <input
              accept=".zip,application/zip"
              className="w-full text-sm text-muted"
              onChange={(event) => props.setFile(event.target.files?.[0] ?? null)}
              type="file"
            />
          </label>
          <Button variant="primary" type="submit" fullWidth disabled={!props.file || props.importing}>
            {props.importing ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />}
            {props.importing ? "导入中..." : "导入并建档"}
          </Button>
        </div>
      </form>
    </Card>
  );
}

export type ProjectAccessCardProps = {
  step: DashboardStep;
  hasSelectedProject: boolean;
  projectPath: string;
  setProjectPath: (value: string) => void;
  savingProjectPath: boolean;
  onSavePath: () => void;
  writingProtocol: boolean;
  onWriteProtocol: () => void;
  scanningAgentResults: boolean;
  onScanAgentResults: () => void;
  syncingContext: boolean;
  onSyncContext: () => void;
  onCopyGlobalRule: () => void;
};

export function ProjectAccessCard(props: ProjectAccessCardProps) {
  const { step, hasSelectedProject } = props;
  const hint = accessHint(step, hasSelectedProject);

  return (
    <Card shadow="card" padding="none" className="overflow-hidden">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-line bg-brand-soft px-5 py-3">
        <div className="flex items-center gap-2">
          <Badge label="项目接入" tone="brand" dot />
          <p className="text-sm text-body">{hint.title}</p>
        </div>
        {hint.cta && hint.ctaHref ? (
          <Link className="inline-flex items-center gap-1 text-sm font-semibold text-brand hover:text-brand-hover" href={hint.ctaHref}>
            {hint.cta} <ArrowRight className="h-3.5 w-3.5" />
          </Link>
        ) : null}
      </div>

      <div className="p-5">
        <div className="flex items-center gap-2">
          <FolderTree className="h-4 w-4 text-brand" />
          <h3 className="text-sm font-semibold text-ink">本地项目接入</h3>
        </div>
        <p className="mt-1 text-xs leading-5 text-muted">
          绑定真实项目文件夹后，才能扫描 Agent 结果、读取 Git evidence、同步上下文。不会扫描用户主目录。
        </p>
        <input
          className="mt-3 h-10 w-full rounded-field border border-line bg-elevated px-3 text-sm outline-none transition focus:border-brand focus-visible:shadow-focus disabled:cursor-not-allowed disabled:bg-surfaceAlt disabled:text-muted"
          onChange={(event) => props.setProjectPath(event.target.value)}
          placeholder={hasSelectedProject ? "真实项目文件夹路径" : "先在项目下拉选择一个项目"}
          value={props.projectPath}
          disabled={!hasSelectedProject}
        />
        <div className="mt-3 grid gap-2 sm:grid-cols-2">
          <Button
            variant="primary"
            size="sm"
            disabled={!hasSelectedProject || !props.projectPath.trim() || props.savingProjectPath}
            onClick={props.onSavePath}
            title="只记录本地项目根目录，切换项目和刷新页面后继续复用，不写入目标项目文件。"
          >
            {props.savingProjectPath ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <Save className="h-3.5 w-3.5" />}
            保存路径
          </Button>
          <Button
            variant="secondary"
            size="sm"
            disabled={!hasSelectedProject || !props.projectPath.trim() || props.writingProtocol}
            onClick={props.onWriteProtocol}
            title="在目标项目生成 ProjectFlow 协议、上下文目录和结果收件箱，供 Agent 按规则写回结果。"
          >
            {props.writingProtocol ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <FileCode2 className="h-3.5 w-3.5" />}
            写入/刷新协议
          </Button>
          <Button
            variant="secondary"
            size="sm"
            disabled={!hasSelectedProject || !props.projectPath.trim() || props.scanningAgentResults}
            onClick={props.onScanAgentResults}
            title="读取目标项目的 ProjectFlow 结果收件箱，把 Agent 写回内容转成待审查变更。"
          >
            {props.scanningAgentResults ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <ScanLine className="h-3.5 w-3.5" />}
            扫描 Agent Result
          </Button>
          <Button
            variant="secondary"
            size="sm"
            disabled={!hasSelectedProject || !props.projectPath.trim() || props.syncingContext}
            onClick={props.onSyncContext}
            title="把已经采纳和确认的项目档案写回目标项目上下文目录，供后续 Agent 读取。"
          >
            {props.syncingContext ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <FolderTree className="h-3.5 w-3.5" />}
            同步确认上下文
          </Button>
          <Button
            variant="secondary"
            size="sm"
            disabled={!hasSelectedProject}
            onClick={props.onCopyGlobalRule}
            title="复制给其他 Agent 使用的通用规则，让它们按 ProjectFlow 协议输出结果。"
          >
            <Clipboard className="h-3.5 w-3.5" />
            复制规则
          </Button>
        </div>
      </div>
    </Card>
  );
}

function accessHint(step: DashboardStep, hasSelectedProject: boolean): { title: string; cta?: string; ctaHref?: string } {
  switch (step.kind) {
    case "no_project":
      return { title: "还没有项目 —— 导入项目 zip 即可创建第一个项目。", cta: undefined };
    case "no_material":
      return { title: "当前项目还没有可分析的材料，导入完整 zip 后生成画像。", cta: undefined };
    case "no_path":
      return { title: "绑定本地项目文件夹路径后，才能扫描 Agent 结果与今日变化。", cta: undefined };
    case "has_pending":
      return { title: `当前有 ${step.count} 条待确认变更。`, cta: "去变更审查", ctaHref: "/tasks" };
    case "scan_updates":
      return { title: "项目已就绪，扫描 Agent Result 或刷新今日变化获取新候选。", cta: undefined };
    default:
      return { title: hasSelectedProject ? "项目接入就绪。" : "导入项目 zip 开始。" };
  }
}
