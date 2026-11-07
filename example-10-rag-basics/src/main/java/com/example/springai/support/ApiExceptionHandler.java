package com.example.springai.support;

import java.time.Instant;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

/**
 * 将示例接口异常转换为稳定的中文错误响应。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(RagRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(
            RagRequestException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(error("INVALID_REQUEST", exception.getMessage(), request));
    }

    @ExceptionHandler(RagUpstreamException.class)
    public ResponseEntity<ApiError> handleUpstream(
            RagUpstreamException exception, HttpServletRequest request) {
        logger.warn("RAG 上游响应不可用：{}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(error("UPSTREAM_RESPONSE_INVALID", exception.getMessage(), request));
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ApiError> handleInvalidRequest(
            Exception exception, HttpServletRequest request) {
        logger.warn("RAG 请求参数不合法，类型={}", exception.getClass().getSimpleName());
        return ResponseEntity.badRequest()
                .body(error("INVALID_REQUEST", "请求参数不合法", request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(
            Exception exception, HttpServletRequest request) {
        logger.error("RAG 请求处理失败，类型={}", exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error("RAG_PROCESSING_FAILED", "RAG 请求处理失败，请检查模型与网络配置", request));
    }

    private ApiError error(String code, String message, HttpServletRequest request) {
        return new ApiError(code, message, request.getRequestURI(), Instant.now());
    }

    public record ApiError(String code, String message, String path, Instant timestamp) {
    }
}
