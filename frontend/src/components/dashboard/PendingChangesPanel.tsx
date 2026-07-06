import Link from "next/link";
import { ArrowRight, RefreshCw, ScanLine } from "lucide-react";
import { Badge, Button, Card } from "@/components/ui";
import type { EvidenceBundle, GitHubStatus, WorkSessionCandidate, WorkSessionScanResult } from "@/lib/api";
import { compactProjectPath } from "@/lib/project-insights";

type PendingChangesPanelProps = {
  scan: WorkSessionScanResult | null;
  workSessions: WorkSessionCandidate[];
  bundles: EvidenceBundle[];
  hasProjectPath: boolean;
  scanning: boolean;
  onScan: () => void;
  github: GitHubStatus | null;
};

export function PendingChangesPanel({ scan, workSessions, bundles, hasProjectPath, scanning, onScan, github }: PendingChangesPanelProps) {
  const segments = scan?.segments ?? [];
  const batch = scan?.batch;

  return (
    <Card shadow="card" padding="none" className="overflow-hidden">
      <div className="flex flex-wrap items-start justify-between gap-4 border-b border-line px-5 py-4">
        <div className="max-w-2xl">
          <div className="flex items-center gap-2">
            <ScanLine className="h-4 w-4 text-brand" />
            <h2 className="text-sm font-semibold text-ink">待整理变更</h2>
          </div>
          <p className="mt-1 text-xs leading-5 text-muted">从上次确认点读取本地 Git 与 Agent result，再归并为可确认的开发推进段。</p>
        </div>
        <Button
          variant="primary"
          size="sm"
          disabled={!hasProjectPath || scanning}
          onClick={onScan}
          title={hasProjectPath ? "分析从上次整理点到当前 HEAD 的新变化" : "先绑定本地项目路径"}
        >
          {scanning ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <ScanLine className="h-3.5 w-3.5" />}
          分析新变化
        </Button>
      </div>

      <div className="border-b border-line px-5 py-3 text-xs leading-5 text-slate-600">
        <span className="font-semibold text-slate-800">GitHub：</span>
        {github ? githubSummary(github) : hasProjectPath ? "正在检查可选增强数据源。" : "绑定本地项目后可检查 GitHub CLI；本地 Git 分析不依赖它。"}
      </div>

      {batch ? (
        <div className="border-b border-line bg-surfaceAlt px-5 py-3 text-xs text-muted">
          <span className="font-semibold text-ink">{batch.newCommitCount} 个提交</span>
          <span> · {batch.changedFileCount} 个文件 · {segments.length} 个开发推进段</span>
          {batch.firstScan ? <span className="ml-2 text-warning-fg">首次扫描最近 30 个提交</span> : null}
          {batch.worktreeDirty ? <span className="ml-2 text-warning-fg">包含未提交变化</span> : null}
        </div>
      ) : null}

      {segments.length > 0 ? (
        <div className="divide-y divide-line">
          {segments.map((segment) => (
            <article className="px-5 py-4" key={segment.id}>
              <div className="flex flex-wrap items-center gap-2">
                <Badge label={segment.qualityStatus === "NEEDS_MANUAL" ? "需人工整理" : segmentStatus(segment.status)} tone={segment.qualityStatus === "NEEDS_MANUAL" ? "warning" : segment.status === "PENDING" ? "warning" : "slate"} />
                <Badge label={segment.generationMode === "MODEL" ? `模型归并 · ${segment.modelProvider}` : "本地规则兜底"} />
                <span className="text-xs text-muted">{segment.includedCommitRefs.length} 提交 · {segment.affectedFiles.length} 文件 · {segment.includedAgentResultRefs.length} Agent result</span>
              </div>
              <h3 className="mt-2 text-sm font-semibold text-ink">{segment.title}</h3>
              <p className="mt-1 max-w-3xl text-sm leading-6 text-muted">{segment.plainSummary}</p>
              <ul className="mt-3 space-y-1 text-sm leading-6 text-slate-700">
                {segment.mainChanges.map((change) => <li key={change}>• {change}</li>)}
              </ul>
              {segment.qualityReason || segment.fallbackReason ? (
                <p className="mt-3 rounded-md bg-amber-50 px-3 py-2 text-xs leading-5 text-amber-900">{segment.qualityReason || segment.fallbackReason}</p>
              ) : null}
              <details className="mt-3 text-xs text-muted">
                <summary className="cursor-pointer font-medium text-slate-700 hover:text-ink">查看证据细节</summary>
                <div className="mt-2 space-y-2 rounded-field bg-surfaceAlt p-3">
                  <p>证据引用：{segment.evidenceRefs.length}</p>
                  {segment.commitUrls.map((url) => <p key={url}><a className="font-medium text-brand hover:underline" href={url} rel="noreferrer" target="_blank">{url}</a></p>)}
                  <div className="flex flex-wrap gap-2">
                    {segment.affectedFiles.slice(0, 8).map((file) => (
                      <code className="max-w-full break-all rounded-field bg-white px-2 py-1" key={file}>{compactProjectPath(file)}</code>
                    ))}
                  </div>
                </div>
              </details>
            </article>
          ))}
          <div className="flex justify-end px-5 py-4">
            <Link className="inline-flex items-center gap-1 text-sm font-semibold text-brand hover:text-brand-hover" href="/tasks">
              进入沉淀确认 <ArrowRight className="h-4 w-4" />
            </Link>
          </div>
          {batch ? <details className="border-t border-line bg-surfaceAlt px-5 py-4 text-xs text-slate-600">
            <summary className="cursor-pointer font-semibold text-slate-700">分析详情 / 诊断信息</summary>
            <dl className="mt-3 grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
              <Diagnostic label="归并方式" value={batch.segmentationMode === "MODEL" ? "模型归并" : "本地规则兜底"} />
              <Diagnostic label="模型状态" value={`${batch.modelStatus}${batch.modelProvider ? ` · ${batch.modelProvider}` : ""}`} />
              <Diagnostic label="GitHub / 远程" value={`${batch.githubStatus} · ${batch.remoteRelation}`} />
              <Diagnostic label="Agent result" value={`读取 ${batch.agentResultCount} 条`} />
              <Diagnostic label="工作区" value={batch.worktreeDirty ? "包含未提交变化" : "无未提交变化"} />
              <Diagnostic label="总耗时" value={`${batch.totalScanMs} ms`} />
              <Diagnostic label="Git / 模型 / GitHub" value={`${batch.gitScanMs} / ${batch.modelSegmentMs} / ${batch.githubInspectMs} ms`} />
              <Diagnostic label="扫描指纹" value={batch.scanFingerprint.slice(0, 12)} />
            </dl>
            {batch.fallbackReason ? <p className="mt-3 text-amber-800">{batch.fallbackReason}</p> : null}
          </details> : null}
        </div>
      ) : (
        <div className="px-5 py-6 text-sm leading-6 text-muted">
          {hasProjectPath ? "当前还没有待整理变更。分析后，ProjectFlow 会在这里显示开发推进段。" : "先绑定本地项目路径，ProjectFlow 才能读取 Git 变化。"}
        </div>
      )}

      {workSessions.length > 0 || bundles.length > 0 ? (
        <details className="border-t border-line px-5 py-4 text-sm">
          <summary className="cursor-pointer font-medium text-slate-700">兼容证据记录</summary>
          <p className="mt-2 text-xs leading-5 text-muted">旧 WorkSession 与 EvidenceBundle 仍保留，可用于追溯历史数据，不再作为主流程。</p>
        </details>
      ) : null}
    </Card>
  );
}

