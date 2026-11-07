package com.example.springai;

import java.util.ArrayList;
import java.util.List;

import org.springframework.util.StringUtils;

/**
 * 对示例项目的请求做轻量、可复用的边界校验。
 */
final class TemplateRequestValidation {

    private TemplateRequestValidation() {
    }

    static String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new InvalidTemplateRequestException(fieldName + "不能为空");
        }
        return value;
    }

    static String optionalText(String value) {
        return value == null ? "" : value;
    }

    static int requireRange(int value, String fieldName, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new InvalidTemplateRequestException(
                    fieldName + "必须在 " + minimum + " 到 " + maximum + " 之间");
        }
        return value;
    }

    static List<String> optionalTextList(List<String> values, String fieldName) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> copy = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            String value = values.get(index);
            if (!StringUtils.hasText(value)) {
                throw new InvalidTemplateRequestException(
                        fieldName + "第 " + (index + 1) + " 项不能为空");
            }
            copy.add(value);
        }
        return List.copyOf(copy);
    }

}
