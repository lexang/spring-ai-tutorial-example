package com.example.springai;

/**
 * 模型没有返回可解析或不满足业务约束的结构化结果时抛出的异常。
 */
public final class InvalidStructuredOutputResponseException extends RuntimeException {

    public InvalidStructuredOutputResponseException(String message) {
        super(message);
    }

}
