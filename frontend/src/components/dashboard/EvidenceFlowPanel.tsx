import Link from "next/link";
import { ArrowRight, FileCode2, History, RefreshCw, ScanLine } from "lucide-react";
import { Badge, Button, Card } from "@/components/ui";
import type { EvidenceBundle, WorkSessionCandidate } from "@/lib/api";
import { compactProjectPath } from "@/lib/project-insights";

export type EvidenceFlowPanelProps = {
  workSessions: WorkSessionCandidate[];
  bundles: EvidenceBundle[];
  hasProjectPath: boolean;
  selectedProjectId: string;
  scanningWorkSessions: boolean;
  creatingEvidenceFor: string;
  draftingChangeFor: string;
  onScanWorkSessions: () => void;
  onCreateEvidenceBundle: (sessionId: string) => void;
  onDraftChange: (bundleId: string) => void;
};

export function EvidenceFlowPanel(props: EvidenceFlowPanelProps) {
  const bundleBySession = new Map(props.bundles.map((bundle) => [bundle.workSessionId, bundle]));
  const visibleSessions = props.workSessions.slice(0, 3);
  const workSessionIds = new Set(visibleSessions.map((session) => session.sessionId));
  const orphanBundles = props.bundles
    .filter((bundle) => !workSessionIds.has(bundle.workSessionId))
    .slice(0, Math.max(0, 3 - visibleSessions.length));

  return (
    <Card shadow="card" padding="none" className="overflow-hidden">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line px-5 py-4">
        <div>
          <div className="flex items-center gap-2">
            <History className="h-4 w-4 text-brand" />
            <h3 className="text-sm font-semibold text-ink">本次开发总结</h3>
          </div>
          <p className="mt-1 text-xs leading-5 text-muted">开发后回来刷新今日开发，把 Git evidence 整理成待确认内容，采纳后进入项目资产和输出来源。</p>
        </div>
        <Button
          variant="secondary"
          size="sm"
          disabled={!props.hasProjectPath || props.scanningWorkSessions}
          onClick={props.onScanWorkSessions}
          title={props.hasProjectPath ? "读取已绑定项目的 Git 变化，生成今日开发候选。" : "先绑定真实项目文件夹路径。"}
        >
          {props.scanningWorkSessions ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <ScanLine className="h-3.5 w-3.5" />}
          刷新今日开发
        </Button>
      </div>

      {visibleSessions.length || orphanBundles.length ? (
        <div className="divide-y divide-line">
          {visibleSessions.map((session) => (
            <EvidenceFlowRow
              bundle={bundleBySession.get(session.sessionId)}
              creatingEvidenceFor={props.creatingEvidenceFor}
              draftingChangeFor={props.draftingChangeFor}
              key={session.sessionId}
              onCreateEvidenceBundle={props.onCreateEvidenceBundle}
              onDraftChange={props.onDraftChange}
              selectedProjectId={props.selectedProjectId}
              session={session}
            />
          ))}
          {orphanBundles.map((bundle) => (
            <EvidenceFlowRow
              bundle={bundle}
              creatingEvidenceFor={props.creatingEvidenceFor}
              draftingChangeFor={props.draftingChangeFor}
              key={bundle.id}
              onCreateEvidenceBundle={props.onCreateEvidenceBundle}
              onDraftChange={props.onDraftChange}
              selectedProjectId={props.selectedProjectId}
            />
          ))}
        </div>
      ) : (
        <div className="p-5 text-sm leading-6 text-muted">
          {props.hasProjectPath
            ? "还没有今日开发记录。点击刷新后，如果当前项目有 Git 改动或提交，这里会出现本次开发候选。"
            : "先绑定本地项目路径，ProjectFlow 才能从这个项目读取 Git evidence。"}
        </div>
      )}
    </Card>
  );
}

