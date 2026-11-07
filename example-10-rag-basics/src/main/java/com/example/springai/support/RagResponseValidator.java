package com.example.springai.support;

import org.springframework.util.StringUtils;

/**
 * 统一校验 ChatClient 的可空文本响应。
 */
public final class RagResponseValidator {

    private RagResponseValidator() {
    }

    public static String requireContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new RagUpstreamException("模型未返回可用文本，请稍后重试");
        }
        return content;
    }
}
