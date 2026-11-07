package com.example.springai.support;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class KnowledgeExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> invalid(IllegalArgumentException e) { return error(HttpStatus.BAD_REQUEST, e.getMessage()); }
    @ExceptionHandler(KnowledgeNotFoundException.class)
    public ResponseEntity<ApiError> notFound(KnowledgeNotFoundException e) { return error(HttpStatus.NOT_FOUND, e.getMessage()); }
    @ExceptionHandler(KnowledgeConflictException.class)
    public ResponseEntity<ApiError> conflict(KnowledgeConflictException e) { return error(HttpStatus.CONFLICT, e.getMessage()); }
    @ExceptionHandler(KnowledgeOperationException.class)
    public ResponseEntity<ApiError> operation(KnowledgeOperationException e) { return error(HttpStatus.BAD_GATEWAY, e.getMessage()); }
    private ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), message, Instant.now()));
    }
    public record ApiError(int status, String message, Instant timestamp) { }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> malformed(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(new ApiError(400, "请求 JSON 格式不正确", Instant.now()));
    }
}
