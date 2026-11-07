package com.example.springai;

/**
 * 表示示例接口收到无效的用户输入。
 */
public final class InvalidDemoRequestException extends IllegalArgumentException {

    public InvalidDemoRequestException(String message) {
        super(message);
    }

}
