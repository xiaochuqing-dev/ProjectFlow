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
  { key: "import", label: "导入项目", description: "添加 zip，生成第一版画像。" },
  { key: "profile", label: "看到画像", description: "确认项目定位和结构。" },
  { key: "path", label: "绑定路径", description: "连接真实项目目录。" },
  { key: "develop", label: "开发一天", description: "开发后刷新变化。" },
  { key: "review", label: "变更审查", description: "采纳可信项目事实。" },
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

  const completedSteps: ProjectFlowStepKey[] = [];
  if (hasProject) completedSteps.push("import");
  if (hasMaterials) completedSteps.push("profile");
  if (hasPath) completedSteps.push("path");
  if (hasWorkSessions || hasEvidenceBundles || hasPendingChanges) completedSteps.push("develop");
  if (hasPendingChanges || hasOutputs) completedSteps.push("review");
  if (hasOutputs) completedSteps.push("output");

  if (!hasProject) {
    return state("NO_PROJECT", "先导入项目", "选择项目 zip，ProjectFlow 会生成项目画像和文件结构。", "导入项目", "import", completedSteps, "导入项目后会看到画像，然后绑定真实路径。");
  }
  if (!hasMaterials) {
    return state("NO_PROFILE", "补齐项目画像", "当前项目还没有可分析材料，先添加完整 zip。", "添加项目 zip", "profile", completedSteps, "画像是后续变更审查和输出的基础。");
  }
  if (!hasPath) {
    return state("NO_LOCAL_PATH", "绑定真实路径", "填写真实项目文件夹路径，ProjectFlow 才能读取 Git evidence。", "保存路径", "path", completedSteps, "绑定后即可开发一天，回来刷新变化。");
  }
  if (hasPendingChanges) {
    return state("HAS_PENDING_CHANGES", "去变更审查", `当前有 ${input.pendingChanges.length} 条候选变更待确认。`, "变更审查", "review", completedSteps, "采纳后会写入项目档案、成长记录和输出素材。", "/tasks");
  }
  if (hasEvidenceBundles) {
    return state("HAS_EVIDENCE_BUNDLES", "生成候选变更", `已有 ${input.evidenceBundles.length} 个证据包，可转成候选变更。`, "查看证据包", "review", completedSteps, "证据包只是证据，生成并采纳候选变更后才会进入项目档案。");
  }
  if (hasWorkSessions) {
    return state("HAS_WORK_SESSIONS", "生成证据包", `已发现 ${input.workSessions.length} 轮开发活动。`, "生成证据包", "develop", completedSteps, "证据包会汇总文件、行数、Git evidence 和 Agent 声明。");
  }
  return state("READY_TO_OUTPUT", "刷新变化或生成输出", "项目已接入。开发后刷新变化；已有确认内容时可生成输出。", "刷新变化", "develop", completedSteps, "完整流程是：导入项目 -> 看到画像 -> 绑定路径 -> 开发一天 -> 回来看变化 -> 审查 -> 生成输出。");
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
