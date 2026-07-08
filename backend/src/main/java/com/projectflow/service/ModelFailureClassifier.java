package com.projectflow.service;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;

/**
 * V3.3.4 小阶段修复：把模型失败原因细分为用户可理解的具体类别。
 *
 * 不再笼统返回 CALL_FAILED。区分超时 / 鉴权 / 限流 / 服务异常 / 网络 / 格式 / 证据 / 未知，
 * 让用户一眼看懂模型为什么没参与、当前结果是什么来源。
 * 内部枚举只出现在诊断信息里，前端统一翻译成人话。
 */
public final class ModelFailureClassifier {

    public static final String REQUEST_TIMEOUT = "REQUEST_TIMEOUT";
    public static final String HTTP_401_OR_403 = "HTTP_401_OR_403";
    public static final String HTTP_429 = "HTTP_429";
    public static final String HTTP_5XX = "HTTP_5XX";
    public static final String NETWORK_ERROR = "NETWORK_ERROR";
    public static final String JSON_PARSE_FAILED = "JSON_PARSE_FAILED";
    public static final String EVIDENCE_REJECTED = "EVIDENCE_REJECTED";
    public static final String UNKNOWN_CALL_FAILED = "UNKNOWN_CALL_FAILED";

    private ModelFailureClassifier() {
    }

    /**
     * 根据 HTTP 状态码分类失败原因。仅用于非 2xx 响应。
     */
    public static String classifyHttpStatus(int statusCode) {
        if (statusCode == 401 || statusCode == 403) return HTTP_401_OR_403;
        if (statusCode == 429) return HTTP_429;
        if (statusCode >= 500 && statusCode < 600) return HTTP_5XX;
        return UNKNOWN_CALL_FAILED;
    }

    /**
     * 根据异常分类失败原因。用于请求本身抛异常（超时 / 网络断开 / JSON 解析失败）。
     */
    public static String classifyException(Exception failure) {
        if (failure == null) return UNKNOWN_CALL_FAILED;
        if (failure instanceof HttpTimeoutException) return REQUEST_TIMEOUT;
        if (failure instanceof ConnectException) return NETWORK_ERROR;
        if (failure instanceof ModelGatewayService.ModelHttpException) {
            return classifyHttpStatus(((ModelGatewayService.ModelHttpException) failure).statusCode());
        }
        String message = failure.getMessage() == null ? "" : failure.getMessage().toLowerCase();
        // JSON 解析失败：网关 parseModelJson / extractJsonObject 抛出的 IOException。
        if (message.contains("json") || message.contains("not json") || message.contains("empty model content")) {
            return JSON_PARSE_FAILED;
        }
        if (failure instanceof IOException) return NETWORK_ERROR;
        return UNKNOWN_CALL_FAILED;
    }

    /**
     * 把分类码翻译成中文人话，供 fallbackReason / 诊断信息使用。
     */
    public static String humanReason(String code, String providerName) {
        String provider = providerName == null || providerName.isBlank() ? "模型" : providerName;
        return switch (code) {
            case REQUEST_TIMEOUT -> provider + " 请求超时，模型在设定时间内没有返回，本次先展示本地事实摘要。";
            case HTTP_401_OR_403 -> provider + " 返回鉴权失败（401/403），可能是 API key 错误或权限不足。";
            case HTTP_429 -> provider + " 返回 429，可能是限流，请稍后重试。";
            case HTTP_5XX -> provider + " 服务异常（5xx），本次先展示本地事实摘要，可稍后重新分析。";
            case NETWORK_ERROR -> "网络连接失败，可能与代理或 baseUrl 有关，本次先展示本地事实摘要。";
            case JSON_PARSE_FAILED -> "模型返回内容不是可解析结构，本次先展示本地事实摘要。";
            case EVIDENCE_REJECTED -> "模型结果引用的证据不可用，本次先展示本地事实摘要。";
            default -> provider + " 调用失败，本次先展示本地事实摘要。";
        };
    }
}
