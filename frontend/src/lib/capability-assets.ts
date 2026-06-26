import type { ProjectMemory } from "@/lib/api";
import { capabilityBulletItems } from "@/lib/project-memory-display";
import { capabilityNameOf, isFallbackName } from "@/lib/capability-names";

export type CapabilityAssetStatus = "已确认" | "可补充" | "待补证据";

export type CapabilityAssetDetail = {
  problem: string;
  importance: string;
  recognized: string[];
  evidence: string[];
  readme: string;
  resume: string;
  interview: string;
};

export type CapabilityAsset = {
  name: string;
  oneLine: string;
  status: CapabilityAssetStatus;
  evidenceCount: number;
  scenes: string[];
  reusableExpression: string;
  rawFact: string;
  detail: CapabilityAssetDetail;
};

const SCENES = ["README", "简历", "面试", "周报"];

export function buildCapabilityAssets(memory: ProjectMemory | null): CapabilityAsset[] {
  const items = capabilityBulletItems(memory?.completedCapabilities ?? "");
  return items.map((item) => {
    const name = capabilityNameOf(item);
    const detail = buildDetail(item, memory, name);
    return {
      name,
      oneLine: detail.problem,
      status: assetStatus(memory, item),
      evidenceCount: detail.evidence.length,
      scenes: SCENES,
      reusableExpression: detail.readme,
      rawFact: item,
      detail,
    };
  });
}

function assetStatus(memory: ProjectMemory | null, item: string): CapabilityAssetStatus {
  const hasEvidence = Boolean(memory?.technicalDecisions || memory?.showcaseAssets);
  if (isFallbackName(capabilityNameOf(item)) && !hasEvidence) {
    return "待补证据";
  }
  return hasEvidence ? "已确认" : "可补充";
}

function buildDetail(item: string, memory: ProjectMemory | null, name: string): CapabilityAssetDetail {
  return {
    problem: capabilityProblem(item),
    importance: "它说明项目已经不只是保存过程记录，而是能把真实开发活动沉淀成后续 README、简历描述、项目复盘和面试讲解可以复用的工程资产。",
    recognized: recognizedItems(item, memory),
    evidence: [
      "来自已确认项目资产。",
      memory?.updatedAt ? `最近更新于 ${new Date(memory.updatedAt).toLocaleString()}` : "暂无更细来源时间。",
      "更细证据可在相关资产卡的“为什么可信？”中继续追溯。",
    ],
    readme: reusableReadme(name, item),
    resume: reusableResume(name, item),
    interview: reusableInterview(name, item),
  };
}

function capabilityProblem(value: string) {
  if (/zip|结构|目录|技术栈|文件/i.test(value)) {
    return "帮助用户从导入项目中理解工程结构、核心模块和运行线索，减少首次理解项目的成本。";
  }
  if (/Git|证据|变化|开发/i.test(value)) {
    return "帮助用户把零散开发变化整理成可审查、可追溯、可复用的开发成果。";
  }
  if (/输出|README|简历|周报|复盘/i.test(value)) {
    return "帮助用户把已确认项目资产转化为对外展示和阶段汇报材料。";
  }
  return "帮助用户把一次具体开发成果沉淀为后续可复用、可展示的项目能力。";
}

function recognizedItems(value: string, memory: ProjectMemory | null) {
  return [
    value,
    memory?.technicalDecisions ? `相关技术决策：${firstLine(memory.technicalDecisions)}` : "暂无关联技术决策。",
    memory?.showcaseAssets ? `可展示成果：${firstLine(memory.showcaseAssets)}` : "暂无单独成果素材。",
  ];
}

function reusableReadme(name: string, _item: string) {
  return `沉淀了“${name}”，可结合项目资产、开发证据和用户确认内容，用于 README 项目亮点说明。`;
}

function reusableResume(name: string, _item: string) {
  return `在项目中落地“${name}”，负责把开发过程和成果整理成可追溯、可复用的工程资产。`;
}

function reusableInterview(name: string, _item: string) {
  return `可围绕“${name}”展开面试讲解：遇到的工程问题、采取的做法、产出的可复用资产。`;
}

function firstLine(value: string) {
  return value.split(/\r?\n/).map((line) => line.trim()).find(Boolean) ?? value;
}
