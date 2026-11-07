package com.example.springai;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 统一入门示例的参数错误和模型错误。 */
@RestControllerAdvice
public class GettingStartedExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GettingStartedExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalidInput(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> invalidBinding(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of("message", "请求参数缺失或类型不正确"));
    }

    @ExceptionHandler(AiServiceException.class)
    public ResponseEntity<Map<String, String>> unavailable(AiServiceException exception) {
        // 日志只记录错误类别，避免异常正文包含供应商凭据、请求或响应。
        log.warn("模型调用失败，错误类别：{}", exception.getCause() == null
                ? exception.getClass().getSimpleName() : exception.getCause().getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", exception.getMessage()));
    }
}
