import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import assert from "node:assert/strict";

const root = process.cwd();

function read(path) {
  return readFileSync(join(root, path), "utf8");
}

const expectedFiles = [
  "src/hooks/useProjectSelection.ts",
  "src/hooks/useAutoDismissNotice.ts",
  "src/components/sources/SourceCardList.tsx",
  "src/lib/text-summary.ts",
];

for (const file of expectedFiles) {
  assert.ok(existsSync(join(root, file)), `${file} should exist for P2 shared UI convergence`);
}

const pages = {
  tasks: read("src/app/tasks/page.tsx"),
  devLogs: read("src/app/dev-logs/page.tsx"),
  outputs: read("src/app/ai-review/page.tsx"),
  intelligence: read("src/app/project-intelligence/page.tsx"),
  imports: read("src/app/imports/page.tsx"),
  settings: read("src/app/settings/page.tsx"),
};

for (const [name, source] of Object.entries(pages)) {
  assert.match(source, /useProjectSelection/, `${name} should use shared project selection`);
  assert.doesNotMatch(source, /listProjects/, `${name} should not duplicate project list loading`);
  assert.doesNotMatch(source, /rememberSelectedProjectId/, `${name} should not duplicate selection persistence`);
  assert.doesNotMatch(source, /window\.setTimeout\(\(\) => \{\s*setNotice\(""\);\s*setError\(""\);/s, `${name} should not duplicate toast timeout cleanup`);
}

for (const [name, source] of Object.entries({
  tasks: pages.tasks,
  devLogs: pages.devLogs,
  outputs: pages.outputs,
  intelligence: pages.intelligence,
  settings: pages.settings,
})) {
  assert.match(source, /useAutoDismissNotice/, `${name} should use shared notice cleanup`);
  assert.match(source, /Toast/, `${name} should use shared toast`);
}

assert.match(pages.devLogs, /@\/components\/sources\/SourceCardList/, "dev logs should use shared source cards");
assert.match(pages.outputs, /@\/components\/sources\/SourceCardList/, "outputs should use shared source cards");
assert.doesNotMatch(pages.devLogs, /function SourceCardList/, "dev logs should not define SourceCardList locally");
assert.doesNotMatch(pages.outputs, /function SourceCardList/, "outputs should not define SourceCardList locally");
assert.doesNotMatch(pages.devLogs, /function firstUsefulLine/, "dev logs should use shared firstUsefulLine");
assert.doesNotMatch(pages.outputs, /function firstUsefulLine/, "outputs should use shared firstUsefulLine");
assert.doesNotMatch(pages.tasks, /function StatusPill/, "tasks should use shared Badge instead of local StatusPill");
assert.doesNotMatch(pages.devLogs, /function StatusPill/, "dev logs should use shared Badge instead of local StatusPill");

assert.match(pages.settings, /ProjectContextBar/, "settings should use the shared project context bar for model usage project selection");

const workSessionDetail = read("src/app/work-sessions/[sessionId]/page.tsx");
assert.match(workSessionDetail, /@\/components\/ui/, "work session detail should use shared UI primitives");
assert.doesNotMatch(workSessionDetail, /function Card\(/, "work session detail should not define a local Card primitive");
assert.doesNotMatch(workSessionDetail, /function Metric\(/, "work session detail should not define a local Metric primitive");

console.log("shared UI convergence checks passed");
