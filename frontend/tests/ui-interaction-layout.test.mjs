import { readFileSync } from "node:fs";
import { join } from "node:path";
import assert from "node:assert/strict";

const root = process.cwd();
const dashboard = readFileSync(join(root, "src/app/dashboard/page.tsx"), "utf8");
const devLogs = readFileSync(join(root, "src/app/dev-logs/page.tsx"), "utf8");
const outputs = readFileSync(join(root, "src/app/ai-review/page.tsx"), "utf8");
const primitives = readFileSync(join(root, "src/components/ui/primitives.tsx"), "utf8");
const activityFeed = readFileSync(join(root, "src/components/dashboard/ActivityFeed.tsx"), "utf8");
const architectureQuickEntry = readFileSync(join(root, "src/components/dashboard/ArchitectureQuickEntry.tsx"), "utf8");
const flowGuideDialog = readFileSync(join(root, "src/components/dashboard/FlowGuideDialog.tsx"), "utf8");
const workSessionDetail = readFileSync(join(root, "src/app/work-sessions/[sessionId]/page.tsx"), "utf8");
const changeDetail = readFileSync(join(root, "src/app/project-changes/[changeId]/page.tsx"), "utf8");
const changeEvidence = readFileSync(join(root, "src/app/project-changes/[changeId]/evidence/page.tsx"), "utf8");

assert.match(dashboard, /showFlowGuide/, "dashboard should open the onboarding flow from a button instead of always showing the full guide");
assert.match(dashboard, /FlowGuideDialog/, "dashboard should render the flow guide in a standalone dialog");
assert.match(dashboard, /打开上手流程/, "dashboard should expose a compact top-bar guide button");
assert.match(flowGuideDialog, /FlowStepStrip/, "flow steps should be displayed as colored rounded strip cards inside the dialog");
assert.doesNotMatch(dashboard, /<ProjectFlowGuide state=\{projectFlowState\} \/>/, "dashboard should not permanently render the large guide block");

assert.match(dashboard, /InteractiveStat/, "dashboard metrics should be clickable interaction entries, not dead number cards");
assert.match(dashboard, /statsFocus/, "dashboard should use metric clicks to focus related content");
assert.match(dashboard, /ArchitectureQuickEntry/, "dashboard should put a compact architecture entry in the right rail");
assert.match(architectureQuickEntry, /架构入口/, "right rail should expose the architecture summary entry");
assert.match(activityFeed, /ActivityGroup/, "recent activity should be grouped by review state instead of one flat mixed feed");
assert.match(activityFeed, /activityImpactSummary/, "recent activity should describe what changed, not only that something changed");
assert.match(activityFeed, /今日变化在左侧闭环处理/, "recent activity should not duplicate the main daily change loop");
assert.match(dashboard, /InfoBubble/, "non-clickable project status and model state should render as information bubbles, not button-like controls");

assert.match(outputs, /SourceQuickFilter/, "output source pills should be clickable source filters");
assert.match(outputs, /activeSourcePanel/, "output page should switch visible source detail from top pills");
assert.match(outputs, /SourceCardList/, "output source details should use compact source cards");

assert.match(devLogs, /DailySourceGrid/, "daily review sources should use a compact source grid");
assert.match(devLogs, /SourceStateChip/, "daily review source state should be rendered as non-clickable status chips inside one merged entry");
assert.match(devLogs, /查看全部来源/, "daily review sources should expose a single merged source action");
assert.match(devLogs, /SourceCardList/, "daily review source panels should render cards instead of long raw paragraphs");
assert.doesNotMatch(devLogs, /<p className="whitespace-pre-line"/, "daily review source items should not render long template paragraphs");

assert.match(primitives, /hover:-translate-y-0\.5/, "clickable buttons should provide clear hover movement");
assert.match(primitives, /active:translate-y-0/, "clickable buttons should provide active press feedback");

assert.match(workSessionDetail, /具体改了什么/, "git evidence detail page should explicitly explain what changed");
assert.match(workSessionDetail, /ChangeIntentCard/, "git evidence detail page should summarize change intent");
assert.match(workSessionDetail, /FileChangeSummary/, "git evidence detail page should classify changed files");
assert.match(workSessionDetail, /EvidenceTimeline/, "git evidence detail page should show evidence as structured timeline items");

assert.match(changeDetail, /自动化审查/, "structured change detail should be positioned as automated review");
assert.match(changeDetail, /沉淀确认/, "suggested sediment detail should prioritize the confirmation decision");
assert.match(changeDetail, /建议写入项目沉淀/, "suggested sediment detail should show sediment candidates before manual correction");
assert.match(changeDetail, /修正 AI 总结/, "manual change editing should be a secondary correction action");
assert.match(changeDetail, /\/project-changes\/\$\{change\.id\}\/evidence/, "structured change detail should send long evidence to the evidence page");
assert.doesNotMatch(changeDetail, /function EvidenceCard/, "structured change detail should not inline long evidence cards");
assert.doesNotMatch(changeDetail, /<details className="rounded-md border border-line bg-slate-50 p-3">/, "structured change detail should not expand raw evidence inline");

assert.match(changeEvidence, /完整证据/, "change evidence page should identify itself as the traceability view");
assert.match(changeEvidence, /groupFilesByModule/, "change evidence page should group affected files by module");
assert.match(changeEvidence, /复制路径/, "change evidence page should make paths copyable");
assert.match(changeEvidence, /测试证据/, "change evidence page should show test evidence");
assert.match(changeEvidence, /构建证据/, "change evidence page should show build evidence");

console.log("ui interaction layout checks passed");
