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
  completedSteps: ProjectFlowStepKey[];
  nextStep: ProjectFlowStepKey;
  helper: string;
};

export const projectFlowSteps: Array<{ key: ProjectFlowStepKey; label: string; description: string }> = [
  { key: "import", label: "导入项目", description: "添加 zip，生成第一版项目理解。" },
  { key: "profile", label: "看到项目理解", description: "确认项目定位和结构。" },
  { key: "path", label: "绑定路径", description: "连接真实项目目录。" },
  { key: "develop", label: "开发一天", description: "开发后刷新今日开发。" },
  { key: "review", label: "开发成果审查", description: "采纳可信项目事实。" },
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
    return state("NO_PROFILE", "补齐项目理解", "当前项目还没有可分析材料，先添加完整 zip。", "添加项目 zip", "profile", completedSteps, "项目理解是后续开发成果审查和输出的基础。");
  }
  if (!hasPath) {
    return state("NO_LOCAL_PATH", "绑定真实路径", "填写真实项目文件夹路径，ProjectFlow 才能读取 Git evidence。", "绑定本地项目", "path", completedSteps, "绑定后即可开发一天，回来刷新今日开发。");
  }
  if (hasPendingChanges) {
    return state("HAS_PENDING_CHANGES", "去开发成果审查", `当前有 ${input.pendingChanges.length} 条待确认内容。`, "开发成果审查", "review", completedSteps, "采纳后会写入项目资产、项目时间线和输出素材。", "/tasks");
  }
  if (hasEvidenceBundles) {
    return state("HAS_EVIDENCE_BUNDLES", "生成待确认内容", `已有 ${input.evidenceBundles.length} 份原始依据，可转成待确认内容。`, "查看原始依据", "review", completedSteps, "原始依据只是证据，生成并采纳待确认内容后才会进入项目资产。");
  }
  if (hasWorkSessions) {
    return state("HAS_WORK_SESSIONS", "整理原始依据", `已发现 ${input.workSessions.length} 轮开发活动。`, "整理原始依据", "develop", completedSteps, "原始依据会汇总文件、行数、Git evidence 和 Agent 声明。");
  }
  if (hasConfirmedAssets) {
    return state("READY_TO_OUTPUT", "生成成果输出", "项目已沉淀出可复用资产，可以生成 README、简历描述、项目复盘或周报。", "生成成果输出", "output", completedSteps, "成果输出会优先使用已确认项目资产、项目时间线和每日回顾。", "/ai-review");
  }
  return state("READY_TO_OUTPUT", "刷新今日开发或生成输出", "项目已接入。开发后刷新今日开发；已有确认内容时可生成输出。", "刷新今日开发", "develop", completedSteps, "完整流程是：导入项目 -> 看到项目理解 -> 绑定路径 -> 开发一天 -> 刷新今日开发 -> 审查 -> 生成输出。");
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
): ProjectFlowState {
  return { kind, title, description, primaryAction, primaryHref, completedSteps, nextStep, helper };
}