function EvidenceFlowRow({
  bundle,
  creatingEvidenceFor,
  draftingChangeFor,
  onCreateEvidenceBundle,
  onDraftChange,
  selectedProjectId,
  session,
}: {
  bundle?: EvidenceBundle;
  creatingEvidenceFor: string;
  draftingChangeFor: string;
  onCreateEvidenceBundle: (sessionId: string) => void;
  onDraftChange: (bundleId: string) => void;
  selectedProjectId: string;
  session?: WorkSessionCandidate;
}) {
  const sessionId = session?.sessionId ?? bundle?.workSessionId ?? "";
  const status = evidenceStatus(bundle);
  const files = (bundle?.files.length ? bundle.files : session?.files ?? []).slice(0, 2);
  const title = bundle?.taskIntent || session?.taskIntent || "今日工作候选";
  const metrics = bundle
    ? `${bundle.changedFiles} 文件 · +${bundle.addedLines}/-${bundle.deletedLines}`
    : session
      ? `${session.changedFiles} 文件 · +${session.addedLines}/-${session.deletedLines}`
      : "等待证据";

  return (
    <article className="grid gap-3 p-4 md:grid-cols-[minmax(0,1fr)_auto]">
      <div className="min-w-0">
        <div className="mb-2 flex flex-wrap items-center gap-2">
          <Badge label={status.label} tone={status.tone} />
          <span className="text-xs text-muted">{metrics}</span>
        </div>
        <p className="line-clamp-2 text-sm font-semibold leading-6 text-ink">{title}</p>
        <div className="mt-2 flex flex-wrap gap-2">
          {files.length ? files.map((file) => (
            <span className="max-w-full break-all rounded-field bg-surfaceAlt px-2 py-1 font-mono text-xs text-muted" key={file}>
              {compactProjectPath(file)}
            </span>
          )) : <span className="text-xs text-muted">暂无文件证据</span>}
        </div>
      </div>
      <EvidenceFlowAction
        bundle={bundle}
        creating={creatingEvidenceFor === sessionId}
        drafting={Boolean(bundle && draftingChangeFor === bundle.id)}
        onCreateEvidenceBundle={() => sessionId && onCreateEvidenceBundle(sessionId)}
        onDraftChange={() => bundle && onDraftChange(bundle.id)}
        selectedProjectId={selectedProjectId}
      />
    </article>
  );
}

function EvidenceFlowAction({
  bundle,
  creating,
  drafting,
  onCreateEvidenceBundle,
  onDraftChange,
  selectedProjectId,
}: {
  bundle?: EvidenceBundle;
  creating: boolean;
  drafting: boolean;
  onCreateEvidenceBundle: () => void;
  onDraftChange: () => void;
  selectedProjectId: string;
}) {
  if (!bundle) {
    return (
      <Button variant="secondary" size="sm" disabled={creating} onClick={onCreateEvidenceBundle}>
        {creating ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <FileCode2 className="h-3.5 w-3.5" />}
        整理原始依据
      </Button>
    );
  }
  if (bundle.nextAction === "GENERATE_CHANGE") {
    return (
      <Button variant="primary" size="sm" disabled={drafting} onClick={onDraftChange}>
        {drafting ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : <ArrowRight className="h-3.5 w-3.5" />}
        生成待确认内容
      </Button>
    );
  }
  if (bundle.nextAction === "REVIEW_CHANGE") {
    return (
      <Link href="/tasks">
        <Button variant="primary" size="sm">
          去开发成果审查 <ArrowRight className="h-3.5 w-3.5" />
        </Button>
      </Link>
    );
  }
  if (bundle.nextAction === "VIEW_MEMORY") {
    return (
      <Link href="/project-intelligence">
        <Button variant="secondary" size="sm">
          看项目资产 <ArrowRight className="h-3.5 w-3.5" />
        </Button>
      </Link>
    );
  }
  return selectedProjectId ? (
    <Link href="/ai-review">
      <Button variant="secondary" size="sm">
        生成输出 <ArrowRight className="h-3.5 w-3.5" />
      </Button>
    </Link>
  ) : null;
}

function evidenceStatus(bundle?: EvidenceBundle): { label: string; tone: "brand" | "warning" | "success" | "slate" } {
  if (!bundle) {
    return { label: "待整理依据", tone: "slate" };
  }
  if (bundle.status === "READY_FOR_CHANGE") {
    return { label: "原始依据就绪", tone: "brand" };
  }
  if (bundle.status === "CHANGE_DRAFTED") {
    return { label: "待审查", tone: "warning" };
  }
  if (bundle.status === "CHANGE_ACCEPTED") {
    return { label: "已入资产", tone: "success" };
  }
  return { label: "已归档", tone: "slate" };
}
