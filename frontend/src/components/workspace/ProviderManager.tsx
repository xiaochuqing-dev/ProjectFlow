"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { CheckCircle2, KeyRound, Pencil, Plus, RefreshCw, ShieldCheck, Sparkles, Star, Trash2 } from "lucide-react";
import {
  createAiProvider, updateAiProvider, deleteAiProvider, listAiProviders, testAiProvider,
  type AiProvider, type AiProviderPayload, type AiProviderAuthMode, type ProviderTestResult,
} from "@/lib/api";
import { readSession } from "@/lib/auth";
import { providerFormPayload, providerProtocolLabels, providerUpdatePayload } from "@/lib/provider-settings";
import { WorkspaceDialog } from "./WorkspaceDialog";

const demoProvider: AiProvider = {
  id: "demo-provider", name: "OpenAI · 示例", type: "OPENAI", modelName: "示例模型",
  baseUrl: "https://api.openai.com/v1", protocol: "OPENAI_RESPONSES", authMode: "PROTOCOL_DEFAULT",
  endpointOverride: null, authHeaderName: null, queryKeyName: null, safeHeaderNames: [],
  requestTimeoutSeconds: 240, supportsTemperature: null, supportsJsonMode: null, supportsStructuredOutput: null,
  supportsReasoning: null, supportsReasoningControl: null, temperature: 0.3, maxTokens: 16000,
  defaultEnabled: true, purposeTags: [], apiKeyConfigured: true, lastProbeProfile: null, lastProbedAt: null,
  createdAt: "", updatedAt: "",
};

