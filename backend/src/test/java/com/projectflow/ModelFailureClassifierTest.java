package com.projectflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;

import org.junit.jupiter.api.Test;

import com.projectflow.service.ModelFailureClassifier;
import com.projectflow.service.ModelGatewayService;

/**
 * V3.3.4 小阶段修复：模型失败原因分类测试。
 * 验证超时 / 鉴权 / 限流 / 5xx / 网络 / JSON 解析失败 / 证据被拒 / 未知 都能正确分类并翻译成人话。
 */
class ModelFailureClassifierTest {

    @Test
    void classifiesHttpRequestTimeout() {
        Exception timeout = new HttpTimeoutException("request timed out");
        assertThat(ModelFailureClassifier.classifyException(timeout)).isEqualTo(ModelFailureClassifier.REQUEST_TIMEOUT);
        assertThat(ModelFailureClassifier.humanReason(ModelFailureClassifier.REQUEST_TIMEOUT, "DeepSeek"))
            .contains("请求超时")
            .contains("DeepSeek");
    }

    @Test
    void classifiesConnectExceptionAsNetworkError() {
        Exception connect = new ConnectException("Connection refused");
        assertThat(ModelFailureClassifier.classifyException(connect)).isEqualTo(ModelFailureClassifier.NETWORK_ERROR);
        assertThat(ModelFailureClassifier.humanReason(ModelFailureClassifier.NETWORK_ERROR, "DeepSeek"))
            .contains("网络连接失败")
            .contains("代理");
    }

    @Test
    void classifiesHttpStatus401AsAuthFailure() {
        assertThat(ModelFailureClassifier.classifyHttpStatus(401)).isEqualTo(ModelFailureClassifier.PROVIDER_AUTH_FAILED);
        assertThat(ModelFailureClassifier.classifyHttpStatus(403)).isEqualTo(ModelFailureClassifier.PROVIDER_AUTH_FAILED);
        assertThat(ModelFailureClassifier.humanReason(ModelFailureClassifier.PROVIDER_AUTH_FAILED, "DeepSeek"))
            .contains("鉴权失败")
            .contains("401/403");
    }

    @Test
    void classifiesHttpStatus429AsRateLimit() {
        assertThat(ModelFailureClassifier.classifyHttpStatus(429)).isEqualTo(ModelFailureClassifier.PROVIDER_RATE_LIMITED);
        assertThat(ModelFailureClassifier.humanReason(ModelFailureClassifier.PROVIDER_RATE_LIMITED, "DeepSeek"))
            .contains("429")
            .contains("限流");
    }

    @Test
    void classifiesHttpStatus5xxAsServerError() {
        assertThat(ModelFailureClassifier.classifyHttpStatus(500)).isEqualTo(ModelFailureClassifier.PROVIDER_5XX);
        assertThat(ModelFailureClassifier.classifyHttpStatus(502)).isEqualTo(ModelFailureClassifier.PROVIDER_5XX);
        assertThat(ModelFailureClassifier.classifyHttpStatus(503)).isEqualTo(ModelFailureClassifier.PROVIDER_5XX);
        assertThat(ModelFailureClassifier.humanReason(ModelFailureClassifier.PROVIDER_5XX, "DeepSeek"))
            .contains("5xx")
            .contains("服务异常");
    }

    @Test
    void classifiesJsonParseException() {
        Exception jsonError = new IOException("model content is not JSON");
        assertThat(ModelFailureClassifier.classifyException(jsonError)).isEqualTo(ModelFailureClassifier.JSON_PARSE_FAILED);
        assertThat(ModelFailureClassifier.humanReason(ModelFailureClassifier.JSON_PARSE_FAILED, "DeepSeek"))
            .contains("JSON 语法");
    }

    @Test
    void classifiesEmptyModelContentAsJsonParseFailed() {
        Exception emptyContent = new IOException("empty model content");
        assertThat(ModelFailureClassifier.classifyException(emptyContent)).isEqualTo(ModelFailureClassifier.JSON_PARSE_FAILED);
    }

    @Test
    void distinguishesTypedEmptyContentAndTruncationFailures() {
        Exception empty = new ModelGatewayService.ModelEmptyContentException("empty", null);
        Exception truncated = new ModelGatewayService.ModelOutputTruncatedException("truncated", null, null);

        assertThat(ModelFailureClassifier.classifyException(empty)).isEqualTo(ModelFailureClassifier.EMPTY_CONTENT);
        assertThat(ModelFailureClassifier.classifyException(truncated)).isEqualTo(ModelFailureClassifier.OUTPUT_BUDGET_EXHAUSTED);
        assertThat(ModelFailureClassifier.humanReason(ModelFailureClassifier.OUTPUT_BUDGET_EXHAUSTED, "DeepSeek")).contains("预算");
    }

    @Test
    void classifiesModelHttpExceptionByStatus() {
        ModelGatewayService.ModelHttpException http401 = new ModelGatewayService.ModelHttpException(401);
        ModelGatewayService.ModelHttpException http500 = new ModelGatewayService.ModelHttpException(500);
        assertThat(ModelFailureClassifier.classifyException(http401)).isEqualTo(ModelFailureClassifier.PROVIDER_AUTH_FAILED);
        assertThat(ModelFailureClassifier.classifyException(http500)).isEqualTo(ModelFailureClassifier.PROVIDER_5XX);
    }

    @Test
    void classifiesUnknownExceptionAsUnknownCallFailed() {
        Exception unknown = new RuntimeException("something unexpected");
        assertThat(ModelFailureClassifier.classifyException(unknown)).isEqualTo(ModelFailureClassifier.UNKNOWN_CALL_FAILED);
        assertThat(ModelFailureClassifier.humanReason(ModelFailureClassifier.UNKNOWN_CALL_FAILED, "DeepSeek"))
            .contains("调用失败");
    }

    @Test
    void classifiesNullExceptionAsUnknown() {
        assertThat(ModelFailureClassifier.classifyException(null)).isEqualTo(ModelFailureClassifier.UNKNOWN_CALL_FAILED);
    }

    @Test
    void classifiesGenericIOExceptionAsNetworkError() {
        Exception io = new IOException("broken pipe");
        assertThat(ModelFailureClassifier.classifyException(io)).isEqualTo(ModelFailureClassifier.NETWORK_ERROR);
    }

    @Test
    void humanReasonUsesModelWhenProviderNameBlank() {
        String reason = ModelFailureClassifier.humanReason(ModelFailureClassifier.REQUEST_TIMEOUT, null);
        assertThat(reason).contains("模型").contains("请求超时");
    }
}
