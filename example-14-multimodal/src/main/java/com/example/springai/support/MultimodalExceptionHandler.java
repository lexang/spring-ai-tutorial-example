package com.example.springai.support;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class MultimodalExceptionHandler {

    @ExceptionHandler(FeatureUnavailableException.class)
    public ResponseEntity<ApiError> handleFeatureUnavailable(FeatureUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new ApiError(HttpStatus.SERVICE_UNAVAILABLE.value(), exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleInvalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
            .body(new ApiError(HttpStatus.BAD_REQUEST.value(), exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler(MultimodalOperationException.class)
    public ResponseEntity<ApiError> handleModelOperation(MultimodalOperationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(new ApiError(HttpStatus.BAD_GATEWAY.value(), exception.getMessage(), Instant.now()));
    }

    public record ApiError(int status, String message, Instant timestamp) {
    }

}
