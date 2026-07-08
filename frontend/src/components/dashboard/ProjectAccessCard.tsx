import type { FormEvent } from "react";
import Link from "next/link";
import { ArrowRight, Clipboard, ExternalLink, FileCode2, FolderTree, RefreshCw, Save, ScanLine, Terminal, Upload } from "lucide-react";
import { Badge, Button, Card } from "@/components/ui";
import type { GitHubLoginGuide, GitHubStatus } from "@/lib/api";
import { githubStatusLabel, remoteRelationLabel } from "@/lib/status-labels";
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
              选择完整项目 zip，创建新的项目理解和文件结构理解。node_modules、.next、target、dist、build 会按运行产物处理，不计入源码重点。
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
  // V3.3.4: GitHub 接入状态与操作前移到项目接入区域。
  hasProjectPath: boolean;
  github: GitHubStatus | null;
  refreshingGitHub: boolean;
  openingTerminal: boolean;
  loginGuide: GitHubLoginGuide | null;
  onRefreshGitHub: () => void;
  onShowGitHubLogin: () => void;
  onOpenLoginTerminal: () => void;
  onClearLoginGuide: () => void;
  // V3.3.4: 模型状态（与 GitHub 一起作为项目接入状态展示）。
  modelName: string | null;
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
        <div className="mt-3 grid gap-2 sm:grid-cols-[minmax(0,1fr)_auto]">
          <Button
            variant="primary"
            size="sm"
            disabled={!hasSelectedProject || !props.projectPath.trim() || props.savingProjectPath}
            onClick={props.onSavePath}
            title="绑定真实项目根目录，后续分析新变化会复用这个路径，不写入目标项目文件。"
          >
            {props.savingProjectPath ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <Save className="h-3.5 w-3.5" />}
            绑定本地项目
          </Button>
          <span className="self-center text-xs text-muted">分析新变化在下方主流程卡执行。</span>
        </div>

        {/* V3.3.4: 项目接入状态。本地路径 / 模型 / GitHub 都属于项目接入，不再把 GitHub 只藏在待整理变更里。 */}
        <div className="mt-4 rounded-field border border-line bg-surfaceAlt p-3 text-xs leading-5">
          <p className="mb-2 font-semibold text-slate-800">项目接入状态</p>
          <dl className="grid gap-2 sm:grid-cols-3">
            <div>
              <dt className="text-slate-500">本地项目路径</dt>
              <dd className="mt-0.5 font-medium text-slate-800">{props.hasProjectPath ? "已绑定" : "未绑定"}</dd>
            </div>
            <div>
              <dt className="text-slate-500">模型</dt>
              <dd className="mt-0.5 font-medium text-slate-800">{props.modelName ? props.modelName : "未配置"}</dd>
            </div>
            <div>
              <dt className="text-slate-500">GitHub</dt>
              <dd className="mt-0.5 font-medium text-slate-800">{githubAccessSummary(props.github, props.hasProjectPath)}</dd>
            </div>
          </dl>

          {/* V3.3.4: GitHub 操作入口。未接入时提供登录/安装/重新检查；已接入时提供刷新同步状态。 */}
          {props.hasProjectPath && props.github ? (
            <div className="mt-3 flex flex-wrap items-center gap-2">
              {props.github.status === "CONNECTED" ? (
                <button
                  className="inline-flex items-center gap-1 rounded-md border border-line bg-white px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
                  disabled={props.refreshingGitHub}
                  onClick={props.onRefreshGitHub}
                  title="刷新同步状态只读取远程提交信息，不会修改本地代码。"
                  type="button"
                >
                  <RefreshCw className={`h-3 w-3 ${props.refreshingGitHub ? "animate-spin" : ""}`} />
                  刷新同步状态
                </button>
              ) : props.github.status === "NOT_AUTHENTICATED" ? (
                <>
                  <button
                    className="inline-flex items-center gap-1 rounded-md border border-line bg-white px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
                    disabled={props.openingTerminal}
                    onClick={props.onOpenLoginTerminal}
                    title="打开一个新终端执行 GitHub 登录命令（固定命令，不接受自定义）。"
                    type="button"
                  >
                    {props.openingTerminal ? <RefreshCw className="h-3 w-3 animate-spin" /> : <Terminal className="h-3 w-3" />}
                    打开登录终端
                  </button>
                  <button
                    className="inline-flex items-center gap-1 rounded-md border border-line bg-white px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50"
                    onClick={props.onShowGitHubLogin}
                    title="复制登录命令到终端手动执行。"
                    type="button"
                  >
                    <Clipboard className="h-3 w-3" />
                    复制登录命令
                  </button>
                  <button
                    className="inline-flex items-center gap-1 rounded-md border border-line bg-white px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
                    disabled={props.refreshingGitHub}
                    onClick={props.onRefreshGitHub}
                    type="button"
                  >
                    重新检查
                  </button>
                </>
              ) : props.github.status === "NOT_INSTALLED" ? (
                <>
                  <a
                    className="inline-flex items-center gap-1 rounded-md border border-line bg-white px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50"
                    href="https://cli.github.com/"
                    rel="noreferrer"
                    target="_blank"
                  >
                    查看安装说明 <ExternalLink className="h-3 w-3" />
                  </a>
                  <button
                    className="inline-flex items-center gap-1 rounded-md border border-line bg-white px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
                    disabled={props.refreshingGitHub}
                    onClick={props.onRefreshGitHub}
                    type="button"
                  >
                    重新检查
                  </button>
                </>
              ) : (
                <button
                  className="inline-flex items-center gap-1 rounded-md border border-line bg-white px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
                  disabled={props.refreshingGitHub}
                  onClick={props.onRefreshGitHub}
                  type="button"
                >
                  <RefreshCw className={`h-3 w-3 ${props.refreshingGitHub ? "animate-spin" : ""}`} />
                  重新检查
                </button>
              )}
            </div>
          ) : null}
          <p className="mt-2 text-slate-500">GitHub 是可选接入；接入后可获得远程同步状态、commit 链接和仓库来源辅助，本地 Git 分析不依赖它。刷新同步状态只读取远程提交信息，不会修改本地代码。</p>

          {/* V3.3.4: 登录命令展示（复制命令 fallback / 明确命令）。 */}
          {props.loginGuide && props.loginGuide.command ? (
            <div className="mt-2 rounded-field border border-line bg-white p-2">
              <div className="flex items-center justify-between">
                <p className="text-slate-600">登录命令（在终端手动执行）：</p>
                <button className="text-slate-500 hover:text-slate-900" onClick={props.onClearLoginGuide} type="button">关闭</button>
              </div>
              <code className="mt-1 block break-all rounded-field bg-surfaceAlt px-2 py-1 font-mono text-slate-900">{props.loginGuide.command}</code>
              <button
                className="mt-1 rounded-md border border-line bg-white px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50"
                onClick={() => { navigator.clipboard?.writeText(props.loginGuide?.command ?? ""); }}
                type="button"
              >
                复制命令
              </button>
            </div>
          ) : null}
        </div>

        <details className="mt-4 rounded-field border border-line bg-surfaceAlt">
          <summary className="cursor-pointer px-3 py-2 text-sm font-semibold text-body hover:bg-elevated">
            Agent 高级设置
          </summary>
          <div className="grid gap-2 border-t border-line p-3 sm:grid-cols-2">
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
              title="读取目标项目的 ProjectFlow 结果收件箱，把 Agent 写回内容转成待确认内容。"
            >
              {props.scanningAgentResults ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <ScanLine className="h-3.5 w-3.5" />}
              扫描 Agent 写回内容
            </Button>
            <Button
              variant="secondary"
              size="sm"
              disabled={!hasSelectedProject || !props.projectPath.trim() || props.syncingContext}
              onClick={props.onSyncContext}
              title="把已经确认的项目沉淀写回目标项目上下文目录，供后续 Agent 读取。"
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
              复制 Agent 规则
            </Button>
          </div>
        </details>
      </div>
    </Card>
  );
}

