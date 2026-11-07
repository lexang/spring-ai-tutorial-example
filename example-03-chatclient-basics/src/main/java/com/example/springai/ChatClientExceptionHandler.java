package com.example.springai;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 将示例接口的失败转换为稳定的中文错误响应，不向客户端透传供应商异常。
 */
@RestControllerAdvice
public class ChatClientExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatClientExceptionHandler.class);

    @ExceptionHandler(InvalidDemoRequestException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRequest(InvalidDemoRequestException exception) {
        return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", exception.getMessage()
        ));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String, Object>> handleMalformedRequest(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "请求参数格式不正确"
        ));
    }

    @ExceptionHandler(EmptyChatResponseException.class)
    public ResponseEntity<Map<String, Object>> handleEmptyResponse(EmptyChatResponseException exception) {
        String requestId = UUID.randomUUID().toString();
        log.warn("模型返回空结果，requestId={}，原因={}", requestId, exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "success", false,
                "error", "模型没有返回可用结果，请稍后重试",
                "requestId", requestId
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpectedException(Exception exception) {
        String requestId = UUID.randomUUID().toString();
        log.error("AI 请求处理失败，requestId={}，异常类型={}", requestId,
                exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "success", false,
                "error", "AI 服务暂时不可用，请稍后重试",
                "requestId", requestId
        ));
    }

}
