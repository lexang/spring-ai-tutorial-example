package com.example.springai.service;

import com.example.springai.support.ChatOperationException;
import com.example.springai.support.FeatureUnavailableException;
import com.example.springai.support.RequestRules;

import java.util.EnumMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 使用 MessageChatMemoryAdvisor 的可选模型对话服务。
 */
@Service
public class ChatConversationService {

    private static final Logger log = LoggerFactory.getLogger(ChatConversationService.class);

    private final Map<MemoryStorage, ChatClient> chatClients;

    private final MemoryManagementService memoryManagementService;

    public ChatConversationService(
            ObjectProvider<ChatModel> chatModelProvider,
            @Qualifier("inMemoryChatMemory") ChatMemory inMemoryChatMemory,
            @Qualifier("jdbcChatMemory") ChatMemory jdbcChatMemory,
            MemoryManagementService memoryManagementService) {
        this.memoryManagementService = memoryManagementService;

        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            this.chatClients = Map.of();
            return;
        }

        EnumMap<MemoryStorage, ChatClient> clients = new EnumMap<>(MemoryStorage.class);
        clients.put(MemoryStorage.IN_MEMORY, buildChatClient(chatModel, inMemoryChatMemory));
        clients.put(MemoryStorage.JDBC, buildChatClient(chatModel, jdbcChatMemory));
        this.chatClients = Map.copyOf(clients);
    }

    public ModelStatus status() {
        boolean enabled = !this.chatClients.isEmpty();
        String hint = enabled
                ? "ChatModel 已启用，可以调用 MessageChatMemoryAdvisor 对话端点"
                : "设置 SPRING_AI_CHAT_MODEL=anthropic 和 MINIMAX_API_KEY 后重启应用";
        return new ModelStatus(enabled, hint);
    }

    public ChatReply chat(String storageValue, String conversationIdValue, String messageValue) {
        MemoryStorage storage = MemoryStorage.parse(storageValue);
        String conversationId = RequestRules.conversationId(conversationIdValue);
        String message = RequestRules.message(messageValue);
        ChatClient chatClient = this.chatClients.get(storage);
        if (chatClient == null) {
            throw new FeatureUnavailableException(
                    "ChatModel 未启用，请设置 SPRING_AI_CHAT_MODEL=anthropic 和 MINIMAX_API_KEY 后重启应用");
        }

        try {
            log.info("调用模型，存储={}，消息长度={}", storage.pathValue(), message.length());
            String response = chatClient.prompt()
                    .user(message)
                    .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .content();
            if (!StringUtils.hasText(response)) {
                throw new IllegalStateException("模型未返回可展示文本，请检查响应与重试策略");
            }
            return new ChatReply(
                    conversationId,
                    storage.pathValue(),
                    response,
                    this.memoryManagementService.snapshot(storage, conversationId).messageCount());
        }
        catch (RuntimeException exception) {
            throw new ChatOperationException(
                    "模型调用失败，请检查 API Key、模型名称、兼容接口地址和网络连接", exception);
        }
    }

    private ChatClient buildChatClient(ChatModel chatModel, ChatMemory chatMemory) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    public record ModelStatus(boolean enabled, String hint) {
    }

    public record ChatReply(
            String conversationId,
            String storage,
            String response,
            int memoryMessageCount) {
    }
}
