import type {
  Project,
  ProjectCurrentState,
  ProjectHistoryOverview,
  ProjectHistoryStory,
} from "./api";

export const workspaceViews = [
  "projects",
  "current",
  "history",
  "handoff",
  "project-settings",
  "settings",
] as const;
export type WorkspaceView = (typeof workspaceViews)[number];
export const viewLabels: Record<WorkspaceView, string> = {
  projects: "项目库",
  current: "当前状态",
  history: "项目历程",
  handoff: "Agent 交接",
  "project-settings": "项目设置",
  settings: "全局设置",
};

export type WorkspaceStory = {
  id: string;
  title: string;
  summary: string;
  date: string;
  before: string;
  change: string;
  after: string;
  evidence: string;
  status: "confirmed" | "attention" | "conflict" | "recorded";
  chapter: string;
};
export type WorkspaceChapter = {
  id: string;
  title: string;
  summary: string;
  range: string;
  version: string;
};
export type WorkspaceProject = {
  id: string;
  name: string;
  description: string;
  summary: string;
  judgment: string;
  status: string;
  updated: string;
  phase: string;
  phaseRange: string;
  color: string;
  stories: WorkspaceStory[];
  chapters: WorkspaceChapter[];
  attention: string[];
  unknowns: string[];
  repoUrl?: string;
  stale?: boolean;
  degraded?: boolean;
  source?: Project;
  confirmedChanges?: string[];
};

// Hand-authored design fixtures. These are never sent to the API or stored as project facts.
const corporation: WorkspaceProject = {
  id: "corporation",
  name: "Corporation-Agent",
  description: "企业级 AI 智能协作平台",
  color: "indigo",
  status: "运行中",
  updated: "2024年12月10日 14:23",
  phase: "内部验证期",
  phaseRange: "2024.12 – 2025.01",
  summary:
    "项目已完成核心架构与 Agent 协作框架的开发，当前进入企业内部试用阶段。重点验证多 Agent 协同、知识沉淀与权限体系，整体进展符合预期，下一步将聚焦性能优化与商业化场景的验证。",
  judgment: "处于内部验证期，整体进展符合预期",
  attention: ["企业权限模型与现有系统存在集成风险"],
  unknowns: ["部分行业知识库内容仍不完整"],
  stories: [
    {
      id: "s1",
      title: "完成多 Agent 协作调度模块开发",
      summary: "核心调度策略已通过内部测试，性能符合预期",
      date: "12月10日",
      before: "多个 Agent 依次执行任务，缺少统一的调度与状态协调。",
      change: "新增任务路由和协作调度策略，统一处理执行状态与结果。",
      after: "内部测试中的协作任务可以完成调度，真实企业负载仍待验证。",
      evidence: "7d181a",
      status: "confirmed",
      chapter: "c3",
    },
    {
      id: "s2",
      title: "接入企业知识库（内测版）",
      summary: "支持文档解析、向量检索与权限控制",
      date: "12月09日",
      before: "团队知识分散在不同文档中，Agent 无法按权限查询。",
      change: "接入文档解析与检索服务，并加入访问权限控制。",
      after: "内测知识库可供授权 Agent 检索，部分行业材料仍待补齐。",
      evidence: "a3f2c9",
      status: "confirmed",
      chapter: "c3",
    },
    {
      id: "s3",
      title: "优化权限体系与审计日志",
      summary: "完成角色权限模型设计并通过安全评审",
      date: "12月08日",
      before: "权限规则分散，缺少统一的操作追踪。",
      change: "整理角色权限模型，为关键操作增加审计记录。",
      after: "权限操作可追溯，与企业既有系统的集成仍需核查。",
      evidence: "9a0114",
      status: "attention",
      chapter: "c2",
    },
    {
      id: "s4",
      title: "修复长对话场景的上下文丢失问题",
      summary: "增强记忆策略，提升稳定性",
      date: "12月07日",
      before: "长对话中部分先前信息不能稳定保留。",
      change: "调整上下文保留策略，补充长对话回归验证。",
      after: "已覆盖的测试场景可保留上下文，超长对话仍有边界。",
      evidence: "c4e7d2",
      status: "conflict",
      chapter: "c2",
    },
  ],
  chapters: [
    {
      id: "c1",
      version: "1.0",
      title: "核心框架搭建",
      summary: "从最初的协作设想出发，建立 Agent 运行与任务流转的基础。",
      range: "2024.09 – 2024.10",
    },
    {
      id: "c2",
      version: "2.0",
      title: "多 Agent 能力建设",
      summary: "连接知识、记忆与权限，让独立 Agent 开始形成可协作的整体。",
      range: "2024.10 – 2024.12",
    },
    {
      id: "c3",
      version: "3.0",
      title: "企业内测与优化",
      summary: "将协作框架带入真实业务场景，验证稳定性、边界与可用性。",
      range: "2024.12 – 2025.01",
    },
  ],
};

