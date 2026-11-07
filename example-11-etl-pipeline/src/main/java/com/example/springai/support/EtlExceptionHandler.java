package com.example.springai.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一返回中文错误信息，同时避免把本地文件路径和底层异常细节暴露给调用方。
 */
@RestControllerAdvice(basePackages = "com.example.springai")
public class EtlExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(EtlExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException exception) {
        log.warn("ETL 请求参数无效：{}", exception.getMessage());
        return ResponseEntity.badRequest()
                .body(errorBody(HttpStatus.BAD_REQUEST.value(), "请求参数无效", exception.getMessage()));
    }

    @ExceptionHandler(EtlOperationException.class)
    public ResponseEntity<Map<String, Object>> handleOperationFailure(EtlOperationException exception) {
        log.warn("ETL 文档处理未完成：{}", exception.getMessage());
        log.debug("ETL 文档处理异常详情", exception);
        return ResponseEntity.status(422)
                .body(errorBody(422, "文档处理失败", exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpectedFailure(Exception exception) {
        log.error("ETL 服务发生未预期错误", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody(HttpStatus.INTERNAL_SERVER_ERROR.value(), "服务器处理失败",
                        "文档处理失败，请查看服务端日志。"));
    }

    private Map<String, Object> errorBody(int status, String error, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("status", status);
        body.put("error", error);
        body.put("message", message);
        return body;
    }
}
