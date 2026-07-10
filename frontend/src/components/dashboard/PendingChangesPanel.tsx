import Link from "next/link";
import { ArrowRight, RefreshCw, ScanLine } from "lucide-react";
import { Badge, Button, Card } from "@/components/ui";
import type { EvidenceBundle, GitHubStatus, ProjectAnalysisJob, WorkSessionCandidate, WorkSessionScanResult } from "@/lib/api";
import { compactProjectPath } from "@/lib/project-insights";
import { generationModeLabel, githubDiagnosticLabel, githubStatusLabel, mergeModeLabel, modelStatusLabel, qualityStatusLabel as sharedQualityStatusLabel } from "@/lib/status-labels";

type PendingChangesPanelProps = {
  scan: WorkSessionScanResult | null;
  workSessions: WorkSessionCandidate[];
  bundles: EvidenceBundle[];
  hasProjectPath: boolean;
  scanning: boolean;
  onScan: () => void;
  github: GitHubStatus | null;
  // V3.3.3: GitHub 状态与登录指引操作。
  onRefreshGitHub?: () => void;
  refreshingGitHub?: boolean;
  onShowGitHubLogin?: () => void;
  // V3.3.3: 当前活跃分析任务（用于显示阶段进度）。
  activeJob?: ProjectAnalysisJob | null;
};

// V3.3.3: 分析阶段中文映射。用户必须能看懂"现在在做什么"。
const STAGE_LABELS: Record<string, string> = {
  QUEUED: "等待任务启动",
  GIT_SCAN: "正在读取本地 Git 提交与工作区变化",
  GITHUB_INSPECT: "正在检查 GitHub 状态",
  MODEL_ENRICH: "正在调用模型分析开发推进段",
  PERSIST: "正在保存分析结果并生成建议沉淀",
  SUCCEEDED: "分析完成",
  FAILED: "分析失败",
};