export const demoProjects: WorkspaceProject[] = [
  corporation,
  {
    id: "mindstudio",
    name: "MindStudio",
    description: "AI 创意工作台",
    color: "teal",
    status: "探索中",
    updated: "2024年12月09日 16:40",
    summary:
      "创作流程已有第一份体验草稿。现有材料主要是访谈笔记与设计文档，尚不能确认产品实现状态。",
    judgment: "创作流程已有草稿，体验假设仍待验证",
    phase: "概念探索",
    phaseRange: "2024.12",
    stories: [],
    chapters: [],
    attention: [],
    unknowns: ["尚无可验证的实现结果", "访谈材料只代表受访者陈述"],
  },
  {
    id: "dataharbor",
    name: "DataHarbor",
    description: "数据整理与分析平台",
    color: "blue",
    status: "待整理",
    updated: "尚未更新",
    summary:
      "项目已添加，尚未整理项目材料。连接已有文档或本地目录后，可以开始了解当前状态。",
    judgment: "尚无可确认的当前状态",
    phase: "尚未确认",
    phaseRange: "等待项目材料",
    stories: [],
    chapters: [],
    attention: [],
    unknowns: [],
  },
];

export function projectCard(project: Project): WorkspaceProject {
  return {
    id: project.id,
    name: project.name,
    description: project.description || "尚未填写项目说明",
    color: "indigo",
    summary: "尚未读取当前状态。",
    judgment: "尚未读取当前状态",
    status: "已添加",
    updated: "尚未读取",
    phase: "尚未确认",
    phaseRange: "",
    stories: [],
    chapters: [],
    attention: [],
    unknowns: [],
    repoUrl: project.repoUrl,
    source: project,
  };
}

export function persistedProject(
  project: Project,
  current: ProjectCurrentState,
  history: ProjectHistoryOverview,
): WorkspaceProject {
  const chapters = (history.overview.chapters ?? []).map((chapter, index) => ({
    id: chapter.id,
    title: chapter.title,
    summary: chapter.summary,
    range: `${chapter.from?.slice(0, 10) || "未知"} – ${chapter.to?.slice(0, 10) || "未知"}`,
    version: String(index + 1).padStart(2, "0"),
  }));
  const latest = chapters.at(-1);
  return {
    ...projectCard(project),
    summary: current.confirmedState || "尚无可确认的当前状态",
    judgment: current.confirmedState || "尚无可确认的当前状态",
    status: current.degraded
      ? "部分可用"
      : current.stale || current.continuityDirty
        ? "待更新"
        : "已读取",
    updated: current.latestSuccessfulAt
      ? new Date(current.latestSuccessfulAt).toLocaleString("zh-CN", {
          hour12: false,
        })
      : "尚未成功更新",
    phase: latest?.title || "尚未确认阶段",
    phaseRange: latest?.range || "",
    chapters,
    attention: current.conflicts ?? [],
    unknowns: [
      ...new Set([
        ...(current.unknowns ?? []),
        ...(current.limitations ?? []),
        ...history.coverage.gaps,
        ...history.coverage.limitations,
      ]),
    ],
    stale: current.stale || current.continuityDirty,
    degraded: current.degraded,
    confirmedChanges: current.recentConfirmedChanges ?? [],
  };
}

export function persistedStory(story: ProjectHistoryStory): WorkspaceStory {
  return {
    id: story.id,
    title: story.humanTitle,
    summary: story.oneSentenceSummary,
    date: story.occurredTo?.slice(0, 10) || "时间未知",
    before: story.beforeState,
    change: story.change,
    after: story.afterState,
    evidence: story.eventRefs?.[0] || "",
    status: story.conflicts?.length
      ? "conflict"
      : story.unknowns?.length
        ? "attention"
        : "recorded",
    chapter: "",
  };
}

export function workspaceHref(
  view: WorkspaceView,
  demo: boolean,
  projectId?: string,
) {
  const params = new URLSearchParams();
  if (demo) params.set("demo", "1");
  if (projectId) params.set("project", projectId);
  return `/workspace/${view}${params.size ? `?${params}` : ""}`;
}
