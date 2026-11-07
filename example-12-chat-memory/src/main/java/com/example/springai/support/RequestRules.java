package com.example.springai.support;

import java.util.regex.Pattern;

/**
 * HTTP 接口与服务层共用的输入边界。
 */
public final class RequestRules {

    private static final Pattern CONVERSATION_ID_PATTERN =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,35}$");

    private static final int MAX_MESSAGE_LENGTH = 8_000;

    private RequestRules() {
    }

    public static String conversationId(String value) {
        if (value == null || !CONVERSATION_ID_PATTERN.matcher(value).matches()) {
            throw new InvalidRequestException(
                    "conversationId 必须为 1 到 36 位，只能包含英文字母、数字、点、下划线、冒号或连字符");
        }
        return value;
    }

    public static String message(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException("消息内容不能为空");
        }
        if (value.length() > MAX_MESSAGE_LENGTH) {
            throw new InvalidRequestException("消息内容不能超过 " + MAX_MESSAGE_LENGTH + " 个字符");
        }
        return value;
    }

    public static int topK(Integer value, int defaultValue) {
        int topK = value == null ? defaultValue : value;
        if (topK < 1 || topK > 100) {
            throw new InvalidRequestException("topK 必须在 1 到 100 之间");
        }
        return topK;
    }
}
