package com.example.springai.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * 数据初始化配置
 *
 * 应用启动时自动加载示例文档到向量数据库
 */
@Configuration
public class DataInitializer {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    /**
     * 加载示例文档
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "app.rag",
            name = "initialize-sample-data",
            havingValue = "true",
            matchIfMissing = true)
    public CommandLineRunner loadDocuments(VectorStore vectorStore) {
        return args -> {
            List<Document> documents = List.of(
                // Spring AI 文档
                new Document(
                    "Spring AI 是一个用于构建 AI 应用的 Java 框架。" +
                    "它提供了 ChatClient API、向量数据库集成、RAG 支持等功能。" +
                    "支持 OpenAI、Azure OpenAI、Anthropic、Ollama 等多种模型。",
                    Map.of(
                        "product", "Spring AI",
                        "version", "2.0.2",
                        "category", "介绍",
                        "type", "documentation",
                        "source", "tutorial-demo",
                        "language", "zh-CN"
                    )
                ),
                new Document(
                    "Spring AI 的 ChatClient 提供了流式 API，支持同步和异步调用。" +
                    "可以配置系统提示、温度、最大 token 数等参数。" +
                    "ChatClient 采用 Builder 模式，支持链式调用。",
                    Map.of(
                        "product", "Spring AI",
                        "version", "2.0.2",
                        "category", "API",
                        "type", "documentation",
                        "source", "tutorial-demo",
                        "language", "zh-CN"
                    )
                ),
                new Document(
                    "Spring AI 支持 RAG (检索增强生成)，通过 QuestionAnswerAdvisor " +
                    "可以轻松实现基于文档的问答。支持元数据过滤和自定义模板。" +
                    "RAG 是解决 LLM 知识时效性和专业知识缺失问题的重要技术。",
                    Map.of(
                        "product", "Spring AI",
                        "version", "2.0.2",
                        "category", "RAG",
                        "type", "documentation",
                        "source", "tutorial-demo",
                        "language", "zh-CN"
                    )
                ),
                new Document(
                    "Spring AI 提供了 VectorStore 接口，支持多种向量数据库，" +
                    "包括 PGVector、Chroma、Pinecone、Weaviate 等。" +
                    "VectorStore 用于存储和检索文本嵌入向量。",
                    Map.of(
                        "product", "Spring AI",
                        "version", "2.0.2",
                        "category", "VectorStore",
                        "type", "documentation",
                        "source", "tutorial-demo",
                        "language", "zh-CN"
                    )
                ),

                // Spring Boot 文档
                new Document(
                    "本教学项目使用 Spring Boot 4.1.2 和 Java 17 编译目标。" +
                    "它简化了 Spring 应用的创建和配置，提供了自动配置功能。" +
                    "Spring Boot 遵循约定优于配置的原则。",
                    Map.of(
                        "product", "Spring Boot",
                        "version", "4.1.2",
                        "category", "入门",
                        "type", "documentation",
                        "source", "tutorial-demo",
                        "language", "zh-CN"
                    )
                ),
                new Document(
                    "Spring Boot Actuator 提供了生产就绪的功能，" +
                    "包括健康检查、指标收集、HTTP 跟踪等。" +
                    "可以通过 /actuator 端点访问这些功能。",
                    Map.of(
                        "product", "Spring Boot",
                        "version", "4.1.2",
                        "category", "监控",
                        "type", "documentation",
                        "source", "tutorial-demo",
                        "language", "zh-CN"
                    )
                ),

                // 英文文档 (用于多语言测试)
                new Document(
                    "Spring AI is a Java framework for building AI applications. " +
                    "It provides ChatClient API, vector database integration, and RAG support. " +
                    "Supports OpenAI, Azure OpenAI, Anthropic, Ollama and other models.",
                    Map.of(
                        "product", "Spring AI",
                        "version", "2.0.2",
                        "category", "introduction",
                        "type", "documentation",
                        "source", "tutorial-demo",
                        "language", "en"
                    )
                )
            );

            vectorStore.add(documents);
            logger.info("已加载 {} 个 RAG 示例文档", documents.size());
        };
    }
}
