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
  return [change.sourceRef, change.affectedFiles.split("\n")[0], change.riskNotes ? "含风险备注" : ""]
    .filter(Boolean)
    .join(" · ") || "结构化变更可审查";
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
