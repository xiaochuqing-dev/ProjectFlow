package com.projectflow.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Explicit test/CI-only credential store. It never writes to disk.
 */
public final class InMemoryProviderCredentialStore implements ProviderCredentialStore {
    private static final String PREFIX = "memory:v1:";
    private static final int MAX_SECRET_BYTES = 64 * 1024;

    private final Map<String, byte[]> values = new ConcurrentHashMap<>();

    @Override
    public String writeAndVerify(UUID providerId, String secret) {
        UUID id = requireProviderId(providerId);
        byte[] candidate = utf8Secret(secret);
        String ref = PREFIX + id;
        byte[] previous = values.put(ref, candidate);
        clear(previous);
        try {
            String readBack = read(ref);
            if (!MessageDigest.isEqual(candidate, readBack.getBytes(StandardCharsets.UTF_8))) {
                delete(ref);
                throw new ProviderCredentialStoreException("SECRET_STORE_VERIFY_FAILED", "凭据存储回读校验失败。");
            }
            return ref;
        } catch (RuntimeException failure) {
            if (values.containsKey(ref)) delete(ref);
            throw failure;
        }
    }

    @Override
    public String read(String secretRef) {
        String ref = requireReference(secretRef);
        byte[] value = values.get(ref);
        if (value == null) {
            throw new ProviderCredentialStoreException("SECRET_NOT_FOUND", "凭据不存在或已被删除。");
        }
        return new String(value.clone(), StandardCharsets.UTF_8);
    }

    @Override
    public void delete(String secretRef) {
        if (secretRef == null || secretRef.isBlank()) return;
        byte[] removed = values.remove(secretRef);
        clear(removed);
    }

    @Override
    public Status status(String secretRef) {
        if (secretRef == null || secretRef.isBlank()) return Status.MISSING;
        if (!secretRef.startsWith(PREFIX)) return Status.INVALID;
        return values.containsKey(secretRef) ? Status.CONFIGURED : Status.MISSING;
    }

    /** Test helper; clears all in-memory values without exposing them. */
    public void clearAll() {
        values.values().forEach(InMemoryProviderCredentialStore::clear);
        values.clear();
    }

    private static UUID requireProviderId(UUID providerId) {
        if (providerId == null) {
            throw new ProviderCredentialStoreException("SECRET_PROVIDER_ID_INVALID", "Provider 标识无效。");
        }
        return providerId;
    }

    private static String requireReference(String secretRef) {
        if (secretRef == null || secretRef.isBlank() || !secretRef.startsWith(PREFIX)) {
            throw new ProviderCredentialStoreException("SECRET_REF_INVALID", "凭据引用无效。");
        }
        try {
            UUID.fromString(secretRef.substring(PREFIX.length()));
        } catch (IllegalArgumentException exception) {
            throw new ProviderCredentialStoreException("SECRET_REF_INVALID", "凭据引用无效。", exception);
        }
        return secretRef;
    }

    private static byte[] utf8Secret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new ProviderCredentialStoreException("SECRET_EMPTY", "凭据不能为空。");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_SECRET_BYTES) {
            clear(bytes);
            throw new ProviderCredentialStoreException("SECRET_TOO_LARGE", "凭据长度超出允许范围。");
        }
        return bytes;
    }

    private static void clear(byte[] value) {
        if (value != null) Arrays.fill(value, (byte) 0);
    }
}
