package com.example.springai;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.http.converter.HttpMessageNotReadableException;

/**
 * 将示例接口的输入错误和模型错误转换为稳定的中文响应。
 */
@RestControllerAdvice
public class StructuredOutputExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(StructuredOutputExceptionHandler.class);

    @ExceptionHandler(InvalidStructuredOutputRequestException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRequest(
            InvalidStructuredOutputRequestException exception) {
        return ResponseEntity.badRequest().body(errorBody(exception.getMessage(), null));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, Object>> handleMalformedRequest(Exception exception) {
        String message = "请求参数格式不正确";
        if (exception instanceof MethodArgumentNotValidException validationException
                && validationException.getBindingResult().getFieldError() != null) {
            String defaultMessage = validationException.getBindingResult().getFieldError().getDefaultMessage();
            if (defaultMessage != null && !defaultMessage.isBlank()) {
                message = defaultMessage;
            }
        }
        return ResponseEntity.badRequest().body(errorBody(message, null));
    }

    @ExceptionHandler(InvalidStructuredOutputResponseException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidModelResponse(
            InvalidStructuredOutputResponseException exception) {
        return unavailable(exception, "模型返回的数据不可用，请稍后重试");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpectedException(Exception exception) {
        return unavailable(exception, "AI 服务暂时不可用，请稍后重试");
    }

    private ResponseEntity<Map<String, Object>> unavailable(Exception exception, String message) {
        String requestId = UUID.randomUUID().toString();
        log.error("结构化输出请求失败，requestId={}，异常类型={}", requestId,
                exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorBody(message, requestId));
    }

    private Map<String, Object> errorBody(String message, String requestId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", message);
        if (requestId != null) {
            body.put("requestId", requestId);
        }
        return body;
    }

}
