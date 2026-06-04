package com.projectflow.support;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.projectflow.dto.ApiErrorResponse;
import com.projectflow.dto.ApiErrorResponse.ApiError;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(AppException.class)
    ResponseEntity<ApiErrorResponse> handleAppException(AppException exception) {
        return ResponseEntity
            .status(exception.getStatus())
            .body(new ApiErrorResponse(new ApiError(exception.getCode(), exception.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation() {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new ApiErrorResponse(new ApiError("VALIDATION_ERROR", "Request validation failed")));
    }
}
