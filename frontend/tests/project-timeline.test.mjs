import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import test from "node:test";
import ts from "typescript";

const helperSource = readFileSync(join(process.cwd(), "src/lib/project-timeline.ts"), "utf8");
const compiled = ts.transpileModule(helperSource, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText;
const runtimeModule = { exports: {} };
new Function("exports", "module", compiled)(runtimeModule.exports, runtimeModule);
const {
  timelineGranularityLabels,
  timelineHistoryLabel,
  timelinePeriodLabel,
  timelineRangeLabel,
  timelineStatusLabels,
} = runtimeModule.exports;

const apiSource = readFileSync(join(process.cwd(), "src/lib/api.ts"), "utf8");
const pageSource = readFileSync(join(process.cwd(), "src/app/timeline/page.tsx"), "utf8");
const shellSource = readFileSync(join(process.cwd(), "src/components/AppShell.tsx"), "utf8");

test("timeline API DTO contract exposes overview, period, theme and lifecycle models", () => {
  for (const name of ["TimelineOverview", "TimelinePeriodPage", "TimelinePeriodDetail", "TimelineThemeFacts", "TimelineLifecycle"]) {
    assert.match(apiSource, new RegExp(`export type ${name}`));
  }
});

test("timeline granularity labels cover day, ISO week, month and lifecycle", () => {
  assert.deepEqual(timelineGranularityLabels, { DAY: "按日", WEEK: "按周", MONTH: "按月", LIFECYCLE: "全部" });
});

test("month period key renders as a Chinese month", () => {
  assert.equal(timelinePeriodLabel("MONTH", "2026-07"), "2026 年 7 月");
});

test("day period key renders as a Chinese day", () => {
  assert.equal(timelinePeriodLabel("DAY", "2026-07-15"), "2026 年 7 月 15 日");
});

test("ISO week label preserves the backend period key", () => {
  assert.equal(timelinePeriodLabel("WEEK", "2026-W29"), "ISO 周 2026-W29");
});

test("week range formatting uses API start and end instead of deriving ISO boundaries", () => {
  const label = timelineRangeLabel("2026-07-13T00:00:00Z", "2026-07-20T00:00:00Z", "UTC");
  assert.match(label, /2026\/07\/13/);
  assert.match(label, /2026\/07\/19/);
});

test("timeline summary labels distinguish ready, stale work and model waiting", () => {
  assert.equal(timelineStatusLabels.READY, "自动摘要已更新");
  assert.equal(timelineStatusLabels.GENERATING, "自动摘要正在更新");
  assert.equal(timelineStatusLabels.WAITING_FOR_MODEL, "等待模型配置");
});

test("completed history has an explicit coverage label", () => {
  assert.equal(timelineHistoryLabel("COMPLETED", 630, 630), "Git 历史覆盖已完成");
});

test("partial history includes exact covered and total commit counts", () => {
  assert.match(timelineHistoryLabel("RUNNING", 412, 630), /412 \/ 630 commits/);
});

test("automatic timeline page has no daily-log save action", () => {
  assert.doesNotMatch(pageSource, /保存为当天记录|保存项目历程|确认摘要/);
});

test("automatic timeline page has no future-planning primary UI", () => {
  assert.doesNotMatch(pageSource, /下一步/);
  assert.match(pageSource, /主要演进主题/);
});

test("AppShell main navigation points to 项目历程", () => {
  assert.match(shellSource, /label: "项目历程", href: "\/timeline"/);
  assert.doesNotMatch(shellSource, /label: "每日回顾", href: "\/dev-logs"/);
});

test("legacy dev logs page and API remain available", () => {
  assert.equal(existsSync(join(process.cwd(), "src/app/dev-logs/page.tsx")), true);
  assert.match(apiSource, /export function listDevLogs/);
  assert.match(pageSource, /旧每日回顾兼容入口/);
});

test("theme facts use the project-owned paginated route", () => {
  assert.match(apiSource, /timeline\/themes\/\$\{themeId\}\/facts\?\$\{params\.toString\(\)\}/);
});

test("project switching rejects late responses and secondary failures preserve primary data", () => {
  assert.match(pageSource, /requestGeneration\.current\+\+/);
  assert.match(pageSource, /projectId !== selection\.selectedProjectId/);
  assert.match(pageSource, /Promise\.allSettled/);
  assert.match(pageSource, /setSecondaryError/);
  assert.match(pageSource, /contentResult\.status === "fulfilled"/);
});
