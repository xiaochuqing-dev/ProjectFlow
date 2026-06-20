import type { ReactNode } from "react";
import type { Project } from "@/lib/api";
import { rememberSelectedProjectId } from "@/lib/project-selection";

/* ------------------------------------------------------------------ */
/* PageContainer                                                       */
/* ------------------------------------------------------------------ */

/**
 * 统一页面包裹。替代此前各页重复的
 * `min-h-[calc(100vh-4rem)] bg-surface p-8`。
 */
export function PageContainer({ children, className = "" }: { children: ReactNode; className?: string }) {
  return (
    <div className={`min-h-[calc(100vh-4rem)] bg-surface p-6 md:p-8 ${className}`.trim()}>
      {children}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* ProjectContextBar                                                   */
/* ------------------------------------------------------------------ */

type ProjectContextBarProps = {
  projects: Project[];
  selectedProjectId: string;
  onSelect: (projectId: string) => void;
  /** 项目下拉右侧的状态/指标槽位 */
  leadingExtras?: ReactNode;
  /** 右侧操作槽位 */
  actions?: ReactNode;
  /** 项目为空时下拉的占位文案 */
  placeholder?: string;
};

/**
 * 顶部项目上下文条。
 *
 * 替代此前在 tasks / dev-logs / project-intelligence 等页面里逐字复制的
 * 项目 `<select>` + 包裹栏。下拉内部自动调用 rememberSelectedProjectId。
 */
export function ProjectContextBar({
  projects,
  selectedProjectId,
  onSelect,
  leadingExtras,
  actions,
  placeholder = "选择项目",
}: ProjectContextBarProps) {
  return (
    <section className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-card border border-line bg-elevated p-4 shadow-card">
      <div className="flex flex-wrap items-center gap-3">
        <select
          className="h-10 min-w-64 rounded-field border border-line bg-elevated px-3 text-sm text-ink outline-none transition focus:border-brand focus-visible:shadow-focus"
          disabled={projects.length === 0}
          onChange={(event) => {
            rememberSelectedProjectId(event.target.value);
            onSelect(event.target.value);
          }}
          value={selectedProjectId}
        >
          {projects.length === 0 ? <option value="">{placeholder}</option> : null}
          {projects.map((project) => (
            <option key={project.id} value={project.id}>
              {project.name}
            </option>
          ))}
        </select>
        {leadingExtras}
      </div>
      {actions ? <div className="flex items-center gap-2">{actions}</div> : null}
    </section>
  );
}
