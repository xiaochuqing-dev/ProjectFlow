import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import assert from "node:assert/strict";

const root = process.cwd();

function read(path) {
  return readFileSync(join(root, path), "utf8");
}

const page = read("src/app/project-intelligence/capabilities/page.tsx");

assert.match(page, /能力地图/, "capability page should be the lifecycle capability map");
assert.match(page, /全部事实/, "capability map should explain full-history coverage");

// 序号不能是卡片主标题（不能出现 "能力 {index + 1}" 这类序号角标作主视觉）
assert.doesNotMatch(page, /能力 \{index \+ 1\}/, "capability card title must not be a numeric placeholder like 能力 N");

// 旧能力名与成果表达工具继续保留，避免破坏已有输出兼容。
assert.ok(existsSync(join(root, "src/lib/capability-names.ts")), "capability-names.ts should exist");
assert.ok(existsSync(join(root, "src/lib/capability-assets.ts")), "capability-assets.ts should exist");

const names = read("src/lib/capability-names.ts");
assert.match(names, /能力["')\]]/, "capability names should end with 能力 (e.g. 项目结构识别能力)");
// 兜底应是保守的分桶名"项目资产沉淀能力"，不是"取事实首段 + 能力"
assert.match(names, /项目资产沉淀能力/, "fallback name should be conservative bucket name 项目资产沉淀能力");

assert.match(page, /最近能力变化/, "capability map should show automatic evolution changes");
assert.match(page, /旧版能力卡片/, "legacy cards should remain in a compatibility section");
assert.doesNotMatch(page, /updateCapabilityCard|startCapabilityCardAnalysisJob/, "legacy card actions must not remain in the main capability flow");

console.log("capability asset library checks passed");
