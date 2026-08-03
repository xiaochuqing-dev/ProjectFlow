import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import test from "node:test";
import ts from "typescript";

const root = process.cwd();
const helperSource = readFileSync(join(root, "src/lib/project-history.ts"), "utf8");
const compiled = ts.transpileModule(helperSource, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText;
const runtimeModule = { exports: {} };
new Function("exports", "module", compiled)(runtimeModule.exports, runtimeModule);
const { projectHistoryEntityType, projectHistoryHref, projectHistoryTransitionLabel } = runtimeModule.exports;

const apiSource = readFileSync(join(root, "src/lib/api.ts"), "utf8");
const pageSource = readFileSync(join(root, "src/app/projects/[projectId]/history/page.tsx"), "utf8");

test("stable project history links use frontend routes and encoded entity ids", () => {
  assert.equal(projectHistoryHref("project id"), "/projects/project%20id/history");
  assert.equal(
    projectHistoryHref("project id", "story", "story/id?1"),
    "/projects/project%20id/history?type=story&id=story%2Fid%3F1",
  );
});

test("unknown entity types safely degrade to the overview", () => {
  assert.equal(projectHistoryEntityType("story"), "story");
  assert.equal(projectHistoryEntityType("event"), "overview");
  assert.equal(projectHistoryEntityType(null), "overview");
});

test("evolution transitions have readable labels", () => {
  assert.equal(projectHistoryTransitionLabel("RESTORED"), "恢复");
  assert.equal(projectHistoryTransitionLabel("REAPPLIED"), "重新实现");
});

test("frontend API exposes read-only overview, chapter, story and thread readers", () => {
  for (const name of [
    "getProjectHistoryOverview",
    "getProjectHistoryChapter",
    "getProjectHistoryStory",
    "getProjectHistoryThread",
  ]) {
    assert.match(apiSource, new RegExp(`export function ${name}`));
  }
  assert.doesNotMatch(pageSource, /history\/refresh|method:\s*["']POST["']/);
});

test("preview renders progressive overview, story states, evidence and evolution threads", () => {
  for (const label of ["最早可确认状态", "时间篇章", "Before", "Change", "After", "来源事件下钻", "所属演变链"]) {
    assert.match(pageSource, new RegExp(label));
  }
  assert.match(pageSource, /不代表最终 GUI/);
});
