package com.example.springai.service;

import com.example.springai.support.RequestRules;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 不依赖大模型的 ChatMemory 管理服务。
 */
@Service
public class MemoryManagementService {

    private final ChatMemory inMemoryChatMemory;

    private final ChatMemory jdbcChatMemory;

    public MemoryManagementService(
            @Qualifier("inMemoryChatMemory") ChatMemory inMemoryChatMemory,
            @Qualifier("jdbcChatMemory") ChatMemory jdbcChatMemory) {
        this.inMemoryChatMemory = inMemoryChatMemory;
        this.jdbcChatMemory = jdbcChatMemory;
    }

    public MemorySnapshot addMessage(
            String storageValue, String conversationIdValue, String roleValue, String textValue) {
        MemoryStorage storage = MemoryStorage.parse(storageValue);
        String conversationId = RequestRules.conversationId(conversationIdValue);
        String text = RequestRules.message(textValue);
        MemoryRole role = MemoryRole.parse(roleValue);

        memory(storage).add(conversationId, toMessage(role, text));
        return snapshot(storage, conversationId);
    }

    public MemorySnapshot snapshot(String storageValue, String conversationIdValue) {
        MemoryStorage storage = MemoryStorage.parse(storageValue);
        String conversationId = RequestRules.conversationId(conversationIdValue);
        return snapshot(storage, conversationId);
    }

    public MemorySnapshot clear(String storageValue, String conversationIdValue) {
        MemoryStorage storage = MemoryStorage.parse(storageValue);
        String conversationId = RequestRules.conversationId(conversationIdValue);
        memory(storage).clear(conversationId);
        return snapshot(storage, conversationId);
    }

    public MemorySnapshot snapshot(MemoryStorage storage, String conversationId) {
        List<Message> messages = memory(storage).get(conversationId);
        List<MemoryMessageView> views = IntStream.range(0, messages.size())
                .mapToObj(index -> MemoryMessageView.from(index, messages.get(index)))
                .toList();
        return new MemorySnapshot(
                conversationId,
                storage.pathValue(),
                storage.displayName(),
                storage.maxMessages(),
                views.size(),
                views);
    }

    ChatMemory memory(MemoryStorage storage) {
        return switch (storage) {
            case IN_MEMORY -> this.inMemoryChatMemory;
            case JDBC -> this.jdbcChatMemory;
        };
    }

    private Message toMessage(MemoryRole role, String text) {
        return switch (role) {
            case SYSTEM -> SystemMessage.builder().text(text).build();
            case USER -> UserMessage.builder().text(text).build();
            case ASSISTANT -> AssistantMessage.builder().content(text).build();
        };
    }

    public record MemorySnapshot(
            String conversationId,
            String storage,
            String storageName,
            int maxMessages,
            int messageCount,
            List<MemoryMessageView> messages) {
    }

    public record MemoryMessageView(
            int index,
            String type,
            String text,
            Instant createdAt) {

        private static MemoryMessageView from(int index, Message message) {
            Object timestamp = message.getMetadata().get(JdbcChatMemoryRepository.CONVERSATION_TS);
            Instant createdAt = timestamp instanceof Instant instant ? instant : null;
            return new MemoryMessageView(
                    index,
                    message.getMessageType().name(),
                    message.getText(),
                    createdAt);
        }
    }
}
