import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import ts from "typescript";
const source = readFileSync("src/lib/workspace-claims.ts", "utf8");
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
const runtime = { exports: {} }; new Function("exports", "module", compiled)(runtime.exports, runtime);
const { supportedClaim, claimLabels } = runtime.exports;
test("no source means no plan or progress", () => {
  for (const kind of ["PLAN", "MILESTONE", "PROGRESS", "MATURITY", "USER_GOAL"]) assert.equal(supportedClaim({ text: "下一步", kind, classification: "DECLARED", sources: [] }), null);
});
test("roadmap declaration stays declared and inference cannot become intent", () => {
  const claim = { text: "支持离线导入", kind: "PLAN", classification: "DECLARED", sources: ["README.md:14"] };
  assert.equal(supportedClaim(claim).classification, "DECLARED");
  assert.equal(supportedClaim({ ...claim, classification: "INFERRED" }), null);
});
test("system summaries are labelled and do not imply verified facts", () => {
  assert.equal(claimLabels.INFERRED, "系统归纳");
  assert.equal(supportedClaim({ text: "分支主要改动 Provider 设置", kind: "PURPOSE", classification: "INFERRED", sources: ["diff:base..head"] }).classification, "INFERRED");
});
test("unsupported percentage and encouragement cannot hide in a general summary", () => {
  for (const text of ["整体完成78%", "项目进展符合预期", "团队执行力很强", "下一阶段将发布正式版", "处于内部验证期"]) assert.equal(supportedClaim({ text, kind: "SUMMARY", classification: "INFERRED", sources: ["unrelated-story"] }), null);
});
test("unknown is valid, conflicts remain conflicts", () => {
  assert.equal(supportedClaim({ text: "未知", kind: "SUMMARY", classification: "UNKNOWN", sources: ["event"] }), null);
  assert.equal(supportedClaim({ text: "来源相互矛盾", kind: "SUMMARY", classification: "CONFLICTED", sources: ["a", "b"] }).classification, "CONFLICTED");
});
