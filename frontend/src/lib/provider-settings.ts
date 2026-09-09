import type { AiProvider, AiProviderPayload, ModelProtocol } from "./api";

export const providerProtocolLabels: Record<ModelProtocol, string> = {
  OPENAI_RESPONSES: "OpenAI Responses",
  OPENAI_CHAT_COMPLETIONS: "OpenAI Chat Completions",
  ANTHROPIC_MESSAGES: "Anthropic Messages",
};

// The API returns header names only. Omission preserves stored header values.
// Likewise a blank key explicitly retains the credential through the existing API.
export function providerUpdatePayload(provider: AiProvider): AiProviderPayload {
  return {
    name: provider.name, baseUrl: provider.baseUrl, apiKey: "", modelName: provider.modelName,
    type: provider.type, protocol: provider.protocol, endpointOverride: provider.endpointOverride ?? "",
    authMode: provider.authMode, authHeaderName: provider.authHeaderName ?? "", queryKeyName: provider.queryKeyName ?? "",
    requestTimeoutSeconds: provider.requestTimeoutSeconds ?? 240,
    supportsTemperature: provider.supportsTemperature, supportsJsonMode: provider.supportsJsonMode,
    supportsStructuredOutput: provider.supportsStructuredOutput, supportsReasoning: provider.supportsReasoning,
    supportsReasoningControl: provider.supportsReasoningControl,
    temperature: provider.temperature, maxTokens: provider.maxTokens,
    defaultEnabled: provider.defaultEnabled, purposeTags: provider.purposeTags, clearApiKey: false,
  };
}

export function providerFormPayload(form: FormData, provider?: AiProvider): AiProviderPayload {
  const text = (name: string) => String(form.get(name) ?? "").trim();
  const capability = (name: string) => text(name) === "auto" ? null : text(name) === "true";
  let safeHeaders: Record<string, string> | undefined;
  if (form.get("replaceSafeHeaders") === "on") {
    let parsed: unknown;
    try { parsed = JSON.parse(text("safeHeaders") || "{}"); }
    catch { throw new Error("自定义请求头需要合法的 JSON 对象。"); }
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)
      || Object.values(parsed).some((value) => typeof value !== "string"))
      throw new Error("自定义请求头必须是名称与文字值组成的 JSON 对象。");
    safeHeaders = parsed as Record<string, string>;
  }
  if (form.get("clearApiKey") === "on" && text("apiKey"))
    throw new Error("清除凭据与填写新凭据不能同时选择。");
  return {
    ...(provider ? providerUpdatePayload(provider) : {}),
    name: text("name"), baseUrl: text("baseUrl"), apiKey: text("apiKey"), modelName: text("modelName"),
    type: text("type") as AiProviderPayload["type"], protocol: text("protocol") as ModelProtocol,
    endpointOverride: text("endpointOverride"), authMode: text("authMode") as AiProviderPayload["authMode"],
    authHeaderName: text("authHeaderName"), queryKeyName: text("queryKeyName"),
    ...(safeHeaders === undefined ? {} : { safeHeaders }),
    requestTimeoutSeconds: Number(text("requestTimeoutSeconds")),
    supportsTemperature: capability("supportsTemperature"), supportsJsonMode: capability("supportsJsonMode"),
    supportsStructuredOutput: capability("supportsStructuredOutput"), supportsReasoning: capability("supportsReasoning"),
    supportsReasoningControl: capability("supportsReasoningControl"),
    temperature: Number(text("temperature")), maxTokens: Number(text("maxTokens")),
    defaultEnabled: form.get("defaultEnabled") === "on",
    purposeTags: provider?.purposeTags ?? ["项目分析", "材料解析", "成果生成"],
    clearApiKey: form.get("clearApiKey") === "on",
  };
}
