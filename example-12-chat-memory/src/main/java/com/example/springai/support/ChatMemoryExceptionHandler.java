package com.example.springai.support;

import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 将示例异常转换为稳定的中文 HTTP 错误响应。
 */
@RestControllerAdvice
public class ChatMemoryExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatMemoryExceptionHandler.class);

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiError> handleInvalidRequest(
            InvalidRequestException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(FeatureUnavailableException.class)
    public ResponseEntity<ApiError> handleFeatureUnavailable(
            FeatureUnavailableException exception, HttpServletRequest request) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), request);
    }

    @ExceptionHandler(ChatOperationException.class)
    public ResponseEntity<ApiError> handleChatOperation(
            ChatOperationException exception, HttpServletRequest request) {
        log.warn("模型或向量记忆操作失败，类型={}", exception.getClass().getSimpleName());
        return response(HttpStatus.BAD_GATEWAY, exception.getMessage(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "请求体不是有效的 JSON，或字段类型不正确", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(
            IllegalArgumentException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "请求参数不合法", request);
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new ApiError(
                        Instant.now(),
                        status.value(),
                        errorName(status),
                        message,
                        request.getRequestURI()));
    }

    private String errorName(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "请求错误";
            case SERVICE_UNAVAILABLE -> "服务暂不可用";
            case BAD_GATEWAY -> "上游服务错误";
            default -> "请求处理失败";
        };
    }

    public record ApiError(
            Instant timestamp,
            int status,
            String error,
            String message,
            String path) {
    }
}
