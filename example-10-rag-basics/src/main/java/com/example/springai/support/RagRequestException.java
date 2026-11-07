package com.example.springai.support;

/**
 * 用户请求参数错误。
 */
public class RagRequestException extends IllegalArgumentException {

    public RagRequestException(String message) {
        super(message);
    }
}
