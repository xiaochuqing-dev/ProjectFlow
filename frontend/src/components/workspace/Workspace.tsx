"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import {
  Activity,
  ArrowRight,
  Bell,
  BookOpenText,
  Check,
  ChevronRight,
  CircleHelp,
  FileText,
  Folder,
  GitCommitHorizontal,
  Home,
  Layers,
  Link2,
  Menu,
  PanelRightOpen,
  Plus,
  RefreshCw,
  Search,
  Settings,
  Sparkles,
  X,
} from "lucide-react";
import { Button } from "@/components/ui/primitives";
import {
  getProject,
  getProjectCurrentState,
  getProjectHistoryOverview,
  getProjectAnalysisJob,
  listProjectAnalysisJobs,
  listProjectHistoryStories,
  listProjects,
  refreshProjectHistory,
  type ProjectAnalysisJob,
} from "@/lib/api";
import { readSession } from "@/lib/auth";
import {
  demoProjects,
  persistedProject,
  persistedStory,
  projectCard,
  viewLabels,
  workspaceHref,
  workspaceViews,
  type WorkspaceProject,
  type WorkspaceStory,
  type WorkspaceView,
} from "@/lib/workspace-preview";
import {
  CurrentPage,
  HandoffPage,
  LibraryPage,
  SettingsPage,
  ProjectMark,
} from "./WorkspacePages";
import { HistoryPage } from "./HistoryReader";
import { StoryDialog } from "./WorkspaceStoryDialog";

const navIcons = {
  projects: Folder,
  current: Activity,
  history: BookOpenText,
  handoff: Sparkles,
  "project-settings": Settings,
  settings: Settings,
};
const activeJob = (job: ProjectAnalysisJob) =>
  ["QUEUED", "RUNNING", "CANCEL_REQUESTED"].includes(job.status);

