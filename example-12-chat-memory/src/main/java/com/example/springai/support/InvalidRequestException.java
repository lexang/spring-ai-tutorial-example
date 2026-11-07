package com.example.springai.support;

/**
 * 请求参数不符合示例约束。
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
