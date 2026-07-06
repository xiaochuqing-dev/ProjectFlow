import { readFileSync } from "node:fs";
import { join } from "node:path";
import assert from "node:assert/strict";

const root = process.cwd();
const read = (path) => readFileSync(join(root, path), "utf8");

const userFacingFiles = [
  "src/app/dashboard/page.tsx",
  "src/components/dashboard/DashboardStats.tsx",
  "src/components/dashboard/ActivityFeed.tsx",
  "src/components/dashboard/PendingChangesPanel.tsx",
  "src/app/tasks/page.tsx",
  "src/components/tasks/ChangeReviewList.tsx",
  "src/components/tasks/ChangeReviewSidebar.tsx",
  "src/components/tasks/change-review-utils.ts",
  "src/app/project-changes/[changeId]/page.tsx",
  "src/app/project-changes/[changeId]/evidence/page.tsx",
  "src/app/project-analysis-records/[recordId]/page.tsx",
  "src/app/project-intelligence/page.tsx",
  "src/app/project-intelligence/capabilities/page.tsx",
  "src/app/project-intelligence/fact-sources/page.tsx",
  "src/app/project-intelligence/changes/page.tsx",
  "src/app/project-intelligence/timeline/page.tsx",
  "src/app/project-intelligence/analysis-records/page.tsx",
  "src/app/projects/[projectId]/files/page.tsx",
  "src/app/dev-logs/page.tsx",
  "src/app/dev-logs/sources/page.tsx",
  "src/app/ai-review/page.tsx",
  "src/app/settings/page.tsx",
  "src/lib/daily-review-sources.ts",
  "src/lib/project-flow-state.ts",
  "src/lib/project-memory-display.ts",
];

for (const file of userFacingFiles) {
  const source = read(file);
  assert.doesNotMatch(source, />[^<]*字段来源链[^<]*</, `${file} should not render 字段来源链 as visible copy`);
  assert.doesNotMatch(source, />[^<]*项目画像[^<]*</, `${file} should not render 项目画像 as visible copy`);
  assert.doesNotMatch(source, />[^<]*Work Session[^<]*</, `${file} should not render Work Session as visible copy`);
  assert.doesNotMatch(source, />[^<]*任务证据[^<]*</, `${file} should not render 任务证据 as visible copy`);
  assert.doesNotMatch(source, />[^<]*时间线变化[^<]*</, `${file} should not render 时间线变化 as visible copy`);
  assert.doesNotMatch(source, />[^<]*能力清单[^<]*</, `${file} should not render 能力清单 as visible copy`);
  assert.doesNotMatch(source, /sourceId:\s*\$\{/, `${file} should not render raw sourceId by default`);
}

const projectIntelligence = read("src/app/project-intelligence/page.tsx");
assert.match(projectIntelligence, /SedimentOverview/, "project sediment should be the primary overview");
assert.match(projectIntelligence, /建议沉淀/, "project sediment should expose pending recommendations as a secondary entry");
assert.doesNotMatch(projectIntelligence, /label="可信依据"/, "可信依据 should not be a primary right-rail entry");
assert.doesNotMatch(projectIntelligence, /label="时间线变化"/, "时间线变化 should be merged into project timeline");

const capabilities = read("src/app/project-intelligence/capabilities/page.tsx");
assert.match(capabilities, /能力与成果/, "capabilities page should be renamed to 能力与成果");
for (const text of ["解决什么问题", "为什么重要", "来源证据", "可复用表达"]) {
  assert.match(capabilities, new RegExp(text), `capability asset cards should show ${text}`);
}

const flowState = read("src/lib/project-flow-state.ts");
assert.match(flowState, /hasConfirmedAssets/, "flow state should detect confirmed project assets separately from generated outputs");
assert.match(flowState, /生成成果输出/, "confirmed assets should guide users toward output generation");

const outputs = read("src/app/ai-review/page.tsx");
assert.match(outputs, /输出素材完整度/, "output page should show output material readiness");
assert.match(outputs, /已连接素材/, "output page should list connected source materials");
assert.match(outputs, /建议补充素材/, "output page should list missing material suggestions");
assert.match(outputs, /不是项目质量评分/, "output readiness should not be framed as project quality scoring");

console.log("product language convergence checks passed");
