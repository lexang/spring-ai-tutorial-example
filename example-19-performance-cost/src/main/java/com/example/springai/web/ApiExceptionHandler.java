package com.example.springai.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.springai.service.PerformanceService;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getDefaultMessage())
            .orElse("请求参数无效");
        return response(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        PerformanceService.BudgetExceededException.class
    })
    public ResponseEntity<Map<String, Object>> badRequest(RuntimeException exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> malformedRequest(HttpMessageNotReadableException exception) {
        return response(HttpStatus.BAD_REQUEST, "请求 JSON 格式不正确");
    }

    @ExceptionHandler(PerformanceService.ModelOperationException.class)
    public ResponseEntity<Map<String, Object>> modelFailure(PerformanceService.ModelOperationException exception) {
        return response(HttpStatus.BAD_GATEWAY, "AI 服务未返回可用结果，请稍后重试");
    }

    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("message", message == null ? "请求失败" : message);
        return ResponseEntity.status(status).body(body);
    }

}
