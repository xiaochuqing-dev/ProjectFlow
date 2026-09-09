"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { ProviderManager } from "./ProviderManager";
import {
  Activity,
  ArrowDownToLine,
  ArrowRight,
  BookOpen,
  BookOpenText,
  Check,
  CheckCircle2,
  ChevronRight,
  Circle,
  Copy,
  Database,
  ExternalLink,
  FileText,
  Flag,
  Folder,
  FolderOpen,
  GitBranch,
  Globe,
  HardDrive,
  Layers,
  Link2,
  Monitor,
  Moon,
  Plus,
  Search,
  Settings,
  ShieldCheck,
  Sparkles,
  TriangleAlert,
  Zap,
} from "lucide-react";
import { Button, Card, SectionHeader } from "@/components/ui/primitives";
import {
  getWorkspaceContextPackage,
  listAiProviders,
  type AiProvider,
  type WorkspaceContextPackage,
} from "@/lib/api";
import { readSession } from "@/lib/auth";
import {
  type WorkspaceProject,
  type WorkspaceStory,
  type WorkspaceView,
} from "@/lib/workspace-preview";

type PageProps = { project: WorkspaceProject; demo: boolean };
type Href = (view: WorkspaceView, id?: string) => string;
type StoryAction = (story: WorkspaceStory) => void;

export function ProjectMark({
  color = "indigo",
  small = false,
}: {
  color?: string;
  small?: boolean;
}) {
  return (
    <span
      className={`pf-project-mark ${color} ${small ? "small" : ""}`}
      aria-hidden="true"
    >
      <span />
      <span />
    </span>
  );
}

