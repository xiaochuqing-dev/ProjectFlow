package com.projectflow.service;

/** Safe, machine-readable failure from a ProviderCredentialStore. */
public final class ProviderCredentialStoreException extends RuntimeException {
    private final String code;

    public ProviderCredentialStoreException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ProviderCredentialStoreException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
