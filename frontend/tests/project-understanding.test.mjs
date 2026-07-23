import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import test from "node:test";

const root = process.cwd();
const read = (path) => readFileSync(join(root, path), "utf8");
const api = read("src/lib/api.ts");
const pagePath = "src/app/project-intelligence/understanding/page.tsx";
const page = read(pagePath);
const memoryPage = read("src/app/project-intelligence/page.tsx");

test("current understanding has focused API and page entry", () => {
  assert.equal(existsSync(join(root, pagePath)), true);
  assert.match(api, /export type ProjectUnderstandingSnapshot/);
  assert.match(api, /export function refreshProjectUnderstanding/);
  assert.match(api, /export function getProjectUnderstanding/);
  assert.match(api, /PROJECT_UNDERSTANDING_REFRESH/);
  assert.match(memoryPage, /当前项目理解/);
});

test("page exposes trust calibration instead of presenting all claims as facts", () => {
  assert.match(page, /已观察/);
  assert.match(page, /模型推断/);
  assert.match(page, /仍然未知/);
  assert.match(page, /证据覆盖/);
  assert.match(page, /没有 Git/);
  assert.doesNotMatch(page, /自动确认为项目事实/);
});

test("refresh is a recoverable persisted job and GET remains separate", () => {
  assert.match(page, /getProjectAnalysisJob/);
  assert.match(page, /listProjectAnalysisJobs/);
  assert.match(page, /cancelProjectAnalysisJob/);
  assert.match(page, /retryProjectAnalysisJob/);
  assert.match(page, /刷新页面不会中断/);
  assert.match(page, /getProjectUnderstanding/);
  assert.match(page, /refreshProjectUnderstanding/);
});
