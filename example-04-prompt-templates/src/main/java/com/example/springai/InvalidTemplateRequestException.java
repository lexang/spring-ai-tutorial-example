package com.example.springai;

/**
 * 表示示例接口收到的参数不满足业务约束。
 */
final class InvalidTemplateRequestException extends IllegalArgumentException {

    InvalidTemplateRequestException(String message) {
        super(message);
    }

}
