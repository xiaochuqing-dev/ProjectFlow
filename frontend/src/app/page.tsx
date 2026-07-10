import Link from "next/link";
import { ArrowRight, FolderGit2, ScanSearch, Sparkles } from "lucide-react";

const highlights = [
  { icon: FolderGit2, title: "接入项目", detail: "导入或绑定本地项目目录" },
  { icon: ScanSearch, title: "整理变更", detail: "基于 Git 和本地证据回顾开发结果" },
  { icon: Sparkles, title: "沉淀成果", detail: "确认后形成可复用的项目档案" },
];

export default function Home() {
  return (
    <main className="min-h-screen bg-[#020817] px-6 py-8 text-white md:px-10 lg:px-16">
      <section className="mx-auto flex min-h-[calc(100vh-4rem)] max-w-6xl flex-col justify-center">
        <div className="mb-12 flex items-center gap-3">
          <div className="grid h-10 w-10 place-items-center rounded-xl bg-brand text-sm font-bold text-white">PF</div>
          <div>
            <p className="text-base font-semibold">ProjectFlow</p>
            <p className="text-xs text-blue-100/60">本地开发过程整理工具</p>
          </div>
        </div>

        <div className="max-w-3xl">
          <p className="mb-4 text-sm font-medium tracking-[0.2em] text-cyan-200">LOCAL-FIRST WORKBENCH</p>
          <h1 className="text-4xl font-semibold leading-tight md:text-6xl">把真实开发过程沉淀为可复用的项目成果</h1>
          <p className="mt-6 max-w-2xl text-lg leading-8 text-blue-100/75">
            ProjectFlow 运行在你的本地环境中，无需注册或登录。接入项目后，从待整理变更开始回顾、确认和沉淀。
          </p>
          <Link
            className="mt-10 inline-flex items-center gap-2 rounded-2xl bg-[#2f7cff] px-6 py-4 text-base font-semibold text-white shadow-[0_18px_48px_rgba(47,124,255,0.42)] transition hover:bg-[#4b8dff]"
            href="/dashboard"
          >
            进入工作台
            <ArrowRight className="h-5 w-5" />
          </Link>
        </div>

        <div className="mt-16 grid gap-4 md:grid-cols-3">
          {highlights.map(({ icon: Icon, title, detail }) => (
            <div className="rounded-2xl border border-blue-200/15 bg-white/5 p-5" key={title}>
              <Icon className="mb-5 h-5 w-5 text-cyan-200" />
              <h2 className="font-medium">{title}</h2>
              <p className="mt-2 text-sm leading-6 text-blue-100/65">{detail}</p>
            </div>
          ))}
        </div>
      </section>
    </main>
  );
}
