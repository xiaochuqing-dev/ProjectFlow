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
  assert.match(api, /export function getProjectEvolutionBridges/);
  assert.match(api, /export type ProjectEvolutionBridge/);
  assert.match(api, /PROJECT_UNDERSTANDING_REFRESH/);
  assert.match(memoryPage, /当前项目理解/);
});

test("page exposes trust calibration instead of presenting all claims as facts", () => {
  assert.match(page, /已观察/);
  assert.match(page, /模型推断/);
  assert.match(page, /仍然未知/);
  assert.match(page, /证据覆盖/);
  assert.match(page, /没有 Git/);
  assert.match(page, /证据支持的项目演进/);
  assert.doesNotMatch(page, /自动确认为项目事实/);
  assert.doesNotMatch(page, /根据目录名推断历史/);
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

test("V3.7 response and page expose adaptive evidence views", () => {
  assert.match(api, /export type EvidenceSourceMap/);
  assert.match(api, /export type SemanticScout/);
  assert.match(api, /export type DynamicProjectProfile/);
  assert.match(api, /export type HistoricalCoverage/);
  assert.match(api, /toolsToInvoke: string\[\]/);
  assert.match(page, /Evidence Sources/);
  assert.match(page, /自适应分析计划/);
  assert.match(page, /自适应项目档案/);
  assert.match(page, /Historical Coverage/);
  assert.match(page, /当前目录没有可分析内容，不生成架构、能力或时间线/);
});

test("V3.7.1 exposes executed capability, packing, diversity and honest history diagnostics", () => {
  assert.match(api, /export type AnalysisExecution/);
  assert.match(api, /export type ContextPackingDiagnostics/);
  assert.match(api, /export type EvidenceDiversityMetrics/);
  assert.match(api, /diversityMetrics: EvidenceDiversityMetrics/);
  assert.match(api, /export type HistoricalCoverageBreakdown/);
  assert.match(api, /breakdown: HistoricalCoverageBreakdown/);
  assert.match(page, /执行与校验/);
  assert.match(page, /上下文打包/);
  assert.match(page, /来源类别覆盖/);
  assert.match(page, /历史维度置信度/);
});
