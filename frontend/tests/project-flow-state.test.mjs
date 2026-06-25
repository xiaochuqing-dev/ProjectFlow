import { readFileSync } from "node:fs";
import { join } from "node:path";
import assert from "node:assert/strict";

const root = process.cwd();
const source = readFileSync(join(root, "src/lib/project-flow-state.ts"), "utf8");

assert.match(source, /export function resolveProjectFlowState/, "state resolver should be exported");
assert.match(source, /NO_PROJECT/, "state resolver should include no-project state");
assert.match(source, /NO_LOCAL_PATH/, "state resolver should include local path state");
assert.match(source, /HAS_WORK_SESSIONS/, "state resolver should include work-session state");
assert.match(source, /HAS_EVIDENCE_BUNDLES/, "state resolver should include evidence bundle state");
assert.match(source, /HAS_PENDING_CHANGES/, "state resolver should include pending change state");
assert.match(source, /READY_TO_OUTPUT/, "state resolver should include output-ready state");

assert.match(source, /导入项目/, "flow copy should guide import without docs");
assert.match(source, /绑定路径/, "flow copy should guide path binding without docs");
assert.match(source, /整理原始依据/, "flow copy should guide evidence preparation without exposing bundles first");
assert.match(source, /开发成果审查/, "flow copy should guide review");
assert.match(source, /生成输出/, "flow copy should guide output generation");

console.log("project-flow-state static checks passed");