export function Workspace({ view }: { view: WorkspaceView }) {
  const query = useSearchParams();
  const demo = query.get("demo") === "1";
  const projectId = query.get("project") || (demo ? "corporation" : "");
  const [projects, setProjects] = useState<WorkspaceProject[]>(
    demo ? demoProjects : [],
  );
  const [project, setProject] = useState<WorkspaceProject | null>(
    demo ? (demoProjects.find((p) => p.id === projectId) ?? null) : null,
  );
  const [loading, setLoading] = useState(!demo);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);
  const [evidenceOpen, setEvidenceOpen] = useState(true);
  const [narrowEvidence, setNarrowEvidence] = useState(false);
  const [mobileNav, setMobileNav] = useState(false);
  const [selectedStory, setSelectedStory] = useState<WorkspaceStory | null>(
    null,
  );
  const [toast, setToast] = useState("");
  const [demoRefreshing, setDemoRefreshing] = useState(false);
  const [starting, setStarting] = useState(false);
  const [job, setJob] = useState<ProjectAnalysisJob | null>(null);
  const [search, setSearch] = useState("");
  const command = useRef<HTMLDialogElement>(null);
  const main = useRef<HTMLElement>(null);
  const shell = useRef<HTMLDivElement>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const startingRef = useRef(false);
  const scopeRef = useRef("");
  scopeRef.current = `${demo}:${projectId}`;
  const href = (next: WorkspaceView, id = projectId) =>
    workspaceHref(next, demo, id);

  useEffect(() => {
    let active = true;
    setError("");
    setJob(null);
    setSelectedStory(null);
    setMobileNav(false);
    setDemoRefreshing(false);
    setStarting(false);
    startingRef.current = false;
    if (timer.current) clearTimeout(timer.current);
    if (demo) {
      setProjects(demoProjects);
      setProject(demoProjects.find((p) => p.id === projectId) ?? null);
      setLoading(false);
      return;
    }
    setProject(null);
    setProjects([]);
    setLoading(true);
    const token = readSession().accessToken;
    const reads = [
      listProjects(token).then((items) => {
        if (active) setProjects(items.map(projectCard));
      }),
    ];
    if (projectId)
      reads.push(
        Promise.all([
          getProject(token, projectId),
          getProjectCurrentState(token, projectId),
          getProjectHistoryOverview(token, projectId),
          listProjectHistoryStories(token, projectId),
        ]).then(([p, current, history, stories]) => {
          if (!active) return;
          if (
            current.presentationRevision &&
            history.presentationRevision &&
            current.presentationRevision !== history.presentationRevision
          )
            throw new Error("项目状态在读取期间发生变化，请重新读取。");
          setProject({
            ...persistedProject(p, current, history),
            stories: stories.items
              .filter((s) => !s.hiddenByDefault && s.role !== "SUPPORTING")
              .map(persistedStory),
          });
        }),
      );
    Promise.all(reads)
      .catch((e) => {
        if (active)
          setError(e instanceof Error ? e.message : "暂时无法读取项目");
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    if (projectId)
      listProjectAnalysisJobs(token, projectId)
        .then((jobs) => {
          if (active)
            setJob(
              jobs.find(
                (item) =>
                  item.jobType === "PROJECT_HISTORY_REFRESH" && activeJob(item),
              ) ?? null,
            );
        })
        .catch(() => {
          /* The primary read exposes service errors; job discovery is optional. */
        });
    return () => {
      active = false;
    };
  }, [demo, projectId]);

  // An explicit refresh re-reads the same project without replacing its previous trustworthy view.
  useEffect(() => {
    if (!reload || demo || !projectId) return;
    let active = true;
    const token = readSession().accessToken;
    Promise.all([
      getProject(token, projectId),
      getProjectCurrentState(token, projectId),
      getProjectHistoryOverview(token, projectId),
      listProjectHistoryStories(token, projectId),
    ])
      .then(([p, current, history, stories]) => {
        if (!active) return;
        if (
          current.presentationRevision &&
          history.presentationRevision &&
          current.presentationRevision !== history.presentationRevision
        )
          throw new Error("项目状态在读取期间发生变化，请重新读取。");
        setProject({
          ...persistedProject(p, current, history),
          stories: stories.items
            .filter((s) => !s.hiddenByDefault && s.role !== "SUPPORTING")
            .map(persistedStory),
        });
        setError("");
      })
      .catch((e) => {
        if (active) setError(e.message);
      });
    return () => {
      active = false;
    };
  }, [reload, demo, projectId]);

  useEffect(() => {
    if (!job || !activeJob(job)) return;
    let active = true;
    const timeout = setTimeout(() => {
      getProjectAnalysisJob(readSession().accessToken, job.id)
        .then((next) => {
          if (!active) return;
          setJob(next);
          if (!activeJob(next)) {
            setReload((n) => n + 1);
            setToast(
              next.status === "SUCCEEDED"
                ? "项目状态已更新"
                : next.status === "SUCCEEDED_WITH_WARNINGS"
                  ? "更新完成，部分信息需要注意"
                  : next.errorMessage || "更新未完成，已保留上次可确认结果",
            );
          }
        })
        .catch((e) => {
          if (active) {
            setError(e.message);
            setJob(null);
          }
        });
    }, 2000);
    return () => {
      active = false;
      clearTimeout(timeout);
    };
  }, [job]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        command.current?.showModal();
      }
      if (event.key === "Escape") {
        setNarrowEvidence(false);
        setMobileNav(false);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => {
      window.removeEventListener("keydown", onKey);
      if (timer.current) clearTimeout(timer.current);
    };
  }, []);
  useEffect(() => {
    main.current?.scrollTo(0, 0);
    setMobileNav(false);
    setNarrowEvidence(false);
  }, [view]);
  useEffect(() => {
    const onResize = () => {
      if (window.innerWidth > 620) setMobileNav(false);
      if (window.innerWidth > 1199) setNarrowEvidence(false);
    };
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, []);
  useEffect(() => {
    if (!mobileNav && !narrowEvidence) return;
    const root = shell.current;
    const drawer = root?.querySelector<HTMLElement>(mobileNav ? ".pf-sidebar" : ".pf-evidence");
    if (!root || !drawer) return;
    const previous = document.activeElement as HTMLElement | null;
    const siblings = Array.from(root.children).filter((element) => element !== drawer && element instanceof HTMLElement) as HTMLElement[];
    const inert = siblings.map((element) => element.inert);
    siblings.forEach((element) => { element.inert = true; });
    const focusables = () => Array.from(drawer.querySelectorAll<HTMLElement>("a[href],button:not(:disabled),input:not(:disabled),summary,[tabindex='0']"))
      .filter((element) => element.getClientRects().length > 0);
    focusables()[0]?.focus();
    const onTab = (event: KeyboardEvent) => {
      if (event.key !== "Tab") return;
      const items = focusables();
      if (event.shiftKey && document.activeElement === items[0]) { event.preventDefault(); items.at(-1)?.focus(); }
      else if (!event.shiftKey && document.activeElement === items.at(-1)) { event.preventDefault(); items[0]?.focus(); }
    };
    drawer.addEventListener("keydown", onTab);
    return () => {
      drawer.removeEventListener("keydown", onTab);
      siblings.forEach((element, index) => { element.inert = inert[index]; });
      if (previous?.isConnected) previous.focus();
    };
  }, [mobileNav, narrowEvidence]);
  useEffect(() => {
    if (!toast) return;
    const id = setTimeout(() => setToast(""), 6500);
    return () => clearTimeout(id);
  }, [toast]);

  async function refresh() {
    if (startingRef.current || demoRefreshing || (job && activeJob(job)))
      return;
    if (demo) {
      setDemoRefreshing(true);
      timer.current = setTimeout(() => {
        setDemoRefreshing(false);
        setToast("示例状态已重载，未扫描项目或调用模型");
      }, 1400);
      return;
    }
    if (!projectId) return;
    const requestedScope = scopeRef.current;
    startingRef.current = true;
    setStarting(true);
    try {
      const next = await refreshProjectHistory(
        readSession().accessToken,
        projectId,
      );
      if (scopeRef.current === requestedScope) {
        setJob(next);
        if (activeJob(next)) setToast("正在后台更新，当前结果继续保留");
        else {
          setReload((n) => n + 1);
          setToast(
            next.status.startsWith("SUCCEEDED")
              ? "已读取这次更新的保存结果"
              : next.errorMessage || "更新未完成，已保留上次结果",
          );
        }
      }
    } catch (e) {
      if (scopeRef.current === requestedScope)
        setToast(e instanceof Error ? e.message : "更新暂未启动");
    } finally {
      if (scopeRef.current === requestedScope) {
        startingRef.current = false;
        setStarting(false);
      }
    }
  }
  const refreshing =
    starting || demoRefreshing || Boolean(job && activeJob(job));
  const showStory = (story: WorkspaceStory) => {
    setNarrowEvidence(false);
    setSelectedStory(story);
    setEvidenceOpen(true);
  };

  return (
    <div
      ref={shell}
      className={`pf-workspace ${!evidenceOpen ? "pf-detail-closed" : ""} ${mobileNav ? "pf-nav-open" : ""} ${narrowEvidence ? "pf-narrow-evidence-open" : ""}`}
    >
      <a className="pf-skip" href="#workspace-main">
        跳到主要内容
      </a>
      <aside className="pf-sidebar" aria-label="侧边栏" role={mobileNav ? "dialog" : undefined} aria-modal={mobileNav || undefined}>
        {mobileNav && <button className="pf-icon-button pf-nav-close" aria-label="收起导航" onClick={() => setMobileNav(false)}><X size={18} /></button>}
        <Link
          className="pf-brand"
          href={href("projects")}
          aria-label="ProjectFlow 项目库"
        >
          <ProjectMark />
          <span>
            <strong>ProjectFlow</strong>
            <small>让项目的故事持续向前</small>
          </span>
        </Link>
        <nav aria-label="主导航">
          {workspaceViews
            .filter((v) => v !== "settings")
            .map((item) => {
              const Icon = navIcons[item];
              return (
                <Link
                  key={item}
                  href={href(item)}
                  className={`pf-nav-item ${item === view ? "active" : ""}`}
                  aria-current={item === view ? "page" : undefined}
                >
                  <Icon size={18} />
                  <span>{viewLabels[item]}</span>
                </Link>
              );
            })}
        </nav>
        <div className="pf-recents-title">
          <span>最近项目</span>
          <Link href="/projects" aria-label="添加项目">
            <Plus size={15} />
          </Link>
        </div>
        <div className="pf-recents">
          {projects.slice(0, 5).map((item) => (
            <Link
              key={item.id}
              className={`pf-recent ${item.id === projectId ? "selected" : ""}`}
              href={href("current", item.id)}
            >
              <ProjectMark color={item.color} small />
              <span>
                <strong>{item.name}</strong>
                <small>{item.description}</small>
              </span>
              {item.id === projectId && <i />}
            </Link>
          ))}
        </div>
        <div
          className="pf-sidebar-landscape"
          role="img"
          aria-label="午夜蓝山脉与青色河流"
        />
        <p className="pf-sidebar-motto">
          Good ideas.
          <br />
          Build a longer tomorrow.
        </p>
        <div className="pf-account">
          <span className="pf-avatar">P</span>
          <span>
            <strong>{demo ? "ProjectFlow Team" : readSession().user.username}</strong>
            <small>专注 · 持续 · 更远</small>
          </span>
          <Link
            href={href("settings")}
            aria-label="全局设置"
            aria-current={view === "settings" ? "page" : undefined}
          >
            <Settings size={16} />
          </Link>
        </div>
      </aside>

      <header className="pf-topbar">
        <button
          className="pf-icon-button pf-mobile-menu"
          aria-label="展开导航"
          aria-expanded={mobileNav}
          onClick={() => { setMobileNav(!mobileNav); setNarrowEvidence(false); }}
        >
          <Menu size={18} />
        </button>
        <div className="pf-breadcrumb">
          <Link href={href("projects")} aria-label="项目库首页">
            <Home size={15} />
          </Link>
          <span>/</span>
          <Link href={href("projects")}>项目库</Link>
          {!["projects", "settings"].includes(view) && (
            <>
              <span>/</span>
              <span>{project?.name || "选择项目"}</span>
            </>
          )}
          <span>/</span>
          <span>{viewLabels[view]}</span>
        </div>
        <button
          className="pf-search-entry"
          onClick={() => command.current?.showModal()}
        >
          <Search size={14} />
          <span>搜索项目、页面…</span>
          <kbd>Ctrl + K</kbd>
        </button>
        {view === "current" ? (
          <Button
            className="pf-button pf-primary"
            onClick={refresh}
            disabled={refreshing || (!demo && !projectId)}
          >
            <RefreshCw size={16} className={refreshing ? "pf-spin" : ""} />
            {refreshing ? "正在更新状态" : "更新项目状态"}
          </Button>
        ) : (
          <Link
            className="pf-button pf-primary"
            href={projectId ? href("current") : href("projects")}
          >
            <Activity size={16} />
            {projectId ? "返回当前状态" : "打开项目库"}
          </Link>
        )}
        <button
          className="pf-icon-button pf-notifications"
          aria-label="查看需要注意的事项"
          onClick={() => {
            setEvidenceOpen(true);
            setToast(
              project
                ? [...project.attention, ...project.unknowns].join("；") ||
                    "当前没有需要注意的事项"
                : "选择项目后查看需要注意的事项",
            );
          }}
        >
          <Bell size={17} />
          {!!project?.attention.length && <i />}
        </button>
        <Link
          className="pf-avatar pf-top-avatar"
          href={href("settings")}
          aria-label="打开全局设置"
        >
          P
        </Link>
      </header>

      <main className="pf-main" id="workspace-main" ref={main}>
        <div className="pf-page-heading">
          <div>
            <h1>{viewLabels[view]}</h1>
            <p>
              {
                {
                  projects: "每一个项目，都有值得延续的故事。",
                  current: "把握项目的现在，让下一步更清晰。",
                  history: "从最初的想法，到每一次真实的向前。",
                  handoff: "让下一位 Agent，从你已经走到的地方继续。",
                  "project-settings": "管理这个项目的来源、连接与使用策略。",
                  settings: "让 ProjectFlow 按你的方式运行。",
                }[view]
              }
            </p>
          </div>
          <div className="pf-heading-meta">
            <span>{demo ? "设计预览 · 示例数据" : "本地项目 · V4 工作区"}</span>
            <details className="pf-view-menu">
              <summary aria-label="工作区选项">···</summary>
              <div>
                <Link href={workspaceHref("current", true, "corporation")}>
                  查看设计示例
                </Link>
                <Link href="/workspace/projects">打开真实项目</Link>
                <Link href="/dashboard">兼容工作台</Link>
              </div>
            </details>
          </div>
        </div>
        {refreshing && (
          <div role="status" className="pf-refresh-status">
            <RefreshCw size={14} className="pf-spin" />
            {demo
              ? "正在重新读取示例状态，现有内容继续保留"
              : job?.stageMessage || "正在后台更新项目状态，现有内容继续保留"}
          </div>
        )}
        {error && (
          <div className="pf-error" role="alert">
            <CircleHelp size={19} />
            <div>
              <strong>暂时无法读取完整项目</strong>
              <p>{error}</p>
              <button onClick={() => window.location.reload()}>重新读取</button>
            </div>
          </div>
        )}
        {loading && (
          <div className="pf-loading" role="status">
            <div />
            <div />
            <div />
            <span>正在读取已保存的项目内容…</span>
          </div>
        )}
        {!loading && view === "projects" && (
          <LibraryPage
            projects={projects}
            demo={demo}
            onOpen={(id) => href("current", id)}
          />
        )}
        {!loading && view === "settings" && (
          <SettingsPage global demo={demo} project={project} href={href} />
        )}
        {!loading &&
          !["projects", "settings"].includes(view) &&
          (!project ? (
            <section className="pf-empty">
              <Folder size={38} />
              <h2>选择一个项目，继续它的故事</h2>
              <p>可从项目库打开已有项目，也可以添加本地目录或导入 ZIP。</p>
              <Link className="pf-button pf-primary" href={href("projects")}>
                打开项目库
                <ArrowRight size={15} />
              </Link>
            </section>
          ) : (
            <>
              {view === "current" && (
                <CurrentPage
                  project={project}
                  demo={demo}
                  href={href}
                  onStory={showStory}
                />
              )}
              {view === "history" && (
                <HistoryPage
                  key={project.id}
                  project={project}
                  demo={demo}
                  onStory={showStory}
                />
              )}
              {view === "handoff" && (
                <HandoffPage
                  key={`${project.id}-${reload}`}
                  project={project}
                  demo={demo}
                  onToast={setToast}
                />
              )}
              {view === "project-settings" && (
                <SettingsPage
                  global={false}
                  demo={demo}
                  project={project}
                  href={href}
                />
              )}
            </>
          ))}
      </main>
      {evidenceOpen ? (
        <EvidencePanel
          project={project}
          demo={demo}
          onClose={() => {
            setEvidenceOpen(false);
            setNarrowEvidence(false);
          }}
          onStory={showStory}
          view={view}
          modal={narrowEvidence}
        />
      ) : (
        <button
          className="pf-reopen pf-button"
          onClick={() => {
            setEvidenceOpen(true);
            setNarrowEvidence(true);
          }}
          aria-label="打开工程证据面板"
        >
          <PanelRightOpen size={17} />
          工程证据
        </button>
      )}
      {evidenceOpen && (
        <button
          className="pf-narrow-evidence-toggle pf-button"
          aria-label={narrowEvidence ? "收起工程证据" : "展开工程证据"}
          aria-expanded={narrowEvidence}
          onClick={() => {
            setNarrowEvidence(!narrowEvidence);
            setMobileNav(false);
          }}
        >
          <PanelRightOpen size={17} />
          {narrowEvidence ? "收起证据" : "工程证据"}
        </button>
      )}
      {toast && (
        <div className="pf-toast" role="status">
          <Check size={16} />
          <span>{toast}</span>
          <button aria-label="关闭提示" onClick={() => setToast("")}>
            <X size={14} />
          </button>
        </div>
      )}
      <dialog
        className="pf-dialog pf-command"
        ref={command}
        aria-label="搜索项目与页面"
      >
        <div className="pf-command-search">
          <Search size={19} />
          <input
            aria-label="搜索项目和页面"
            placeholder="搜索项目或页面名称…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          <button
            className="pf-icon-button"
            aria-label="关闭搜索"
            onClick={() => command.current?.close()}
          >
            <X size={19} />
          </button>
        </div>
        <div className="pf-command-results">
          <p>页面</p>
          {workspaceViews
            .filter((v) =>
              viewLabels[v].toLowerCase().includes(search.toLowerCase()),
            )
            .map((v) => (
              <Link
                href={href(v)}
                key={v}
                onClick={() => command.current?.close()}
              >
                <span>{viewLabels[v]}</span>
                <ArrowRight size={16} />
              </Link>
            ))}
          <p>项目</p>
          {projects
            .filter((p) =>
              (p.name + p.description)
                .toLowerCase()
                .includes(search.toLowerCase()),
            )
            .map((p) => (
              <Link
                key={p.id}
                href={href("current", p.id)}
                onClick={() => command.current?.close()}
              >
                <span>{p.name}</span>
                <small>{p.description}</small>
              </Link>
            ))}
          {search &&
            !workspaceViews.some((v) =>
              viewLabels[v].toLowerCase().includes(search.toLowerCase()),
            ) &&
            !projects.some((p) =>
              (p.name + p.description)
                .toLowerCase()
                .includes(search.toLowerCase()),
            ) && <div className="pf-no-results">未找到匹配结果</div>}
        </div>
        <footer>
          <kbd>Tab</kbd> 选择 <kbd>Enter</kbd> 打开 <kbd>Esc</kbd> 关闭
        </footer>
      </dialog>
      {selectedStory && project && (
        <StoryDialog
          key={`${project.id}-${selectedStory.id}`}
          story={selectedStory}
          project={project}
          demo={demo}
          onClose={() => setSelectedStory(null)}
        />
      )}
    </div>
  );
}

function EvidencePanel({
  project,
  demo,
  onClose,
  onStory,
  view,
  modal,
}: {
  project: WorkspaceProject | null;
  demo: boolean;
  onClose: () => void;
  onStory: (story: WorkspaceStory) => void;
  view: WorkspaceView;
  modal: boolean;
}) {
  const [tab, setTab] = useState("最新提交");
  return (
    <aside className="pf-evidence" aria-label="工程详情与证据" role={modal ? "dialog" : undefined} aria-modal={modal || undefined}>
      <div className="pf-evidence-title">
        <Link2 size={18} />
        <h2>{view === "settings" ? "配置说明 / 边界" : "工程详情 / 证据"}</h2>
        <button
          className="pf-icon-button"
          onClick={onClose}
          aria-label="关闭工程证据面板"
        >
          <X size={16} />
        </button>
      </div>
      {view !== "settings" && (
        <div className="pf-evidence-tabs" aria-label="证据分类">
          {["最新提交", "相关文档", "关联讨论"].map((item) => (
            <button
              key={item}
              aria-pressed={tab === item}
              className={tab === item ? "active" : ""}
              onClick={() => setTab(item)}
            >
              {item}
            </button>
          ))}
        </div>
      )}
      <div className="pf-evidence-content">
        {view === "settings" ? (
          <div className="pf-evidence-note">
            <Settings />
            <h3>全局模型配置</h3>
            <p>API Key、Endpoint、Protocol 与默认模型统一在全局管理。</p>
            <p>项目设置只显示使用策略和配置入口。</p>
            <p>可在中央区域添加、编辑、测试和删除 Provider。未填写新 Key 会保留已有凭据。</p>
          </div>
        ) : tab === "最新提交" ? (
          <>
            {project?.stories.length ? (
              project.stories.slice(0, 4).map((story, index) => (
                <button
                  className="pf-evidence-card"
                  key={story.id}
                  onClick={() => onStory(story)}
                >
                  <GitCommitHorizontal size={21} className="pf-git-icon" />
                  <span className="pf-evidence-card-body">
                    <span className="pf-evidence-card-heading">
                      <strong>
                        {demo ? "Commit" : "变化记录"}{" "}
                        <em>
                          {demo
                            ? story.evidence
                            : String(index + 1).padStart(2, "0")}
                        </em>
                      </strong>
                      <span className={`pf-chip ${story.status}`}>
                        {demo
                          ? ["已确认", "已确认", "需关注", "存在冲突"][index]
                          : "查看证据"}
                      </span>
                    </span>
                    <span className="pf-evidence-subject">
                      {demo
                        ? [
                            "feat: 新增多 Agent 调度策略",
                            "feat: 接入企业知识库模块",
                            "refactor: 优化权限模型结构",
                            "fix: 修复长对话上下文丢失",
                          ][index]
                        : story.title}
                    </span>
                    {demo && (
                      <small>
                        作者　
                        {
                          ["Alex Chen", "Li Wen", "Zhang Min", "DevOps Bot"][
                            index
                          ]
                        }
                      </small>
                    )}
                    <small>
                      时间　
                      {demo
                        ? `2024-12-${10 - index} ${["11:24", "18:03", "16:20", "10:11"][index]}`
                        : story.date}
                    </small>
                  </span>
                  <ChevronRight size={14} />
                </button>
              ))
            ) : (
              <p className="pf-evidence-empty">
                {project
                  ? "尚无可展示的工程证据。连接材料并更新状态后，可在这里进一步核查。"
                  : "打开项目后，在这里查看它的工程证据与来源。"}
              </p>
            )}
            {!!project?.stories.length && (
              <Link
                className="pf-button pf-evidence-more"
                href={
                  workspaceHref("history", demo, project.id)
                }
              >
                <Layers size={14} />
                查看更多证据
                <ArrowRight size={15} />
              </Link>
            )}
          </>
        ) : demo && project?.id === "corporation" && tab === "相关文档" ? (
          ["协作框架设计说明", "企业知识库接入指南", "内部验证记录"].map(
            (title, i) => (
              <button
                className="pf-document-row"
                key={title}
                onClick={() => onStory(project.stories[i])}
              >
                <FileText size={21} />
                <span>
                  {title}
                  <small>示例材料 · 点击查看关联变化</small>
                </span>
                <ChevronRight size={14} />
              </button>
            ),
          )
        ) : (
          <div className="pf-evidence-note">
            <FileText size={26} />
            <h3>
              {tab === "关联讨论"
                ? "尚未接入讨论来源"
                : "从变化记录查看来源材料"}
            </h3>
            <p>
              {tab === "关联讨论"
                ? "讨论入口已保留。接入真实讨论来源后，在这里展示关联关系。"
                : "打开变化记录后，可继续查看对应的文档、事件与证据。"}
            </p>
          </div>
        )}
      </div>
      <div className="pf-evidence-footer">
        <span>“</span>
        <p>
          真实的工程证据
          <br />
          让项目的历史可追溯，让未来更可控。
        </p>
        <i />
        <small>
          From Evidence to a More
          <br />
          Reliable Tomorrow.
        </small>
        {demo && <label>此处为设计示例，非真实项目记录</label>}
      </div>
    </aside>
  );
}