export function PendingChangesPanel({
  scan,
  workSessions,
  bundles,
  hasProjectPath,
  scanning,
  onScan,
  github,
  onRefreshGitHub,
  refreshingGitHub,
  activeJob,
}: PendingChangesPanelProps) {
  const segments = scan?.segments ?? [];
  const batch = scan?.batch;

  // ponytail: 后端 segment 的数组字段在偶发情况下可能为 null（Java record + Jackson 序列化），
  // 这里统一兜底，避免 .map() 抛 "Cannot read properties of null" 触发 global-error。
  const safeSegments = segments.map((segment) => ({
    ...segment,
    mainChanges: segment.mainChanges ?? [],
    includedCommitRefs: segment.includedCommitRefs ?? [],
    includedAgentResultRefs: segment.includedAgentResultRefs ?? [],
    affectedFiles: segment.affectedFiles ?? [],
    evidenceRefs: segment.evidenceRefs ?? [],
    commitUrls: segment.commitUrls ?? [],
    uncertainties: segment.uncertainties ?? [],
  }));

  const stage = activeJob?.stage ?? "";
  const stageMessage = activeJob?.stageMessage ?? "";
  const showProgress = scanning && stage && stage !== "SUCCEEDED" && stage !== "FAILED";
  const elapsedMs = computeElapsedMs(activeJob);
  const isModelStage = stage === "MODEL_ENRICH";

  // V3.3.3: 解析分析口径 JSON。
  const scope = parseAnalysisScope(batch?.analysisScope ?? null);

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

      {/* V3.3.4: GitHub 简要状态。接入与登录入口已前移到「项目接入」区域，这里只保留状态摘要。 */}
      <div className="border-b border-line px-5 py-3 text-xs leading-5 text-slate-600">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="min-w-0 flex-1">
            <span className="font-semibold text-slate-800">GitHub：</span>
            {github ? githubSummary(github) : hasProjectPath ? "正在检查可选数据源。" : "绑定本地项目后可检查 GitHub CLI；本地 Git 分析不依赖它。"}
          </div>
          {hasProjectPath && github && github.status === "CONNECTED" ? (
            <button
              className="inline-flex items-center gap-1 rounded-md border border-line bg-white px-2 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
              disabled={refreshingGitHub}
              onClick={onRefreshGitHub}
              title="刷新同步状态只读取远程提交信息，不会修改本地代码。"
              type="button"
            >
              <RefreshCw className={`h-3 w-3 ${refreshingGitHub ? "animate-spin" : ""}`} />
              刷新同步状态
            </button>
          ) : null}
        </div>
        {github && github.status !== "CONNECTED" ? (
          <p className="mt-1 text-slate-500">GitHub 接入与登录入口见上方「项目接入」区域。本地 Git 分析不依赖 GitHub。</p>
        ) : null}
      </div>

      {/* V3.3.3: 分析进度可视化。用户能看到当前阶段和已等待时间。 */}
      {showProgress ? (
        <div className="border-b border-line bg-surfaceAlt px-5 py-3 text-xs leading-5">
          <div className="flex flex-wrap items-center gap-2">
            <RefreshCw className="h-3 w-3 animate-spin text-brand" />
            <span className="font-semibold text-ink">{STAGE_LABELS[stage] ?? stage}</span>
            {elapsedMs > 0 ? <span className="text-muted">已等待 {formatElapsed(elapsedMs)}</span> : null}
          </div>
          {stageMessage ? <p className="mt-1 text-slate-600">{stageMessage}</p> : null}
          {isModelStage ? (
            <p className="mt-1 text-slate-500">
              模型正在分析提交、文件、diff 和 Agent result；这一步可能需要几分钟。页面可以离开，任务会继续运行，ProjectFlow 会等待完整结果。
            </p>
          ) : null}
        </div>
      ) : null}

      {batch ? (
        <div className="border-b border-line bg-surfaceAlt px-5 py-3 text-xs text-muted">
          <span className="font-semibold text-ink">{batch.newCommitCount} 个提交</span>
          <span> · {batch.changedFileCount} 个文件 · {safeSegments.length} 个开发推进段</span>
          {batch.firstScan ? <span className="ml-2 text-warning-fg">首次扫描最近 30 个提交</span> : null}
          {batch.worktreeDirty ? <span className="ml-2 text-warning-fg">包含未提交变化</span> : null}
        </div>
      ) : null}

      {safeSegments.length > 0 ? (
        <div className="divide-y divide-line">
          {safeSegments.map((segment) => (
            <article className="px-5 py-4" key={segment.id}>
              <div className="flex flex-wrap items-center gap-2">
                <Badge label={qualityStatusLabel(segment.qualityStatus)} tone={qualityBadgeTone(segment.qualityStatus, segment.status)} />
                <Badge label={generationModeLabel(segment.generationMode, segment.modelProvider)} />
                <span className="text-xs text-muted">{segment.includedCommitRefs.length} 提交 · {segment.affectedFiles.length} 文件 · {segment.includedAgentResultRefs.length} Agent result</span>
              </div>
              <h3 className="mt-2 text-sm font-semibold text-ink break-words">{segment.title}</h3>
              <p className="mt-1 max-w-3xl text-sm leading-6 text-muted break-words line-clamp-4">{segment.plainSummary}</p>
              <ul className="mt-3 space-y-1 text-sm leading-6 text-slate-700">
                {segment.mainChanges.slice(0, 6).map((change, index) => (
                  <li className="break-words line-clamp-2" key={`${change}-${index}`}>• {change}</li>
                ))}
              </ul>
              {segment.qualityReason || segment.fallbackReason ? (
                <p className="mt-3 rounded-md bg-amber-50 px-3 py-2 text-xs leading-5 text-amber-900">{qualityReasonText(segment)}</p>
              ) : null}
              <details className="mt-3 text-xs text-muted">
                <summary className="cursor-pointer font-medium text-slate-700 hover:text-ink">查看证据细节</summary>
                <div className="mt-2 space-y-2 rounded-field bg-surfaceAlt p-3">
                  <p>证据引用：{segment.evidenceRefs.length}</p>
                  {segment.commitUrls.map((url) => <p key={url} className="min-w-0"><a className="font-medium text-brand hover:underline break-all line-clamp-1" href={url} rel="noreferrer" target="_blank">{url}</a></p>)}
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
          {/* V3.3.3: 分析口径展示 + 诊断信息。让用户知道本次用了什么来源。 */}
          {batch ? <details className="border-t border-line bg-surfaceAlt px-5 py-4 text-xs text-slate-600" open={Boolean(scope)}>
            <summary className="cursor-pointer font-semibold text-slate-700">分析口径 / 诊断信息</summary>
            {scope ? (
              <div className="mt-3 mb-3 rounded-field border border-line bg-white p-3">
                <p className="mb-2 font-semibold text-slate-800">本次分析口径</p>
                <dl className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
                  <Diagnostic label="本地 Git" value={scopeString(scope.localGit, "参与")} />
                  <Diagnostic label="工作区 diff" value={scopeString(scope.worktreeDiff, "无")} />
                  <Diagnostic label="staged diff" value={scopeString(scope.staged, "无")} />
                  <Diagnostic label="untracked files" value={scopeString(scope.untracked, "无")} />
                  <Diagnostic label="Agent result" value={scopeString(scope.agentResults, "读取 0 条")} />
                  <Diagnostic label="GitHub" value={scopeString(scope.github, "未参与")} />
                  <Diagnostic label="GitHub 状态" value={scopeString(githubStatusLabel(String(scope.githubStatus ?? "")), "-")} />
                  <Diagnostic label="模型" value={scopeString(modelStatusLabel(String(scope.modelStatus ?? "")), "未配置")} />
                  <Diagnostic label="归并方式" value={scopeString(mergeModeLabel(String(scope.mergeMode ?? "")), "本地事实摘要")} />
                  <Diagnostic label="未提交内容" value={scope.hasUncommitted === true ? "存在" : "无"} />
                  <Diagnostic label="远程未同步" value={scope.hasRemoteUnsynced === true ? "存在" : "无"} />
                  <Diagnostic label="证据缺口" value={scope.evidenceGap === true ? "存在" : "无"} />
                  {scope.evidenceGap === true && scope.evidenceGapReason ? (
                    <Diagnostic label="缺口原因" value={String(scope.evidenceGapReason)} />
                  ) : null}
                  {scope.providerName ? <Diagnostic label="Provider / 模型" value={`${scopeString(scope.providerName, "-")} / ${scopeString(scope.modelName, "-")}`} /> : null}
                  {scope.finishReason !== undefined ? <Diagnostic label="finish reason" value={scopeString(scope.finishReason, "未返回")} /> : null}
                  {scope.effectiveMaxTokens !== undefined ? <Diagnostic label="Max Tokens（配置 / 任务 / 实际）" value={`${scopeString(scope.providerMaxTokens, "0")} / ${scopeString(scope.taskPolicyMaxTokens, "0")} / ${scopeString(scope.effectiveMaxTokens, "0")}`} /> : null}
                  {scope.effectiveTemperature !== undefined ? <Diagnostic label="Temperature（配置 / 实际）" value={`${scopeString(scope.providerTemperature, "0")} / ${scopeString(scope.effectiveTemperature, "0")}`} /> : null}
                  {scope.totalTokens !== undefined ? <Diagnostic label="Token（输入 / 输出 / 总计）" value={`${scopeString(scope.promptTokens, "0")} / ${scopeString(scope.completionTokens, "0")} / ${scopeString(scope.totalTokens, "0")}`} /> : null}
                  {scope.outputTruncated !== undefined ? <Diagnostic label="截断 / 紧凑重试" value={`${scope.outputTruncated === true ? "检测到截断" : "未检测到截断"} / ${scope.compactRetryAttempted === true ? scope.compactRetrySucceeded === true ? "重试成功" : "重试后仍为部分结果" : "未重试"}`} /> : null}
                </dl>
              </div>
            ) : null}
            <dl className="grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
              <Diagnostic label="归并方式" value={mergeModeLabel(batch.segmentationMode)} />
              <Diagnostic label="模型状态" value={modelStatusLabel(batch.modelStatus, batch.modelProvider)} />
              <Diagnostic label="GitHub / 远程" value={githubDiagnosticLabel(batch.githubStatus, batch.remoteRelation)} />
              <Diagnostic label="Agent result" value={`读取 ${batch.agentResultCount} 条`} />
              <Diagnostic label="工作区" value={batch.worktreeDirty ? "包含未提交变化" : "无未提交变化"} />
              <Diagnostic label="总耗时" value={`${batch.totalScanMs} ms`} />
              <Diagnostic label="Git / 模型 / GitHub" value={`${batch.gitScanMs} / ${batch.modelSegmentMs} / ${batch.githubInspectMs} ms`} />
              <Diagnostic label="扫描指纹" value={(batch.scanFingerprint ?? "").slice(0, 12)} />
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
    if (status.remoteRelation === "local_ahead") return `已接入 ${status.nameWithOwner}，本地领先 ${status.localAhead} 个提交（未推送）。`;
    if (status.remoteRelation === "remote_ahead") return `已接入 ${status.nameWithOwner}，远程领先 ${status.remoteAhead} 个提交，建议先同步本地代码。`;
    if (status.remoteRelation === "diverged") return `已接入 ${status.nameWithOwner}，本地与远程已分叉，当前分析可能不完整。`;
    return `已接入 ${status.nameWithOwner || "GitHub 仓库"}，${status.remoteRelation === "synced" ? "本地与远程已同步。" : "当前分支没有 upstream。"}`;
  }
  if (status.status === "CONNECTION_TIMEOUT") return "连接超时，可能需要代理；本地 Git 分析仍可使用。";
  if (status.status === "NOT_INSTALLED") return "未安装 GitHub CLI，本地 Git 分析仍可使用。";
  if (status.status === "NOT_AUTHENTICATED") return "GitHub CLI 未登录，仅使用本地 Git 信息。";
  if (status.status === "PERMISSION_DENIED") return "GitHub 权限不足，请检查登录账号或仓库权限；本地 Git 分析不受影响。";
  return status.warnings[0] ?? "GitHub 当前不可用，本地 Git 分析仍可使用。";
}

// V3.3.4: 质量状态标签复用共享映射，避免重复定义。
function qualityStatusLabel(qualityStatus: string): string {
  return sharedQualityStatusLabel(qualityStatus);
}

function qualityBadgeTone(qualityStatus: string, status: string): "success" | "warning" | "slate" {
  if (status === "CONFIRMED") return "success";
  if (qualityStatus === "PASS") return "warning";
  return "slate";
}

function qualityReasonText(segment: { qualityReason: string; fallbackReason: string; qualityStatus: string }): string {
  if (segment.qualityStatus === "NEEDS_CHINESE_REWRITE") return "模型输出含未中文化内容，需人工改写为简体中文人话。" + (segment.qualityReason ? "（" + segment.qualityReason + "）" : "");
  if (segment.qualityStatus === "NEEDS_EVIDENCE") return "缺少证据引用，需补充来源。" + (segment.qualityReason ? "（" + segment.qualityReason + "）" : "");
  if (segment.qualityStatus === "LOW_CONFIDENCE") return "模型置信度较低，建议人工复核。" + (segment.qualityReason ? "（" + segment.qualityReason + "）" : "");
  return segment.qualityReason || segment.fallbackReason;
}

function computeElapsedMs(job: ProjectAnalysisJob | null | undefined): number {
  if (!job || !job.currentStepStartedAt) return 0;
  const start = new Date(job.currentStepStartedAt).getTime();
  if (!Number.isFinite(start)) return 0;
  const end = job.completedAt ? new Date(job.completedAt).getTime() : Date.now();
  return Math.max(0, end - start);
}

function formatElapsed(ms: number): string {
  if (ms < 1000) return `${ms} ms`;
  const seconds = Math.floor(ms / 1000);
  if (seconds < 60) return `${seconds} 秒`;
  const minutes = Math.floor(seconds / 60);
  const rem = seconds % 60;
  return `${minutes} 分 ${rem} 秒`;
}

// V3.3.3: 解析分析口径 JSON（宽容解析，后端可能返回空字符串或旧数据）。
type AnalysisScope = Record<string, string | number | boolean>;

function parseAnalysisScope(raw: string | null): AnalysisScope | null {
  if (!raw || !raw.trim()) return null;
  try {
    const parsed = JSON.parse(raw);
    if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
      return parsed as AnalysisScope;
    }
    return null;
  } catch {
    return null;
  }
}

function scopeString(value: string | number | boolean | undefined, fallback: string): string {
  if (value === undefined || value === null || value === "") return fallback;
  return String(value);
}
