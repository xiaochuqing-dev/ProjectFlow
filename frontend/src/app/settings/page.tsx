"use client";

import { FormEvent, useEffect, useState } from "react";
import { CheckCircle2, KeyRound, RefreshCw, Settings2, ShieldCheck } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import {
  createAiProvider,
  listAiProviders,
  testAiProvider,
  type AiProvider,
  type AiProviderType,
} from "@/lib/api";
import { readSession } from "@/lib/auth";

export default function SettingsPage() {
  const [providers, setProviders] = useState<AiProvider[]>([]);
  const [saving, setSaving] = useState(false);
  const [testingProviderId, setTestingProviderId] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  useEffect(() => {
    refreshProviders();
  }, []);

  useEffect(() => {
    if (!notice && !error) {
      return;
    }
    const timeout = window.setTimeout(() => {
      setNotice("");
      setError("");
    }, 4200);
    return () => window.clearTimeout(timeout);
  }, [error, notice]);

  async function refreshProviders() {
    const session = readSession();
    if (!session) {
      return;
    }
    setError("");
    try {
      const providerItems = await listAiProviders(session.accessToken);
      setProviders(providerItems.filter((provider) => provider.id !== null));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "模型配置加载失败");
    }
  }

  async function handleSaveProvider(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const session = readSession();
    if (!session) {
      return;
    }

    const formData = new FormData(event.currentTarget);
    setSaving(true);
    setError("");
    setNotice("");
    try {
      const provider = await createAiProvider(session.accessToken, {
        name: String(formData.get("name")),
        baseUrl: String(formData.get("baseUrl")),
        apiKey: String(formData.get("apiKey")),
        modelName: String(formData.get("modelName")),
        type: String(formData.get("type")) as AiProviderType,
        temperature: Number(formData.get("temperature")),
        maxTokens: Number(formData.get("maxTokens")),
        defaultEnabled: true,
        purposeTags: ["项目分析", "材料解析", "成果生成"],
      });
      setProviders((current) => [provider, ...current]);
      setNotice("Provider 已保存。API key 不会回显到前端。");
      event.currentTarget.reset();
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Provider 保存失败");
    } finally {
      setSaving(false);
    }
  }

  async function handleTest(provider: AiProvider) {
    const session = readSession();
    if (!session || !provider.id) {
      return;
    }
    setTestingProviderId(provider.id);
    setError("");
    setNotice("");
    try {
      const result = await testAiProvider(session.accessToken, provider.id);
      setNotice(result.message);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Provider 测试失败");
    } finally {
      setTestingProviderId("");
    }
  }

  return (
    <AppShell eyebrow="账号与模型" title="个人设置">
      <div className="grid gap-6 p-6 xl:grid-cols-[420px_minmax(0,1fr)]">
        <section className="rounded-md border border-line bg-white p-5 shadow-panel">
          <div className="mb-5 flex items-center gap-3">
            <div className="grid h-10 w-10 place-items-center rounded-md bg-slate-950 text-white">
              <KeyRound className="h-5 w-5" />
            </div>
            <div>
              <h2 className="font-semibold text-slate-950">AI Provider</h2>
              <p className="text-sm text-muted">配置 DeepSeek 或 OpenAI-compatible 模型。</p>
            </div>
          </div>

          <form className="space-y-3" onSubmit={handleSaveProvider}>
            <label className="block">
              <span className="mb-1 block text-sm font-medium text-slate-700">Provider 名称</span>
              <input className="w-full rounded-md border border-line px-3 py-2 text-sm outline-none focus:border-brand" name="name" placeholder="DeepSeek" required />
            </label>
            <label className="block">
              <span className="mb-1 block text-sm font-medium text-slate-700">类型</span>
              <select className="w-full rounded-md border border-line bg-white px-3 py-2 text-sm outline-none focus:border-brand" defaultValue="DEEPSEEK" name="type">
                <option value="DEEPSEEK">DeepSeek</option>
                <option value="OPENAI_COMPATIBLE">OpenAI-compatible</option>
                <option value="CUSTOM">自定义</option>
              </select>
            </label>
            <label className="block">
              <span className="mb-1 block text-sm font-medium text-slate-700">API Base URL</span>
              <input className="w-full rounded-md border border-line px-3 py-2 text-sm outline-none focus:border-brand" defaultValue="https://api.deepseek.com" name="baseUrl" placeholder="https://api.deepseek.com" required />
            </label>
            <label className="block">
              <span className="mb-1 block text-sm font-medium text-slate-700">Model Name</span>
              <input className="w-full rounded-md border border-line px-3 py-2 text-sm outline-none focus:border-brand" defaultValue="deepseek-v4-pro" name="modelName" placeholder="deepseek-v4-pro" required />
            </label>
            <label className="block">
              <span className="mb-1 block text-sm font-medium text-slate-700">API Key</span>
              <input className="w-full rounded-md border border-line px-3 py-2 text-sm outline-none focus:border-brand" name="apiKey" placeholder="只保存到后端，不回显" type="password" />
            </label>
            <div className="grid grid-cols-2 gap-3">
              <label className="block">
                <span className="mb-1 block text-sm font-medium text-slate-700">Temperature</span>
                <input className="w-full rounded-md border border-line px-3 py-2 text-sm outline-none focus:border-brand" defaultValue="0.2" max="2" min="0" name="temperature" step="0.1" type="number" />
              </label>
              <label className="block">
                <span className="mb-1 block text-sm font-medium text-slate-700">Max Tokens</span>
                <input className="w-full rounded-md border border-line px-3 py-2 text-sm outline-none focus:border-brand" defaultValue="8192" max="5000000" min="1" name="maxTokens" type="number" />
              </label>
            </div>
            <p className="text-xs leading-5 text-muted">
              DeepSeek 填 Base URL，不要填完整 `/chat/completions`；如果误填，后端会自动修正。Max Tokens 上限为 5000000，实际可用长度仍由模型服务决定。
            </p>
            <button className="flex w-full items-center justify-center gap-2 rounded-md bg-brand px-4 py-2.5 text-sm font-semibold text-white hover:bg-blue-600 disabled:opacity-60" disabled={saving} type="submit">
              {saving ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Settings2 className="h-4 w-4" />}
              {saving ? "保存中..." : "保存模型配置"}
            </button>
          </form>
        </section>

        <section className="space-y-6">
          {error ? <div className="rounded-md border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{error}</div> : null}
          {notice ? <div className="rounded-md border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">{notice}</div> : null}

          <div className="rounded-md border border-line bg-white shadow-panel">
            <div className="border-b border-line px-5 py-3">
              <h2 className="font-semibold text-slate-950">已保存 Provider</h2>
            </div>
            <div className="divide-y divide-line">
              {providers.map((provider) => (
                <div className="grid gap-3 px-5 py-4 text-sm lg:grid-cols-[1fr_140px_120px]" key={provider.id ?? provider.name}>
                  <div>
                    <p className="font-semibold text-slate-950">{provider.name}</p>
                    <p className="mt-1 text-muted">{provider.type} · {provider.modelName}</p>
                    <p className="mt-1 truncate text-xs text-muted">{provider.baseUrl}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    <CheckCircle2 className={`h-4 w-4 ${provider.apiKeyConfigured ? "text-emerald-600" : "text-slate-400"}`} />
                    {provider.apiKeyConfigured ? "Key 已配置" : "Key 未配置"}
                  </div>
                  <button
                    className="rounded-md border border-line bg-white px-3 py-2 font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-60"
                    disabled={!provider.id || testingProviderId === provider.id}
                    onClick={() => handleTest(provider)}
                    type="button"
                  >
                    {testingProviderId === provider.id ? "测试中..." : "测试连接"}
                  </button>
                </div>
              ))}
              {providers.length === 0 ? (
                <div className="p-8 text-center text-sm text-muted">还没有真实模型配置。项目 zip 仍可生成本地项目画像。</div>
              ) : null}
            </div>
          </div>

          <div className="rounded-md border border-line bg-white p-5 shadow-panel">
            <div className="mb-3 flex items-center gap-2">
              <ShieldCheck className="h-5 w-5 text-emerald-600" />
              <h2 className="font-semibold text-slate-950">安全规则</h2>
            </div>
            <ul className="space-y-2 text-sm leading-6 text-slate-600">
              <li>API key 不提交到 Git，也不会回显到前端响应。</li>
              <li>项目管理页只显示配置状态，不承载完整 key 表单。</li>
              <li>连接测试失败时只返回友好错误，不暴露请求头、key 或内部堆栈。</li>
            </ul>
          </div>
        </section>
      </div>
    </AppShell>
  );
}