export function CurrentPage({
  project,
  demo,
  href,
  onStory,
}: PageProps & { href: Href; onStory: StoryAction }) {
  const fullDemo = demo && project.id === "corporation";
  return (
    <div className="pf-current-page">
      <Card
        className={`pf-panel pf-hero ${!project.stories.length ? "pf-hero-quiet" : ""}`}
        shadow="none"
      >
        <div className="pf-hero-art" />
        <div className="pf-hero-heading">
          <ProjectMark color={project.color} />
          <div>
            <h2>{project.name}</h2>
            <p>{project.description}</p>
          </div>
          <span className="pf-chip running">{project.status}</span>
        </div>
        <p className="pf-hero-summary">{project.summary}</p>
        <div className="pf-hero-stats">
          <div className="pf-progress-group">
            {fullDemo ? (
              <div
                className="pf-progress-ring"
                aria-label="示例进度 78%，不是项目完成度计算"
              >
                <span>78%</span>
              </div>
            ) : (
              <div className="pf-state-orb">
                <Activity size={23} />
              </div>
            )}
            <span>
              <small>{fullDemo ? "整体进度 · 示例" : "当前记录"}</small>
              <strong>{fullDemo ? "按计划进行" : project.status}</strong>
            </span>
          </div>
          <div className="pf-hero-stat">
            <span className="pf-stat-orb">
              <Layers size={20} />
            </span>
            <span>
              <small>当前阶段</small>
              <strong>{project.phase}</strong>
              <small>{project.phaseRange}</small>
            </span>
          </div>
          {fullDemo && (
            <div className="pf-hero-stat">
              <span className="pf-stat-orb purple">
                <Flag size={20} />
              </span>
              <span>
                <small>下一里程碑 · 用户计划</small>
                <strong>企业试点发布</strong>
                <small>2025年1月15日</small>
              </span>
            </div>
          )}
        </div>
        {fullDemo && (
          <div className="pf-hero-caption">
            <span>
              更长远的项目
              <br />
              创造更有价值的未来
            </span>
            <small>
              Continuity
              <br />
              Creates Greater Value
            </small>
          </div>
        )}
      </Card>
      {(project.stale || project.degraded) && (
        <div className="pf-notice">
          <TriangleAlert size={16} />
          <p>
            {project.degraded
              ? "部分信息暂时无法更新，保留已保存的可追溯结果。"
              : "项目有新材料或状态可能过期，更新后可查看最新结果。"}
          </p>
        </div>
      )}
      <div className="pf-current-columns">
        <Card className="pf-panel pf-changes" shadow="none">
          <SectionHeader
            className="pf-section-heading"
            icon={<Zap className="pf-glow-icon violet" size={26} />}
            title="最近主要变化"
            subtitle="仅展示已确认的关键进展"
            actions={
              <Link className="pf-text-link" href={href("history")}>
                查看全部
                <ArrowRight size={15} />
              </Link>
            }
          />
          <div className="pf-change-list">
            {demo
              ? project.stories.slice(0, 4).map((story, i) => (
                  <button
                    className="pf-change-row"
                    key={story.id}
                    onClick={() => onStory(story)}
                  >
                    <span
                      className={`pf-timeline-dot ${i === 3 ? "quiet" : ""}`}
                    />
                    <span className="pf-change-copy">
                      <strong>{story.title}</strong>
                      <small>{story.summary}</small>
                    </span>
                    <time>{story.date}</time>
                  </button>
                ))
              : project.confirmedChanges?.slice(0, 4).map((change) => (
                  <Link
                    className="pf-change-row"
                    key={change}
                    href={href("history")}
                  >
                    <span className="pf-timeline-dot" />
                    <span className="pf-change-copy">
                      <strong>{change}</strong>
                      <small>查看项目历程与来源</small>
                    </span>
                    <ChevronRight size={14} />
                  </Link>
                ))}
            {!(demo
              ? project.stories.length
              : project.confirmedChanges?.length) && (
              <div className="pf-inline-empty">
                <BookOpenText size={29} />
                <h3>还没有可确认的变化</h3>
                <p>
                  材料中的设想和计划会保留原有身份，只有可追溯的结果出现在这里。
                </p>
                <Link href={href("project-settings")}>
                  查看项目来源
                  <ArrowRight size={14} />
                </Link>
              </div>
            )}
          </div>
        </Card>
        <Card className="pf-panel pf-judgment" shadow="none">
          <SectionHeader
            className="pf-section-heading"
            icon={<Layers className="pf-glow-icon" size={24} />}
            title="当前判断"
            actions={
              <span className="pf-chip running">
                {fullDemo ? "运行平稳" : project.status}
              </span>
            }
          />
          <div className="pf-judgment-copy">
            <h3>{project.judgment}</h3>
            <p>
              {fullDemo
                ? "在真实企业场景中进行小范围内测，验证多 Agent 协作、知识沉淀与权限体系的稳定性与可用性。目前核心功能运行稳定，用户反馈积极，下一步将聚焦性能优化与商业化场景验证。"
                : project.summary}
            </p>
          </div>
          <div className="pf-attention">
            <div className="pf-attention-title">
              <TriangleAlert size={20} />
              <strong>需要关注的事项</strong>
              <span className="pf-chip attention">
                {project.attention.length + project.unknowns.length} 项待跟进
              </span>
            </div>
            {[
              ...project.attention.map((text) => ({ text, conflict: true })),
              ...project.unknowns.map((text) => ({ text, conflict: false })),
            ]
              .slice(0, 2)
              .map(({ text, conflict }, i) => (
                <div className="pf-attention-row" key={text}>
                  <i className={conflict ? "red" : "amber"} />
                  <div>
                    <p>{text}</p>
                    {fullDemo && (
                      <small>
                        {i === 0
                          ? "需与 IT 团队进一步确认对接方案"
                          : "建议在试点前补充关键领域数据"}
                      </small>
                    )}
                  </div>
                  {fullDemo && <time>{i === 0 ? "12月09日" : "12月08日"}</time>}
                </div>
              ))}
            {!project.attention.length && !project.unknowns.length && (
              <p className="pf-attention-clear">现有记录中没有待关注事项。</p>
            )}
            {project.attention.length + project.unknowns.length > 2 && (
              <Link href={href("handoff")}>
                查看全部注意事项
                <ArrowRight size={13} />
              </Link>
            )}
          </div>
        </Card>
      </div>
      {!!project.chapters.length && (
        <Card className="pf-panel pf-history-preview" shadow="none">
          <div className="pf-history-preview-heading">
            <BookOpen className="pf-glow-icon" size={23} />
            <h2>历程概览</h2>
            <p>关键里程碑与阶段成果 · 完整记录请前往项目历程</p>
            <Link className="pf-button pf-small" href={href("history")}>
              查看完整项目历程
              <ArrowRight size={15} />
            </Link>
          </div>
          <div className="pf-mini-timeline">
            {project.chapters.slice(-3).map((chapter, i) => (
              <Link
                key={chapter.id}
                href={`${href("history")}&chapter=${encodeURIComponent(chapter.id)}`}
                className={i === 2 ? "current" : ""}
              >
                {i === 2 ? (
                  <span className="pf-milestone-current" />
                ) : (
                  <CheckCircle2 size={18} />
                )}
                <span>
                  <small>{chapter.version}</small>
                  <strong>{chapter.title}</strong>
                  <small>{chapter.range}</small>
                </span>
              </Link>
            ))}
            {fullDemo && (
              <div className="pf-planned-milestone">
                <Circle size={17} />
                <span>
                  <small>4.0 · 计划</small>
                  <strong>正式发布</strong>
                  <small>2025 Q1+</small>
                </span>
              </div>
            )}
          </div>
        </Card>
      )}
      <div className="pf-agent-quote">
        <span className="pf-quote-symbol">“</span>
        <Link href={href("handoff")}>来自 Agent 的一句话</Link>
        <p>
          “　
          {fullDemo
            ? "整体方向正确，团队执行力很强。下一个里程碑将带来更大的价值。"
            : "保留已确认的结果，也把尚未确认的边界留给下一位协作者。"}
          　”
        </p>
        <small>{demo ? "示例寄语" : "交接提示"}</small>
      </div>
    </div>
  );
}

