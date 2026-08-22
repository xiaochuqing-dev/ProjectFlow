package com.projectflow.service.model;

import java.io.IOException;
import java.util.regex.Pattern;

public final class ModelProtocolHttpException extends IOException {
    private static final Pattern SAFE_TOKEN = Pattern.compile("[a-z][a-z0-9_.:-]{0,63}");
    private static final Pattern CREDENTIAL_SHAPED = Pattern.compile(
        "(?:^|[_.:-])(?:sk|pk|rk|ark|bearer|token|secret|authorization|password|credential)(?:$|[_.:-])"
    );
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
        String safe = value.trim();
        if (!SAFE_TOKEN.matcher(safe).matches() || CREDENTIAL_SHAPED.matcher(safe).find()) return "";
        return safe;
    }
}
