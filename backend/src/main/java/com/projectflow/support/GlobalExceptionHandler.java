package com.projectflow.support;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiErrorResponse> handleMaxUploadSizeExceeded() {
        return ResponseEntity
            .status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(new ApiErrorResponse(new ApiError("UPLOAD_TOO_LARGE", "项目 zip 超过本地导入上限。请删除 node_modules、构建产物、日志和大型二进制资源后重新压缩。")));
    }
}
