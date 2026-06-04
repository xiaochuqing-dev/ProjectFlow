import { Activity, BookOpenText, CheckCircle2, Sparkles } from "lucide-react";
import { AppShell } from "@/components/AppShell";

export default function DashboardPage() {
  return (
    <AppShell title="工作台总览">
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
                ["账号", "已登录", CheckCircle2],
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
    </AppShell>
  );
}
