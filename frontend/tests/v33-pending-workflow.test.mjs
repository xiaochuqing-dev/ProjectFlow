import { readFileSync } from "node:fs";
import { join } from "node:path";
import assert from "node:assert/strict";

const root = process.cwd();
const dashboard = readFileSync(join(root, "src/app/dashboard/page.tsx"), "utf8");
const pendingPanel = readFileSync(join(root, "src/components/dashboard/PendingChangesPanel.tsx"), "utf8");
const access = readFileSync(join(root, "src/components/dashboard/ProjectAccessCard.tsx"), "utf8");
const tasks = readFileSync(join(root, "src/app/tasks/page.tsx"), "utf8");
const reviewList = readFileSync(join(root, "src/components/tasks/ChangeReviewList.tsx"), "utf8");
const shell = readFileSync(join(root, "src/components/AppShell.tsx"), "utf8");
const api = readFileSync(join(root, "src/lib/api.ts"), "utf8");

assert.match(dashboard, /添加项目/, "dashboard must retain add-project entry");
assert.match(dashboard, /ZipImportPanel/, "dashboard must retain zip import");
assert.match(access, /绑定本地项目/, "local project binding must remain available");
assert.match(dashboard, /PendingChangesPanel/, "dashboard should render the V3.3 pending-change panel");
assert.match(pendingPanel, /待整理变更/, "pending panel should name the new scan boundary");
assert.match(pendingPanel, /分析新变化/, "pending panel should expose the primary scan action");
assert.match(pendingPanel, /开发推进段/, "pending panel should explain segment grouping");
assert.match(pendingPanel, /<details/, "raw evidence must be collapsed by default");
assert.doesNotMatch(pendingPanel, /刷新今日开发|开发成果审查|项目资产入库台/, "old workflow language must leave the primary panel");

assert.match(shell, /沉淀确认/, "navigation should use sediment confirmation language");
assert.match(shell, /项目沉淀/, "navigation should use project sediment language");
assert.match(tasks, /title="沉淀确认"/, "tasks route should keep its URL but use the V3.3 title");
assert.match(reviewList, /NEW_SEDIMENT/, "review must support creating a sediment");
assert.match(reviewList, /MERGE_EXISTING/, "review must support merging a sediment");
assert.match(reviewList, /EVIDENCE_ONLY/, "review must support evidence-only confirmation");
assert.match(reviewList, /IGNORE/, "review must support ignoring a suggestion");
assert.match(reviewList, /<details[\s\S]*旧版候选/, "legacy AiSuggestion content should be collapsed");

assert.match(api, /export type ChangeBatch/, "API contract should expose change batches");
assert.match(api, /export type DevelopmentSegment/, "API contract should expose development segments");
assert.match(api, /export type ProjectSediment/, "API contract should expose confirmed sediments");
assert.match(api, /confirmProjectChange/, "API contract should expose four-action confirmation");

console.log("V3.3 pending workflow checks passed");
