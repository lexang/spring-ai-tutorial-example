package com.example.springai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import com.example.springai.inventory.ToolQueryService.InvalidToolRequestException;
import com.example.springai.inventory.ToolQueryService.ToolQueryFailureException;

import java.time.Instant;

/**
 * 示例接口的统一异常处理器。
 *
 * 对外只返回稳定的中文错误信息，日志仅记录异常类型，避免泄漏模型供应商、
 * 文件路径或下游系统细节。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvalidToolRequestException.class)
    public ResponseEntity<ApiError> handleToolInput(InvalidToolRequestException exception) {
        return response(HttpStatus.BAD_REQUEST, "请求参数无效", exception.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException exception) {
        return response(HttpStatus.BAD_REQUEST, "请求格式无效", "请提供合法 JSON 和有效的 mode 枚举值");
    }

    @ExceptionHandler(ToolQueryFailureException.class)
    public ResponseEntity<ApiError> handleToolQuery(ToolQueryFailureException exception) {
        log.warn("库存问答失败，异常类型: {}", exception.getCause().getClass().getSimpleName());
        return response(HttpStatus.BAD_GATEWAY, "库存问答失败", "模型或工具暂时无法完成请求，请检查服务配置");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException exception) {
        log.warn("请求处理异常，类型: {}", exception.getClass().getSimpleName());
        return response(HttpStatus.BAD_REQUEST, "请求参数无效", "请检查请求参数及服务配置");
    }

    @ExceptionHandler(ToolExecutionException.class)
    public ResponseEntity<ApiError> handleToolExecution(ToolExecutionException exception) {
        log.warn("工具执行失败，类型: {}", exception.getClass().getSimpleName());
        return response(HttpStatus.BAD_GATEWAY, "工具执行失败", "工具暂时无法完成请求，请稍后重试");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        log.error("服务处理失败，类型: {}", exception.getClass().getSimpleName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "服务处理失败", "系统暂时无法处理请求，请稍后重试");
    }

    private static ResponseEntity<ApiError> response(HttpStatus status, String error, String message) {
        String safeMessage = message == null || message.isBlank() ? error : message;
        return ResponseEntity.status(status)
                .body(new ApiError(status.value(), error, safeMessage, Instant.now()));
    }

    public record ApiError(int status, String error, String message, Instant timestamp) {
    }
}
