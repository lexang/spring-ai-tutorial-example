package com.example.springai.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 向量存储配置
 *
 * 配置用于存储文档向量的 VectorStore
 * 这里使用 SimpleVectorStore（内存存储）作为示例
 */
@Configuration
public class VectorStoreConfig {

    /**
     * 创建简单的内存向量存储
     *
     * @param embeddingModel 当前启用的 EmbeddingModel
     * @return VectorStore 实例
     */
    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.model", name = "embedding", havingValue = "transformers")
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        // SimpleVectorStore 是一个简单的内存向量存储
        // 适合开发和测试，生产环境建议使用 PgVector、Chroma 等
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