export function LibraryPage({
  projects,
  demo,
  onOpen,
}: {
  projects: WorkspaceProject[];
  demo: boolean;
  onOpen: (id: string) => string;
}) {
  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState("全部项目");
  const matching = projects.filter(
    (p) =>
      (p.name + p.description).toLowerCase().includes(search.toLowerCase()) &&
      (filter !== "需要注意" || p.attention.length + p.unknowns.length > 0),
  );
  return (
    <div className="pf-library-page">
      <section className="pf-library-intro">
        <div>
          <span className="pf-eyebrow">YOUR PROJECTS, IN CONTINUITY</span>
          <h2>让每一个想法，持续向前。</h2>
          <p>打开项目，找回当前状态、来时的路径与下一次交接的起点。</p>
        </div>
        <Link className="pf-button pf-primary" href="/projects">
          <Plus size={16} />
          添加项目
        </Link>
      </section>
      <div className="pf-library-toolbar">
        <div className="pf-segmented">
          {["全部项目", "需要注意"].map((item) => (
            <button
              aria-pressed={filter === item}
              className={filter === item ? "active" : ""}
              key={item}
              onClick={() => setFilter(item)}
            >
              {item}
              {item === "全部项目" && <span>{projects.length}</span>}
            </button>
          ))}
        </div>
        <label className="pf-filter-search">
          <Search size={15} />
          <input
            placeholder="搜索项目…"
            aria-label="筛选项目"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </label>
      </div>
      <div className="pf-project-grid">
        {matching.map((p) => (
          <Link
            href={onOpen(p.id)}
            key={p.id}
            className={`pf-library-card ${p.color}`}
          >
            <div className="pf-library-card-head">
              <ProjectMark color={p.color} />
              <span className="pf-chip running">{p.status}</span>
            </div>
            <h3>{p.name}</h3>
            <p className="pf-library-description">{p.description}</p>
            <p className="pf-library-summary">
              {p.source ? "打开项目，读取已保存的状态与历程。" : p.judgment}
            </p>
            <div className="pf-library-card-foot">
              <span>
                {p.attention.length + p.unknowns.length > 0 ? (
                  <>
                    <i />
                    {p.attention.length + p.unknowns.length} 项需要注意
                  </>
                ) : (
                  "等待下一次向前"
                )}
              </span>
              <ArrowRight size={17} />
            </div>
            <small>{p.updated}</small>
          </Link>
        ))}
        {!matching.length && (
          <div className="pf-empty">
            <FolderOpen size={35} />
            <h3>
              {projects.length
                ? "没有找到匹配的项目"
                : "这里将保存你的项目故事"}
            </h3>
            <p>
              {projects.length
                ? "试试其他关键词或切换到全部项目。"
                : "从已有目录、文档或 ZIP 开始，无需先准备 Git 仓库。"}
            </p>
            <Link className="pf-button" href="/projects">
              添加或导入项目
              <Plus size={15} />
            </Link>
          </div>
        )}
      </div>
      <div className="pf-import-strip">
        <FolderOpen size={27} />
        <div>
          <h3>从你已有的材料开始</h3>
          <p>本地目录、ZIP、研究文档，都是一个项目的起点。</p>
        </div>
        <Link href="/projects" className="pf-text-link">
          绑定目录 / 导入 ZIP
          <ArrowRight size={16} />
        </Link>
      </div>
      <p className="pf-page-footnote">
        {demo
          ? "当前为三个独立设计示例。添加项目将进入真实项目库。"
          : "项目库只读取已保存的项目信息。打开项目不会自动扫描或调用模型。"}
      </p>
    </div>
  );
}

