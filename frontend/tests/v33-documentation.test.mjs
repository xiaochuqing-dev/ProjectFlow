import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

const root = join(process.cwd(), "..");
const read = (path) => readFileSync(join(root, path), "utf8");

assert.ok(existsSync(join(root, "start.bat")), "root start.bat should provide the simple Windows entry");
assert.match(read("start.bat"), /start-projectflow-embedded\.bat/i, "start.bat should delegate to the maintained embedded launcher");

const readme = read("README.md");
for (const term of ["ProjectFlow V3.3", "待整理变更", "开发推进段", "建议沉淀", "项目沉淀", "GitHub CLI"]) {
  assert.match(readme, new RegExp(term), `README should explain ${term}`);
}
assert.match(readme, /本地 Git.*主数据源/s, "README should retain local Git as the primary source");
assert.match(readme, /Agent result.*增强数据源/s, "README should explain agent results as enrichment");
assert.match(readme, /不再以.*今日开发.*主边界/s, "README should explain the cursor-based boundary");

const agents = read("AGENTS.md");
assert.match(agents, /PROJECTFLOW V3\.3\.\d+(?:\.\d+)? CONTEXT START/, "AGENTS should contain a bounded V3.3 context entry");
assert.match(agents, /\.projectflow\/AGENT_PROTOCOL\.md/, "AGENTS should route agents to the detailed protocol");

const context = read("PROJECT_CONTEXT.md");
assert.match(context, /Current V3\.3/, "compact project context should identify the current release");
assert.match(context, /待整理变更.*开发推进段.*建议沉淀.*项目沉淀/s, "compact project context should record the current workflow");

for (const path of [
  ".projectflow/AGENT_PROTOCOL.md",
  ".projectflow/agent-protocol.md",
  ".projectflow/context/project-profile.md",
  ".projectflow/context/requirements.md",
  ".projectflow/context/confirmed-decisions.md",
  ".projectflow/context/known-risks.md",
  ".projectflow/context/update-history.md",
  ".projectflow/templates/result.json",
  ".projectflow/agent-results/.gitkeep",
]) {
  assert.ok(existsSync(join(root, path)), `${path} should be versioned`);
}

const protocol = read(".projectflow/AGENT_PROTOCOL.md");
assert.match(protocol, /taskGoal/);
assert.match(protocol, /actualChanges/);
assert.match(protocol, /verification/);
assert.match(protocol, /unfinished/);
assert.match(protocol, /sedimentCandidates/);
assert.match(protocol, /not_run/);
assert.match(protocol, /仓库相对路径/);

console.log("V3.3 documentation checks passed");
