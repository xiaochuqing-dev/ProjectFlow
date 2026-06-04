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
    <div className="w-full max-w-[430px] rounded-2xl border border-blue-300/20 bg-[#07152d]/72 p-8 shadow-[0_28px_90px_rgba(0,28,80,0.48)] backdrop-blur-xl">
      <div className="mb-8">
        <div className="mb-4 inline-flex items-center gap-2 rounded-full border border-blue-300/20 bg-blue-400/10 px-3 py-1 text-xs text-blue-100">
          <ShieldCheck className="h-3.5 w-3.5" />
          专业项目流程管理
        </div>
        <h1 className="text-2xl font-semibold tracking-normal">
          {isRegister ? "创建 ProjectFlow 账号" : "登录 ProjectFlow"}
        </h1>
        <p className="mt-3 text-sm leading-6 text-blue-100/76">
          管理项目空间、任务推进、开发日志和 AI 复盘输出，让真实开发过程沉淀成可展示的工程资产。
        </p>
      </div>

      <form className="space-y-5" onSubmit={handleSubmit}>
        {isRegister ? (
          <label className="block">
            <span className="mb-2 block text-sm text-blue-50/86">用户名</span>
            <span className="flex items-center gap-3 rounded-xl border border-blue-200/18 bg-white/8 px-4 py-3 text-sm text-white shadow-inner shadow-blue-950/30">
              <UserRound className="h-4 w-4 text-cyan-200" />
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
          <span className="mb-2 block text-sm text-blue-50/86">邮箱</span>
          <span className="flex items-center gap-3 rounded-xl border border-blue-200/18 bg-white/8 px-4 py-3 text-sm text-white shadow-inner shadow-blue-950/30">
            <Mail className="h-4 w-4 text-cyan-200" />
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
          <span className="mb-2 block text-sm text-blue-50/86">密码</span>
          <span className="flex items-center gap-3 rounded-xl border border-blue-200/18 bg-white/8 px-4 py-3 text-sm text-white shadow-inner shadow-blue-950/30">
            <LockKeyhole className="h-4 w-4 text-cyan-200" />
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
          <p className="rounded-lg border border-rose-300/20 bg-rose-500/12 px-3 py-2 text-sm text-rose-100">
            {error}
          </p>
        ) : null}

        <button
          className="group flex w-full items-center justify-center gap-2 rounded-xl bg-[#2f7cff] px-4 py-3 text-sm font-semibold text-white shadow-[0_16px_42px_rgba(47,124,255,0.38)] transition hover:bg-[#4b8dff] disabled:cursor-not-allowed disabled:opacity-60"
          disabled={submitting}
          type="submit"
        >
          {submitting ? "处理中..." : isRegister ? "创建账号" : "进入工作台"}
          <ArrowRight className="h-4 w-4 transition group-hover:translate-x-0.5" />
        </button>
      </form>

      <div className="mt-6 flex items-center justify-between border-t border-blue-200/12 pt-6 text-sm text-blue-100/70">
        <span>{isRegister ? "已有账号？" : "还没有账号？"}</span>
        <Link className="font-medium text-cyan-100 hover:text-white" href={isRegister ? "/login" : "/register"}>
          {isRegister ? "去登录" : "创建账号"}
        </Link>
      </div>
    </div>
  );
}
