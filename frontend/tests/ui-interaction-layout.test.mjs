import { readFileSync } from "node:fs";
import { join } from "node:path";
import assert from "node:assert/strict";

const root = process.cwd();
const dashboard = readFileSync(join(root, "src/app/dashboard/page.tsx"), "utf8");
const devLogs = readFileSync(join(root, "src/app/dev-logs/page.tsx"), "utf8");
const outputs = readFileSync(join(root, "src/app/ai-review/page.tsx"), "utf8");

assert.match(dashboard, /showFlowGuide/, "dashboard should open the onboarding flow from a button instead of always showing the full guide");
assert.match(dashboard, /FlowGuideDialog/, "dashboard should render the flow guide in a standalone dialog");
assert.match(dashboard, /打开上手流程/, "dashboard should expose a compact top-bar guide button");
assert.match(dashboard, /FlowStepStrip/, "flow steps should be displayed as colored rounded strip cards inside the dialog");
assert.doesNotMatch(dashboard, /<ProjectFlowGuide state=\{projectFlowState\} \/>/, "dashboard should not permanently render the large guide block");

assert.match(dashboard, /InteractiveStat/, "dashboard metrics should be clickable interaction entries, not dead number cards");
assert.match(dashboard, /statsFocus/, "dashboard should use metric clicks to focus related content");
assert.match(dashboard, /ArchitectureQuickEntry/, "dashboard should put a compact architecture entry in the right rail");
assert.match(dashboard, /架构入口/, "right rail should expose the architecture summary entry");

assert.match(outputs, /SourceQuickFilter/, "output source pills should be clickable source filters");
assert.match(outputs, /activeSourcePanel/, "output page should switch visible source detail from top pills");
assert.match(outputs, /SourceCardList/, "output source details should use compact source cards");

assert.match(devLogs, /DailySourceGrid/, "daily review sources should use a compact source grid");
assert.match(devLogs, /SourceCardList/, "daily review source panels should render cards instead of long raw paragraphs");
assert.doesNotMatch(devLogs, /<p className="whitespace-pre-line"/, "daily review source items should not render long template paragraphs");

console.log("ui interaction layout checks passed");
