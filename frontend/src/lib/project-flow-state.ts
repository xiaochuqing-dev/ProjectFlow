import type { AiOutput, EvidenceBundle, Project, ProjectChange, ProjectMaterial, ProjectMemory, WorkSessionCandidate } from "./api";

export type ProjectFlowStateKind =
  | "NO_PROJECT"
  | "NO_PROFILE"
  | "NO_LOCAL_PATH"
  | "HAS_WORK_SESSIONS"
  | "HAS_EVIDENCE_BUNDLES"
  | "HAS_PENDING_CHANGES"
  | "READY_TO_OUTPUT";

export type ProjectFlowStepKey =
  | "import"
  | "profile"
  | "path"
  | "develop"
  | "review"
  | "output";

export type ProjectFlowStateInput = {
  project?: Project;
  materials: ProjectMaterial[];
  memory: ProjectMemory | null;
  workSessions: WorkSessionCandidate[];
  evidenceBundles: EvidenceBundle[];
  pendingChanges: ProjectChange[];
  outputs?: AiOutput[];
};

export type ProjectFlowState = {
  kind: ProjectFlowStateKind;
  title: string;
  description: string;
  primaryAction: string;
  primaryHref?: string;
  secondaryAction?: string;
  secondaryHref?: string;
  completedSteps: ProjectFlowStepKey[];
  nextStep: ProjectFlowStepKey;
  helper: string;
};

export const projectFlowSteps: Array<{ key: ProjectFlowStepKey; label: string; description: string }> = [
  { key: "import", label: "导入项目", description: "添加 zip，生成第一版项目理解。" },
  { key: "profile", label: "看到项目理解", description: "确认项目定位和结构。" },
  { key: "path", label: "绑定路径", description: "连接真实项目目录。" },
  { key: "develop", label: "产生变化", description: "开发后分析新变化。" },
  { key: "review", label: "沉淀确认", description: "确认有证据的建议沉淀。" },
  { key: "output", label: "生成输出", description: "产出周报、README 或简历素材。" },
];

export function resolveProjectFlowState(input: ProjectFlowStateInput): ProjectFlowState {
  const hasProject = Boolean(input.project);
  const hasMaterials = input.materials.length > 0;
  const hasPath = Boolean(input.memory?.localProjectPath?.trim());
  const hasWorkSessions = input.workSessions.length > 0;
  const hasEvidenceBundles = input.evidenceBundles.length > 0;
  const hasPendingChanges = input.pendingChanges.length > 0;
  const hasOutputs = Boolean(input.outputs?.length);
  const hasConfirmedAssets = Boolean(
    input.memory?.completedCapabilities?.trim()
    || input.memory?.technicalDecisions?.trim()
    || input.memory?.developerLearnings?.trim()
    || input.memory?.showcaseAssets?.trim()
    || input.memory?.currentRisks?.trim()
  );

  const completedSteps: ProjectFlowStepKey[] = [];
  if (hasProject) completedSteps.push("import");
  if (hasMaterials) completedSteps.push("profile");
  if (hasPath) completedSteps.push("path");
  if (hasWorkSessions || hasEvidenceBundles || hasPendingChanges) completedSteps.push("develop");
  if (hasPendingChanges || hasOutputs) completedSteps.push("review");
  if (hasOutputs) completedSteps.push("output");

  if (!hasProject) {
    return state("NO_PROJECT", "先导入项目", "选择项目 zip，ProjectFlow 会生成项目理解和文件结构。", "导入项目", "import", completedSteps, "导入项目后会看到项目理解，然后绑定真实路径。");
  }
  if (!hasMaterials) {
    return state("NO_PROFILE", "补齐项目理解", "当前项目还没有可分析材料，先添加完整 zip。", "添加项目 zip", "profile", completedSteps, "项目理解是后续沉淀确认和输出的基础。");
  }
  if (!hasPath) {
    return state("NO_LOCAL_PATH", "绑定真实路径", "填写真实项目文件夹路径，ProjectFlow 才能读取 Git evidence。", "绑定本地项目", "path", completedSteps, "绑定后即可持续开发，再回来分析新变化。");
  }
  if (hasPendingChanges) {
    return state("HAS_PENDING_CHANGES", "去沉淀确认", `当前有 ${input.pendingChanges.length} 条建议沉淀。`, "沉淀确认", "review", completedSteps, "确认后会写入项目沉淀、项目时间线和输出素材。", "/tasks");
  }
  if (hasEvidenceBundles) {
    return state("HAS_EVIDENCE_BUNDLES", "整理开发推进段", `已有 ${input.evidenceBundles.length} 份兼容原始依据，可整理为建议沉淀。`, "查看原始依据", "review", completedSteps, "原始依据只是证据，经用户确认后才会进入项目沉淀。");
  }
  if (hasWorkSessions) {
    return state("HAS_WORK_SESSIONS", "整理原始依据", `已发现 ${input.workSessions.length} 轮开发活动。`, "整理原始依据", "develop", completedSteps, "原始依据会汇总文件、行数、Git evidence 和 Agent 声明。");
  }
  if (hasConfirmedAssets) {
    return state("READY_TO_OUTPUT", "查看能力与成果", "项目已沉淀出可复用资产。先查看能力与成果，确认无误后再生成输出。", "查看能力与成果", "output", completedSteps, "能力与成果页展示每条能力的证据、用途和可复用表达；确认后再进入成果输出。", "/project-intelligence/capabilities", "生成成果输出", "/ai-review");
  }
  return state("READY_TO_OUTPUT", "分析新变化或生成输出", "项目已接入。开发后分析新变化；已有确认沉淀时可生成输出。", "分析新变化", "develop", completedSteps, "完整流程是：导入项目 -> 看到项目理解 -> 绑定路径 -> 分析新变化 -> 沉淀确认 -> 生成输出。");
}

function state(
  kind: ProjectFlowStateKind,
  title: string,
  description: string,
  primaryAction: string,
  nextStep: ProjectFlowStepKey,
  completedSteps: ProjectFlowStepKey[],
  helper: string,
  primaryHref?: string,
  secondaryAction?: string,
  secondaryHref?: string,
): ProjectFlowState {
  return { kind, title, description, primaryAction, primaryHref, secondaryAction, secondaryHref, completedSteps, nextStep, helper };
}
