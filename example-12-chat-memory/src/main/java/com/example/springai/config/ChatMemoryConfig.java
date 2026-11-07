package com.example.springai.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 为同一套接口提供内存和 JDBC 两种 ChatMemory。
 */
@Configuration(proxyBeanMethods = false)
public class ChatMemoryConfig {

    public static final int IN_MEMORY_MAX_MESSAGES = 6;

    public static final int JDBC_MAX_MESSAGES = 20;

    @Bean("inMemoryChatMemory")
    public ChatMemory inMemoryChatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(IN_MEMORY_MAX_MESSAGES)
                .build();
    }

    @Bean("jdbcChatMemory")
    @Primary
    public ChatMemory jdbcChatMemory(JdbcChatMemoryRepository jdbcChatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(jdbcChatMemoryRepository)
                .maxMessages(JDBC_MAX_MESSAGES)
                .build();
    }
}
