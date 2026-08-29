package com.projectflow.service;

import java.util.UUID;

/**
 * Explicit release-platform failure. It deliberately has no plaintext
 * fallback, environment-file fallback or database fallback.
 */
public final class UnavailableProviderCredentialStore implements ProviderCredentialStore {
    private static final String MESSAGE = "当前运行平台未提供受支持的 Provider 凭据存储，请在受支持环境中重新配置。";

    @Override
    public String writeAndVerify(UUID providerId, String secret) {
        throw unavailable();
    }

    @Override
    public String read(String secretRef) {
        throw unavailable();
    }

    @Override
    public void delete(String secretRef) {
        if (secretRef != null && !secretRef.isBlank()) throw unavailable();
    }

    @Override
    public Status status(String secretRef) {
        return secretRef == null || secretRef.isBlank() ? Status.MISSING : Status.UNAVAILABLE;
    }

    @Override
    public boolean available() {
        return false;
    }

    private ProviderCredentialStoreException unavailable() {
        return new ProviderCredentialStoreException("SECRET_STORE_UNAVAILABLE", MESSAGE);
    }
}
