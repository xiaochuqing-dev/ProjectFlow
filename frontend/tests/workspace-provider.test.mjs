import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import ts from "typescript";

const source = readFileSync("src/lib/provider-settings.ts", "utf8");
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText;
const runtime = { exports: {} };
new Function("exports", "module", compiled)(runtime.exports, runtime);
const { providerUpdatePayload, providerFormPayload } = runtime.exports;
const provider = {
  id: "provider", name: "Configured service", baseUrl: "https://example.test/v1", modelName: "configured-model",
  type: "CUSTOM", protocol: "OPENAI_RESPONSES", endpointOverride: "/custom/responses", authMode: "API_KEY_HEADER",
  authHeaderName: "X-API-Key", queryKeyName: "", requestTimeoutSeconds: 420,
  safeHeaderNames: ["X-Client-Name"], supportsTemperature: false, supportsJsonMode: true,
  supportsStructuredOutput: false, supportsReasoning: true, supportsReasoningControl: true,
  temperature: 0.8, maxTokens: 48000, purposeTags: ["existing-purpose"], defaultEnabled: true,
  apiKeyConfigured: true,
};
function form() {
  const value = new FormData();
  for (const [key, field] of Object.entries(providerUpdatePayload(provider))) {
    if (key === "defaultEnabled") { if (field) value.set(key, "on"); }
    else if (typeof field !== "object") value.set(key, String(field ?? ""));
  }
  return value;
}

test("default switching preserves all saved configuration without reconstructing secrets or header values", () => {
  const payload = providerUpdatePayload(provider);
  for (const key of ["protocol", "endpointOverride", "authMode", "authHeaderName", "queryKeyName", "requestTimeoutSeconds", "supportsTemperature", "supportsJsonMode", "supportsStructuredOutput", "supportsReasoning", "supportsReasoningControl", "purposeTags", "maxTokens", "temperature"]) {
    assert.deepEqual(payload[key], provider[key], key);
  }
  assert.equal(payload.apiKey, "");
  assert.equal(payload.clearApiKey, false);
  assert.equal(Object.hasOwn(payload, "safeHeaders"), false);
  assert.equal(Object.hasOwn(payload, "safeHeaderNames"), false);
});

test("a normal edit retains key, hidden header values and custom purpose tags", () => {
  const value = form(); value.set("name", "Renamed service"); value.set("supportsStructuredOutput", "auto");
  const payload = providerFormPayload(value, provider);
  assert.equal(payload.name, "Renamed service");
  assert.equal(payload.apiKey, ""); assert.equal(payload.clearApiKey, false);
  assert.equal(Object.hasOwn(payload, "safeHeaders"), false);
  assert.deepEqual(payload.purposeTags, ["existing-purpose"]);
  assert.equal(payload.supportsStructuredOutput, null);
  assert.equal(payload.supportsTemperature, false);
  assert.equal(payload.supportsReasoningControl, true);
});

test("credential removal requires explicit clearing and cannot silently override a new credential", () => {
  const value = form(); value.set("clearApiKey", "on");
  assert.equal(providerFormPayload(value, provider).clearApiKey, true);
  value.set("apiKey", "synthetic-test-input");
  assert.throws(() => providerFormPayload(value, provider), /不能同时选择/);
});

test("custom headers are replaced only on opt-in, and malformed input cannot become a save", () => {
  const value = form(); value.set("safeHeaders", '{"X-Client-Name":"GUI"}');
  assert.equal(Object.hasOwn(providerFormPayload(value, provider), "safeHeaders"), false);
  value.set("replaceSafeHeaders", "on");
  assert.deepEqual(providerFormPayload(value, provider).safeHeaders, { "X-Client-Name": "GUI" });
  value.set("safeHeaders", ""); assert.deepEqual(providerFormPayload(value, provider).safeHeaders, {});
  for (const invalid of ["[1]", "null", '{"name": 1}', "invalid"]) {
    value.set("safeHeaders", invalid); assert.throws(() => providerFormPayload(value, provider), /JSON/);
  }
});