function Diagnostic({ label, value }: { label: string; value: string }) {
  return <div><dt className="text-slate-500">{label}</dt><dd className="mt-0.5 break-all font-medium text-slate-800">{value}</dd></div>;
}

function githubSummary(status: GitHubStatus) {
  if (status.status === "CONNECTED") {
    if (status.remoteRelation === "local_ahead") return `已接入 ${status.nameWithOwner}，本地领先 ${status.localAhead} 个提交。`;
    if (status.remoteRelation === "remote_ahead") return `已接入 ${status.nameWithOwner}，远程领先 ${status.remoteAhead} 个提交，建议先同步本地代码。`;
    if (status.remoteRelation === "diverged") return `已接入 ${status.nameWithOwner}，本地与远程已分叉。`;
    return `已接入 ${status.nameWithOwner || "GitHub 仓库"}，${status.remoteRelation === "synced" ? "本地与远程已同步。" : "当前分支没有 upstream。"}`;
  }
  if (status.status === "CONNECTION_TIMEOUT") return "连接超时，可能需要代理；本地 Git 分析仍可使用。";
  if (status.status === "NOT_INSTALLED") return "未检测到 GitHub CLI，本地 Git 分析仍可使用。";
  if (status.status === "NOT_AUTHENTICATED") return "GitHub CLI 未登录，仅使用本地 Git 信息。";
  return status.warnings[0] ?? "GitHub 当前不可用，本地 Git 分析仍可使用。";
}

function segmentStatus(status: string) {
  if (status === "CONFIRMED") return "已确认";
  if (status === "IGNORED") return "已忽略";
  if (status === "NEEDS_REVIEW") return "需复核";
  return "待确认";
}
