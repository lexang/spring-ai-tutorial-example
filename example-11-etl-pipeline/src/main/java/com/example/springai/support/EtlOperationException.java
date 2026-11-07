package com.example.springai.support;

/**
 * 表示请求本身合法，但指定文档无法由当前 Reader 或 Writer 完成处理。
 */
public class EtlOperationException extends RuntimeException {

    public EtlOperationException(String message) {
        super(message);
    }

    public EtlOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
