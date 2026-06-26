import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import assert from "node:assert/strict";

const root = process.cwd();

function read(path) {
  return readFileSync(join(root, path), "utf8");
}

const page = read("src/app/project-intelligence/capabilities/page.tsx");

// 小卡列表：默认不铺开所有字段，存在"查看详情"入口
assert.match(page, /查看详情/, "capability page should render compact cards with a detail entry");

// 序号不能是卡片主标题（不能出现 "能力 {index + 1}" 这类序号角标作主视觉）
assert.doesNotMatch(page, /能力 \{index \+ 1\}/, "capability card title must not be a numeric placeholder like 能力 N");

// 能力名映射文件应存在，能力名以"能力"结尾而非整句事实
assert.ok(existsSync(join(root, "src/lib/capability-names.ts")), "capability-names.ts should exist");
assert.ok(existsSync(join(root, "src/lib/capability-assets.ts")), "capability-assets.ts should exist");

const names = read("src/lib/capability-names.ts");
assert.match(names, /能力["')\]]/, "capability names should end with 能力 (e.g. 项目结构识别能力)");
// 兜底应是保守的分桶名"项目资产沉淀能力"，不是"取事实首段 + 能力"
assert.match(names, /项目资产沉淀能力/, "fallback name should be conservative bucket name 项目资产沉淀能力");

// 存在可复用场景标签
assert.match(page, /README/, "capability card should show reusable scene labels (README)");
assert.match(page, /简历/, "capability card should show reusable scene labels (简历)");

// 存在"生成能力解读"入口（P4 才实现按钮逻辑，但入口文案先占位）
assert.match(page, /生成能力解读/, "capability page should have a generate-interpretation entry");

console.log("capability asset library checks passed");
