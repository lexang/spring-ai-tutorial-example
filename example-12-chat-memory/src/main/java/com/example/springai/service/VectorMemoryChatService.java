package com.example.springai.service;

import com.example.springai.support.ChatOperationException;
import com.example.springai.support.FeatureUnavailableException;
import com.example.springai.support.RequestRules;

import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.VectorStoreChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 可选的向量语义记忆服务。
 */
@Service
public class VectorMemoryChatService {

    private static final Logger log = LoggerFactory.getLogger(VectorMemoryChatService.class);

    private static final int DEFAULT_TOP_K = 5;

    private static final String CONVERSATION_ID_METADATA = "conversationId";

    private final boolean chatModelAvailable;

    private final boolean embeddingModelAvailable;

    private final Optional<ChatClient> chatClient;

    private final Optional<VectorStore> vectorStore;

    public VectorMemoryChatService(
            ObjectProvider<ChatModel> chatModelProvider,
            ObjectProvider<EmbeddingModel> embeddingModelProvider) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        this.chatModelAvailable = chatModel != null;
        this.embeddingModelAvailable = embeddingModel != null;

        if (chatModel == null || embeddingModel == null) {
            this.chatClient = Optional.empty();
            this.vectorStore = Optional.empty();
            return;
        }

        VectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        ChatClient client = ChatClient.builder(chatModel)
                .defaultSystem("你是一个使用长期对话记忆的助手，请使用中文回答。")
                .defaultAdvisors(VectorStoreChatMemoryAdvisor.builder(store)
                        .defaultTopK(DEFAULT_TOP_K)
                        .build())
                .build();
        this.vectorStore = Optional.of(store);
        this.chatClient = Optional.of(client);
    }

    public VectorStatus status() {
        boolean enabled = this.chatClient.isPresent();
        String hint;
        if (enabled) {
            hint = "ChatModel 与 EmbeddingModel 均已启用，可以使用向量语义记忆";
        }
        else if (!this.chatModelAvailable && !this.embeddingModelAvailable) {
            hint = "请同时设置 SPRING_AI_CHAT_MODEL=anthropic、MINIMAX_API_KEY 和 SPRING_AI_EMBEDDING_MODEL=transformers";
        }
        else if (!this.chatModelAvailable) {
            hint = "EmbeddingModel 已启用，但仍需设置 SPRING_AI_CHAT_MODEL=anthropic 和 MINIMAX_API_KEY";
        }
        else {
            hint = "ChatModel 已启用，但仍需设置 SPRING_AI_EMBEDDING_MODEL=transformers";
        }
        return new VectorStatus(
                enabled,
                this.chatModelAvailable,
                this.embeddingModelAvailable,
                DEFAULT_TOP_K,
                hint);
    }

    public VectorReply chat(
            String conversationIdValue, String messageValue, Integer requestedTopK) {
        String conversationId = RequestRules.conversationId(conversationIdValue);
        String message = RequestRules.message(messageValue);
        int topK = RequestRules.topK(requestedTopK, DEFAULT_TOP_K);
        ChatClient client = requireChatClient();

        try {
            log.info("调用向量记忆模型，会话={}，topK={}，消息长度={}",
                    conversationId, topK, message.length());
            String response = client.prompt()
                    .user(message)
                    .advisors(advisor -> {
                        advisor.param(ChatMemory.CONVERSATION_ID, conversationId);
                        advisor.param(VectorStoreChatMemoryAdvisor.TOP_K, topK);
                    })
                    .call()
                    .content();
            return new VectorReply(
                    conversationId,
                    topK,
                    Objects.requireNonNullElse(response, ""));
        }
        catch (RuntimeException exception) {
            throw new ChatOperationException(
                    "向量记忆调用失败，请检查模型配置、ONNX 资源和网络连接", exception);
        }
    }

    public ClearVectorMemoryReply clear(String conversationIdValue) {
        String conversationId = RequestRules.conversationId(conversationIdValue);
        VectorStore store = requireVectorStore();
        try {
            var filter = new FilterExpressionBuilder()
                    .eq(CONVERSATION_ID_METADATA, conversationId)
                    .build();
            store.delete(filter);
            return new ClearVectorMemoryReply(conversationId, true);
        }
        catch (RuntimeException exception) {
            throw new ChatOperationException("清除向量记忆失败，请检查 VectorStore 状态", exception);
        }
    }

    private ChatClient requireChatClient() {
        return this.chatClient.orElseThrow(() -> new FeatureUnavailableException(
                "向量记忆未启用，请同时配置 ChatModel 和 Transformers EmbeddingModel"));
    }

    private VectorStore requireVectorStore() {
        return this.vectorStore.orElseThrow(() -> new FeatureUnavailableException(
                "向量记忆未启用，请同时配置 ChatModel 和 Transformers EmbeddingModel"));
    }

    public record VectorStatus(
            boolean enabled,
            boolean chatModelAvailable,
            boolean embeddingModelAvailable,
            int defaultTopK,
            String hint) {
    }

    public record VectorReply(String conversationId, int topK, String response) {
    }

    public record ClearVectorMemoryReply(String conversationId, boolean cleared) {
    }
}
