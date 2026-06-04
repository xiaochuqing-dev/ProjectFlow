import { Activity, BookOpenText, CheckCircle2, LayoutDashboard, Sparkles } from "lucide-react";

const navItems = ["总览", "项目", "任务", "开发日志", "导入", "AI 复盘"];

export default function Home() {
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
            <p className="text-sm text-muted">第 1 轮健康检查</p>
            <h1 className="text-lg font-semibold">工程骨架已就绪</h1>
          </div>
          <div className="rounded-full border border-line bg-slate-50 px-4 py-2 text-sm text-slate-600">
            Next.js Frontend
          </div>
        </header>

        <div className="grid gap-6 p-8 lg:grid-cols-[1.2fr_0.8fr]">
          <section className="rounded-lg border border-line bg-white p-6 shadow-panel">
            <div className="mb-6 flex items-center gap-3">
              <div className="grid h-11 w-11 place-items-center rounded-xl bg-blue-50 text-brand">
                <Activity className="h-5 w-5" />
              </div>
              <div>
                <h2 className="text-xl font-semibold">系统状态</h2>
                <p className="text-sm text-muted">前端页面用于验证 Next.js、Tailwind 与中文界面基线。</p>
              </div>
            </div>
            <div className="grid gap-3 sm:grid-cols-3">
              {[
                ["前端", "运行正常", CheckCircle2],
                ["后端", "/api/health", BookOpenText],
                ["AI", "Mock Provider", Sparkles],
              ].map(([label, value, Icon]) => (
                <div className="rounded-lg border border-line bg-slate-50 p-4" key={label as string}>
                  <Icon className="mb-4 h-5 w-5 text-brand" />
                  <p className="text-sm text-muted">{label as string}</p>
                  <p className="mt-1 font-semibold">{value as string}</p>
                </div>
              ))}
            </div>
          </section>

          <section className="rounded-lg border border-line bg-white p-6 shadow-panel">
            <h2 className="text-base font-semibold">设计约束</h2>
            <ul className="mt-4 space-y-3 text-sm text-slate-600">
              <li>左侧全局导航在登录后页面常驻。</li>
              <li>顶部导航按页面场景承载筛选、标签和主要操作。</li>
              <li>产品界面中文为主，英文技术名词自然保留。</li>
              <li>登录页后续使用本地背景图，并在右侧空白区放置专业卡片。</li>
            </ul>
          </section>
        </div>
      </section>
    </main>
  );
}
