package com.example.springai;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 统一处理示例接口异常，向客户端返回稳定的中文错误结构，避免泄漏模型供应商或本地文件路径。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(EmbeddingBatchController.EmbeddingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleEmbeddingFailure(
            EmbeddingBatchController.EmbeddingFailureException exception, HttpServletRequest request) {
        log.warn("嵌入模型失败，类型={}", exception.getCause().getClass().getSimpleName());
        return buildResponse(HttpStatus.BAD_GATEWAY, "嵌入模型未返回有效向量，请检查模型配置", request.getRequestURI());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException exception,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, Object>> handleMalformedRequest(Exception exception,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "请求参数格式不正确", request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("处理请求失败，类型={}", exception.getClass().getSimpleName());
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "服务暂时不可用，请稍后重试",
                request.getRequestURI());
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message, String path) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status == HttpStatus.BAD_REQUEST ? "请求参数错误" : "服务器错误");
        body.put("message", message == null || message.isBlank() ? "请求无效" : message);
        body.put("path", path);
        return ResponseEntity.status(status).body(body);
    }
}
