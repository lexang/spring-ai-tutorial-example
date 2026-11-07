package com.example.springai.service;

import com.example.springai.support.InvalidRequestException;

import java.util.Locale;

/**
 * 离线写入接口支持的消息角色。
 */
public enum MemoryRole {
    SYSTEM,
    USER,
    ASSISTANT;

    public static MemoryRole parse(String value) {
        if (value == null) {
            throw new InvalidRequestException("role 不能为空，只支持 system、user 或 assistant");
        }
        try {
            return MemoryRole.valueOf(value.toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("role 只支持 system、user 或 assistant");
        }
    }
}
