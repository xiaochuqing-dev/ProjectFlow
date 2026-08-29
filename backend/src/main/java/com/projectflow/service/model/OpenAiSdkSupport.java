package com.projectflow.service.model;

import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.Timeout;
import com.projectflow.entity.AiProvider;
import com.projectflow.entity.AiProviderAuthMode;
import com.projectflow.service.AiProviderHeaderPolicy;
import com.projectflow.service.AiProviderUrlGuard;

final class OpenAiSdkSupport {
    private OpenAiSdkSupport() {}

    static OpenAIOkHttpClient.Builder clientBuilder(CanonicalModelRequest request, AiProviderUrlGuard urlGuard) {
        AiProvider provider = request.provider();
        String key = request.credential();
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Provider credential unavailable");
        }
        OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder()
            .baseUrl(urlGuard.sdkBaseUrl(provider.getBaseUrl(), provider.getProtocol(), provider.getEndpointOverride()))
            .apiKey(key)
            .timeout(Timeout.builder()
                .connect(request.connectionTimeout())
                .read(request.requestTimeout())
                .write(request.requestTimeout())
                .request(request.requestTimeout())
                .build())
            .maxRetries(0);
        AiProviderAuthMode mode = provider.getAuthMode();
        if (mode == AiProviderAuthMode.API_KEY_HEADER) {
            builder.removeHeaders("Authorization").putHeader(headerName(provider), key);
        } else if (mode == AiProviderAuthMode.QUERY_API_KEY) {
            builder.removeHeaders("Authorization").putQueryParam(queryName(provider), key);
        } else if (mode == AiProviderAuthMode.NONE) {
            builder.removeHeaders("Authorization");
        }
        AiProviderHeaderPolicy.requireSafe(provider.getSafeHeaders()).forEach(builder::putHeader);
        return builder;
    }

    private static String headerName(AiProvider provider) {
        return AiProviderHeaderPolicy.requireCredentialHeaderName(
            provider.getAuthHeaderName() == null ? "x-api-key" : provider.getAuthHeaderName()
        );
    }

    private static String queryName(AiProvider provider) {
        return AiProviderHeaderPolicy.requireCredentialQueryName(
            provider.getQueryKeyName() == null ? "api_key" : provider.getQueryKeyName()
        );
    }
}
