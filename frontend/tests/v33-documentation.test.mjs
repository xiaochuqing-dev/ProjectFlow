import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

const root = join(process.cwd(), "..");
const read = (path) => readFileSync(join(root, path), "utf8");

assert.ok(existsSync(join(root, "Start-ProjectFlow.bat")), "root Start-ProjectFlow.bat should provide the portable Windows entry");
assert.match(read("Start-ProjectFlow.bat"), /start-projectflow-embedded\.bat/i, "portable launcher should delegate to the maintained embedded launcher");
assert.doesNotMatch(read("Start-ProjectFlow.bat"), /[A-Z]:\\Users\\/i, "portable launcher must not contain a personal absolute path");
assert.match(read("start.bat"), /Start-ProjectFlow\.bat/i, "legacy start.bat should delegate to the portable launcher");

const embeddedLauncher = read("start-projectflow-embedded.ps1");
assert.match(embeddedLauncher, /package-lock\.json/, "embedded launcher should fingerprint frontend dependencies");
assert.match(embeddedLauncher, /Arguments @\("ci"\)/, "embedded launcher should install missing or changed frontend dependencies");
assert.match(embeddedLauncher, /Arguments @\("run", "build"\)/, "embedded launcher should rebuild the production frontend");
assert.match(embeddedLauncher, /last-embedded-build\.json/, "embedded launcher should record runtime build evidence");

const readme = read("README.md");
for (const term of ["ProjectFlow V3.4.3", "Project Fact", "Change Batch", "Development Segment", "Project Records", "Project Memory", "Project Capability", "Capability Evolution", "Project Memory Gateway", "Hermes MCP", "GitHub CLI"]) {
  assert.match(readme, new RegExp(term), `README should explain ${term}`);
}
assert.match(readme, /本地 Git.*主数据源/s, "README should retain local Git as the primary source");
assert.match(readme, /Agent result files are an enhancement/, "README should explain agent results as enrichment");
assert.match(readme, /ProjectFactCursor.*after fact ingestion succeeds/s, "README should explain the automatic fact cursor boundary");

const agents = read("AGENTS.md");
assert.match(agents, /PROJECTFLOW V3\.4\.2 CONTEXT START/, "AGENTS should contain the bounded V3.4.2 context entry");
assert.match(agents, /\.projectflow\/AGENT_PROTOCOL\.md/, "AGENTS should route agents to the detailed protocol");

const context = read("PROJECT_CONTEXT.md");
assert.match(context, /ProjectFlow V3\.4\.3/, "compact project context should identify the current release");
assert.match(context, /分析新变化.*DevelopmentSegment.*ProjectFact.*项目记忆/s, "compact project context should record the automatic fact workflow");

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
  "docs/project-capability-map.md",
  "docs/project-memory-gateway.md",
  "docs/hermes-mcp-integration.md",
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

console.log("V3.4 documentation checks passed");