function accessHint(step: DashboardStep, hasSelectedProject: boolean): { title: string; cta?: string; ctaHref?: string } {
  switch (step.kind) {
    case "no_project":
      return { title: "还没有项目 -- 导入项目 zip 即可创建第一个项目。", cta: undefined };
    case "no_material":
      return { title: "当前项目还没有可分析的材料，导入完整 zip 后生成画像。", cta: undefined };
    case "no_path":
      return { title: "绑定本地项目文件夹路径后，才能分析新变化与扫描 Agent 写回内容。", cta: undefined };
    case "has_pending":
      return { title: `当前有 ${step.count} 条建议沉淀。`, cta: "去沉淀确认", ctaHref: "/tasks" };
    case "scan_updates":
      return { title: "项目已就绪，分析新变化获取待整理变更。", cta: undefined };
    default:
      return { title: hasSelectedProject ? "项目接入就绪。" : "导入项目 zip 开始。" };
  }
}

// V3.3.4: GitHub 接入状态摘要文案。
function githubAccessSummary(github: GitHubStatus | null, hasProjectPath: boolean): string {
  if (!github) return hasProjectPath ? "正在检查" : "绑定路径后可检查";
  if (github.status === "CONNECTED") {
    const relation = remoteRelationLabel(github.remoteRelation);
    if (github.remoteRelation === "local_ahead") return `已接入 ${github.nameWithOwner}，本地领先 ${github.localAhead}`;
    if (github.remoteRelation === "remote_ahead") return `已接入 ${github.nameWithOwner}，远程领先 ${github.remoteAhead}`;
    if (github.remoteRelation === "diverged") return `已接入 ${github.nameWithOwner}，已分叉`;
    return `已接入 ${github.nameWithOwner}，${relation}`;
  }
  return githubStatusLabel(github.status);
}
