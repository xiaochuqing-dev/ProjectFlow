import { readFileSync } from "node:fs";
import { join } from "node:path";
import assert from "node:assert/strict";

const root = process.cwd();
const center = readFileSync(join(root, "src/app/sediment-review/page.tsx"), "utf8");
const detail = readFileSync(join(root, "src/app/sediment-review/[batchId]/page.tsx"), "utf8");
const dashboard = readFileSync(join(root, "src/components/dashboard/PendingChangesPanel.tsx"), "utf8");
const capabilities = readFileSync(join(root, "src/app/project-intelligence/capabilities/page.tsx"), "utf8");
const archive = readFileSync(join(root, "src/app/project-intelligence/page.tsx"), "utf8");
const api = readFileSync(join(root, "src/lib/api.ts"), "utf8");

assert.match(center, /按时间和扫描批次逐步处理/);
assert.match(center, /正式建议/);
assert.match(center, /本地草稿/);
assert.match(detail, /逐条决策并实时更新批次进度/);
assert.match(detail, /上一条/);
assert.match(detail, /下一条/);
assert.match(detail, /稍后处理并返回/);
assert.match(detail, /本地事实草稿/);
assert.match(detail, /高可信推荐/);
assert.match(dashboard, /最新分析批次/);
assert.match(dashboard, /<details className="border-b border-line">/);
assert.match(capabilities, /待能力分析/);
assert.match(capabilities, /getCapabilityAnalysisOverview/);
assert.match(archive, /sedimentTimeGroups/);
assert.match(api, /export type SedimentReviewBatch/);
assert.match(api, /capabilityStatus/);

console.log("V3.3.6 sediment review checks passed");
