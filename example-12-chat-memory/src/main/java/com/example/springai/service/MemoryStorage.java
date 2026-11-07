package com.example.springai.service;

import com.example.springai.config.ChatMemoryConfig;
import com.example.springai.support.InvalidRequestException;

import java.util.Locale;

/**
 * 示例暴露的两种窗口记忆存储。
 */
public enum MemoryStorage {

    IN_MEMORY("in-memory", "InMemory", ChatMemoryConfig.IN_MEMORY_MAX_MESSAGES),
    JDBC("jdbc", "JDBC/H2", ChatMemoryConfig.JDBC_MAX_MESSAGES);

    private final String pathValue;

    private final String displayName;

    private final int maxMessages;

    MemoryStorage(String pathValue, String displayName, int maxMessages) {
        this.pathValue = pathValue;
        this.displayName = displayName;
        this.maxMessages = maxMessages;
    }

    public String pathValue() {
        return this.pathValue;
    }

    public String displayName() {
        return this.displayName;
    }

    public int maxMessages() {
        return this.maxMessages;
    }

    public static MemoryStorage parse(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "in-memory", "inmemory" -> IN_MEMORY;
            case "jdbc" -> JDBC;
            default -> throw new InvalidRequestException("storage 只支持 in-memory 或 jdbc");
        };
    }
}
