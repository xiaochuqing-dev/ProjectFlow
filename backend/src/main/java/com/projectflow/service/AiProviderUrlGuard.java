package com.projectflow.service;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.projectflow.support.AppException;

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
        return uri.toString();
    }

    public URI chatCompletionsUri(String baseUrl) {
        return URI.create(validateBaseUrl(baseUrl) + "/chat/completions");
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
}
