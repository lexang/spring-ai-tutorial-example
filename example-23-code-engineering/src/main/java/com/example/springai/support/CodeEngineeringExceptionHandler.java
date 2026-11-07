package com.example.springai.support;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CodeEngineeringExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ApiError(400, exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler(CodeEngineeringException.class)
    public ResponseEntity<ApiError> operation(CodeEngineeringException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(new ApiError(502, exception.getMessage(), Instant.now()));
    }

    public record ApiError(int status, String message, Instant timestamp) {
    }

}
