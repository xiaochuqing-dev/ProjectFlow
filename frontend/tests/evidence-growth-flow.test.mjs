import { readFileSync } from "node:fs";
import { join } from "node:path";
import assert from "node:assert/strict";

const root = process.cwd();
const dashboard = readFileSync(join(root, "src/app/dashboard/page.tsx"), "utf8");
const tasks = readFileSync(join(root, "src/app/tasks/page.tsx"), "utf8");
const changeReviewSidebar = readFileSync(join(root, "src/components/tasks/ChangeReviewSidebar.tsx"), "utf8");
const intelligence = readFileSync(join(root, "src/app/project-intelligence/page.tsx"), "utf8");
const outputs = readFileSync(join(root, "src/app/ai-review/page.tsx"), "utf8");
const api = readFileSync(join(root, "src/lib/api.ts"), "utf8");
const evidenceFlowPanel = readFileSync(join(root, "src/components/dashboard/EvidenceFlowPanel.tsx"), "utf8");
const projectAccessCard = readFileSync(join(root, "src/components/dashboard/ProjectAccessCard.tsx"), "utf8");

assert.match(api, /status: "READY_FOR_CHANGE"/, "evidence bundles should expose lifecycle status");
assert.match(api, /nextAction: "GENERATE_CHANGE"/, "evidence bundles should expose the next user action");
assert.match(api, /draftProjectChangeFromEvidenceBundle/, "frontend API should create structured changes from evidence bundles");

assert.match(dashboard, /EvidenceFlowPanel/, "dashboard should render the evidence-to-review flow");
assert.match(evidenceFlowPanel, /今日变化闭环/, "dashboard evidence panel should name the post-development feedback loop");
assert.match(evidenceFlowPanel, /生成证据包/, "dashboard evidence panel should let users create evidence packages from work sessions");
assert.match(evidenceFlowPanel, /生成候选变更/, "dashboard evidence panel should let users turn evidence into reviewable changes");
assert.match(dashboard, /采纳后会写入项目档案和事实来源/, "dashboard should explain what happens after review");
assert.doesNotMatch(
  evidenceFlowPanel,
  /!bundleBySession\.has\(bundle\.workSessionId\)/,
  "orphan evidence bundles should be compared against visible work sessions, not against their own bundle map",
);
assert.match(evidenceFlowPanel, /workSessionIds/, "dashboard evidence panel should keep evidence bundles visible when the matching work session is no longer in the current slice");

assert.match(tasks, /ChangeReviewSidebar/, "change review route should render the write-target sidebar");
assert.match(changeReviewSidebar, /采纳后写入/, "change review should preview where accepted facts go");
assert.match(changeReviewSidebar, /changeMemoryTargets/, "change review should derive project-memory target fields");
assert.match(changeReviewSidebar, /每日回顾、README 草稿、周报/, "change review should explain downstream reuse");

assert.match(intelligence, /成长时间线/, "project profile should expose project growth history");
assert.match(intelligence, /输出来源/, "project profile copy should connect growth records to outputs");

assert.match(outputs, /生成依据/, "output generation should show source readiness");
assert.match(outputs, /OutputSourceMetric/, "output generation should summarize available source assets");
assert.match(outputs, /缺少来源时仍可生成草稿/, "output generation should warn when sources are incomplete");

assert.match(projectAccessCard, /node_modules、\.next、target、dist、build/, "zip import guidance should classify dependencies and build output as runtime artifacts");

console.log("evidence growth flow checks passed");
