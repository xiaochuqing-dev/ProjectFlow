import { readFileSync } from "node:fs";
import { join } from "node:path";
import assert from "node:assert/strict";

const root = process.cwd();
const intelligence = readFileSync(join(root, "src/app/project-intelligence/page.tsx"), "utf8");
const detail = readFileSync(join(root, "src/app/project-sediments/[sedimentId]/page.tsx"), "utf8");
const settings = readFileSync(join(root, "src/app/settings/page.tsx"), "utf8");
const api = readFileSync(join(root, "src/lib/api.ts"), "utf8");

assert.match(intelligence, /title="项目沉淀"/, "project intelligence route should adopt sediment title");
assert.match(intelligence, /timeGroup\.items\.map/, "overview should render confirmed sediment objects inside status and time groups");
assert.match(intelligence, /sediments\.length === 0/, "overview should teach the empty state");
assert.doesNotMatch(intelligence, /\{fieldConfig\.map\([\s\S]*暂无已确认内容/, "default overview must not render every empty subjective field");
assert.match(intelligence, /兼容档案字段/, "legacy memory fields should remain available but secondary");

assert.match(detail, /它解决的问题/, "detail first screen should explain the problem solved");
assert.match(detail, /来源概览/, "detail should summarize sources");
assert.match(detail, /可复用出口/, "detail should expose reuse destinations");
assert.match(detail, /<details/, "raw evidence should be collapsed");
assert.match(detail, /开发者备注/, "subjective notes must have an explicit developer-owned entry");
assert.match(detail, /updateProjectSedimentNotes/, "developer notes should persist through the dedicated endpoint");

assert.match(settings, /Agent 写回协议/, "settings should show Agent bridge health");
assert.match(settings, /GitHub CLI/, "settings should show optional GitHub CLI state");
assert.match(settings, /本地 Git 分析仍可使用/, "GitHub absence must be explained as non-blocking");
assert.match(api, /getAgentBridgeHealth/, "API should expose Agent bridge health");
assert.match(api, /getProjectGitHubStatus/, "API should expose optional GitHub state");

console.log("V3.3 sediment detail checks passed");
