package com.example.springai;

import java.util.LinkedHashMap;
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
 * 将示例接口异常转换为稳定的中文响应，不向客户端透传供应商错误详情。
 */
@RestControllerAdvice
public class TemplateExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(TemplateExceptionHandler.class);

    @ExceptionHandler(InvalidTemplateRequestException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRequest(InvalidTemplateRequestException exception) {
        return ResponseEntity.badRequest().body(errorBody(false, exception.getMessage(), null));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String, Object>> handleMalformedRequest(Exception exception) {
        return ResponseEntity.badRequest().body(errorBody(false, "请求参数格式不正确", null));
    }

    @ExceptionHandler(EmptyTemplateResponseException.class)
    public ResponseEntity<Map<String, Object>> handleEmptyResponse(EmptyTemplateResponseException exception) {
        return unavailable(exception, "模型没有返回可用结果，请稍后重试");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpectedException(Exception exception) {
        return unavailable(exception, "AI 服务暂时不可用，请稍后重试");
    }

    private ResponseEntity<Map<String, Object>> unavailable(Exception exception, String message) {
        String requestId = UUID.randomUUID().toString();
        log.error("Prompt 模板请求失败，requestId={}，异常类型={}", requestId,
                exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(errorBody(false, message, requestId));
    }

    private Map<String, Object> errorBody(boolean success, String message, String requestId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", success);
        body.put("error", message);
        if (requestId != null) {
            body.put("requestId", requestId);
        }
        return body;
    }

}
