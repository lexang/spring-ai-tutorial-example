package com.example.springai;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 为示例 REST API 提供稳定、中文且不泄露服务端细节的错误响应。 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(QAService.ChatDisabledException.class)
    public ResponseEntity<ApiError> handleChatDisabled(QAService.ChatDisabledException exception,
            HttpServletRequest request) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "CHAT_DISABLED", exception.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleInvalidArgument(
            IllegalArgumentException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", exception.getMessage(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_JSON",
                "请求体 JSON 格式错误，或字段类型与接口定义不一致", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(
            MissingServletRequestParameterException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
                "缺少必填参数: " + exception.getParameterName(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER_TYPE",
                "参数格式不正确: " + exception.getName(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("请求处理失败，异常类型={}", exception.getClass().getSimpleName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "服务器处理请求失败，请查看服务端日志", request);
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status, String code, String message, HttpServletRequest request) {
        ApiError body = new ApiError(
                code,
                message != null && !message.isBlank() ? message : "请求处理失败",
                request.getRequestURI(),
                OffsetDateTime.now(ZoneOffset.UTC));
        return ResponseEntity.status(status).body(body);
    }

    public record ApiError(String code, String message, String path, OffsetDateTime timestamp) {
    }
}