export function HandoffPage({
  project,
  demo,
  onToast,
}: PageProps & { onToast: (text: string) => void }) {
  const [context, setContext] = useState<WorkspaceContextPackage | null>(null);
  const [loading, setLoading] = useState(!demo);
  const [error, setError] = useState("");
  const [includeMaterials, setIncludeMaterials] = useState(true);
  const content = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (demo) return;
    let active = true;
    getWorkspaceContextPackage(readSession().accessToken, project.id)
      .then((value) => {
        if (active) setContext(value);
      })
      .catch((e) => {
        if (active) setError(e.message);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [demo, project.id]);
  const facts = demo
    ? project.stories
        .filter((s) => s.status === "confirmed")
        .map((s) => s.title)
    : context?.currentStrongFacts.map((f) => f.statement) || [];
  const unknowns = demo
    ? [...project.attention, ...project.unknowns]
    : [
        ...(context?.conflicts.map((f) => f.statement) || []),
        ...(context?.unknowns.map((f) => f.statement) || []),
      ];
  const materials =
    demo && project.id === "corporation"
      ? ["协作框架设计说明", "企业知识库接入指南", "内部验证记录与已知边界"]
      : context?.suggestedDeepReadTargets || [];
  const summary =
    context?.currentProjectState?.confirmedState || project.summary;
  const limitations = context
    ? [
        ...context.limitations,
        ...context.unreadScope.map((s) => `未读取范围：${s}`),
        ...(context.truncated
          ? ["交接内容已按预算缩减，不能代表全部项目材料。"]
          : []),
      ]
    : demo
      ? ["示例内容只用于界面评审，不可作为真实工程事实。"]
      : [];
  async function copy() {
    const text = `${demo ? "设计示例，非真实项目事实\n\n" : ""}${project.name} · Agent 交接\n最近更新：${context?.generatedAt || project.updated}\n\n当前状态\n${summary}\n\n已确认事实\n${facts.join("\n") || "暂无可确认事实"}\n\n近期变化\n${(context?.latestVerifiedChanges.map((s) => s.statement) || project.stories.map((s) => s.summary)).join("\n")}\n\n未解决的冲突与未确认项\n${unknowns.join("\n") || "当前记录中暂无"}\n\n适用范围与限制\n${limitations.join("\n") || "仅包含已保存、按预算选取的项目材料。"}${includeMaterials ? `\n\n建议继续查看\n${materials.join("\n") || "暂无推荐材料"}` : ""}`;
    try {
      await navigator.clipboard.writeText(text);
      onToast("交接内容已复制，包含未确认项和适用限制");
    } catch {
      const selection = window.getSelection();
      const range = document.createRange();
      if (content.current && selection) {
        range.selectNodeContents(content.current);
        selection.removeAllRanges();
        selection.addRange(range);
      }
      onToast("剪贴板暂不可用，已选中交接正文，可按 Ctrl+C 复制");
    }
  }
  return (
    <div className="pf-handoff-page">
      <div className="pf-handoff-toolbar">
        <span className="pf-chip running">
          <ShieldCheck size={13} />
          {demo ? "交接示例" : "已保存的项目上下文"}
        </span>
        <label>
          <input
            type="checkbox"
            checked={includeMaterials}
            onChange={(e) => setIncludeMaterials(e.target.checked)}
          />
          包含推荐材料
        </label>
        <Button
          className="pf-button pf-primary"
          onClick={copy}
          disabled={loading || !!error}
        >
          <Copy size={15} />
          复制交接内容
        </Button>
      </div>
      {loading && <p role="status">正在读取已保存的交接内容…</p>}
      {error && (
        <p className="pf-notice" role="alert">
          {error}，未使用示例内容替代。
        </p>
      )}
      <Card className="pf-panel pf-handoff-document" shadow="none">
        <div ref={content}>
          <header>
            <span className="pf-eyebrow">CONTEXT FOR THE NEXT CHAPTER</span>
            <h2>
              {project.name}
              <span>项目交接</span>
            </h2>
            <p>
              最近更新　
              {context?.generatedAt
                ? new Date(context.generatedAt).toLocaleString("zh-CN")
                : project.updated}
            </p>
          </header>
          <section>
            <div className="pf-document-number">01</div>
            <div>
              <h3>项目现在做到哪里了</h3>
              <p>{summary}</p>
            </div>
          </section>
          <section>
            <div className="pf-document-number">02</div>
            <div>
              <h3>可以继续使用的已确认事实</h3>
              <ul className="pf-fact-list">
                {facts.map((f) => (
                  <li key={f}>
                    <CheckCircle2 size={15} />
                    {f}
                  </li>
                ))}
              </ul>
              {!facts.length && (
                <p>当前没有足够材料确认事实，请先核查项目来源。</p>
              )}
            </div>
          </section>
          <section>
            <div className="pf-document-number">03</div>
            <div>
              <h3>近期发生了什么</h3>
              {(
                context?.latestVerifiedChanges.map((f) => f.statement) ||
                project.stories.slice(0, 2).map((s) => s.summary)
              ).map((s) => (
                <p key={s}>{s}</p>
              ))}
              {!project.stories.length &&
                !context?.latestVerifiedChanges.length && (
                  <p>尚无已确认的近期变化。</p>
                )}
            </div>
          </section>
          <section className="pf-document-caution">
            <div className="pf-document-number">
              <TriangleAlert size={18} />
            </div>
            <div>
              <h3>仍未解决的冲突与未确认项</h3>
              {unknowns.map((u) => (
                <p key={u}>{u}</p>
              ))}
              {!unknowns.length && (
                <p>现有记录中暂无，仍需遵守下方适用范围。</p>
              )}
            </div>
          </section>
          {includeMaterials && (
            <section>
              <div className="pf-document-number">04</div>
              <div>
                <h3>建议继续查看的材料</h3>
                {materials.map((m) => (
                  <p className="pf-material-line" key={m}>
                    <FileText size={15} />
                    {m}
                  </p>
                ))}
                {!materials.length && <p>暂无推荐材料。</p>}
              </div>
            </section>
          )}
          <footer>
            <ShieldCheck size={17} />
            <div>
              <strong>适用范围与内容限制</strong>
              {limitations.map((l) => (
                <p key={l}>{l}</p>
              ))}
              {!limitations.length && (
                <p>
                  仅包含已保存、按预算选取的项目材料；未读取的内容不能据此判断。
                </p>
              )}
            </div>
          </footer>
        </div>
      </Card>
    </div>
  );
}

export function SettingsPage({
  global,
  demo,
  project,
  href,
}: {
  global: boolean;
  demo: boolean;
  project: WorkspaceProject | null;
  href: Href;
}) {
  const tabs = global
    ? ["模型与 API", "外观", "数据与备份", "运行环境"]
    : ["项目来源", "Git / GitHub", "Obsidian", "Provider 策略", "高级设置"];
  const [tab, setTab] = useState(tabs[0]);
  const [providers, setProviders] = useState<AiProvider[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(!demo);
  const [quiet, setQuiet] = useState(false);
  useEffect(() => {
    if (!demo && !global) {
      let active = true;
      setLoading(true);
      setError("");
      listAiProviders(readSession().accessToken)
        .then((items) => {
          if (active) setProviders(items);
        })
        .catch((e) => {
          if (active) setError(e.message);
        })
        .finally(() => {
          if (active) setLoading(false);
        });
      return () => {
        active = false;
      };
    }
  }, [demo, global]);
  const selectedProvider = providers.find((p) => p.defaultEnabled);
  const managementUrl =
    global || tab === "Provider 策略"
      ? "/settings"
      : project && !demo
        ? `/projects/${project.id}`
        : "/projects";
  const icons = global
    ? [Sparkles, Moon, Database, Monitor]
    : [Folder, GitBranch, BookOpen, Sparkles, Settings];
  return (
    <div className="pf-settings-page">
      <div
        className="pf-settings-tabs"
        aria-label={global ? "全局设置分类" : "项目设置分类"}
      >
        {tabs.map((item, i) => {
          const Icon = icons[i];
          return (
            <button
              className={tab === item ? "active" : ""}
              aria-pressed={tab === item}
              key={item}
              onClick={() => setTab(item)}
            >
              <Icon size={16} />
              {item}
            </button>
          );
        })}
      </div>
      <div className="pf-settings-body">
        <div className="pf-settings-section-heading">
          <div>
            <h2>{tab}</h2>
            <p>
              {global
                ? "全局配置供项目统一使用，凭据集中管理。"
                : `${project?.name || "当前项目"} 的连接与使用范围。`}
            </p>
          </div>
          <span className="pf-chip running">
            {global ? "全局" : "仅此项目"}
          </span>
        </div>
        {error && (
          <p role="alert" className="pf-notice">
            {error}
          </p>
        )}
        {global && tab === "模型与 API" && <ProviderManager key={String(demo)} demo={demo} />}
        {!global && tab === "项目来源" && (
          <>
            <div className="pf-project-identity">
              <ProjectMark color={project?.color} />
              <div>
                <h3>{project?.name}</h3>
                <p>{project?.description}</p>
              </div>
            </div>
            <div className="pf-settings-row">
              <span className="pf-settings-icon">
                <FolderOpen size={24} />
              </span>
              <div>
                <h3>本地项目材料</h3>
                <p>
                  {demo
                    ? "本地目录 · 演示连接"
                    : "前往项目详情查看或绑定本地目录"}
                </p>
                <small>文档、源码与其他项目材料都可以作为来源。</small>
              </div>
              <Link className="pf-button" href={managementUrl}>
                管理来源
                <ArrowRight size={14} />
              </Link>
            </div>
            <div className="pf-settings-row">
              <span className="pf-settings-icon">
                <ArrowDownToLine size={23} />
              </span>
              <div>
                <h3>导入已有材料</h3>
                <p>添加文档、文本或导入 ZIP 项目。</p>
              </div>
              <Link className="pf-text-link" href="/projects">
                打开项目导入
                <ArrowRight size={15} />
              </Link>
            </div>
            <div className="pf-notice">
              <Circle size={13} />
              <p>
                添加材料不等于确认事实。当前状态与历程只在显式更新后读取新的结果。
              </p>
            </div>
          </>
        )}
        {!global && tab === "Git / GitHub" && (
          <>
            <div className="pf-connection-banner">
              <GitBranch size={30} />
              <div>
                <h3>Git / GitHub</h3>
                <p>本地证据与可选的远程协作来源</p>
              </div>
              <span className="pf-chip running">
                {demo
                  ? "演示连接"
                  : project?.repoUrl
                    ? "已配置地址"
                    : "未配置远程"}
              </span>
            </div>
            <div className="pf-fields">
              <div className="wide">
                <label>远程仓库</label>
                <p>
                  {demo
                    ? "示例组织 / Corporation-Agent"
                    : project?.repoUrl || "尚未配置"}
                </p>
              </div>
              <div>
                <label>连接检查</label>
                <p>{demo ? "示例状态" : "尚未进行实时检查"}</p>
              </div>
              <div>
                <label>分支协作</label>
                <p>后续提供</p>
              </div>
            </div>
            <p className="pf-settings-explanation">
              GitHub
              为可选来源。当前入口用于管理接入；项目当前状态只保留少量协作摘要。
            </p>
            <Link className="pf-button" href={managementUrl}>
              管理 Git / GitHub 接入
              <ExternalLink size={14} />
            </Link>
          </>
        )}
        {!global && tab === "Obsidian" && (
          <>
            <div className="pf-connection-banner">
              <BookOpen size={30} />
              <div>
                <h3>延续到你的知识空间</h3>
                <p>把项目历程投影为可阅读的长期笔记。</p>
              </div>
            </div>
            <div className="pf-settings-row">
              <div>
                <h3>Vault / 项目投影</h3>
                <p>
                  当前通过仓库内 CLI 配置与同步，界面尚不能读取实时同步状态。
                </p>
              </div>
              <span className="pf-chip attention">状态未读取</span>
            </div>
            <details className="pf-source-details">
              <summary>查看已有同步方式</summary>
              <p>
                使用仓库内 integrations/obsidian/projectflow_obsidian.py，传入已有 Vault、
                专用受管目录和项目 ID。先执行 validate 和 dry-run，核对后再执行 sync。
                默认 CORE 投影保留有界阅读内容，更多故事与主线需要显式选择。
                ProjectFlow 只更新受管内容，保留用户自己的笔记。
              </p>
            </details>
            <div className="pf-settings-note">
              <ShieldCheck size={20} />
              <p>
                Obsidian 是知识投影。同步不会改变项目事实，也不会自动调用模型。
              </p>
            </div>
          </>
        )}
        {!global && tab === "Provider 策略" && (
          <>
            <div className="pf-connection-banner">
              <Sparkles size={28} />
              <div>
                <h3>使用全局默认 Provider</h3>
                <p>当前分析统一使用全局默认模型，不保存项目级覆盖。</p>
              </div>
            </div>
            <div className="pf-settings-row">
              <div>
                <h3>
                  {demo
                    ? "OpenAI · 示例"
                    : loading ? "正在读取默认配置…" : error ? "默认配置暂不可读取" : selectedProvider?.name || "尚未配置默认 Provider"}
                </h3>
                <p>项目级绑定尚未开放；此处不会保存独立 API Key。</p>
              </div>
              <Link className="pf-button" href={href("settings")}>
                前往全局设置
                <ArrowRight size={14} />
              </Link>
            </div>
          </>
        )}
        {global && tab === "外观" && (
          <>
            <div className="pf-theme-swatch">
              <div>
                <ProjectMark />
                <i />
                <i />
                <i />
              </div>
              <span>
                <Check size={15} />
                午夜蓝
              </span>
            </div>
            <div className="pf-settings-row">
              <div>
                <h3>弱化背景装饰</h3>
                <p>降低环境图亮度，让长时间阅读更专注。</p>
              </div>
              <label className="pf-toggle">
                <input
                  type="checkbox"
                  aria-label="弱化背景装饰"
                  checked={quiet}
                  onChange={(e) => {
                    setQuiet(e.target.checked);
                    document
                      .querySelector(".pf-workspace")
                      ?.classList.toggle("pf-quiet", e.target.checked);
                  }}
                />
                <span />
              </label>
            </div>
            <p className="pf-page-footnote">
              外观调整仅作用于当前工作区。系统的减少动态效果偏好会自动生效。
            </p>
          </>
        )}
        {global && tab === "数据与备份" && (
          <>
            <div className="pf-connection-banner">
              <Database size={30} />
              <div>
                <h3>数据留在你的本地</h3>
                <p>项目资料、已确认事实与历史记录由现有本地核心管理。</p>
              </div>
            </div>
            <div className="pf-settings-row">
              <HardDrive size={25} />
              <div>
                <h3>数据目录与备份</h3>
                <p>备份与恢复使用已有运行工具，本轮不增加新的数据存储。</p>
              </div>
            </div>
            <details className="pf-source-details">
              <summary>查看运行工具</summary>
              <p>
                scripts/release/backup-projectflow.ps1
                提供已有备份入口。恢复前应停止运行服务，并使用
                restore-projectflow.ps1 的既有校验流程。
              </p>
            </details>
          </>
        )}
        {global && tab === "运行环境" && (
          <>
            <div className="pf-connection-banner">
              <Monitor size={30} />
              <div>
                <h3>本地运行 · 浏览器工作区</h3>
                <p>V4.0-C 工作区 · Java Core V3.10</p>
              </div>
              <span className="pf-chip running">桌面壳待定</span>
            </div>
            <div className="pf-settings-row">
              <Globe size={25} />
              <div>
                <h3>现有启动入口</h3>
                <p>继续使用仓库根目录 Start-ProjectFlow.bat 启动前后端。</p>
              </div>
              <Link href="/dashboard" className="pf-text-link">
                打开原版工作台
                <ArrowRight size={15} />
              </Link>
            </div>
          </>
        )}
        {!global && tab === "高级设置" && (
          <>
            <div className="pf-settings-row">
              <Settings size={25} />
              <div>
                <h3>项目管理</h3>
                <p>项目名称、描述与既有管理操作保留在项目详情。</p>
              </div>
              <Link className="pf-button" href={managementUrl}>
                打开项目详情
                <ExternalLink size={14} />
              </Link>
            </div>
            <div className="pf-settings-row">
              <Link2 size={24} />
              <div>
                <h3>Agent 交接与上下文</h3>
                <p>已确认事实、未确认项与材料范围共同构成交接内容。</p>
              </div>
              <Link href={href("handoff")} className="pf-text-link">
                打开交接
                <ArrowRight size={15} />
              </Link>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
