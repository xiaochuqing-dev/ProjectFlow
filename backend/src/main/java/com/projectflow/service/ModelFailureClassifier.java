package com.projectflow.service;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
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
    public static final String PROVIDER_AUTH_FAILED = "PROVIDER_AUTH_FAILED";
    public static final String PROVIDER_RATE_LIMITED = "PROVIDER_RATE_LIMITED";
    public static final String PROVIDER_5XX = "PROVIDER_5XX";
    public static final String NETWORK_ERROR = "NETWORK_ERROR";
    public static final String EMPTY_CONTENT = "EMPTY_CONTENT";
    public static final String OUTPUT_BUDGET_EXHAUSTED = "OUTPUT_BUDGET_EXHAUSTED";
    public static final String REASONING_EXHAUSTED_OUTPUT = "REASONING_EXHAUSTED_OUTPUT";
    public static final String JSON_PARSE_FAILED = "JSON_PARSE_FAILED";
    public static final String SCHEMA_MISMATCH = "SCHEMA_MISMATCH";
    public static final String SCHEMA_REPAIR_FAILED = "SCHEMA_REPAIR_FAILED";
    public static final String SCHEMA_UNRECOGNIZED = SCHEMA_MISMATCH;
    public static final String EVIDENCE_REJECTED = "EVIDENCE_REJECTED";
    public static final String SECRET_STORE_UNAVAILABLE = "SECRET_STORE_UNAVAILABLE";
    public static final String SECRET_MIGRATION_FAILED = "SECRET_MIGRATION_FAILED";
    public static final String SECRET_CLEANUP_FAILED = "SECRET_CLEANUP_FAILED";
    public static final String UNKNOWN_CALL_FAILED = "UNKNOWN_CALL_FAILED";
    // 兼容旧诊断读取；新写入统一使用上方 V3.3.8 语义。
    public static final String HTTP_401_OR_403 = PROVIDER_AUTH_FAILED;
    public static final String HTTP_429 = PROVIDER_RATE_LIMITED;
    public static final String HTTP_5XX = PROVIDER_5XX;
    public static final String OUTPUT_TRUNCATED = OUTPUT_BUDGET_EXHAUSTED;

    private ModelFailureClassifier() {
    }

    /**
     * 根据 HTTP 状态码分类失败原因。仅用于非 2xx 响应。
     */
    public static String classifyHttpStatus(int statusCode) {
        if (statusCode == 401 || statusCode == 403) return PROVIDER_AUTH_FAILED;
        if (statusCode == 429) return PROVIDER_RATE_LIMITED;
        if (statusCode >= 500 && statusCode < 600) return PROVIDER_5XX;
        return UNKNOWN_CALL_FAILED;
    }

    /**
     * 根据异常分类失败原因。用于请求本身抛异常（超时 / 网络断开 / JSON 解析失败）。
     */
    public static String classifyException(Exception failure) {
        if (failure == null) return UNKNOWN_CALL_FAILED;
        if (failure instanceof ModelGatewayService.ModelCredentialException credential) return credential.code();
        if (failure instanceof ModelGatewayService.ModelHttpException) {
            return classifyHttpStatus(((ModelGatewayService.ModelHttpException) failure).statusCode());
        }
        if (failure instanceof ModelGatewayService.ModelSchemaRepairException) return SCHEMA_REPAIR_FAILED;
        if (failure instanceof ModelGatewayService.ModelSchemaMismatchException) return SCHEMA_MISMATCH;
        if (failure instanceof ModelGatewayService.ModelOutputTruncatedException truncated) {
            return truncated.diagnostics() != null && truncated.diagnostics().reasoningBudgetExhausted()
                ? REASONING_EXHAUSTED_OUTPUT : OUTPUT_BUDGET_EXHAUSTED;
        }
        if (failure instanceof ModelGatewayService.ModelEmptyContentException) return EMPTY_CONTENT;
        if (failure instanceof ModelGatewayService.ModelResponseFormatException) return JSON_PARSE_FAILED;
        if (hasCause(failure, HttpTimeoutException.class)
            || hasCause(failure, SocketTimeoutException.class)
            || causeMessageContains(failure, "timeout")
            || causeMessageContains(failure, "timed out")) {
            return REQUEST_TIMEOUT;
        }
        if (hasCause(failure, ConnectException.class)) return NETWORK_ERROR;
        String message = failure.getMessage() == null ? "" : failure.getMessage().toLowerCase();
        // JSON 解析失败：网关 parseModelJson / extractJsonObject 抛出的 IOException。
        if (message.contains("json") || message.contains("not json") || message.contains("empty model content")) {
            return JSON_PARSE_FAILED;
        }
        if (failure instanceof IOException) return NETWORK_ERROR;
        return UNKNOWN_CALL_FAILED;
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 12; depth++, current = current.getCause()) {
            if (type.isInstance(current)) return true;
        }
        return false;
    }

    private static boolean causeMessageContains(Throwable failure, String marker) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 12; depth++, current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains(marker)) return true;
        }
        return false;
    }

    /**
     * 把分类码翻译成中文人话，供 fallbackReason / 诊断信息使用。
     */
    public static String humanReason(String code, String providerName) {
        String provider = providerName == null || providerName.isBlank() ? "模型" : providerName;
        return switch (code) {
            case REQUEST_TIMEOUT -> provider + " 请求超时，模型在设定时间内没有返回，本次先展示本地事实摘要。";
            case PROVIDER_AUTH_FAILED -> provider + " 返回鉴权失败（401/403），请检查 API Key 和模型权限；不会自动重试。";
            case PROVIDER_RATE_LIMITED -> provider + " 返回限流（429），已停止本次分析，可稍后重试。";
            case PROVIDER_5XX -> provider + " 服务异常（5xx），已停止本次分析并保留旧结果。";
            case NETWORK_ERROR -> "网络连接失败，可能与代理或 baseUrl 有关，本次先展示本地事实摘要。";
            case EMPTY_CONTENT -> "模型服务已响应，但没有返回可分析内容，本次先展示本地事实摘要。";
            case OUTPUT_BUDGET_EXHAUSTED -> "模型输出预算耗尽；系统已提高预算执行一次截断恢复，仍失败时保留可恢复条目或旧结果。";
            case REASONING_EXHAUSTED_OUTPUT -> "模型 reasoning 疑似占满共享预算；系统已提高可见输出预算重试，仍失败时保留旧结果。";
            case JSON_PARSE_FAILED -> "模型已返回内容，但 JSON 语法无法解析，本次先展示本地事实摘要。";
            case SCHEMA_MISMATCH -> "模型返回 JSON 可以读取，但结构不符合目标；系统会执行一次定向 Schema 修复。";
            case SCHEMA_REPAIR_FAILED -> "模型结果结构偏离目标，定向 Schema 修复仍未成功；已保留旧结果。";
            case EVIDENCE_REJECTED -> "模型结果引用的证据不可用，本次先展示本地事实摘要。";
            case SECRET_STORE_UNAVAILABLE, SECRET_MIGRATION_FAILED -> provider + " 的安全凭据存储不可用，未发起模型请求。";
            case SECRET_CLEANUP_FAILED -> provider + " 的安全凭据清理失败，未删除模型配置。";
            default -> provider + " 调用失败，本次先展示本地事实摘要。";
        };
    }
}
