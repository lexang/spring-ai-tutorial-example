package com.example.springai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

/**
 * 统一返回中文错误，并避免把供应商异常详情直接暴露给调用方。
 */
@RestControllerAdvice
public class CoreConceptsExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CoreConceptsExceptionHandler.class);

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> handleBinding(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "请求参数不合法", "message", "请求参数缺失或类型不正确"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "请求参数不合法",
                "message", exception.getMessage() == null ? "请检查请求参数" : exception.getMessage()
        ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleModelState(IllegalStateException exception) {
        log.warn("模型响应状态异常：{}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", "模型服务未返回可用结果",
                "message", "请检查模型、凭据和网络配置"
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception exception) {
        log.error("核心概念示例处理请求失败，异常类型：{}", exception.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(Map.of(
                "error", "服务处理失败",
                "message", "请查看服务端日志"
        ));
    }
}
