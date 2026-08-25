package com.projectflow.migration;

/** Safe startup failure carrying a stable migration/recovery code. */
public final class SchemaMigrationException extends RuntimeException {
    private final String code;

    public SchemaMigrationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public SchemaMigrationException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
