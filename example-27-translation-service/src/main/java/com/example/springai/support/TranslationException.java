package com.example.springai.support;

/**
 * 翻译服务在调用模型或处理结果时发生的领域异常，映射为 HTTP 502。
 */
public class TranslationException extends RuntimeException {

    public TranslationException(String message, Throwable cause) {
        super(message, cause);
    }

    public TranslationException(String message) {
        super(message);
    }

}
