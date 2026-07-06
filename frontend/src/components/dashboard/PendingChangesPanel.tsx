import Link from "next/link";
import { ArrowRight, RefreshCw, ScanLine } from "lucide-react";
import { Badge, Button, Card } from "@/components/ui";
import type { EvidenceBundle, WorkSessionCandidate, WorkSessionScanResult } from "@/lib/api";
import { compactProjectPath } from "@/lib/project-insights";

type PendingChangesPanelProps = {
  scan: WorkSessionScanResult | null;
  workSessions: WorkSessionCandidate[];
  bundles: EvidenceBundle[];
  hasProjectPath: boolean;
  scanning: boolean;
  onScan: () => void;
};

export function PendingChangesPanel({ scan, workSessions, bundles, hasProjectPath, scanning, onScan }: PendingChangesPanelProps) {
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

      {batch ? (
        <div className="border-b border-line bg-surfaceAlt px-5 py-3 text-xs text-muted">
          <span className="font-semibold text-ink">{batch.newCommitCount} 个提交</span>
          <span> · {batch.changedFileCount} 个文件 · {segments.length} 个开发推进段</span>
          {batch.firstScan ? <span className="ml-2 text-warning-fg">首次扫描最近 30 个提交</span> : null}
        </div>
      ) : null}

      {segments.length > 0 ? (
        <div className="divide-y divide-line">
          {segments.map((segment) => (
            <article className="px-5 py-4" key={segment.id}>
              <div className="flex flex-wrap items-center gap-2">
                <Badge label={segmentStatus(segment.status)} tone={segment.status === "PENDING" ? "warning" : "slate"} />
                <span className="text-xs text-muted">{segment.includedCommitRefs.length} 提交 · {segment.affectedFiles.length} 文件 · {segment.includedAgentResultRefs.length} Agent result</span>
              </div>
              <h3 className="mt-2 text-sm font-semibold text-ink">{segment.title}</h3>
              <p className="mt-1 max-w-3xl text-sm leading-6 text-muted">{segment.plainSummary}</p>
              <details className="mt-3 text-xs text-muted">
                <summary className="cursor-pointer font-medium text-slate-700 hover:text-ink">查看证据细节</summary>
                <div className="mt-2 space-y-2 rounded-field bg-surfaceAlt p-3">
                  <p>证据引用：{segment.evidenceRefs.length}</p>
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

function segmentStatus(status: string) {
  if (status === "CONFIRMED") return "已确认";
  if (status === "IGNORED") return "已忽略";
  if (status === "NEEDS_REVIEW") return "需复核";
  return "待确认";
}
