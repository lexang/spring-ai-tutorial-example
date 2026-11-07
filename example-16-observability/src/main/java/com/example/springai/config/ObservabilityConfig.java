package com.example.springai.config;

import java.util.List;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientAttributes;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.springai.advisor.RequestContextAdvisor;
import com.example.springai.model.DeterministicEmbeddingModel;
import com.example.springai.model.OfflineChatModel;

/**
 * 把所有 Spring AI 组件连接到同一个观察注册表，并准备离线演示数据。
 */
@Configuration
public class ObservabilityConfig {

    @Bean
    ChatModel offlineChatModel(ObservationRegistry observationRegistry) {
        return new OfflineChatModel(observationRegistry);
    }

    @Bean
    EmbeddingModel deterministicEmbeddingModel() {
        return new DeterministicEmbeddingModel();
    }

    @Bean
    SimpleVectorStore vectorStore(EmbeddingModel embeddingModel,
            ObservationRegistry observationRegistry) {
        return SimpleVectorStore.builder(embeddingModel)
            .observationRegistry(observationRegistry)
            .customObservationConvention(new PrivacyVectorObservationConvention())
            .build();
    }

    @Bean
    ChatClient chatClient(ChatModel chatModel, ObservationRegistry observationRegistry) {
        return ChatClient.builder(chatModel, observationRegistry, null, null)
            .defaultAdvisors(advisors -> {
                advisors.advisors(new RequestContextAdvisor());
                // 本示例没有工具，关闭 2.0 默认自动注册的 ToolCallingAdvisor，
                // 让观察结果只反映本篇实际演示的 Advisor。
                advisors.param(ChatClientAttributes.TOOL_CALLING_ADVISOR_AUTO_REGISTER.getKey(), false);
            })
            .build();
    }

    @Bean
    DemoDataInitializer demoDataInitializer(SimpleVectorStore vectorStore) {
        return new DemoDataInitializer(vectorStore);
    }

    /** 启动时写入少量确定性文档，确保无需数据库也能观察 add/query。 */
    static final class DemoDataInitializer {

        private final SimpleVectorStore vectorStore;

        DemoDataInitializer(SimpleVectorStore vectorStore) {
            this.vectorStore = vectorStore;
            this.vectorStore.add(List.of(
                Document.builder().id("spring-ai").text("Spring AI 把模型能力统一为 ChatModel、EmbeddingModel 和 VectorStore。")
                    .metadata("topic", "spring-ai").build(),
                Document.builder().id("observability").text("可观测性通过 Micrometer Observation 记录指标、日志和分布式追踪。")
                    .metadata("topic", "observability").build(),
                Document.builder().id("security").text("提示词、模型响应和向量检索内容可能包含敏感信息，默认不导出正文。")
                    .metadata("topic", "security").build()));
        }

    }

}
