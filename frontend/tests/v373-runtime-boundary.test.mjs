import assert from "node:assert/strict";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";

const root = process.cwd();
const sourceRoot = join(root, "src");
const forbidden = [
  "hallucinationRate",
  "analysisAccuracy",
  "modelScore",
  "criticalEvidenceRecall",
  "toolSelectionPrecision",
  "secondStageGain",
];

function files(directory) {
  return readdirSync(directory).flatMap((name) => {
    const path = join(directory, name);
    return statSync(path).isDirectory() ? files(path) : [path];
  });
}

for (const path of files(sourceRoot)) {
  const content = readFileSync(path, "utf8");
  for (const field of forbidden) {
    assert.ok(
      !content.includes(field),
      `${field} is an internal eval metric and must not enter product source: ${relative(root, path)}`,
    );
  }
}

const api = readFileSync(join(root, "src/lib/api.ts"), "utf8");
assert.match(api, /secondStageTriggered/, "product may expose the auditable second-stage decision");
assert.match(api, /FAILED_DEGRADED/, "product should expose degradation state instead of an eval score");
assert.match(api, /analysisDeadlineMode/, "future UI must receive AUTO, FINITE or UNLIMITED");
assert.match(api, /qualityMode/, "quality policy must be explicit and independent");
assert.match(api, /eligibleCapabilities/, "the model-visible capability boundary must be auditable");
assert.match(api, /eligibleViews/, "the model-visible view boundary must be auditable");
assert.match(api, /toolRequests/, "information-gap-driven tool requests must be exposed");

console.log("V3.7.3 runtime and internal-eval/product boundary checks passed");
