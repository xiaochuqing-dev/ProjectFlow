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

assert.match(appShell, /开发成果审查/, "navigation should name review as developer-result review instead of generic task management");
assert.match(appShell, /项目理解/, "navigation should expose project understanding as the project analysis entry");
assert.doesNotMatch(appShell, /变更审查|项目画像/, "main navigation should avoid legacy internal-facing labels");

assert.match(tasks, /开发成果审查/, "review page should present itself as an asset intake desk");
assert.match(tasks, /确认哪些开发成果可以进入项目资产/, "review page should explain confirmation as asset intake");
assert.doesNotMatch(tasks, /任务管理/, "review page should not imply generic task management");

assert.match(dashboard, /刷新今日开发/, "dashboard should use the user-facing daily-development refresh wording");
assert.match(dashboard, /OutputOptionsCard/, "dashboard should surface output value before the final output page");
assert.match(outputOptionsCard, /当前可生成/, "dashboard output card should surface output value before the final output page");
assert.match(outputOptionsCard, /README 草稿/, "dashboard should show README as a reusable output option");
assert.match(outputOptionsCard, /简历描述/, "dashboard should show resume bullets as a reusable output option");

assert.match(projectAccessCard, /绑定本地项目/, "local project access should use binding language instead of saving a path as the main action");
assert.match(projectAccessCard, /Agent 高级设置/, "agent protocol actions should be grouped under advanced settings");
assert.match(projectAccessCard, /<details/, "advanced agent actions should be collapsed by default");

assert.match(intelligence, /项目理解/, "project intelligence page should distinguish project understanding");
assert.match(intelligence, /项目资产/, "project intelligence page should emphasize confirmed assets");
assert.match(intelligence, /为什么可信/, "asset cards should provide a natural trust explanation entry");
assert.doesNotMatch(intelligence, /字段来源链/, "project intelligence first layer should not expose fact-source-chain jargon");
assert.doesNotMatch(intelligence, /长期档案/, "project intelligence first layer should avoid long-term archive jargon");

assert.match(outputs, /项目资产/, "output page should refer to confirmed project assets as source material");
assert.match(outputs, /今日开发/, "output page should connect outputs back to daily development evidence");

console.log("asset workbench language checks passed");
