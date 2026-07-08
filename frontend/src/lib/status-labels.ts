// V3.3.4: 统一的内部枚举 -> 中文人话映射。
// 内部枚举（CALL_FAILED / LOCAL_RULE / CONNECTED / local_ahead 等）只出现在证据细节里，
// 不能直接暴露给普通用户。各组件统一使用这里的映射函数。

// 模型状态枚举 -> 中文。
export function modelStatusLabel(status: string | undefined | null, providerName?: string): string {
  const provider = providerName && providerName.trim() ? providerName.trim() : "模型";
  switch (status) {
    case "SUCCESS": return `${provider} 已参与`;
    case "NOT_CONFIGURED": return "未配置模型";
    case "REQUEST_TIMEOUT": return `${provider} 请求超时`;
    case "HTTP_401_OR_403": return `${provider} 鉴权失败`;
    case "HTTP_429": return `${provider} 限流`;
    case "HTTP_5XX": return `${provider} 服务异常`;
    case "NETWORK_ERROR": return "网络连接失败";
    case "CALL_FAILED": return `${provider} 调用失败`;
    case "UNKNOWN_CALL_FAILED": return `${provider} 调用失败`;
    case "JSON_PARSE_FAILED": return "模型返回格式无效";
    case "EVIDENCE_REJECTED": return "模型证据引用无效";
    case "NO_CHANGES": return "无新变化";
    default: return status && status.trim() ? status : "未配置模型";
  }
}

// V3.3.4 小阶段修复：模型失败原因详细人话描述（用于诊断信息 / fallbackReason 展示）。
export function modelFailureDetail(status: string | undefined | null, providerName?: string): string {
  const provider = providerName && providerName.trim() ? providerName.trim() : "模型";
  switch (status) {
    case "REQUEST_TIMEOUT": return `${provider} 请求超时，模型在设定时间内没有返回，本次先展示本地事实摘要。`;
    case "HTTP_401_OR_403": return `${provider} 返回鉴权失败（401/403），可能是 API key 错误或权限不足。`;
    case "HTTP_429": return `${provider} 返回 429，可能是限流，请稍后重试。`;
    case "HTTP_5XX": return `${provider} 服务异常（5xx），本次先展示本地事实摘要，可稍后重新分析。`;
    case "NETWORK_ERROR": return "网络连接失败，可能与代理或 baseUrl 有关，本次先展示本地事实摘要。";
    case "JSON_PARSE_FAILED": return "模型返回内容不是可解析结构，本次先展示本地事实摘要。";
    case "EVIDENCE_REJECTED": return "模型结果引用的证据不可用，本次先展示本地事实摘要。";
    case "CALL_FAILED":
    case "UNKNOWN_CALL_FAILED": return `${provider} 调用失败，本次先展示本地事实摘要。`;
    default: return "";
  }
}

// 归并方式枚举 -> 中文。
export function mergeModeLabel(mode: string | undefined | null): string {
  switch (mode) {
    case "MODEL": return "模型分析";
    case "LOCAL_RULE": return "本地事实摘要";
    default: return "本地事实摘要";
  }
}

// 生成方式枚举 -> 中文（segment / capability card 的 generationMode）。
export function generationModeLabel(mode: string | undefined | null, providerName?: string): string {
  if (mode === "MODEL") {
    return providerName && providerName.trim() ? `模型 · ${providerName.trim()}` : "模型分析";
  }
  return "本地事实摘要";
}

// GitHub 状态枚举 -> 中文。
export function githubStatusLabel(status: string | undefined | null): string {
  switch (status) {
    case "CONNECTED": return "已接入";
    case "NOT_INSTALLED": return "未安装";
    case "NOT_AUTHENTICATED": return "未登录";
    case "NO_REMOTE": return "未检测到远程仓库";
    case "CONNECTION_TIMEOUT": return "连接超时";
    case "PERMISSION_DENIED": return "权限不足";
    case "FETCH_FAILED": return "刷新失败";
    case "CALL_FAILED": return "调用失败";
    case "JSON_PARSE_FAILED": return "返回格式无效";
    default: return status && status.trim() ? status : "未知";
  }
}

// 远程关系枚举 -> 中文。
export function remoteRelationLabel(relation: string | undefined | null): string {
  switch (relation) {
    case "synced": return "已同步";
    case "local_ahead": return "本地领先";
    case "remote_ahead": return "远程领先";
    case "diverged": return "本地和远程已分叉";
    case "no_upstream": return "未设置上游分支";
    case "github_unavailable": return "GitHub 不可用";
    default: return relation && relation.trim() ? relation : "未知";
  }
}

// GitHub 状态 + 远程关系组合文案（用于诊断信息一行展示）。
export function githubDiagnosticLabel(status: string | undefined | null, relation: string | undefined | null): string {
  const statusText = githubStatusLabel(status);
  const relationText = remoteRelationLabel(relation);
  if (status === "CONNECTED") {
    return `${statusText}，${relationText}`;
  }
  return statusText;
}

// 质量门槛标记器状态 -> 中文。
export function qualityStatusLabel(qualityStatus: string | undefined | null): string {
  switch (qualityStatus) {
    case "PASS": return "待确认";
    case "NEEDS_REVIEW": return "需复核";
    case "NEEDS_CHINESE_REWRITE": return "需中文修正";
    case "NEEDS_EVIDENCE": return "需补证据";
    case "PARTIAL_EVIDENCE": return "部分证据";
    case "LOW_CONFIDENCE": return "低置信度";
    case "NEEDS_MANUAL": return "需人工整理";
    default: return "待确认";
  }
}
