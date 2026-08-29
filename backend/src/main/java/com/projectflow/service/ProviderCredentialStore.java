package com.projectflow.service;

import java.util.UUID;

/**
 * The only persistence boundary for Provider credentials.
 *
 * Implementations must never return a credential in diagnostics, DTOs or
 * exceptions. The returned value from {@link #read(String)} is request-scoped
 * and callers must not retain it after the Model Gateway request completes.
 */
public interface ProviderCredentialStore {
    enum Status {
        CONFIGURED,
        MISSING,
        UNAVAILABLE,
        INVALID
    }

    /**
     * Writes a credential, reads it back and verifies the value before
     * returning its opaque reference.
     */
    String writeAndVerify(UUID providerId, String secret);

    /** Reads a request-scoped credential or throws a safe store exception. */
    String read(String secretRef);

    /** Deletes a credential reference. Missing references are idempotent. */
    void delete(String secretRef);

    /** Returns safe availability state without returning secret material. */
    Status status(String secretRef);

    default boolean available() {
        return true;
    }
}
