package com.example.springai.support;

/**
 * Advisor Pipeline 或底层模型调用失败。
 */
public class AdvisorOperationException extends RuntimeException {

    public AdvisorOperationException(String message) {
        super(message);
    }

    public AdvisorOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
