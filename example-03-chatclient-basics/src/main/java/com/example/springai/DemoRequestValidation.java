package com.example.springai;

import org.springframework.util.StringUtils;

/**
 * 示例接口的轻量参数校验。
 */
final class DemoRequestValidation {

    private DemoRequestValidation() {
    }

    static String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new InvalidDemoRequestException(fieldName + "不能为空");
        }
        return value;
    }

    static String requireIdentifier(String value, String fieldName) {
        return requireText(value, fieldName).trim();
    }

    static double requireTemperature(Double value) {
        if (value == null || !Double.isFinite(value) || value < 0.0 || value > 2.0) {
            throw new InvalidDemoRequestException("temperature 必须是 0 到 2 之间的有限数值");
        }
        return value;
    }

    static int requirePositiveTokenLimit(Integer value) {
        if (value == null || value <= 0) {
            throw new InvalidDemoRequestException("maxTokens 必须是正整数；模型允许的上限请以供应商文档为准");
        }
        return value;
    }

}
