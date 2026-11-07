package com.example.springai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModularRagConfig {

    @Bean
    public ChatClient modularRagClient(ChatClient.Builder builder, VectorStore vectorStore) {
        RetrievalAugmentationAdvisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever(vectorStore))
                .build();

        return builder.clone()
                .defaultAdvisors(ragAdvisor)
                .build();
    }

    @Bean
    public ChatClient allowEmptyContextRagClient(ChatClient.Builder builder, VectorStore vectorStore) {
        RetrievalAugmentationAdvisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever(vectorStore))
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .allowEmptyContext(true)
                        .build())
                .build();

        return builder.clone()
                .defaultAdvisors(ragAdvisor)
                .build();
    }

    private VectorStoreDocumentRetriever documentRetriever(VectorStore vectorStore) {
        return VectorStoreDocumentRetriever.builder()
                .similarityThreshold(0.50)
                .topK(5)
                .vectorStore(vectorStore)
                .build();
    }
}
