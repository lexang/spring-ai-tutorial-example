package com.example.springai.support;

/**
 * 模型或向量存储调用失败。
 */
public class ChatOperationException extends RuntimeException {

    public ChatOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
