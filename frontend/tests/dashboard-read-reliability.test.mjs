import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import test from "node:test";
import ts from "typescript";

const source = readFileSync(join(process.cwd(), "src/lib/dashboard-snapshot.ts"), "utf8");
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText;
const runtimeModule = { exports: {} };
new Function("exports", "module", compiled)(runtimeModule.exports, runtimeModule);
const {
  clearDashboardSnapshot,
  isDashboardSnapshotStale,
  mergeWorkSessionScanResult,
  patchDashboardSnapshot,
  readDashboardSnapshot,
} = runtimeModule.exports;

class MemoryStorage {
  #items = new Map();

  get length() { return this.#items.size; }
  clear() { this.#items.clear(); }
  getItem(key) { return this.#items.get(key) ?? null; }
  key(index) { return [...this.#items.keys()][index] ?? null; }
  removeItem(key) { this.#items.delete(key); }
  setItem(key, value) { this.#items.set(key, String(value)); }
}

function scan(projectId, { batch = null, segments = [], sessions = [], warnings = [] } = {}) {
  return {
    projectId,
    projectPath: `C:/${projectId}`,
    branchName: "master",
    scannedAt: "2026-07-13T12:00:00Z",
    sessions,
    warnings,
    batch,
    segments,
    firstScan: false,
  };
}

test.beforeEach(() => {
  global.window = { sessionStorage: new MemoryStorage() };
});

test.afterEach(() => {
  delete global.window;
});

test("weak work-session refresh preserves the persisted batch and segments", () => {
  const full = scan("project-a", {
    batch: { id: "batch-a", scanStartedAt: "2026-07-13T12:00:00Z" },
    segments: [{ id: "segment-a" }],
    warnings: ["persisted warning"],
  });
  const weak = scan("project-a", { sessions: [{ id: "session-a" }] });

  const merged = mergeWorkSessionScanResult(full, weak);
  assert.equal(merged.batch.id, "batch-a");
  assert.equal(merged.segments[0].id, "segment-a");
  assert.equal(merged.sessions[0].id, "session-a");
  assert.deepEqual(merged.warnings, ["persisted warning"]);
  assert.equal(mergeWorkSessionScanResult(full, weak, true).batch, null);
});

test("dashboard snapshots remain isolated by project", () => {
  patchDashboardSnapshot({
    selectedProjectId: "project-a",
    projects: [],
    workSessionScan: scan("project-a", { batch: { id: "batch-a", scanStartedAt: "2026-07-13T12:00:00Z" } }),
  });
  patchDashboardSnapshot({
    selectedProjectId: "project-b",
    projects: [],
    workSessionScan: scan("project-b", { batch: { id: "batch-b", scanStartedAt: "2026-07-13T12:01:00Z" } }),
  });

  assert.equal(readDashboardSnapshot("project-a").latestBatchId, "batch-a");
  assert.equal(readDashboardSnapshot("project-b").latestBatchId, "batch-b");
  patchDashboardSnapshot({ selectedProjectId: "project-b", workSessionScan: scan("project-a") });
  assert.equal(readDashboardSnapshot("project-b").workSessionScan.batch.id, "batch-b");
  clearDashboardSnapshot("project-a");
  assert.equal(readDashboardSnapshot("project-a"), null);
  assert.equal(readDashboardSnapshot("project-b").latestBatchId, "batch-b");
});

test("legacy single-key snapshot migrates without becoming the source of truth", () => {
  window.sessionStorage.setItem("projectflow:dashboardSnapshot", JSON.stringify({
    capturedAt: "2026-07-13T12:00:00Z",
    selectedProjectId: "legacy-project",
    projects: [],
    workSessionScan: scan("legacy-project", { batch: { id: "legacy-batch", scanStartedAt: "2026-07-13T12:00:00Z" } }),
  }));

  const migrated = readDashboardSnapshot("legacy-project");
  assert.equal(migrated.schemaVersion, 2);
  assert.equal(migrated.latestBatchId, "legacy-batch");
  assert.equal(window.sessionStorage.getItem("projectflow:dashboardSnapshot"), null);
  assert.ok(window.sessionStorage.getItem("projectflow:dashboardSnapshot:legacy-project"));
});

test("snapshot freshness is diagnostic and does not block cached rendering", () => {
  assert.equal(isDashboardSnapshotStale({ capturedAt: new Date().toISOString() }), false);
  assert.equal(isDashboardSnapshotStale({ capturedAt: "2020-01-01T00:00:00Z" }), true);
});
