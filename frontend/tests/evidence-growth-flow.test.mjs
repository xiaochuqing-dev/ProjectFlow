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
const pendingChangesPanel = readFileSync(join(root, "src/components/dashboard/PendingChangesPanel.tsx"), "utf8");
const projectAccessCard = readFileSync(join(root, "src/components/dashboard/ProjectAccessCard.tsx"), "utf8");

assert.match(api, /status: "READY_FOR_CHANGE"/, "evidence bundles should expose lifecycle status");
assert.match(api, /nextAction: "GENERATE_CHANGE"/, "evidence bundles should expose the next user action");
assert.match(api, /draftProjectChangeFromEvidenceBundle/, "frontend API should create structured changes from evidence bundles");

assert.match(dashboard, /PendingChangesPanel/, "dashboard should render the V3.3 pending-change flow");
assert.match(pendingChangesPanel, /分析新变化/, "dashboard should expose the cursor-based scan action");
assert.match(pendingChangesPanel, /开发推进段/, "dashboard should group raw changes into reviewable segments");
assert.match(pendingChangesPanel, /查看项目记录/, "dashboard should route automatically recorded facts to project records");
assert.match(api, /batch: ChangeBatch \| null/, "scan responses should expose a stable change batch");
assert.match(api, /segments: DevelopmentSegment\[\]/, "scan responses should expose deterministic segments");
assert.match(api, /export type ProjectFact/, "frontend API should expose long-lived project facts");
assert.match(api, /listProjectRecordBatches/, "frontend API should expose batch-oriented project records");

assert.match(tasks, /ChangeReviewSidebar/, "change review route should render the write-target sidebar");
assert.match(changeReviewSidebar, /确认后写入/, "change review should preview where confirmed facts go");
assert.match(changeReviewSidebar, /changeMemoryTargets/, "change review should derive project-memory target fields");
assert.match(changeReviewSidebar, /每日回顾、README 草稿、周报/, "change review should explain downstream reuse");

assert.match(intelligence, /项目事实概览/, "project memory should expose the new factual foundation");
assert.match(intelligence, /旧版已确认沉淀/, "legacy confirmed sediments should remain available as compatibility content");

assert.match(outputs, /生成依据/, "output generation should show source readiness");
assert.match(outputs, /OutputSourceMetric/, "output generation should summarize available source assets");
assert.match(outputs, /缺少来源时仍可生成草稿/, "output generation should warn when sources are incomplete");

assert.match(projectAccessCard, /node_modules、\.next、target、dist、build/, "zip import guidance should classify dependencies and build output as runtime artifacts");

console.log("evidence growth flow checks passed");
