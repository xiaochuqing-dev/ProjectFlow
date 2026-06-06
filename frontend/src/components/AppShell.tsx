"use client";

import { ReactNode, useEffect, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { BookOpenText, LayoutDashboard, LogOut, Settings, Sparkles, SquareKanban } from "lucide-react";
import { clearSession, readSession, type AuthUser } from "@/lib/auth";

type AppShellProps = {
  title: string;
  eyebrow?: string;
  children: ReactNode;
  actions?: ReactNode;
};

const navItems = [
  { label: "项目管理", href: "/dashboard", icon: LayoutDashboard },
  { label: "任务", href: "/tasks", icon: SquareKanban },
  { label: "开发日志", href: "/dev-logs", icon: BookOpenText },
  { label: "成果输出", href: "/ai-review", icon: Sparkles },
  { label: "个人设置", href: "/settings", icon: Settings },
];

export function AppShell({ title, eyebrow, actions, children }: AppShellProps) {
  const router = useRouter();
  const pathname = usePathname();
  const [user, setUser] = useState<AuthUser | null>(null);

  useEffect(() => {
    const session = readSession();
    if (!session) {
      router.replace("/login");
      return;
    }
    setUser(session.user);
  }, [router]);

  function handleLogout() {
    clearSession();
    router.replace("/login");
  }

  if (!user) {
    return null;
  }

  return (
    <main className="flex min-h-screen bg-surface text-ink">
      <aside className="flex w-64 shrink-0 flex-col border-r border-line bg-white px-4 py-5">
        <div className="mb-8 flex items-center gap-3 px-2">
          <div className="grid h-10 w-10 place-items-center rounded-xl bg-brand text-sm font-bold text-white">
            PF
          </div>
          <div>
            <p className="text-base font-semibold">ProjectFlow</p>
            <p className="text-xs text-muted">项目过程资产化</p>
          </div>
        </div>
        <nav className="space-y-1">
          {navItems.map((item) => {
            const Icon = item.icon;
            const active = pathname === item.href;
            return (
              <Link
                className={`flex items-center gap-3 rounded-lg px-3 py-2 text-sm ${
                  active
                    ? "bg-blue-50 font-medium text-brand"
                    : "text-slate-600 hover:bg-slate-50"
                }`}
                href={item.href}
                key={item.label}
              >
                <Icon className="h-4 w-4" />
                {item.label}
              </Link>
            );
          })}
        </nav>
      </aside>

      <section className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-16 items-center justify-between border-b border-line bg-white px-8">
          <div>
            <p className="text-sm text-muted">{eyebrow ?? `你好，${user.username}`}</p>
            <h1 className="text-lg font-semibold">{title}</h1>
          </div>
          <div className="flex items-center gap-3">
            {actions}
            <button
              className="flex items-center gap-2 rounded-full border border-line bg-slate-50 px-4 py-2 text-sm text-slate-600 hover:bg-slate-100"
              onClick={handleLogout}
              type="button"
            >
              <LogOut className="h-4 w-4" />
              退出登录
            </button>
          </div>
        </header>
        {children}
      </section>
    </main>
  );
}
