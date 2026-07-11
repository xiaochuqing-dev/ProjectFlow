"use client";

import { FormEvent, Suspense, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { AlertTriangle, CheckCircle2, KeyRound, Pencil, RefreshCw, Settings2, ShieldCheck, Star, Trash2, X } from "lucide-react";
import { AppShell } from "@/components/AppShell";
import { ProjectContextBar, Toast } from "@/components/ui";
import { useAutoDismissNotice } from "@/hooks/useAutoDismissNotice";
import { useProjectSelection } from "@/hooks/useProjectSelection";
import {
  createAiProvider,
  cleanupDuplicateAiProviders,
  deleteAiProvider,
  getAgentBridgeHealth,
  getProjectGitHubStatus,
  listAiProviders,
  listDuplicateAiProviders,
  listProjectModelUsageRecords,
  testAiProvider,
  updateAiProvider,
  type AiProvider,
  type AiProviderType,
  type DuplicateProviderGroup,
  type ModelUsageRecord,
  type AgentBridgeHealth,
  type GitHubStatus,
} from "@/lib/api";
import { readSession } from "@/lib/auth";

export default function SettingsPage() {
  return (
    <Suspense fallback={<AppShell eyebrow="个人设置" title="设置"><div className="min-h-[calc(100vh-4rem)] bg-surface p-8"><div className="h-1 bg-slate-950" /></div></AppShell>}>
      <SettingsPageContent />
    </Suspense>
  );
}

function SettingsPageContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const queryProjectId = searchParams.get("projectId") ?? "";
  const [providers, setProviders] = useState<AiProvider[]>([]);
  const [duplicateGroups, setDuplicateGroups] = useState<DuplicateProviderGroup[]>([]);
  const [editingProvider, setEditingProvider] = useState<AiProvider | null>(null);
  const { projects, selectedProjectId, selectProject, projectError } = useProjectSelection({ queryProjectId });
  function handleSelectProject(projectId: string) {
    selectProject(projectId);
    router.replace(`/settings?projectId=${projectId}`);
  }
  const [usageRecords, setUsageRecords] = useState<ModelUsageRecord[]>([]);
  const [agentHealth, setAgentHealth] = useState<AgentBridgeHealth | null>(null);
  const [githubStatus, setGithubStatus] = useState<GitHubStatus | null>(null);
  const [saving, setSaving] = useState(false);
  const [loadingUsage, setLoadingUsage] = useState(false);
  const [testingProviderId, setTestingProviderId] = useState("");
  const [providerActionId, setProviderActionId] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  useEffect(() => {
    refreshProviders();
  }, []);

  useEffect(() => {
    if (!selectedProjectId) {
      setUsageRecords([]);
      setAgentHealth(null);
      setGithubStatus(null);
      return;
    }
    refreshUsageRecords(selectedProjectId);
    refreshConnectionStatus(selectedProjectId);
  }, [selectedProjectId]);

  useAutoDismissNotice(error, notice, () => {
    setNotice("");
    setError("");
  });

  async function refreshProviders() {
    const session = readSession();
    if (!session) {
      return;
    }
    setError("");
    try {
      const [providerItems, duplicates] = await Promise.all([
        listAiProviders(session.accessToken),
        listDuplicateAiProviders(session.accessToken),
      ]);
      setProviders(providerItems.filter((provider) => provider.id !== null));
      setDuplicateGroups(duplicates);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "模型配置加载失败");
    }
  }

  async function refreshUsageRecords(projectId = selectedProjectId) {
    const session = readSession();
    if (!session || !projectId) {
      return;
    }
    setLoadingUsage(true);
    setError("");
    try {
      const records = await listProjectModelUsageRecords(session.accessToken, projectId);
      setUsageRecords(records);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "模型调用记录加载失败");
    } finally {
      setLoadingUsage(false);
    }
  }

  async function refreshConnectionStatus(projectId: string) {
    const session = readSession();
    if (!session) return;
    const [agentResult, githubResult] = await Promise.allSettled([
      getAgentBridgeHealth(session.accessToken, projectId),
      getProjectGitHubStatus(session.accessToken, projectId),
    ]);
    setAgentHealth(agentResult.status === "fulfilled" ? agentResult.value : null);
    setGithubStatus(githubResult.status === "fulfilled" ? githubResult.value : null);
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
      const payload = {
        name: String(formData.get("name")),
        baseUrl: String(formData.get("baseUrl")),
        apiKey: String(formData.get("apiKey")),
        modelName: String(formData.get("modelName")),
        type: String(formData.get("type")) as AiProviderType,
        temperature: Number(formData.get("temperature")),
        maxTokens: Number(formData.get("maxTokens")),
        defaultEnabled: editingProvider?.defaultEnabled ?? true,
        purposeTags: ["项目分析", "材料解析", "成果生成"],
        clearApiKey: formData.get("clearApiKey") === "on",
      };
      const provider = editingProvider?.id
        ? await updateAiProvider(session.accessToken, editingProvider.id, payload)
        : await createAiProvider(session.accessToken, payload);
      setProviders((current) => mergeSavedProvider(current, provider));
      setNotice(editingProvider
        ? "Provider 已更新。未填写新 Key 时保留原 Key；只有勾选清除后才会删除。"
        : "Provider 已保存。重复保存同一模型配置会更新原记录，不会新增一条。API Key 不会回显到前端。");
      setEditingProvider(null);
      event.currentTarget.reset();
      await refreshProviders();
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

  async function handleDefault(provider: AiProvider) {
    const session = readSession();
    if (!session || !provider.id) return;
    setProviderActionId(provider.id);
    setError("");
    try {
      await updateAiProvider(session.accessToken, provider.id, {
        name: provider.name, baseUrl: provider.baseUrl, apiKey: "", modelName: provider.modelName,
        type: provider.type, temperature: provider.temperature, maxTokens: provider.maxTokens,
        defaultEnabled: !provider.defaultEnabled, purposeTags: provider.purposeTags, clearApiKey: false,
      });
      await refreshProviders();
      setNotice(provider.defaultEnabled ? "已取消默认模型，新分析会提示选择或配置默认 Provider。" : `已将 ${provider.name} 设为唯一默认 Provider。`);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "默认 Provider 更新失败");
    } finally {
      setProviderActionId("");
    }
  }

  async function handleDelete(provider: AiProvider) {
    const session = readSession();
    if (!session || !provider.id) return;
    const confirmed = window.confirm(
      `确认删除 ${provider.name}？\n\n历史分析结果和模型调用记录不会删除；新任务不再使用该配置。若它是当前默认项，请先选择其他默认 Provider。`,
    );
    if (!confirmed) return;
    setProviderActionId(provider.id);
    setError("");
    try {
      await deleteAiProvider(session.accessToken, provider.id);
      if (editingProvider?.id === provider.id) setEditingProvider(null);
      await refreshProviders();
      setNotice(`已删除 ${provider.name}，历史分析结果未受影响。`);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Provider 删除失败");
    } finally {
      setProviderActionId("");
    }
  }

  async function handleCleanupDuplicates(group: DuplicateProviderGroup) {
    const session = readSession();
    const ids = group.duplicates.flatMap((provider) => provider.id ? [provider.id] : []);
    if (!session || ids.length === 0) return;
    const confirmed = window.confirm(
      `确认保留 ${group.recommendedKeeper.name}，删除其余 ${ids.length} 条重复配置？\n\n默认项、已配置 Key 和最近更新项会优先保留。历史分析结果不会删除。`,
    );
    if (!confirmed) return;
    setProviderActionId(group.groupKey);
    try {
      const result = await cleanupDuplicateAiProviders(session.accessToken, ids);
      setProviders(result.remainingProviders);
      await refreshProviders();
      setNotice(`已清理 ${result.deletedCount} 条重复 Provider，历史分析结果未受影响。`);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "重复 Provider 清理失败");
    } finally {
      setProviderActionId("");
    }
  }

  function tokenTotalWithin(days: number) {
    const since = Date.now() - days * 24 * 60 * 60 * 1000;
    return usageRecords
      .filter((record) => new Date(record.createdAt).getTime() >= since)
      .reduce((total, record) => total + record.totalTokens, 0);
  }

  function tokenTotalToday() {
    const now = new Date();
    const start = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
    return usageRecords
      .filter((record) => new Date(record.createdAt).getTime() >= start)
      .reduce((total, record) => total + record.totalTokens, 0);
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

          <form className="space-y-3" key={editingProvider?.id ?? "new-provider"} onSubmit={handleSaveProvider}>
            {editingProvider ? (
              <div className="flex items-center justify-between rounded-md bg-blue-50 px-3 py-2 text-sm text-blue-900">
                <span>正在编辑：{editingProvider.name}</span>
                <button className="inline-flex items-center gap-1 font-semibold" onClick={() => setEditingProvider(null)} type="button"><X className="h-3.5 w-3.5" />取消</button>
              </div>
            ) : null}
            <label className="block">
              <span className="mb-1 block text-sm font-medium text-slate-700">Provider 名称</span>
              <input className="w-full rounded-md border border-line px-3 py-2 text-sm outline-none focus:border-brand" defaultValue={editingProvider?.name ?? ""} name="name" placeholder="DeepSeek" required />
            </label>
            <label className="block">
              <span className="mb-1 block text-sm font-medium text-slate-700">类型</span>
              <select className="w-full rounded-md border border-line bg-white px-3 py-2 text-sm outline-none focus:border-brand" defaultValue={editingProvider?.type ?? "DEEPSEEK"} name="type">
                <option value="DEEPSEEK">DeepSeek</option>
                <option value="OPENAI_COMPATIBLE">OpenAI-compatible</option>
                <option value="CUSTOM">自定义</option>
              </select>
            </label>
            <label className="block">
              <span className="mb-1 block text-sm font-medium text-slate-700">API Base URL</span>
              <input className="w-full rounded-md border border-line px-3 py-2 text-sm outline-none focus:border-brand" defaultValue={editingProvider?.baseUrl ?? "https://api.deepseek.com"} name="baseUrl" placeholder="https://api.deepseek.com" required />
            </label>
            <label className="block">
              <span className="mb-1 block text-sm font-medium text-slate-700">Model Name</span>
              <input className="w-full rounded-md border border-line px-3 py-2 text-sm outline-none focus:border-brand" defaultValue={editingProvider?.modelName ?? "deepseek-chat"} name="modelName" placeholder="deepseek-chat" required />
            </label>
            <label className="block">
              <span className="mb-1 block text-sm font-medium text-slate-700">API Key</span>
              <input className="w-full rounded-md border border-line px-3 py-2 text-sm outline-none focus:border-brand" name="apiKey" placeholder={editingProvider?.apiKeyConfigured ? "留空则保留原 Key" : "只保存到后端，不回显"} type="password" />
            </label>
            {editingProvider?.apiKeyConfigured ? <label className="flex items-center gap-2 text-xs text-rose-700"><input name="clearApiKey" type="checkbox" />明确清除当前 API Key</label> : null}
            <div className="grid grid-cols-2 gap-3">
              <label className="block">
                <span className="mb-1 block text-sm font-medium text-slate-700">Temperature</span>
                <input className="w-full rounded-md border border-line px-3 py-2 text-sm outline-none focus:border-brand" defaultValue={editingProvider?.temperature ?? 0.2} max="2" min="0" name="temperature" step="0.1" type="number" />
              </label>
              <label className="block">
                <span className="mb-1 block text-sm font-medium text-slate-700">Max Tokens</span>
                <input className="w-full rounded-md border border-line px-3 py-2 text-sm outline-none focus:border-brand" defaultValue={editingProvider?.maxTokens ?? 8192} max="200000" min="256" name="maxTokens" type="number" />
              </label>
            </div>
            <p className="text-xs leading-5 text-muted">
              Max Tokens 表示 Provider 或用户配置的输出能力上限，不是每次分析的固定值。ProjectFlow 会按入口、输入规模、输出结构和模型能力动态申请预算；Temperature 会使用配置值、任务建议值或在模型不支持时省略，实际参数与原因可在诊断中查看。
            </p>
            <button className="flex w-full items-center justify-center gap-2 rounded-md bg-brand px-4 py-2.5 text-sm font-semibold text-white hover:bg-blue-600 disabled:opacity-60" disabled={saving} type="submit">
              {saving ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Settings2 className="h-4 w-4" />}
              {saving ? "保存中..." : editingProvider ? "保存修改" : "保存模型配置"}
            </button>
          </form>
        </section>

        <section className="space-y-6">
          <Toast error={error || projectError} notice={notice} />

          <div className="rounded-md border border-line bg-white shadow-panel">
            <div className="border-b border-line px-5 py-3">
              <h2 className="font-semibold text-slate-950">项目接入状态</h2>
            </div>
            <div className="grid gap-4 p-5 md:grid-cols-2">
              <section className="rounded-md bg-slate-50 p-4">
                <div className="flex items-center justify-between gap-3">
                  <h3 className="font-semibold text-slate-950">Agent 写回协议</h3>
                  <span className={`rounded-md px-2 py-1 text-xs font-medium ${agentHealth?.protocolExists && agentHealth.entryRulePresent ? "bg-emerald-100 text-emerald-800" : "bg-amber-100 text-amber-900"}`}>
                    {agentHealth?.protocolExists && agentHealth.entryRulePresent ? "已接入" : "需检查"}
                  </span>
                </div>
                <p className="mt-2 text-sm leading-6 text-slate-600">协议版本 {agentHealth?.protocolVersion ?? "未知"}，写回目录与 AGENTS.md 入口规则会分别检查。</p>
                {agentHealth?.warnings.length ? <p className="mt-2 text-xs leading-5 text-amber-800">{agentHealth.warnings.join("；")}</p> : null}
              </section>
              <section className="rounded-md bg-slate-50 p-4">
                <div className="flex items-center justify-between gap-3">
                  <h3 className="font-semibold text-slate-950">GitHub CLI</h3>
                  <span className={`rounded-md px-2 py-1 text-xs font-medium ${githubStatus?.ghAuthenticated && githubStatus.repoDetected ? "bg-emerald-100 text-emerald-800" : "bg-slate-200 text-slate-700"}`}>
                    {githubStatus?.ghAuthenticated && githubStatus.repoDetected ? "已接入" : "可选增强"}
                  </span>
                </div>
                <p className="mt-2 text-sm leading-6 text-slate-600">
                  {githubStatus?.nameWithOwner || "未读取远程仓库信息"}。本地 Git 分析仍可使用，GitHub CLI 只补充仓库与 commit 链接。
                </p>
                {githubStatus?.warnings.length ? <p className="mt-2 text-xs leading-5 text-muted">{githubStatus.warnings.join("；")}</p> : null}
              </section>
            </div>
          </div>

          <div className="rounded-md border border-line bg-white shadow-panel">
            <div className="border-b border-line px-5 py-3">
              <h2 className="font-semibold text-slate-950">已保存 Provider</h2>
              <p className="mt-1 text-xs text-muted">连接测试只验证地址、模型名和 Key 基本可用，不代表长文本结构化分析一定成功。</p>
            </div>
            <div className="divide-y divide-line">
              {providers.map((provider) => (
                <div className="grid gap-3 px-5 py-4 text-sm lg:grid-cols-[minmax(0,1fr)_140px_minmax(280px,auto)]" key={provider.id ?? provider.name}>
                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="font-semibold text-slate-950">{provider.name}</p>
                      {provider.defaultEnabled ? <span className="rounded-md bg-blue-50 px-2 py-0.5 text-xs font-semibold text-blue-800">默认</span> : null}
                    </div>
                    <p className="mt-1 text-muted">{provider.type} · {provider.modelName}</p>
                    <p className="mt-1 truncate text-xs text-muted">{provider.baseUrl}</p>
                    <p className="mt-1 text-xs text-muted">Provider 能力上限：{provider.maxTokens} tokens · 配置 Temperature {provider.temperature}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    <CheckCircle2 className={`h-4 w-4 ${provider.apiKeyConfigured ? "text-emerald-600" : "text-slate-400"}`} />
                    {provider.apiKeyConfigured ? "Key 已配置" : "Key 未配置"}
                  </div>
                  <div className="flex flex-wrap items-center justify-start gap-2 lg:justify-end">
                    <button className="rounded-md border border-line bg-white px-3 py-2 font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-60" disabled={!provider.id || testingProviderId === provider.id} onClick={() => handleTest(provider)} type="button">
                      {testingProviderId === provider.id ? "测试中..." : "测试连接"}
                    </button>
                    <button className="inline-flex items-center gap-1 rounded-md border border-line px-3 py-2 font-semibold text-slate-700 hover:bg-slate-50" onClick={() => setEditingProvider(provider)} type="button"><Pencil className="h-3.5 w-3.5" />编辑</button>
                    <button className="inline-flex items-center gap-1 rounded-md border border-line px-3 py-2 font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-60" disabled={providerActionId === provider.id} onClick={() => handleDefault(provider)} type="button"><Star className="h-3.5 w-3.5" />{provider.defaultEnabled ? "取消默认" : "设为默认"}</button>
                    <button className="inline-flex items-center gap-1 rounded-md border border-rose-200 px-3 py-2 font-semibold text-rose-700 hover:bg-rose-50 disabled:opacity-60" disabled={providerActionId === provider.id} onClick={() => handleDelete(provider)} type="button"><Trash2 className="h-3.5 w-3.5" />删除</button>
                  </div>
                </div>
              ))}
              {providers.length === 0 ? (
                <div className="p-8 text-center text-sm text-muted">还没有真实模型配置。项目 zip 仍可生成本地项目理解。</div>
              ) : null}
            </div>
          </div>

          {duplicateGroups.length ? (
            <div className="rounded-md border border-amber-200 bg-amber-50 p-5 shadow-panel">
              <div className="flex items-center gap-2"><AlertTriangle className="h-5 w-5 text-amber-700" /><h2 className="font-semibold text-amber-950">发现重复 Provider</h2></div>
              <p className="mt-2 text-sm leading-6 text-amber-900">系统只给出保留建议，不会自动删除。清理前会再次确认，历史分析结果和调用记录不会受影响。</p>
              <div className="mt-3 space-y-3">
                {duplicateGroups.map((group) => (
                  <div className="rounded-md border border-amber-200 bg-white p-3 text-sm" key={group.groupKey}>
                    <p className="font-semibold text-slate-900">建议保留：{group.recommendedKeeper.name} · {group.recommendedKeeper.modelName}</p>
                    <p className="mt-1 text-xs text-slate-600">待清理 {group.duplicates.length} 条相同类型、Base URL 和模型名的配置。</p>
                    <button className="mt-2 rounded-md bg-amber-900 px-3 py-2 text-xs font-semibold text-white disabled:opacity-60" disabled={providerActionId === group.groupKey} onClick={() => handleCleanupDuplicates(group)} type="button">确认清理重复配置</button>
                  </div>
                ))}
              </div>
            </div>
          ) : null}

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

          <div className="rounded-md border border-line bg-white shadow-panel">
            <div className="flex flex-col gap-3 border-b border-line px-5 py-3 lg:flex-row lg:items-center lg:justify-between">
              <div>
                <h2 className="font-semibold text-slate-950">模型调用记录</h2>
                <p className="mt-1 text-sm text-muted">按项目查看成果生成等模型/模板调用的 token 估算和状态。</p>
              </div>
              <div className="flex gap-2">
                <button
                  className="rounded-md border border-line bg-white px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-60"
                  disabled={!selectedProjectId || loadingUsage}
                  onClick={() => refreshUsageRecords()}
                  type="button"
                >
                  {loadingUsage ? "刷新中..." : "刷新"}
                </button>
              </div>
            </div>
            <ProjectContextBar
              onSelect={handleSelectProject}
              projects={projects}
              selectedProjectId={selectedProjectId}
            />
            <div className="divide-y divide-line">
              {usageRecords.length > 0 ? (
                <div className="grid gap-3 px-5 py-4 text-sm md:grid-cols-3">
                  <div className="rounded-md bg-slate-50 p-3">
                    <p className="text-xs text-muted">今日 Token</p>
                    <p className="mt-1 text-xl font-semibold text-slate-950">{tokenTotalToday()}</p>
                  </div>
                  <div className="rounded-md bg-slate-50 p-3">
                    <p className="text-xs text-muted">7 天 Token</p>
                    <p className="mt-1 text-xl font-semibold text-slate-950">{tokenTotalWithin(7)}</p>
                  </div>
                  <div className="rounded-md bg-slate-50 p-3">
                    <p className="text-xs text-muted">30 天 Token</p>
                    <p className="mt-1 text-xl font-semibold text-slate-950">{tokenTotalWithin(30)}</p>
                  </div>
                </div>
              ) : null}
              {usageRecords.slice(0, 8).map((record) => (
                <div className="grid gap-3 px-5 py-4 text-sm lg:grid-cols-[1fr_120px_110px_110px]" key={record.id}>
                  <div>
                    <p className="font-semibold text-slate-950">{record.operation}</p>
                    <p className="mt-1 text-muted">{record.providerName} · {record.modelName} · {new Date(record.createdAt).toLocaleString("zh-CN")}</p>
                    {record.qualityWarnings ? <p className="mt-1 text-xs text-amber-700">质量提示：{record.qualityWarnings.replaceAll("\n", "；")}</p> : null}
                  </div>
                  <div>
                    <p className="text-xs text-muted">状态</p>
                    <p className={record.status === "SUCCEEDED" ? "font-semibold text-emerald-700" : "font-semibold text-rose-700"}>{record.status}</p>
                  </div>
                  <div>
                    <p className="text-xs text-muted">Token</p>
                    <p className="font-semibold text-slate-800">{record.totalTokens}{record.usageEstimated ? " 估算" : ""}</p>
                  </div>
                  <div>
                    <p className="text-xs text-muted">耗时</p>
                    <p className="font-semibold text-slate-800">{record.latencyMs} ms</p>
                  </div>
                </div>
              ))}
              {usageRecords.length === 0 ? (
                <div className="p-8 text-center text-sm text-muted">
                  {selectedProjectId ? "当前项目还没有模型调用记录。生成一次成果输出后会出现记录。" : "先创建项目，再查看模型调用记录。"}
                </div>
              ) : null}
            </div>
          </div>
        </section>
      </div>
    </AppShell>
  );
}

function mergeSavedProvider(current: AiProvider[], provider: AiProvider) {
  if (!provider.id) {
    return current;
  }
  return current.some((item) => item.id === provider.id)
    ? current.map((item) => (item.id === provider.id ? provider : item))
    : [provider, ...current];
}
