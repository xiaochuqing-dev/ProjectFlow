"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Activity, BookOpenText, CheckCircle2, LayoutDashboard, LogOut, Sparkles } from "lucide-react";
import { clearSession, readSession, type AuthUser } from "@/lib/auth";

const navItems = ["总览", "项目", "任务", "开发日志", "导入", "AI 复盘"];

export default function DashboardPage() {
  const router = useRouter();
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
          {navItems.map((item, index) => (
            <a
              className={`flex items-center gap-3 rounded-lg px-3 py-2 text-sm ${
                index === 0
                  ? "bg-blue-50 font-medium text-brand"
                  : "text-slate-600 hover:bg-slate-50"
              }`}
              href="#"
              key={item}
            >
              <LayoutDashboard className="h-4 w-4" />
              {item}
            </a>
          ))}
        </nav>
      </aside>

      <section className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-16 items-center justify-between border-b border-line bg-white px-8">
          <div>
            <p className="text-sm text-muted">你好，{user.username}</p>
            <h1 className="text-lg font-semibold">工作台总览</h1>
          </div>
          <button
            className="flex items-center gap-2 rounded-full border border-line bg-slate-50 px-4 py-2 text-sm text-slate-600 hover:bg-slate-100"
            onClick={handleLogout}
            type="button"
          >
            <LogOut className="h-4 w-4" />
            退出登录
          </button>
        </header>

        <div className="grid gap-6 p-8 lg:grid-cols-[1.2fr_0.8fr]">
          <section className="rounded-lg border border-line bg-white p-6 shadow-panel">
            <div className="mb-6 flex items-center gap-3">
              <div className="grid h-11 w-11 place-items-center rounded-xl bg-blue-50 text-brand">
                <Activity className="h-5 w-5" />
              </div>
              <div>
                <h2 className="text-xl font-semibold">认证状态</h2>
                <p className="text-sm text-muted">当前页面已具备客户端会话保护和退出流程。</p>
              </div>
            </div>
            <div className="grid gap-3 sm:grid-cols-3">
              {[
                ["账号", user.email, CheckCircle2],
                ["后端", "/api/auth/me", BookOpenText],
                ["AI", "Mock Provider", Sparkles],
              ].map(([label, value, Icon]) => (
                <div className="rounded-lg border border-line bg-slate-50 p-4" key={label as string}>
                  <Icon className="mb-4 h-5 w-5 text-brand" />
                  <p className="text-sm text-muted">{label as string}</p>
                  <p className="mt-1 truncate font-semibold">{value as string}</p>
                </div>
              ))}
            </div>
          </section>

          <section className="rounded-lg border border-line bg-white p-6 shadow-panel">
            <h2 className="text-base font-semibold">第 2 轮范围</h2>
            <ul className="mt-4 space-y-3 text-sm text-slate-600">
              <li>注册、登录、JWT 生成与当前用户校验。</li>
              <li>登录页使用蓝色背景图和右侧专业卡片。</li>
              <li>登录后页面保留左侧全局导航和顶部页面栏。</li>
              <li>下一轮进入项目空间 CRUD。</li>
            </ul>
          </section>
        </div>
      </section>
    </main>
  );
}
