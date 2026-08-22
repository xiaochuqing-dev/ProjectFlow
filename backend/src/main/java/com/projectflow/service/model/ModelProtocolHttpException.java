package com.projectflow.service.model;

import java.io.IOException;

public final class ModelProtocolHttpException extends IOException {
    private final int statusCode;
    private final String errorType;
    private final String errorCode;
    private final String errorParam;

    public ModelProtocolHttpException(int statusCode, Throwable cause) {
        this(statusCode, "", "", "", cause);
    }

    public ModelProtocolHttpException(
        int statusCode,
        String errorType,
        String errorCode,
        String errorParam,
        Throwable cause
    ) {
        super("model HTTP " + statusCode, cause);
        this.statusCode = statusCode;
        this.errorType = safeToken(errorType);
        this.errorCode = safeToken(errorCode);
        this.errorParam = safeToken(errorParam);
    }

    public int statusCode() { return statusCode; }
    public String errorType() { return errorType; }
    public String errorCode() { return errorCode; }
    public String errorParam() { return errorParam; }

    private static String safeToken(String value) {
        if (value == null || value.isBlank()) return "";
        String safe = value.trim().replaceAll("[^A-Za-z0-9_.:-]", "_");
        return safe.length() <= 120 ? safe : safe.substring(0, 120);
    }
}
