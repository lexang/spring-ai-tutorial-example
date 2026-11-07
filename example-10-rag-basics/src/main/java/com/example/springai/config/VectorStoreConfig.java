package com.example.springai.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 向量存储配置
 *
 * 使用 SimpleVectorStore (内存向量存储) 用于演示
 */
@Configuration
public class VectorStoreConfig {

    /**
     * 创建 SimpleVectorStore Bean
     * 注意：SimpleVectorStore 是内存存储，应用重启后数据会丢失
     * 生产环境建议使用 PGVector, Chroma 等持久化向量存储
     */
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
