package com.example.springai.support;

/**
 * 测试生成器在调用模型或处理结果时发生的领域异常，映射为 HTTP 502。
 */
public class TestGenException extends RuntimeException {

    public TestGenException(String message, Throwable cause) {
        super(message, cause);
    }

    public TestGenException(String message) {
        super(message);
    }

}
