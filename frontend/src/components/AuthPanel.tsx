"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { ArrowRight, LockKeyhole, Mail, ShieldCheck, UserRound } from "lucide-react";
import { login, register } from "@/lib/api";
import { saveSession } from "@/lib/auth";

type AuthPanelProps = {
  mode: "login" | "register";
};

export function AuthPanel({ mode }: AuthPanelProps) {
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const isRegister = mode === "register";

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setSubmitting(true);

    try {
      const result = isRegister
        ? await register(username, email, password)
        : await login(email, password);
      saveSession(result);
      router.push("/dashboard");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "请求失败，请稍后重试");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="w-full max-w-[540px] rounded-[28px] border border-blue-200/22 bg-[#07152d]/78 p-9 shadow-[0_34px_120px_rgba(0,28,80,0.56)] backdrop-blur-2xl md:p-11 lg:w-[clamp(540px,38vw,860px)] lg:max-w-none xl:mr-4">
      <div className="mb-9">
        <div className="mb-5 inline-flex items-center gap-2 rounded-full border border-blue-300/22 bg-blue-400/12 px-4 py-1.5 text-sm text-blue-100">
          <ShieldCheck className="h-4 w-4" />
          专业项目流程管理
        </div>
        <h1 className="text-3xl font-semibold tracking-normal md:text-[34px]">
          {isRegister ? "创建 ProjectFlow 账号" : "登录 ProjectFlow"}
        </h1>
        <p className="mt-4 max-w-[430px] text-base leading-7 text-blue-100/78">
          管理项目空间、任务推进、开发日志和 AI 复盘输出，让真实开发过程沉淀成可展示的工程资产。
        </p>
      </div>

      <form className="space-y-6" onSubmit={handleSubmit}>
        {isRegister ? (
          <label className="block">
            <span className="mb-2.5 block text-sm text-blue-50/86">用户名</span>
            <span className="flex items-center gap-3 rounded-2xl border border-blue-200/20 bg-white/9 px-4 py-4 text-base text-white shadow-inner shadow-blue-950/30">
              <UserRound className="h-5 w-5 text-cyan-200" />
              <input
                className="min-w-0 flex-1 bg-transparent outline-none placeholder:text-blue-100/42"
                onChange={(event) => setUsername(event.target.value)}
                placeholder="用于显示在工作台"
                required
                type="text"
                value={username}
              />
            </span>
          </label>
        ) : null}

        <label className="block">
          <span className="mb-2.5 block text-sm text-blue-50/86">邮箱</span>
          <span className="flex items-center gap-3 rounded-2xl border border-blue-200/20 bg-white/9 px-4 py-4 text-base text-white shadow-inner shadow-blue-950/30">
            <Mail className="h-5 w-5 text-cyan-200" />
            <input
              className="min-w-0 flex-1 bg-transparent outline-none placeholder:text-blue-100/42"
              onChange={(event) => setEmail(event.target.value)}
              placeholder="you@example.com"
              required
              type="email"
              value={email}
            />
          </span>
        </label>

        <label className="block">
          <span className="mb-2.5 block text-sm text-blue-50/86">密码</span>
          <span className="flex items-center gap-3 rounded-2xl border border-blue-200/20 bg-white/9 px-4 py-4 text-base text-white shadow-inner shadow-blue-950/30">
            <LockKeyhole className="h-5 w-5 text-cyan-200" />
            <input
              className="min-w-0 flex-1 bg-transparent outline-none placeholder:text-blue-100/42"
              minLength={8}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="至少 8 位"
              required
              type="password"
              value={password}
            />
          </span>
        </label>

        {error ? (
          <p className="rounded-xl border border-rose-300/20 bg-rose-500/12 px-4 py-3 text-sm text-rose-100">
            {error}
          </p>
        ) : null}

        <button
          className="group flex w-full items-center justify-center gap-2 rounded-2xl bg-[#2f7cff] px-5 py-4 text-base font-semibold text-white shadow-[0_18px_48px_rgba(47,124,255,0.42)] transition hover:bg-[#4b8dff] disabled:cursor-not-allowed disabled:opacity-60"
          disabled={submitting}
          type="submit"
        >
          {submitting ? "处理中..." : isRegister ? "创建账号" : "进入工作台"}
          <ArrowRight className="h-5 w-5 transition group-hover:translate-x-0.5" />
        </button>
      </form>

      <div className="mt-7 flex items-center justify-between border-t border-blue-200/12 pt-7 text-sm text-blue-100/70">
        <span>{isRegister ? "已有账号？" : "还没有账号？"}</span>
        <Link className="font-medium text-cyan-100 hover:text-white" href={isRegister ? "/login" : "/register"}>
          {isRegister ? "去登录" : "创建账号"}
        </Link>
      </div>
    </div>
  );
}
