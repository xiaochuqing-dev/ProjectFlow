package com.projectflow.dto;

public record ApiErrorResponse(ApiError error) {
    public record ApiError(String code, String message) {
    }
}
