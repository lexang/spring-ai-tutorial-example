package com.example.springai.support;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AdvisorExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMalformedBody(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(new ApiError(400, "请求体格式不正确", Instant.now()));
    }

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

    @ExceptionHandler(AdvisorOperationException.class)
    public ResponseEntity<ApiError> handleAdvisorOperation(AdvisorOperationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(new ApiError(HttpStatus.BAD_GATEWAY.value(), exception.getMessage(), Instant.now()));
    }

    public record ApiError(int status, String message, Instant timestamp) {
    }

}