export function ProviderManager({ demo }: { demo: boolean }) {
  const [providers, setProviders] = useState<AiProvider[]>(demo ? [demoProvider] : []);
  const [loading, setLoading] = useState(!demo);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [revision, setRevision] = useState(0);
  const [editor, setEditor] = useState<AiProvider | "new" | null>(null);
  const [deleting, setDeleting] = useState<AiProvider | null>(null);
  const [busy, setBusy] = useState("");
  const busyRef = useRef(false);
  const alive = useRef(true);
  const [probes, setProbes] = useState<Record<string, ProviderTestResult>>({});
  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
  useEffect(() => {
    if (demo) return;
    let active = true;
    setLoading(true);
    setError("");
    listAiProviders(readSession().accessToken)
      .then((items) => { if (active) setProviders(items.filter((item) => item.id)); })
      .catch((e) => { if (active) setError(e instanceof Error ? e.message : "配置列表读取失败"); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [demo, revision]);

  function saved(provider: AiProvider) {
    setProviders((current) => [provider, ...current.filter((p) => p.id !== provider.id)
      .map((p) => provider.defaultEnabled ? { ...p, defaultEnabled: false } : p)]);
    setProbes((current) => { const next = { ...current }; if (provider.id) delete next[provider.id]; return next; });
  }

  async function action(provider: AiProvider, kind: "default" | "delete" | "test") {
    if (!provider.id || busyRef.current) return;
    busyRef.current = true;
    setBusy(`${provider.id}:${kind}`);
    setError("");
    setNotice("");
    try {
      if (kind === "test") {
        if (demo) setNotice("示例连接测试已演示；没有发出请求，也不表示真实服务可用。");
        else {
          const result = await testAiProvider(readSession().accessToken, provider.id);
          if (alive.current) setProbes((current) => ({ ...current, [provider.id!]: result }));
        }
      } else if (kind === "delete") {
        if (!demo) await deleteAiProvider(readSession().accessToken, provider.id);
        if (alive.current) {
          setProviders((current) => current.filter((p) => p.id !== provider.id));
          setDeleting(null);
          setNotice(demo ? "示例配置已移除，未删除真实 Provider。" : "Provider 已删除，历史分析结果继续保留。");
        }
      } else {
        const payload = { ...providerUpdatePayload(provider), defaultEnabled: !provider.defaultEnabled };
        const result = demo ? { ...provider, defaultEnabled: payload.defaultEnabled }
          : await updateAiProvider(readSession().accessToken, provider.id, payload);
        if (alive.current) {
          saved(result);
          setNotice(`${demo ? "示例：" : ""}${result.defaultEnabled ? "已设为全局默认 Provider。" : "已取消默认选择；新分析需要明确的默认 Provider。"}`);
        }
      }
    } catch (e) {
      if (alive.current) setError(e instanceof Error ? e.message : "操作失败，请重试。");
    } finally {
      busyRef.current = false;
      if (alive.current) setBusy("");
    }
  }

  return (
    <section className="pf-provider-manager" aria-label="Provider 管理">
      <div className="pf-provider-toolbar">
        <p>{demo ? "仅演示交互，修改仅保留在当前页面。请勿输入真实凭据。" : "统一管理模型服务与凭据。新分析使用全局默认配置。"}</p>
        <button className="pf-button pf-primary" disabled={loading || !!busy} onClick={() => { setError(""); setEditor("new"); }}>
          <Plus size={16} />添加 Provider
        </button>
        {!demo && <button className="pf-icon-button" aria-label="重新读取 Provider" disabled={loading || !!busy}
          onClick={() => setRevision((n) => n + 1)}><RefreshCw size={16} /></button>}
      </div>
      {notice && <p className="pf-notice" role="status"><CheckCircle2 size={17} />{notice}</p>}
      {error && !deleting && <p className="pf-error" role="alert">{error}</p>}
      {loading && <p role="status">正在读取 Provider 配置…</p>}
      {!loading && !error && !providers.length && <div className="pf-inline-empty">
        <KeyRound size={28} /><h3>添加第一个模型服务</h3><p>当前没有已保存的 Provider。配置后可显式分析项目材料。</p>
      </div>}
      {!loading && providers.length > 0 && !providers.some((p) => p.defaultEnabled) && <p className="pf-notice">
        尚未选择全局默认 Provider。已保存的配置可以编辑，新的分析暂不能自动选择模型。
      </p>}
      <div className="pf-provider-list">
        {providers.map((provider) => {
          const probe = probes[provider.id!];
          const deleteBlocked = provider.defaultEnabled && providers.length > 1;
          return <article className="pf-panel pf-provider-card" key={provider.id} aria-label={provider.name}>
            <header>
              <span className="pf-provider-icon"><Sparkles size={23} /></span>
              <div><h3>{provider.name}</h3><p>{provider.modelName} · {providerProtocolLabels[provider.protocol]}</p></div>
              <span className={`pf-chip ${provider.defaultEnabled ? "running" : "recorded"}`}>{provider.defaultEnabled ? "全局默认" : "已保存"}</span>
            </header>
            <p className="pf-provider-endpoint">{provider.baseUrl}</p>
            <p className={`pf-credential-state ${provider.apiKeyConfigured ? "" : "missing"}`}>
              <ShieldCheck size={16} />{provider.apiKeyConfigured ? `${demo ? "示例凭据状态" : "凭据已保存"} · 不回显` : "未保存凭据"}
              {provider.authMode === "NONE" && <small>当前使用无认证模式</small>}
            </p>
            <div className="pf-provider-actions">
              <button className="pf-button" disabled={!!busy || loading} onClick={() => { setError(""); setEditor(provider); }}><Pencil size={14} />编辑</button>
              <button className="pf-button" disabled={!!busy || loading} onClick={() => action(provider, "test")}>
                <RefreshCw size={14} className={busy === `${provider.id}:test` ? "pf-spin" : ""} />{busy === `${provider.id}:test` ? "正在测试…" : "测试连接"}
              </button>
              <button className="pf-button" disabled={!!busy || loading} onClick={() => action(provider, "default")}><Star size={14} />{provider.defaultEnabled ? "取消默认" : "设为默认"}</button>
              <button className="pf-button pf-danger" disabled={!!busy || loading || deleteBlocked}
                aria-describedby={deleteBlocked ? `delete-limit-${provider.id}` : undefined}
                onClick={() => { setError(""); setDeleting(provider); }}><Trash2 size={14} />删除</button>
            </div>
            {deleteBlocked && <p className="pf-page-footnote" id={`delete-limit-${provider.id}`}>删除前请先将另一项设为默认，或取消此项默认选择。</p>}
            {probe && <div className={`pf-probe-result ${probe.ok ? "success" : "failure"}`} role={probe.ok ? "status" : "alert"}>
              <strong>{probe.ok ? "连接测试通过" : "连接测试未通过"}</strong><p>{probe.message}</p>
              <details className="pf-source-details"><summary>查看连接检查详情</summary>
                <dl className="pf-diagnostic-list">{Object.entries(probe.profile ?? {}).filter(([, v]) => !Array.isArray(v)).map(([key, value]) => <div key={key}><dt>{key}</dt><dd>{String(value)}</dd></div>)}</dl>
                {probe.profile?.warnings.map((warning) => <p key={warning}>{warning}</p>)}
              </details>
            </div>}
          </article>;
        })}
      </div>
      <p className="pf-page-footnote">测试连接会使用已保存的配置发出少量请求，可能产生费用；通过不代表长文本分析质量。默认选择可取消，当前没有独立的启用/禁用开关。</p>
      {editor && <ProviderEditor key={typeof editor === "string" ? editor : editor.id} demo={demo}
        provider={typeof editor === "string" ? undefined : editor} defaultEnabled={!providers.some((p) => p.defaultEnabled)}
        onClose={() => setEditor(null)} onSaved={(provider) => {
          saved(provider); setEditor(null);
          setNotice(demo ? "示例配置已更新，未写入真实服务。" : "Provider 已保存，凭据不回显。未填写新凭据时保留原凭据。");
        }} />}
      {deleting && <WorkspaceDialog title="删除 Provider" busy={!!busy} onClose={() => { setDeleting(null); setError(""); }} className="pf-confirm-dialog">
        <div className="pf-dialog-body"><h2>删除「{deleting.name}」？</h2>
          <p>{demo ? "只移除当前示例配置。" : "将移除此配置及其凭据，新任务不再使用它。历史分析结果与调用记录会保留。"}</p>
          {error && <p className="pf-error" role="alert">{error}</p>}
        </div>
        <footer className="pf-dialog-actions"><button className="pf-button" disabled={!!busy} onClick={() => { setDeleting(null); setError(""); }}>取消</button>
          <button className="pf-button pf-danger" disabled={!!busy} onClick={() => action(deleting, "delete")}>{busy ? "正在删除…" : "确认删除"}</button></footer>
      </WorkspaceDialog>}
    </section>
  );
}

function ProviderEditor({ provider, demo, defaultEnabled, onSaved, onClose }: {
  provider?: AiProvider; demo: boolean; defaultEnabled: boolean; onSaved: (provider: AiProvider) => void; onClose: () => void;
}) {
  const form = useRef<HTMLFormElement>(null);
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);
  const [error, setError] = useState("");
  const [confirm, setConfirm] = useState(false);
  const [authMode, setAuthMode] = useState<AiProviderAuthMode>(provider?.authMode ?? "PROTOCOL_DEFAULT");
  const [replaceHeaders, setReplaceHeaders] = useState(false);
  const [clearKey, setClearKey] = useState(false);
  const pending = useRef<AiProviderPayload | null>(null);

  async function save(event?: FormEvent) {
    event?.preventDefault();
    if (busyRef.current || !form.current) return;
    setError("");
    let payload: AiProviderPayload;
    try { payload = confirm && pending.current ? pending.current : providerFormPayload(new FormData(form.current), provider); }
    catch (e) { setError(e instanceof Error ? e.message : "请检查表单内容。"); return; }
    if (provider?.apiKeyConfigured && !confirm && (payload.apiKey || payload.clearApiKey
      || payload.baseUrl !== provider.baseUrl || payload.endpointOverride !== (provider.endpointOverride ?? "")
      || payload.authMode !== provider.authMode || payload.protocol !== provider.protocol
      || payload.authHeaderName !== (provider.authHeaderName ?? "") || payload.queryKeyName !== (provider.queryKeyName ?? ""))) {
      pending.current = payload;
      setConfirm(true); return;
    }
    busyRef.current = true;
    setBusy(true);
    try {
      let result: AiProvider;
      if (demo) {
        // Never retain the supplied secret, even in the in-memory demo record.
        const { apiKey, clearApiKey, safeHeaders, ...configuration } = payload;
        result = {
          ...demoProvider, ...provider, ...configuration, id: provider?.id ?? `demo-${Date.now()}`,
          apiKeyConfigured: !clearApiKey && (!!apiKey || !!provider?.apiKeyConfigured),
          safeHeaderNames: safeHeaders ? Object.keys(safeHeaders) : provider?.safeHeaderNames ?? [],
          lastProbeProfile: null, lastProbedAt: null,
        };
      } else result = provider?.id
        ? await updateAiProvider(readSession().accessToken, provider.id, payload)
        : await createAiProvider(readSession().accessToken, payload);
      form.current?.reset();
      pending.current = null;
      onSaved(result);
    } catch (e) {
      setError(e instanceof Error ? e.message : "保存失败，请检查连接后重试。");
      setConfirm(false);
      pending.current = null;
    } finally { busyRef.current = false; setBusy(false); }
  }

  return <WorkspaceDialog title={provider ? "编辑 Provider" : "添加 Provider"} onClose={onClose} busy={busy} className="pf-provider-dialog">
    <form ref={form} onSubmit={save}>
      <div className="pf-dialog-body">
        <p>{demo ? "示例模式：请勿填写真实凭据。所有交互仅在此页面演示。" : "凭据通过现有安全存储保存，已保存的内容不会重新展示。"}</p>
        <fieldset disabled={busy || confirm} className="pf-provider-fields">
          <label>配置名称<input name="name" required maxLength={120} defaultValue={provider?.name} autoComplete="off" /></label>
          <label>模型标识<input name="modelName" required maxLength={160} defaultValue={provider?.modelName} placeholder="服务商提供的准确模型标识" autoComplete="off" /></label>
          <label className="wide">服务地址<input name="baseUrl" type="url" required maxLength={500} defaultValue={provider?.baseUrl} placeholder="https://api.example.com/v1" autoComplete="off" /></label>
          <label>Provider 类型<select name="type" defaultValue={provider?.type ?? "OPENAI_COMPATIBLE"}>
            <option value="OPENAI_COMPATIBLE">OpenAI 兼容服务</option><option value="OPENAI">OpenAI</option><option value="ANTHROPIC">Anthropic</option>
            <option value="DEEPSEEK">DeepSeek</option><option value="CUSTOM">自定义</option><option value="MOCK">本地固定模型</option>
          </select></label>
          <label>模型协议<select name="protocol" defaultValue={provider?.protocol ?? "OPENAI_CHAT_COMPLETIONS"}>
            {Object.entries(providerProtocolLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select></label>
          <label className="wide">{provider ? "新 API Key（留空保留）" : "API Key"}<input name="apiKey" type="password" aria-label={provider ? "新 API Key（留空保留）" : "API Key"} aria-describedby="provider-key-hint" maxLength={5000} autoComplete="new-password" spellCheck={false} readOnly={clearKey} />
            <small id="provider-key-hint">{provider?.apiKeyConfigured ? "已有凭据；填写新值后需要确认替换。" : "当前没有已保存的凭据。无认证服务可留空。"}</small>
          </label>
          {!!provider?.apiKeyConfigured && <label className="pf-check-field wide"><input type="checkbox" name="clearApiKey" checked={clearKey} onChange={(e) => {
            setClearKey(e.target.checked);
            if (e.target.checked) { const input = form.current?.elements.namedItem("apiKey") as HTMLInputElement; if (input) input.value = ""; }
          }} />清除已保存的凭据</label>}
          <label className="pf-check-field wide"><input type="checkbox" name="defaultEnabled" defaultChecked={provider?.defaultEnabled ?? defaultEnabled} />设为全局默认 Provider</label>
        </fieldset>
        <details className="pf-source-details pf-provider-advanced">
          <summary>高级设置：认证、请求限制与能力覆盖</summary>
          <fieldset disabled={busy || confirm} className="pf-provider-fields">
            <label>认证方式<select name="authMode" value={authMode} onChange={(e) => setAuthMode(e.target.value as AiProviderAuthMode)}>
              <option value="PROTOCOL_DEFAULT">跟随协议</option><option value="BEARER">Bearer</option><option value="API_KEY_HEADER">API Key 请求头</option>
              <option value="ANTHROPIC_STANDARD">Anthropic 标准</option><option value="NONE">无需认证</option><option value="QUERY_API_KEY">API Key 查询参数</option>
            </select></label>
            <label>单次请求超时（秒）<input name="requestTimeoutSeconds" type="number" min={30} max={900} required defaultValue={provider?.requestTimeoutSeconds ?? 240} /></label>
            <label className="wide">端点覆盖（可选）<input name="endpointOverride" maxLength={500} defaultValue={provider?.endpointOverride ?? ""} /></label>
            <label>认证头名称<input name="authHeaderName" maxLength={120} required={authMode === "API_KEY_HEADER"} defaultValue={provider?.authHeaderName ?? ""} /></label>
            <label>认证查询参数名称<input name="queryKeyName" maxLength={120} required={authMode === "QUERY_API_KEY"} defaultValue={provider?.queryKeyName ?? ""} /></label>
            <label>输出上限（Token）<input name="maxTokens" type="number" required min={256} max={200000} defaultValue={provider?.maxTokens ?? 16000} /></label>
            <label>Temperature<input name="temperature" type="number" required min={0} max={2} step={0.01} defaultValue={provider?.temperature ?? 0.3} /></label>
            {([
              ["supportsTemperature", "Temperature 参数"], ["supportsJsonMode", "JSON 模式"], ["supportsStructuredOutput", "结构化输出"],
              ["supportsReasoning", "推理能力"], ["supportsReasoningControl", "推理强度控制"],
            ] as const).map(([key, label]) => <label key={key}>{label}<select name={key} defaultValue={provider?.[key] == null ? "auto" : String(provider[key])}>
              <option value="auto">自动判断</option><option value="true">明确支持</option><option value="false">不支持</option>
            </select></label>)}
            <p className="wide pf-page-footnote">能力覆盖用于已知服务配置；不支持的参数不会发送。当前设置不提供独立推理强度值，具体值由已有模型能力策略决定。输出上限不是消耗目标。</p>
            <label className="pf-check-field wide"><input type="checkbox" name="replaceSafeHeaders" checked={replaceHeaders} onChange={(e) => setReplaceHeaders(e.target.checked)} />替换自定义请求头</label>
            <p className="wide pf-page-footnote">{provider?.safeHeaderNames?.length ? `已有请求头：${provider.safeHeaderNames.join("、")}。` : "没有已保存的自定义请求头。"}不勾选时保留原值。凭据请使用上方认证配置，不能放入自定义请求头。</p>
            <label className="wide" hidden={!replaceHeaders}>自定义请求头 JSON<textarea name="safeHeaders" rows={3} spellCheck={false} placeholder={'{"X-Client-Name": "ProjectFlow"}'} /></label>
          </fieldset>
        </details>
        {confirm && <div className="pf-notice pf-secret-confirm" role="alert">
          <ShieldCheck size={22} /><div><h3>确认凭据或认证目标变更</h3>
            <p>{clearKey ? "保存后将清除原凭据。" : "保存后将替换凭据或改变使用它的服务地址、协议或认证方式。"}请确认这是你要使用的模型服务。</p>
            <button type="button" className="pf-button" disabled={busy} onClick={() => { pending.current = null; setConfirm(false); }}>返回检查</button>
          </div>
        </div>}
        {error && <p className="pf-error" role="alert">{error}</p>}
      </div>
      <footer className="pf-dialog-actions">
        <button type="button" className="pf-button" disabled={busy} onClick={onClose}>取消</button>
        {confirm ? <button type="button" className="pf-button pf-primary" disabled={busy} onClick={() => save()}>{busy ? "正在保存…" : "确认变更并保存"}</button>
          : <button className="pf-button pf-primary" disabled={busy} type="submit">{busy ? "正在保存…" : "保存 Provider"}</button>}
      </footer>
    </form>
  </WorkspaceDialog>;
}
