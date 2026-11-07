package com.example.springai;

import org.springframework.util.StringUtils;

/**
 * 对查询参数做统一的边界校验，避免空字符串进入模型调用。
 */
final class StructuredOutputRequestValidation {

    private StructuredOutputRequestValidation() {
    }

    static String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new InvalidStructuredOutputRequestException(fieldName + "不能为空");
        }
        return value.trim();
    }

}
