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
assert.ok(existsSync(join(root, "src/app/project-changes/[changeId]/evidence/page.tsx")), "structured change evidence page should exist");
assert.ok(existsSync(join(root, "src/app/dev-logs/sources/page.tsx")), "daily review source detail page should exist");
assert.ok(existsSync(join(root, "src/app/dev-logs/sources/[sourceId]/page.tsx")), "daily review source cards should have a drill-in detail page");
assert.ok(existsSync(join(root, "src/app/project-intelligence/capabilities/page.tsx")), "completed capabilities should have a focused page instead of expanding in the profile");
assert.ok(existsSync(join(root, "src/app/project-intelligence/timeline/page.tsx")), "growth timeline page should exist");
assert.ok(existsSync(join(root, "src/app/project-intelligence/fact-sources/page.tsx")), "fact source chain page should exist");
assert.ok(existsSync(join(root, "src/app/project-intelligence/changes/page.tsx")), "archive changes page should exist");

assert.match(changeReviewList, /href=\{`\/project-changes\/\$\{change\.id\}/, "change review list should link each structured change to its detail page");
assert.match(changeReviewList, /完整审查/, "change review list should expose a clear detail review action");

assert.match(devLogs, /\/dev-logs\/sources\?projectId=/, "daily review source summary should link to the source detail page");
assert.equal(devLogs.match(/\/dev-logs\/sources\?projectId=/g)?.length ?? 0, 1, "daily review sources should expose one merged source entry instead of four duplicate buttons");
assert.doesNotMatch(devLogs, />有<\/span>|>无<\/span>/, "daily source cards should not duplicate counts with yes/no labels");

assert.match(intelligence, /\/project-intelligence\/timeline\?projectId=/, "project profile should link to growth timeline page");
assert.match(intelligence, /\/project-intelligence\/capabilities\?projectId=/, "project profile should link completed capabilities to a focused page");
assert.match(intelligence, /CompletedCapabilitiesCard/, "completed capabilities should render as a compact entry card on the profile");
assert.doesNotMatch(intelligence, /label="可信依据"/, "可信依据 should not be a primary right-rail entry");
assert.doesNotMatch(intelligence, /label="时间线变化"/, "时间线变化 should be merged into project timeline");
assert.match(intelligence, /ArchiveEntryCard/, "project profile right rail should use navigation entries instead of long embedded lists");
assert.match(intelligence, /项目资产工作台/, "project profile should present archive fields as an asset workbench");
assert.match(intelligence, /手动修正字段/, "manual archive editing should be a secondary correction action");
assert.match(intelligence, /archiveEntryToneStyles/, "project profile entry cards should use distinct color markers");

assert.match(resourceTimeline, /FilterSelect/, "long-term resource pages should share month/type/status filters");
assert.match(resourceTimeline, /groupByDay/, "long-term resource pages should group records by date");
assert.match(resourceTimeline, /搜索标题、来源或摘要/, "long-term resource pages should support keyword search");
assert.doesNotMatch(resourceTimeline, /<details/, "long-term resource lists should not inline-expand long record details");
assert.doesNotMatch(resourceTimeline, /item\.detail/, "long-term resource lists should not render full detail payloads");
assert.match(resourceTimeline, /查看详情/, "long-term resource cards should route to a detail page when one exists");
assert.match(resourceTimeline, /compactPath/, "long-term resource cards should compress long paths instead of showing full path strings");

assert.doesNotMatch(intelligence, /最近：\$\{analysisRecords\[0\]\.summary\}/, "project profile entry cards should not embed the latest analysis summary");
assert.match(intelligence, /latestAt=/, "project profile entry cards should show latest time as navigation metadata");
assert.match(intelligence, /latestLabel=/, "project profile entry cards should show compact latest type metadata");

for (const route of [
  "src/app/project-intelligence/timeline/page.tsx",
  "src/app/project-intelligence/fact-sources/page.tsx",
  "src/app/project-intelligence/changes/page.tsx",
  "src/app/project-intelligence/analysis-records/page.tsx",
  "src/app/dev-logs/sources/page.tsx",
]) {
  assert.match(read(route), /ResourceTimeline/, `${route} should use the shared date-grouped resource timeline`);
}

const dailySources = read("src/app/dev-logs/sources/page.tsx");
assert.match(dailySources, /href:/, "daily review source cards should provide drill-in hrefs");
assert.match(dailySources, /\/dev-logs\/sources\/\$\{item\.id\}/, "daily review source cards should link to source detail pages");

assert.match(outputs, /\/dev-logs\/sources\?projectId=/, "output source metrics should link to concrete daily review sources");
assert.match(outputs, /\/project-intelligence\/timeline\?projectId=/, "output source metrics should link to growth records");

console.log("long-term record navigation checks passed");
