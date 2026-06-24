import type { ProjectChange } from "@/lib/api";

export const suggestionLabels = {
  UPDATE_PROJECT_MEMORY: "项目档案",
  CREATE_TASK: "任务",
  CREATE_DEV_LOG: "每日回顾",
  RECORD_TECHNICAL_DECISION: "技术决策",
  RECORD_RISK: "风险",
  RECORD_DEVELOPER_LEARNING: "经验",
  UPDATE_CURRENT_STAGE: "阶段",
  GENERATE_ASSET_SUMMARY: "成果素材",
};

export const changeKindLabels = {
  CAPABILITY: "能力",
  BUGFIX: "修复",
  REFACTOR: "重构",
  CONFIG: "配置",
  DOCS: "文档",
  TEST: "测试",
  RISK: "风险",
  DECISION: "决策",
  LEARNING: "经验",
  ASSET: "素材",
  UNKNOWN: "待判断",
};

export const impactLabels = {
  MAJOR: "主要",
  MINOR: "次要",
  MAINTENANCE: "维护",
  UNCERTAIN: "待判断",
};

export function changePreview(change: ProjectChange) {
  const files = parseAffectedFiles(change.affectedFiles).filter((file) => !isRuntimeArtifact(file));
  const fileLabel = files.length ? `关键文件 ${files.slice(0, 2).map(compactPath).join("、")}${files.length > 2 ? ` 等 ${files.length} 个` : ""}` : "";
  return [archiveTargetsLabel(change), fileLabel, change.riskNotes ? "含风险备注" : ""]
    .filter(Boolean)
    .join(" · ") || "结构化变更可审查";
}

export function changeDisplayTitle(change: Pick<ProjectChange, "changeKind" | "summary" | "title">) {
  const cleaned = change.title
    .replace(/^Evidence Bundle\s*候选变更[:：]\s*/i, "")
    .replace(/^Uncommitted working tree changes[:：]?\s*/i, "")
    .replace(/^(UNKNOWN|MEDIUM|EVIDENCE_BUNDLE|PENDING)[:：\s-]*/i, "")
    .trim();
  if (cleaned && !/^(Evidence Bundle|UNKNOWN|MEDIUM|EVIDENCE_BUNDLE|PENDING)$/i.test(cleaned)) {
    return cleaned;
  }
  const summary = firstSentence(change.summary);
  return summary || `${changeKindLabels[change.changeKind]}变更待审查`;
}

export function changeOutcomeSummary(change: Pick<ProjectChange, "summary" | "details" | "affectedFiles">) {
  const summary = firstSentence(change.summary);
  if (summary && !/Evidence Bundle|归因|置信度|个文件|行/.test(summary)) {
    return summary;
  }
  const detailLine = change.details.split(/\r?\n/).find((line) => line.trim() && !line.includes("新增") && !line.includes("删除"));
  const files = parseAffectedFiles(change.affectedFiles).filter((file) => !isRuntimeArtifact(file));
  if (detailLine) return detailLine.trim();
  if (files.length) return `本次变更影响 ${files.length} 个开发相关文件，完整证据可在详情页追溯。`;
  return "本次变更已进入结构化审查，等待确认是否写入项目档案。";
}

export function changeMemoryTargets(change: ProjectChange) {
  const targets = new Set<string>();
  switch (change.changeKind) {
    case "RISK":
      targets.add("当前风险");
      break;
    case "DECISION":
      targets.add("技术决策");
      break;
    case "LEARNING":
      targets.add("经验沉淀");
      break;
    case "ASSET":
      targets.add("可展示成果");
      break;
    default:
      targets.add("已完成能力");
      break;
  }
  if (change.riskNotes) targets.add("当前风险");
  if (change.decisionNotes) targets.add("技术决策");
  if (change.learningNotes) targets.add("经验沉淀");
  if (change.assetCandidates) targets.add("可展示成果");
  return Array.from(targets);
}

export function archiveTargetsLabel(change: ProjectChange) {
  return `将写入：${changeMemoryTargets(change).join(" / ")}`;
}

export function parseAffectedFiles(value: string) {
  return value
    .split(/\r?\n/)
    .map((line) => line.trim().replace(/^[-*]\s*/, ""))
    .filter(Boolean);
}

export function compactPath(path: string) {
  const normalized = path.replace(/\\/g, "/");
  if (normalized.length <= 58) return normalized;
  const parts = normalized.split("/").filter(Boolean);
  if (parts.length >= 3) {
    return `${parts[0]}/.../${parts.at(-1)}`;
  }
  return `...${normalized.slice(-55)}`;
}

export function isRuntimeArtifact(path: string) {
  const normalized = path.replace(/\\/g, "/").toLowerCase();
  return normalized.startsWith("node_modules/")
    || normalized.includes("/node_modules/")
    || normalized.startsWith(".next/")
    || normalized.includes("/.next/")
    || normalized.startsWith("target/")
    || normalized.includes("/target/")
    || normalized.startsWith("dist/")
    || normalized.includes("/dist/")
    || normalized.startsWith("build/")
    || normalized.includes("/build/")
    || normalized.startsWith(".git/")
    || normalized.includes("/.git/")
    || normalized.startsWith(".codex-run/")
    || normalized.includes("/.codex-run/");
}

function firstSentence(value: string) {
  return value
    .split(/\r?\n/)
    .map((line) => line.trim())
    .find(Boolean)
    ?.replace(/^[-*]\s*/, "")
    .trim() ?? "";
}
