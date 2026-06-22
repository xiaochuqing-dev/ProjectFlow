import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import assert from "node:assert/strict";

const root = process.cwd();
const read = (path) => readFileSync(join(root, path), "utf8");

const tasks = read("src/app/tasks/page.tsx");
const changeReviewList = read("src/components/tasks/ChangeReviewList.tsx");
const devLogs = read("src/app/dev-logs/page.tsx");
const intelligence = read("src/app/project-intelligence/page.tsx");
const outputs = read("src/app/ai-review/page.tsx");
const api = read("src/lib/api.ts");
const resourceTimeline = read("src/components/ResourceTimeline.tsx");

assert.match(api, /getProjectChange/, "frontend API should load one structured change by id");

assert.ok(existsSync(join(root, "src/app/project-changes/[changeId]/page.tsx")), "structured change detail page should exist");
assert.ok(existsSync(join(root, "src/app/dev-logs/sources/page.tsx")), "daily review source detail page should exist");
assert.ok(existsSync(join(root, "src/app/project-intelligence/timeline/page.tsx")), "growth timeline page should exist");
assert.ok(existsSync(join(root, "src/app/project-intelligence/fact-sources/page.tsx")), "fact source chain page should exist");
assert.ok(existsSync(join(root, "src/app/project-intelligence/changes/page.tsx")), "archive changes page should exist");

assert.match(changeReviewList, /href=\{`\/project-changes\/\$\{change\.id\}/, "change review list should link each structured change to its detail page");
assert.match(changeReviewList, /完整审查/, "change review list should expose a clear detail review action");

assert.match(devLogs, /\/dev-logs\/sources\?projectId=/, "daily review source counts should link to the source detail page");
assert.doesNotMatch(devLogs, />有<\/span>|>无<\/span>/, "daily source cards should not duplicate counts with yes/no labels");

assert.match(intelligence, /\/project-intelligence\/timeline\?projectId=/, "project profile should link to growth timeline page");
assert.match(intelligence, /\/project-intelligence\/fact-sources\?projectId=/, "project profile should link to fact source page");
assert.match(intelligence, /\/project-intelligence\/changes\?projectId=/, "project profile should link to archive changes page");
assert.match(intelligence, /ArchiveEntryCard/, "project profile right rail should use navigation entries instead of long embedded lists");
assert.match(intelligence, /项目档案审查工作台/, "project profile should present archive fields as a review workbench");
assert.match(intelligence, /手动修正字段/, "manual archive editing should be a secondary correction action");

assert.match(resourceTimeline, /FilterSelect/, "long-term resource pages should share month/type/status filters");
assert.match(resourceTimeline, /groupByDay/, "long-term resource pages should group records by date");
assert.match(resourceTimeline, /搜索标题、来源或摘要/, "long-term resource pages should support keyword search");
assert.match(resourceTimeline, /详情/, "long-term resource cards should open detailed content from the list");

for (const route of [
  "src/app/project-intelligence/timeline/page.tsx",
  "src/app/project-intelligence/fact-sources/page.tsx",
  "src/app/project-intelligence/changes/page.tsx",
  "src/app/project-intelligence/analysis-records/page.tsx",
  "src/app/dev-logs/sources/page.tsx",
]) {
  assert.match(read(route), /ResourceTimeline/, `${route} should use the shared date-grouped resource timeline`);
}

assert.match(outputs, /\/dev-logs\/sources\?projectId=/, "output source metrics should link to concrete daily review sources");
assert.match(outputs, /\/project-intelligence\/timeline\?projectId=/, "output source metrics should link to growth records");

console.log("long-term record navigation checks passed");
