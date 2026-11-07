package com.example.springai.support;

import org.springframework.util.StringUtils;

/**
 * RAG 接口输入校验。
 */
public final class RagInputValidator {

    private RagInputValidator() {
    }

    public static String requireQuestion(String question) {
        if (!StringUtils.hasText(question)) {
            throw new RagRequestException("问题不能为空");
        }
        return question.trim();
    }
}
