import { readFileSync } from "node:fs";
import { join } from "node:path";
import assert from "node:assert/strict";

const root = process.cwd();
const dashboard = readFileSync(join(root, "src/app/dashboard/page.tsx"), "utf8");
const dashboardWorkspace = readFileSync(join(root, "src/hooks/useDashboardWorkspace.ts"), "utf8");

assert.match(dashboard, /useDashboardWorkspace/, "dashboard should use the shared workspace request-order hook");
assert.match(dashboardWorkspace, /requestRef/, "dashboard workspace hook should track project-context request order");
assert.match(dashboard, /clearProjectContextViewState/, "dashboard should clear old project materials before loading a new project");
assert.match(dashboard, /isLatestContextRequest/, "dashboard should ignore stale project-context responses");
assert.match(dashboard, /setProjectPath\(""\)/, "dashboard should clear old local path while switching projects");
assert.match(dashboard, /setWorkSessionScan\(null\)/, "dashboard should clear old work sessions while switching projects");

console.log("project switch context checks passed");
