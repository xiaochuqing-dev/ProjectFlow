import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import test from "node:test";
import ts from "typescript";

const source = readFileSync(join(process.cwd(), "src/lib/project-fact-memory.ts"), "utf8");
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText;
const runtimeModule = { exports: {} };
new Function("exports", "module", compiled)(runtimeModule.exports, runtimeModule);
const { formatFactOccurredRange, groupProjectRecordBatches } = runtimeModule.exports;

function batch(batchId, occurredTo) {
  return { batchId, factOccurredFrom: occurredTo, factOccurredTo: occurredTo };
}

test("project records group the current week, current month, historical months and older facts", () => {
  const groups = groupProjectRecordBatches([
    batch("week", "2026-07-14T08:00:00+08:00"),
    batch("month", "2026-07-02T08:00:00+08:00"),
    batch("june", "2026-06-20T08:00:00+08:00"),
    batch("old", "2024-01-01T08:00:00+08:00"),
  ], new Date("2026-07-15T12:00:00+08:00"));

  assert.deepEqual(groups.map((group) => group.label), ["本周", "本月", "2026 年 6 月", "更早"]);
  assert.equal(groups[0].items[0].batchId, "week");
});

test("fact occurrence range uses historical occurrence time instead of ingestion time", () => {
  const label = formatFactOccurredRange("2025-01-02T08:00:00+08:00", "2025-01-03T08:00:00+08:00");
  assert.match(label, /2025/);
  assert.match(label, /至/);
});
