package com.projectflow.service;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.projectflow.support.AppException;
import com.projectflow.entity.ModelProtocol;

@Service
public class AiProviderUrlGuard {
    private static final Set<String> LOCAL_DEVELOPMENT_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    public String validateBaseUrl(String baseUrl) {
        URI uri = parse(baseUrl);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = normalizedHost(uri);
        if (host.isBlank()) {
            throw blocked("AI provider URL must include a host.");
        }
        boolean localDevelopmentHost = LOCAL_DEVELOPMENT_HOSTS.contains(host);
        if (!"https".equals(scheme) && !("http".equals(scheme) && localDevelopmentHost)) {
            throw blocked("AI provider URL must use HTTPS. Local HTTP is only allowed for localhost development.");
        }
        if (!localDevelopmentHost && isBlockedIpLiteral(host)) {
            throw blocked("AI provider URL cannot target private, loopback, or metadata IP ranges.");
        }
        if (uri.getUserInfo() != null) {
            throw blocked("AI provider URL cannot contain credentials.");
        }
        return trimTrailingSlash(uri.toString());
    }

    public URI chatCompletionsUri(String baseUrl) {
        return endpointUri(baseUrl, ModelProtocol.OPENAI_CHAT_COMPLETIONS, null);
    }

    public URI endpointUri(String baseUrl, ModelProtocol protocol, String endpointOverride) {
        if (endpointOverride != null && !endpointOverride.isBlank()) {
            URI endpoint = URI.create(validateBaseUrl(endpointOverride));
            if (endpoint.getRawFragment() != null) {
                throw blocked("Endpoint override cannot contain a fragment.");
            }
            if (!endpoint.getPath().endsWith(endpointSuffix(protocol))) {
                throw blocked("Endpoint override must end with the selected protocol path: " + endpointSuffix(protocol));
            }
            return endpoint;
        }
        String validated = validateBaseUrl(baseUrl);
        URI base = URI.create(validated);
        if (base.getRawQuery() != null || base.getRawFragment() != null) {
            throw blocked("Base URL cannot contain query or fragment; use the full endpoint override instead.");
        }
        String suffix = endpointSuffix(protocol);
        return URI.create(base.getPath().endsWith(suffix) ? validated : validated + suffix);
    }

    /** 官方 SDK 接收服务根地址；显式 endpoint 通过剥离标准协议后缀安全映射为 SDK baseUrl。 */
    public String sdkBaseUrl(String baseUrl, ModelProtocol protocol, String endpointOverride) {
        URI endpointUri = endpointUri(baseUrl, protocol, endpointOverride);
        if (endpointUri.getRawQuery() != null || endpointUri.getRawFragment() != null) {
            throw blocked("Official SDK endpoints cannot contain query or fragment parameters.");
        }
        String endpoint = endpointUri.toString();
        String suffix = endpointSuffix(protocol);
        return trimTrailingSlash(endpoint.substring(0, endpoint.length() - suffix.length()));
    }

    public String endpointSuffix(ModelProtocol protocol) {
        return switch (protocol) {
            case OPENAI_RESPONSES -> "/responses";
            case OPENAI_CHAT_COMPLETIONS -> "/chat/completions";
            case ANTHROPIC_MESSAGES -> "/v1/messages";
        };
    }

    private URI parse(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl == null ? "" : baseUrl.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw blocked("AI provider URL is invalid.");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw blocked("AI provider URL is invalid.");
        }
    }

    private String normalizedHost(URI uri) {
        String host = uri.getHost();
        return host == null ? "" : host.toLowerCase(Locale.ROOT);
    }

    private boolean isBlockedIpLiteral(String host) {
        if (host.equals("0.0.0.0") || host.startsWith("127.") || host.startsWith("169.254.")) {
            return true;
        }
        if (host.startsWith("10.") || host.startsWith("192.168.")) {
            return true;
        }
        if (host.matches("172\\.(1[6-9]|2\\d|3[0-1])\\..*")) {
            return true;
        }
        return host.equals("::") || host.equals("0:0:0:0:0:0:0:1") || host.equals("::1");
    }

    private AppException blocked(String message) {
        return new AppException("AI_PROVIDER_URL_BLOCKED", message, HttpStatus.BAD_REQUEST);
    }

    private String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }
}
