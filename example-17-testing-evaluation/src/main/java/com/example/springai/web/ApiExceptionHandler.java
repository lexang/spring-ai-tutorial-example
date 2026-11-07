package com.example.springai.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 将输入校验错误转换为中文响应。 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(com.example.springai.evaluation.EvaluationService.EvaluationFailureException.class)
    public ResponseEntity<Map<String, Object>> handleJudgeFailure(RuntimeException exception) {
        return response(HttpStatus.BAD_GATEWAY, "评估模型执行失败，请检查配置与请求，不输出下游原文");
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleJson(RuntimeException exception) {
        return response(HttpStatus.BAD_REQUEST, "请求体必须是合法 JSON，并符合接口字段类型");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getDefaultMessage())
            .orElse("请求参数不合法");
        return response(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    private static ResponseEntity<Map<String, Object>> response(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }

}
