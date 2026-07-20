package com.projectflow.service.model;

import java.io.IOException;

public final class ModelProtocolHttpException extends IOException {
    private final int statusCode;

    public ModelProtocolHttpException(int statusCode, Throwable cause) {
        super("model HTTP " + statusCode, cause);
        this.statusCode = statusCode;
    }

    public int statusCode() { return statusCode; }
}
