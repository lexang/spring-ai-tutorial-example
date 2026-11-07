package com.example.springai;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** VectorStore 配置和可选的教学数据初始化。 */
@Configuration
public class VectorStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreConfig.class);

    /**
     * 默认使用 SimpleVectorStore。切换 PGvector 时将 app.vector-store.type 设为 pgvector，
     * 并启用 PGvector starter，由其自动配置 VectorStore。
     */
    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    @ConditionalOnProperty(name = "app.vector-store.type", havingValue = "simple", matchIfMissing = true)
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        log.info("创建 SimpleVectorStore，仅用于教学和测试");
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    /** 启动后写入十条简单标量元数据的示例文档。 */
    @Bean
    @ConditionalOnProperty(name = "app.vector-store.initialize-sample-data",
            havingValue = "true", matchIfMissing = true)
    public ApplicationRunner sampleDataInitializer(VectorStore vectorStore) {
        return arguments -> {
            List<Document> sampleDocuments = List.of(
                    document(
                            "Spring AI 是一个用于构建 AI 应用的 Java 框架，提供统一的模型与向量存储 API。",
                            "Spring AI", "介绍", "入门", 2026),
                    document(
                            "Spring AI 的 ChatClient 提供流畅 API，用于构建提示词并调用聊天模型。",
                            "Spring AI", "功能", "入门", 2026),
                    document(
                            "向量数据库用于保存向量嵌入，并按照向量相似度检索文档。",
                            "向量数据库", "概念", "中级", 2026),
                    document(
                            "PGvector 是 PostgreSQL 扩展，支持精确检索、HNSW 和 IVFFlat 索引。",
                            "向量数据库", "技术", "中级", 2026),
                    document(
                            "Java 是一种面向对象的编程语言，广泛用于企业应用开发。",
                            "编程语言", "介绍", "入门", 2025),
                    document(
                            "Spring Boot 通过自动配置和约定减少 Spring 应用的样板配置。",
                            "Spring Boot", "框架", "入门", 2026),
                    document(
                            "机器学习让系统从数据中学习模式，并用于预测或决策。",
                            "人工智能", "概念", "中级", 2025),
                    document(
                            "自然语言处理关注计算机对人类语言的理解、生成和交互。",
                            "人工智能", "技术", "中级", 2025),
                    document(
                            "RAG 会先检索相关文档，再把上下文与用户问题一起交给聊天模型。",
                            "RAG", "模式", "高级", 2026),
                    document(
                            "向量检索是 RAG 的一个关键环节，检索质量会直接影响最终回答。",
                            "RAG", "技术", "高级", 2026));

            vectorStore.add(sampleDocuments);
            log.info("示例文档初始化完成，数量={}", sampleDocuments.size());
        };
    }

    private Document document(String text, String category, String type, String difficulty, int year) {
        String stableId = UUID.nameUUIDFromBytes(text.getBytes(StandardCharsets.UTF_8)).toString();
        return new Document(stableId, text, Map.of(
                "category", category,
                "type", type,
                "language", "zh-CN",
                "difficulty", difficulty,
                "year", year));
    }
}
