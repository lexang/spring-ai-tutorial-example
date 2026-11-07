package com.example.springai;

/**
 * 请求参数不满足示例接口约束时抛出的异常。
 */
public final class InvalidStructuredOutputRequestException extends RuntimeException {

    public InvalidStructuredOutputRequestException(String message) {
        super(message);
    }

}
