export const CAPABILITY_MATURITY_LABELS: Record<string, string> = {
  FORMING: "形成中",
  FORMED: "已形成",
  CONTINUOUSLY_ENHANCED: "持续增强",
  LONG_TERM_STABLE: "长期稳定",
};

export const CAPABILITY_MAP_STATUS_LABELS: Record<string, string> = {
  NOT_INITIALIZED: "等待初始化",
  DIRTY: "等待自动更新",
  QUEUED: "已进入更新队列",
  GENERATING: "正在自动更新",
  READY: "已完整覆盖",
  READY_STALE: "已有结果，新事实待更新",
  WAITING_FOR_MODEL: "等待模型配置",
  FAILED: "自动更新失败",
};

export const CAPABILITY_EVOLUTION_LABELS: Record<string, string> = {
  NEW_CAPABILITY: "形成能力",
  ENHANCE_CAPABILITY: "增强能力",
  ADD_EVIDENCE: "补充事实支撑",
  MERGE_CAPABILITY: "合并重复能力",
  CORRECTION: "纠正能力表达",
};

export function capabilityMaturityLabel(value: string) {
  return CAPABILITY_MATURITY_LABELS[value] ?? value;
}

export function capabilityMapStatusLabel(value: string) {
  return CAPABILITY_MAP_STATUS_LABELS[value] ?? value;
}

export function capabilityEvolutionLabel(value: string) {
  return CAPABILITY_EVOLUTION_LABELS[value] ?? value;
}
