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
const {
  projectHistoryEntityType,
  projectHistoryHref,
  projectHistoryPresentationLabel,
  projectHistoryRewriteStateLabel,
  projectHistoryRoleLabel,
  projectHistorySourceTypeLabel,
  projectHistoryStatusLabel,
  projectHistoryTransitionLabel,
} = runtimeModule.exports;

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

test("history presentation enums have natural first-layer labels", () => {
  assert.equal(projectHistoryRoleLabel("PRIMARY"), "主要变化");
  assert.equal(projectHistoryRoleLabel("SUPPORTING"), "支撑工作");
  assert.equal(projectHistoryPresentationLabel("USER_DECLARED_PRESENTATION"), "经过你的修改");
  assert.equal(projectHistoryPresentationLabel("AUTOMATIC"), "自动整理");
  assert.equal(projectHistoryStatusLabel("READY"), "可阅读");
  assert.equal(projectHistorySourceTypeLabel("PROJECT_FACT"), "项目事实");
  assert.equal(projectHistoryRewriteStateLabel("STALE"), "来源已变化");
});

test("frontend API exposes read-only current state, overview, chapter, story and thread readers", () => {
  for (const name of [
    "getProjectCurrentState",
    "getProjectHistoryOverview",
    "getProjectHistoryChapter",
    "getProjectHistoryStory",
    "getProjectHistoryThread",
    "getProjectHistoryEvidence",
  ]) {
    assert.match(apiSource, new RegExp(`export function ${name}`));
  }
  assert.doesNotMatch(pageSource, /history\/refresh|method:\s*["']POST["']/);
});

test("preview renders progressive overview, story states, evidence and evolution threads", () => {
  for (const label of ["最早可确认状态", "时间篇章", "原来状态", "本次变化", "当前结果", "查看来源事件、Commit 与 Evidence", "涉及文件", "查看 Evidence 详情", "所属演变链"]) {
    assert.match(pageSource, new RegExp(label));
  }
});

test("default history layer hides internal enums behind engineering details", () => {
  assert.doesNotMatch(pageSource, /展示角色 \{story\.role/);
  assert.doesNotMatch(pageSource, /当前展示权威/);
  assert.doesNotMatch(pageSource, /\{story\.authority\} · \{story\.summaryStatus\}/);
  assert.match(pageSource, /查看工程详情与审计信息/);
  assert.ok(pageSource.indexOf("Technical Atom") > pageSource.indexOf("查看工程详情与审计信息"));
});

test("preview only claims the correction controls it actually exposes", () => {
  for (const type of ["RENAME_STORY", "EDIT_SUMMARY", "HIDE_STORY", "PIN_STORY", "RESTORE_AUTOMATIC"]) {
    assert.match(pageSource, new RegExp(type));
  }
  assert.doesNotMatch(pageSource, /MERGE_STORIES|SPLIT_STORY|REATTACH_SUPPORTING/);
});
