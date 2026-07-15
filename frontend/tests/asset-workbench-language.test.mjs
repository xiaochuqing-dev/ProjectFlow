import { readFileSync } from "node:fs";
import { join } from "node:path";
import assert from "node:assert/strict";

const root = process.cwd();
const read = (path) => readFileSync(join(root, path), "utf8");

const appShell = read("src/components/AppShell.tsx");
const dashboard = read("src/app/dashboard/page.tsx");
const tasks = read("src/app/tasks/page.tsx");
const projectAccessCard = read("src/components/dashboard/ProjectAccessCard.tsx");
const outputOptionsCard = read("src/components/dashboard/OutputOptionsCard.tsx");
const intelligence = read("src/app/project-intelligence/page.tsx");
const outputs = read("src/app/ai-review/page.tsx");

assert.match(appShell, /项目记录/, "navigation should expose automatically recorded project facts");
assert.match(appShell, /项目记忆/, "navigation should expose long-lived project memory");
assert.doesNotMatch(appShell, /沉淀处理/, "manual sediment processing must leave the primary navigation");
assert.doesNotMatch(appShell, /变更审查|项目画像/, "main navigation should avoid legacy internal-facing labels");

assert.match(tasks, /title="沉淀确认"/, "review page should present itself as sediment confirmation");
assert.match(tasks, /确认开发推进段应如何进入项目沉淀/, "review page should explain the sediment decision");
assert.doesNotMatch(tasks, /任务管理/, "review page should not imply generic task management");

assert.match(dashboard, /PendingChangesPanel/, "dashboard should expose the cursor-based pending-change workflow");
assert.match(dashboard, /OutputOptionsCard/, "dashboard should surface output value before the final output page");
assert.match(outputOptionsCard, /当前可生成/, "dashboard output card should surface output value before the final output page");
assert.match(outputOptionsCard, /README 草稿/, "dashboard should show README as a reusable output option");
assert.match(outputOptionsCard, /简历描述/, "dashboard should show resume bullets as a reusable output option");

assert.match(projectAccessCard, /绑定本地项目/, "local project access should use binding language instead of saving a path as the main action");
assert.match(projectAccessCard, /Agent 高级设置/, "agent protocol actions should be grouped under advanced settings");
assert.match(projectAccessCard, /<details/, "advanced agent actions should be collapsed by default");

assert.match(intelligence, /title="项目记忆"/, "project intelligence route should present project memory");
assert.match(intelligence, /项目事实概览/, "project memory should prioritize automatically recorded facts");
assert.doesNotMatch(intelligence, /字段来源链/, "project intelligence first layer should not expose fact-source-chain jargon");
assert.doesNotMatch(intelligence, /长期档案/, "project intelligence first layer should avoid long-term archive jargon");

assert.match(outputs, /项目沉淀/, "output page should refer to confirmed project sediments as source material");
assert.match(outputs, /开发推进段/, "output page should connect outputs back to grouped development evidence");

console.log("asset workbench language checks passed");
