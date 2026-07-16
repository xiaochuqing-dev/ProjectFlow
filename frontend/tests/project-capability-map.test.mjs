import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import test from "node:test";
import ts from "typescript";

const root = process.cwd();
const read = (path) => readFileSync(join(root, path), "utf8");
const api = read("src/lib/api.ts");
const page = read("src/app/project-intelligence/capabilities/page.tsx");
const detail = read("src/app/project-intelligence/capabilities/[capabilityId]/page.tsx");
const helper = read("src/lib/project-capabilities.ts");
const compiled = ts.transpileModule(helper, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText;
const runtimeModule = { exports: {} };
new Function("exports", "module", compiled)(runtimeModule.exports, runtimeModule);

test("capability map API exposes overview, stable list, detail, evolution, facts, changes, attention and retry", () => {
  for (const name of ["CapabilityMapOverview", "ProjectCapability", "ProjectCapabilityDetail", "CapabilityEvolution", "CapabilityFact", "CapabilityAttentionPage"]) {
    assert.match(api, new RegExp(`export type ${name}`));
  }
  for (const name of ["getCapabilityMapOverview", "listProjectCapabilities", "getProjectCapability", "listCapabilityEvolutions", "listCapabilityFacts", "listCapabilityMapChanges", "listCapabilityMapAttention", "retryCapabilityMap"]) {
    assert.match(api, new RegExp(`export function ${name}`));
  }
});

test("maturity and map status labels distinguish deterministic user states", () => {
  assert.equal(runtimeModule.exports.capabilityMaturityLabel("FORMING"), "形成中");
  assert.equal(runtimeModule.exports.capabilityMaturityLabel("CONTINUOUSLY_ENHANCED"), "持续增强");
  assert.equal(runtimeModule.exports.capabilityMapStatusLabel("READY_STALE"), "已有结果，新事实待更新");
  assert.equal(runtimeModule.exports.capabilityMapStatusLabel("FAILED"), "自动更新失败");
});

test("main page is automatic capability map without legacy card actions", () => {
  assert.match(page, /能力地图/);
  assert.match(page, /基于项目从创建至今的全部事实/);
  assert.match(page, /最近能力变化/);
  assert.match(page, /能力层需要关注/);
  assert.match(page, /旧版能力卡片/);
  assert.doesNotMatch(page, /startCapabilityCardAnalysisJob|updateCapabilityCard|analyzeProjectCapabilities/);
  assert.doesNotMatch(page, /当前生效结果|重新分析替换候选/);
});

test("project switching ignores late responses and partial failures retain successful data", () => {
  assert.match(page, /requestId\.current/);
  assert.match(page, /Promise\.allSettled/);
  assert.match(page, /status === "fulfilled"/);
  assert.match(page, /loadedProjectId === selectedProjectId/);
});

test("detail traces evolution and facts to batch evidence and supports merged redirect", () => {
  assert.equal(existsSync(join(root, "src/app/project-intelligence/capabilities/[capabilityId]/page.tsx")), true);
  assert.match(detail, /能力演进/);
  assert.match(detail, /来源事实与证据/);
  assert.match(detail, /sediment-review\/\$\{fact\.batchId\}/);
  assert.match(detail, /mergedIntoCapabilityId/);
  assert.match(detail, /成熟度依据/);
  assert.doesNotMatch(detail, /Timeline Theme/);
});
