import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import assert from "node:assert/strict";

const root = process.cwd();

function read(path) {
  return readFileSync(join(root, path), "utf8");
}

function lineCount(path) {
  return read(path).split(/\r?\n/).length;
}

assert.ok(lineCount("src/app/dashboard/page.tsx") <= 800, "dashboard page should stay at or below 800 lines");
assert.ok(lineCount("src/app/tasks/page.tsx") <= 450, "tasks page should stay at or below 450 lines");
assert.ok(lineCount("src/app/project-intelligence/page.tsx") <= 500, "project-intelligence page should stay at or below 500 lines");
assert.ok(lineCount("src/app/project-intelligence/capabilities/page.tsx") <= 400, "capabilities page should stay at or below 400 lines");

const requiredFiles = [
  "src/components/dashboard/ProjectAccessCard.tsx",
  "src/components/dashboard/PendingChangesPanel.tsx",
  "src/components/dashboard/ActivityFeed.tsx",
  "src/components/dashboard/ArchitectureQuickEntry.tsx",
  "src/components/dashboard/FlowGuideDialog.tsx",
  "src/hooks/useDashboardWorkspace.ts",
  "src/components/tasks/ChangeReviewList.tsx",
  "src/components/tasks/ChangeReviewSidebar.tsx",
];

for (const file of requiredFiles) {
  assert.ok(existsSync(join(root, file)), `${file} should exist after P3 decomposition`);
}

const dashboard = read("src/app/dashboard/page.tsx");
assert.doesNotMatch(dashboard, /function PendingChangesPanel/, "dashboard page should not define the pending-change flow locally");
assert.doesNotMatch(dashboard, /function ActivityFeed/, "dashboard page should not define the activity feed locally");
assert.doesNotMatch(dashboard, /function ProjectAccessCard/, "dashboard page should not define project access locally");

const tasks = read("src/app/tasks/page.tsx");
assert.doesNotMatch(tasks, /Payload JSON/, "tasks list page should not expose raw payload editing in the main page");
assert.match(read("src/components/tasks/ChangeReviewSidebar.tsx"), /高级调试：Payload JSON/, "legacy payload editing should be folded into an advanced debug section");

console.log("page decomposition checks passed");
