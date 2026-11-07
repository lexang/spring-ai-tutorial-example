package com.example.springai.config;

import com.example.springai.model.DeterministicEmbeddingModel;
import com.example.springai.model.OfflineKnowledgeChatModel;
import com.example.springai.service.PersonalKnowledgeService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KnowledgeConfig {

    @Bean
    @ConditionalOnProperty(name = "app.ai.mode", havingValue = "offline", matchIfMissing = true)
    public EmbeddingModel deterministicEmbeddingModel() {
        return new DeterministicEmbeddingModel();
    }

    @Bean
    @ConditionalOnProperty(name = "app.ai.mode", havingValue = "offline", matchIfMissing = true)
    public ChatModel offlineKnowledgeChatModel() {
        return new OfflineKnowledgeChatModel();
    }

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @Bean
    public ApplicationRunner knowledgeIndexInitializer(PersonalKnowledgeService service) {
        return args -> service.reindexAll();
    }

}
