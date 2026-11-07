package com.example.springai.support;

import java.time.Instant;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AnalyticsExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ApiError(400, exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler(AnalyticsException.class)
    public ResponseEntity<ApiError> model(AnalyticsException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(new ApiError(502, exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> database(DataAccessException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ApiError(500, "数据分析执行失败", Instant.now()));
    }

    public record ApiError(int status, String message, Instant timestamp) {
    }

}
