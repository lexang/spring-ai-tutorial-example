package com.example.springai.support;

/**
 * 模型或其他上游 AI 组件没有返回可用结果。
 */
public class RagUpstreamException extends RuntimeException {

    public RagUpstreamException(String message) {
        super(message);
    }

    public RagUpstreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
