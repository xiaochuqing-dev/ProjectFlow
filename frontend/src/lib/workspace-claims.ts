/** The visible claim cannot exceed its source. This is a display gate, never a Fact writer. */
export type ClaimClassification = "VERIFIED" | "OBSERVED" | "DECLARED" | "INFERRED" | "UNKNOWN" | "CONFLICTED";
export type WorkspaceClaim = { text: string; kind: string; classification: ClaimClassification; sources: string[] };
const intentKinds = new Set(["PLAN", "MILESTONE", "RELEASE_DATE", "USER_GOAL", "TEAM_INTENT", "COMMERCIAL_PLAN"]);
const quantifiedKinds = new Set(["PROGRESS", "MATURITY", "EXPECTATION"]);
export const claimLabels: Record<ClaimClassification, string> = {
  VERIFIED: "已验证事实", OBSERVED: "已观察事实", DECLARED: "项目明确声明",
  INFERRED: "系统归纳", UNKNOWN: "暂无明确记录", CONFLICTED: "来源有冲突",
};
export function supportedClaim(claim: WorkspaceClaim): WorkspaceClaim | null {
  if (!claim.text.trim() || !claim.sources.length || claim.classification === "UNKNOWN") return null;
  if (intentKinds.has(claim.kind) && claim.classification !== "DECLARED") return null;
  if (quantifiedKinds.has(claim.kind) && !["DECLARED", "VERIFIED", "OBSERVED"].includes(claim.classification)) return null;
  // A source link for a general summary is not a source for intent or a percentage.
  if (claim.classification === "INFERRED" && /\d\s*[%％]|百分之|符合预期|团队执行力|商业化|预计.{0,8}发布|下一(?:步|阶段|里程碑).{0,12}(?:将|计划|发布)|处于.{0,12}(?:期|阶段)/.test(claim.text)) return null;
  return claim;
}
