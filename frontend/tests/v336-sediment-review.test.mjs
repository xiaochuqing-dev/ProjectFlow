import { readFileSync } from "node:fs";
import { join } from "node:path";
import assert from "node:assert/strict";

const root = process.cwd();
const center = readFileSync(join(root, "src/app/sediment-review/page.tsx"), "utf8");
const detail = readFileSync(join(root, "src/app/sediment-review/[batchId]/page.tsx"), "utf8");
const dashboard = readFileSync(join(root, "src/components/dashboard/PendingChangesPanel.tsx"), "utf8");
const capabilities = readFileSync(join(root, "src/app/project-intelligence/capabilities/page.tsx"), "utf8");
const memory = readFileSync(join(root, "src/app/project-intelligence/page.tsx"), "utf8");
const api = readFileSync(join(root, "src/lib/api.ts"), "utf8");

assert.match(center, /title="项目记录"/);
assert.match(center, /项目事实/);
assert.match(center, /需要关注/);
assert.match(center, /查看批次记录/);
assert.match(center, /groupProjectRecordBatches/);
assert.doesNotMatch(center, /继续处理.*条/);
assert.match(detail, /一次查看本批次全部项目事实/);
assert.match(detail, /getProjectFact/);
assert.match(detail, /onToggle/);
assert.doesNotMatch(detail, /confirmProjectChange|NEW_SEDIMENT|MERGE_EXISTING|EVIDENCE_ONLY/);
assert.match(dashboard, /最新分析批次/);
assert.match(dashboard, /自动记录.*项目事实/);
assert.match(capabilities, /自动维护长期能力/);
assert.doesNotMatch(capabilities, /startCapabilityCardAnalysisJob|updateCapabilityCard/);
assert.match(memory, /项目事实概览/);
assert.match(memory, /旧版已确认沉淀/);
assert.match(api, /export type ProjectRecordBatch/);
assert.match(api, /export type ProjectFact/);

console.log("V3.4 project fact memory checks passed");
