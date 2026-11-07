package com.example.springai;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.http.converter.HttpMessageNotReadableException;

/** 将输入错误和模型供应商错误转换为稳定的中文 HTTP 响应。 */
@RestControllerAdvice
public class MultiModelExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(MultiModelExceptionHandler.class);

    @ExceptionHandler({ IllegalArgumentException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, HttpMessageNotReadableException.class })
    public ResponseEntity<ApiError> handleBadRequest(Exception exception) {
        String message = exception instanceof IllegalArgumentException && exception.getMessage() != null
                ? exception.getMessage() : "请求参数格式不正确";
        return ResponseEntity.badRequest().body(new ApiError(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleModelFailure(Exception exception) {
        String requestId = UUID.randomUUID().toString();
        log.error("多模型请求失败，requestId={}，异常类型={}", requestId, exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError("AI 服务暂时不可用，请稍后重试（requestId=" + requestId + "）"));
    }

}
