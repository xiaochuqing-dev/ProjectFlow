"use client";

import { ReactNode } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { BookOpenText, DatabaseZap, History, LayoutDashboard, Settings, Sparkles } from "lucide-react";

type AppShellProps = {
  title: string;
  eyebrow?: string;
  children: ReactNode;
  actions?: ReactNode;
};

const navItems = [
  { label: "工作台", href: "/dashboard", icon: LayoutDashboard },
  { label: "项目记录", href: "/sediment-review", icon: History },
  { label: "项目记忆", href: "/project-intelligence", icon: DatabaseZap },
  { label: "项目历程", href: "/timeline", icon: BookOpenText },
  { label: "成果输出", href: "/ai-review", icon: Sparkles },
  { label: "设置", href: "/settings", icon: Settings },
];

export function AppShell({ title, eyebrow, actions, children }: AppShellProps) {
  const pathname = usePathname();

  return (
    <main className="flex min-h-screen bg-surface text-ink">
      <aside className="flex w-64 shrink-0 flex-col border-r border-line bg-elevated px-3 py-5">
        <div className="mb-7 flex items-center gap-3 px-2">
          <div className="grid h-10 w-10 place-items-center rounded-field bg-brand text-sm font-bold text-white shadow-card">
            PF
          </div>
          <div>
            <p className="text-base font-semibold text-ink">ProjectFlow <span className="text-xs font-medium text-muted">V3.7.2</span></p>
            <p className="text-xs text-muted">通用证据理解与项目演进</p>
          </div>
        </div>
        <nav className="space-y-1">
          {navItems.map((item) => {
            const Icon = item.icon;
            const active = pathname === item.href || pathname.startsWith(`${item.href}/`) || (item.href === "/project-intelligence" && pathname.includes("/files"));
            return (
              <Link
                className={`group relative flex items-center gap-3 rounded-field px-3 py-2 text-sm transition-colors ${
                  active
                    ? "bg-brand-soft font-medium text-brand"
                    : "text-body hover:bg-surfaceAlt hover:text-ink"
                }`}
                href={item.href}
                key={item.label}
              >
                {active ? (
                  <span className="absolute left-0 top-1/2 h-5 w-[3px] -translate-y-1/2 rounded-full bg-brand" />
                ) : null}
                <Icon className={`h-4 w-4 shrink-0 ${active ? "text-brand" : "text-muted group-hover:text-body"}`} />
                {item.label}
              </Link>
            );
          })}
        </nav>
      </aside>

      <section className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-16 items-center justify-between border-b border-line bg-elevated px-8">
          <div className="min-w-0">
            <p className="truncate text-xs text-muted">{eyebrow ?? "本地工作区"}</p>
            <h1 className="text-lg font-semibold text-ink">{title}</h1>
          </div>
          <div className="flex items-center gap-3">
            {actions}
          </div>
        </header>
        {children}
      </section>
    </main>
  );
}
